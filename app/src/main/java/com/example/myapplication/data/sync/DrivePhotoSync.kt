package com.example.myapplication.data.sync

import android.content.Context
import android.net.Uri
import com.example.myapplication.data.model.TravelEntry
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File as JavaFile

sealed class DrivePhotoSyncState {
    object Idle : DrivePhotoSyncState()
    object NotConnected : DrivePhotoSyncState()
    data class Syncing(val done: Int, val total: Int) : DrivePhotoSyncState()
    object Synced : DrivePhotoSyncState()
    data class Error(val message: String) : DrivePhotoSyncState()
}

class DrivePhotoSync(private val context: Context) {

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _syncState = MutableStateFlow<DrivePhotoSyncState>(DrivePhotoSyncState.Idle)
    val syncState: StateFlow<DrivePhotoSyncState> = _syncState.asStateFlow()

    // Maps driveFileId → local cache file URI, populated for photos downloaded from Drive.
    // Composables use this as a fallback when the local photoUri is inaccessible.
    private val _cachedPhotoUris = MutableStateFlow<Map<String, String>>(emptyMap())
    val cachedPhotoUris: StateFlow<Map<String, String>> = _cachedPhotoUris.asStateFlow()

    private var wanderlogFolderId: String? = null

    fun isDriveConnected(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))
    }

    private fun getDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        if (!GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) return null
        val credential = GoogleAccountCredential
            .usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
            .apply { selectedAccount = account.account }
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Wanderlog").build()
    }

    private fun getOrCreateWanderlogFolder(drive: Drive): String {
        wanderlogFolderId?.let { return it }
        val existing = drive.files().list()
            .setQ("name='Wanderlog' and mimeType='application/vnd.google-apps.folder' and trashed=false")
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
        val id = if (existing.files.isNotEmpty()) {
            existing.files[0].id
        } else {
            val meta = File().apply {
                name = "Wanderlog"
                mimeType = "application/vnd.google-apps.folder"
            }
            drive.files().create(meta).setFields("id").execute().id
        }
        return id.also { wanderlogFolderId = it }
    }

    private fun uploadPhoto(drive: Drive, folderId: String, uriString: String): String? =
        runCatching {
            val stream = context.contentResolver.openInputStream(Uri.parse(uriString)) ?: return null
            val content = InputStreamContent("image/jpeg", stream)
            val meta = File().apply {
                name = "wanderlog_${System.currentTimeMillis()}.jpg"
                parents = listOf(folderId)
            }
            drive.files().create(meta, content).setFields("id").execute().id
        }.getOrNull()

    /**
     * Uploads any photos in [entry] that don't yet have a Drive file ID.
     * Returns the updated driveFileIds list (same length as photoUris),
     * or the original list if Drive is not connected or upload fails.
     */
    suspend fun uploadEntryPhotos(entry: TravelEntry): List<String> {
        if (!isDriveConnected()) return entry.driveFileIds
        val drive = getDriveService() ?: return entry.driveFileIds
        return runCatching {
            val folderId = getOrCreateWanderlogFolder(drive)
            val result = entry.driveFileIds.toMutableList()
            while (result.size < entry.photoUris.size) result.add("")
            var changed = false
            entry.photoUris.forEachIndexed { i, uriString ->
                if (result[i].isEmpty()) {
                    uploadPhoto(drive, folderId, uriString)?.let { fileId ->
                        result[i] = fileId
                        changed = true
                    }
                }
            }
            if (changed) result.toList() else entry.driveFileIds
        }.getOrDefault(entry.driveFileIds)
    }

    /**
     * Downloads Drive photos for entries that have Drive IDs but no accessible local file,
     * saving them to cacheDir/drive_photos/. Updates [cachedPhotoUris] when done so composables
     * can recompose with the Drive-backed image.
     */
    fun downloadMissingPhotos(entries: List<TravelEntry>) {
        val needed = entries.flatMap { entry ->
            entry.driveFileIds.filterIndexed { i, id ->
                id.isNotEmpty() &&
                    !_cachedPhotoUris.value.containsKey(id) &&
                    (entry.photoUris.getOrNull(i)?.let { !isUriAccessible(it) } ?: true)
            }
        }.distinct()
        if (needed.isEmpty()) return

        ioScope.launch {
            val drive = getDriveService() ?: return@launch
            val cacheDir = JavaFile(context.cacheDir, "drive_photos").apply { mkdirs() }
            val newEntries = mutableMapOf<String, String>()
            needed.forEach { fileId ->
                val cacheFile = JavaFile(cacheDir, "$fileId.jpg")
                if (!cacheFile.exists()) {
                    runCatching {
                        val out = ByteArrayOutputStream()
                        drive.files().get(fileId).executeMediaAndDownloadTo(out)
                        cacheFile.writeBytes(out.toByteArray())
                    }.onFailure { return@forEach }
                }
                if (cacheFile.exists()) {
                    newEntries[fileId] = Uri.fromFile(cacheFile).toString()
                }
            }
            if (newEntries.isNotEmpty()) {
                _cachedPhotoUris.value = _cachedPhotoUris.value + newEntries
            }
        }
    }

    /**
     * Full sync: uploads pending photos for all entries, then downloads missing ones.
     * Reports progress via [syncState]. [onEntryUpdated] is called for each entry whose
     * driveFileIds changed, so the caller can persist the update to Firestore.
     */
    fun syncAll(entries: List<TravelEntry>, onEntryUpdated: suspend (TravelEntry) -> Unit) {
        if (!isDriveConnected()) {
            _syncState.value = DrivePhotoSyncState.NotConnected
            return
        }
        ioScope.launch {
            val pending = entries.filter { entry ->
                entry.photoUris.isNotEmpty() && (
                    entry.driveFileIds.size < entry.photoUris.size ||
                        entry.driveFileIds.any { it.isEmpty() }
                    )
            }
            if (pending.isEmpty()) {
                _syncState.value = DrivePhotoSyncState.Synced
                downloadMissingPhotos(entries)
                return@launch
            }
            _syncState.value = DrivePhotoSyncState.Syncing(0, pending.size)
            var errors = 0
            pending.forEachIndexed { i, entry ->
                val newIds = uploadEntryPhotos(entry)
                if (newIds != entry.driveFileIds) {
                    runCatching { onEntryUpdated(entry.copy(driveFileIds = newIds)) }
                        .onFailure { errors++ }
                }
                _syncState.value = DrivePhotoSyncState.Syncing(i + 1, pending.size)
            }
            _syncState.value = if (errors == 0) DrivePhotoSyncState.Synced
            else DrivePhotoSyncState.Error("$errors ${if (errors == 1) "photo" else "photos"} couldn't be backed up")
            downloadMissingPhotos(entries)
        }
    }

    private fun isUriAccessible(uriString: String): Boolean = runCatching {
        val uri = Uri.parse(uriString)
        when (uri.scheme) {
            "file" -> uri.path?.let { JavaFile(it).exists() } ?: false
            "content" -> context.contentResolver.openInputStream(uri)?.use { true } ?: false
            else -> false
        }
    }.getOrDefault(false)
}
