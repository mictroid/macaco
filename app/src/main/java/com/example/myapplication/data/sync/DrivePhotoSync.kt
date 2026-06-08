package com.example.myapplication.data.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.myapplication.data.model.TravelEntry
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

    // Buffered error messages for the UI to surface as snackbars.
    private val _errors = Channel<String>(Channel.BUFFERED)
    val errors: Flow<String> = _errors.receiveAsFlow()

    // Maps driveFileId → local cache file URI, populated for photos downloaded from Drive.
    private val _cachedPhotoUris = MutableStateFlow<Map<String, String>>(emptyMap())
    val cachedPhotoUris: StateFlow<Map<String, String>> = _cachedPhotoUris.asStateFlow()

    private var driveFolderId: String? = null

    private companion object {
        const val FOLDER_NAME = "Macaco"
        // Pre-rebrand folder name; migrated to FOLDER_NAME on first sync (see getOrCreateDriveFolder).
        const val LEGACY_FOLDER_NAME = "Wanderlog"
    }

    fun isDriveConnected(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))
    }

    private fun getDriveService(): Drive {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: throw IllegalStateException("No Google account signed in")
        if (!GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE)))
            throw IllegalStateException("Drive permission not granted")
        val credential = GoogleAccountCredential
            .usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
            .apply { selectedAccount = account.account }
        return Drive.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Macaco").build()
    }

    private fun getOrCreateDriveFolder(drive: Drive): String {
        driveFolderId?.let { return it }

        fun findFolder(name: String) = drive.files().list()
            .setQ("name='$name' and mimeType='application/vnd.google-apps.folder' and trashed=false")
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()

        // Prefer the current "Macaco" folder. If it doesn't exist yet but a legacy "Wanderlog"
        // folder (from before the rebrand) does, rename that in place so existing users' photos
        // move with the brand instead of being stranded in an old folder.
        val current = findFolder(FOLDER_NAME)
        val id = when {
            current.files.isNotEmpty() -> current.files[0].id
            else -> {
                val legacy = findFolder(LEGACY_FOLDER_NAME)
                if (legacy.files.isNotEmpty()) {
                    val legacyId = legacy.files[0].id
                    drive.files().update(legacyId, File().apply { name = FOLDER_NAME }).execute()
                    legacyId
                } else {
                    val meta = File().apply {
                        name = FOLDER_NAME
                        mimeType = "application/vnd.google-apps.folder"
                    }
                    drive.files().create(meta).setFields("id").execute().id
                }
            }
        }
        return id.also { driveFolderId = it }
    }

    // Returns the new Drive file ID, or null if the stream is unavailable. Lets other exceptions
    // propagate so callers can count real failures vs missing-stream cases.
    private fun uploadPhoto(drive: Drive, folderId: String, uriString: String): String? {
        val stream = context.contentResolver.openInputStream(Uri.parse(uriString)) ?: return null
        val content = InputStreamContent("image/jpeg", stream)
        val meta = File().apply {
            name = "macaco_${System.currentTimeMillis()}.jpg"
            parents = listOf(folderId)
        }
        return drive.files().create(meta, content).setFields("id").execute().id
    }

    /**
     * Uploads any photos in [entry] that don't yet have a Drive file ID.
     * Returns the updated driveFileIds list (same length as photoUris).
     * Throws if Drive is not reachable — callers should catch and handle.
     */
    suspend fun uploadEntryPhotos(entry: TravelEntry): List<String> {
        if (!isDriveConnected()) return entry.driveFileIds
        val drive = getDriveService()
        val folderId = getOrCreateDriveFolder(drive)
        val result = entry.driveFileIds.toMutableList()
        while (result.size < entry.photoUris.size) result.add("")
        entry.photoUris.forEachIndexed { i, uriString ->
            if (result[i].isEmpty()) {
                uploadPhoto(drive, folderId, uriString)?.let { result[i] = it }
            }
        }
        return result.toList()
    }

    /**
     * Downloads Drive photos for entries that have Drive IDs but no accessible local file.
     * Saves to cacheDir/drive_photos/ and updates [cachedPhotoUris].
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
            val drive = runCatching { getDriveService() }.getOrNull() ?: return@launch
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
     * Now surfaces real upload errors via [syncState] and [errors] instead of silently
     * reporting success when API calls fail.
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

            val totalPhotos = pending.sumOf { e ->
                e.photoUris.size - e.driveFileIds.count { it.isNotEmpty() }
            }.coerceAtLeast(1)
            _syncState.value = DrivePhotoSyncState.Syncing(0, totalPhotos)

            var uploaded = 0
            var failed = 0
            var firstError: String? = null

            pending.forEach { entry ->
                val prevSynced = entry.driveFileIds.count { it.isNotEmpty() }
                val newIds = runCatching { uploadEntryPhotos(entry) }
                    .onFailure { e ->
                        Log.e("DrivePhotoSync", "Upload failed for entry ${entry.id}", e)
                        val msg = friendlyDriveError(e)
                        if (firstError == null) firstError = msg
                        _errors.trySend(msg)
                        failed += entry.photoUris.size - prevSynced
                        return@forEach
                    }
                    .getOrThrow()

                val nowSynced = newIds.count { it.isNotEmpty() }
                uploaded += (nowSynced - prevSynced).coerceAtLeast(0)
                failed += (entry.photoUris.size - nowSynced).coerceAtLeast(0)

                if (newIds != entry.driveFileIds) {
                    runCatching { onEntryUpdated(entry.copy(driveFileIds = newIds)) }
                }
                _syncState.value = DrivePhotoSyncState.Syncing(uploaded, totalPhotos)
            }

            _syncState.value = when {
                failed == 0 -> DrivePhotoSyncState.Synced
                uploaded == 0 -> DrivePhotoSyncState.Error(
                    firstError ?: "Backup failed. Check your Google Drive connection."
                )
                else -> DrivePhotoSyncState.Error("$failed ${if (failed == 1) "photo" else "photos"} couldn't be backed up")
            }
            if (failed == 0) downloadMissingPhotos(entries)
        }
    }

    /** Maps a Drive API exception to a short, user-actionable message. */
    private fun friendlyDriveError(e: Throwable): String {
        val text = e.message ?: ""
        return when {
            text.contains("SERVICE_DISABLED") || text.contains("accessNotConfigured") ->
                "Google Drive isn't enabled for this app yet. The developer needs to turn it on."
            text.contains("403") || text.contains("insufficient") || text.contains("PERMISSION_DENIED") ->
                "Drive access was denied. Try disconnecting and reconnecting your Google account."
            text.contains("401") || text.contains("Unauthorized") || text.contains("invalid_grant") ->
                "Your Google sign-in expired. Disconnect and reconnect to back up photos."
            text.contains("Unable to resolve host") || text.contains("timeout") || text.contains("network") ->
                "No internet connection. Photos will back up when you're back online."
            else -> "Couldn't back up to Google Drive. Please try again."
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
