package com.houseofmmminq.macaco.data.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.houseofmmminq.macaco.data.model.TravelEntry
import com.houseofmmminq.macaco.util.ImageStorage
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File as JavaFile

// Drive photo/video downloads used to run one at a time in a plain forEach loop, so a fresh
// install with many un-cached entries took ~a minute to populate the journal list. Bounding
// concurrency (rather than unbounded async) avoids hammering the Drive API with dozens of
// simultaneous requests on large journals.
private const val DRIVE_DOWNLOAD_CONCURRENCY = 6

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

    /**
     * Resets per-account cached state. The "Macaco" Drive folder id and the downloaded-photo cache
     * belong to the previously signed-in account; without clearing them, uploads after an account
     * switch target the old account's folder (which the new account can't access) and fail with a
     * "couldn't back up" error. Call whenever the signed-in user changes.
     */
    fun onAccountChanged() {
        driveFolderId = null
        _cachedPhotoUris.value = emptyMap()
        // Delete cached Drive photo + video files from the previous account. The directories are
        // re-created automatically when downloadMissingPhotos runs for the new account.
        JavaFile(context.cacheDir, "drive_photos").listFiles()?.forEach { it.delete() }
        JavaFile(context.cacheDir, ImageStorage.DRIVE_VIDEOS).listFiles()?.forEach { it.delete() }
        // Show NotConnected immediately if the new account hasn't granted Drive scope yet,
        // so SettingsScreen shows the reconnect prompt rather than a stale Idle state.
        _syncState.value = if (isDriveConnected()) DrivePhotoSyncState.Idle
                           else DrivePhotoSyncState.NotConnected
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
    suspend fun uploadEntryPhotos(entry: TravelEntry): List<String> = withContext(Dispatchers.IO) {
        // Drive auth/network (GoogleAccountCredential.getToken) hard-crashes if run on the main
        // thread, so the whole upload must stay on IO regardless of the calling dispatcher.
        if (!isDriveConnected()) return@withContext entry.driveFileIds
        val drive = getDriveService()
        val folderId = getOrCreateDriveFolder(drive)
        val result = entry.driveFileIds.toMutableList()
        while (result.size < entry.photoUris.size) result.add("")
        entry.photoUris.forEachIndexed { i, uriString ->
            if (result[i].isEmpty()) {
                uploadPhoto(drive, folderId, uriString)?.let { result[i] = it }
            }
        }
        result.toList()
    }

    /**
     * Auto-upload variant for the save-entry path: catches Drive failures and reports them via
     * [errors] (snackbar) instead of throwing, so a Drive outage or disabled API can't crash the
     * app. Returns the updated driveFileIds, or the entry's existing ids if the upload failed.
     */
    suspend fun uploadEntryPhotosOrReport(entry: TravelEntry): List<String> =
        runCatching { uploadEntryPhotos(entry) }
            .onFailure { e ->
                Log.e("DrivePhotoSync", "Auto-upload failed for entry ${entry.id}", e)
                _errors.trySend(friendlyDriveError(e))
            }
            .getOrDefault(entry.driveFileIds)

    // Returns the new Drive file ID, or null if the stream is unavailable. Mirrors [uploadPhoto]
    // but with the video MIME type / extension.
    private fun uploadVideo(drive: Drive, folderId: String, uriString: String): String? {
        val stream = context.contentResolver.openInputStream(Uri.parse(uriString)) ?: return null
        val content = InputStreamContent("video/mp4", stream)
        val meta = File().apply {
            name = "macaco_${System.currentTimeMillis()}.mp4"
            parents = listOf(folderId)
        }
        return drive.files().create(meta, content).setFields("id").execute().id
    }

    /**
     * Uploads any videos in [entry] that don't yet have a Drive file ID.
     * Returns the updated videoFileIds list (same length as videoUris).
     * Throws if Drive is not reachable — callers should catch and handle. Mirrors [uploadEntryPhotos].
     */
    suspend fun uploadEntryVideos(entry: TravelEntry): List<String> = withContext(Dispatchers.IO) {
        if (!isDriveConnected()) return@withContext entry.videoFileIds
        val drive = getDriveService()
        val folderId = getOrCreateDriveFolder(drive)
        val result = entry.videoFileIds.toMutableList()
        while (result.size < entry.videoUris.size) result.add("")
        entry.videoUris.forEachIndexed { i, uriString ->
            if (result[i].isEmpty()) {
                uploadVideo(drive, folderId, uriString)?.let { result[i] = it }
            }
        }
        result.toList()
    }

    /** Auto-upload variant for the save path — reports failures via [errors] instead of throwing. */
    suspend fun uploadEntryVideosOrReport(entry: TravelEntry): List<String> =
        runCatching { uploadEntryVideos(entry) }
            .onFailure { e ->
                Log.e("DrivePhotoSync", "Auto-upload (video) failed for entry ${entry.id}", e)
                _errors.trySend(friendlyDriveError(e))
            }
            .getOrDefault(entry.videoFileIds)

    /**
     * Downloads Drive photos for entries that have Drive IDs but no accessible local file.
     * Saves to cacheDir/drive_photos/ and updates [cachedPhotoUris].
     */
    fun downloadMissingPhotos(entries: List<TravelEntry>) {
        ioScope.launch { downloadMissing(entries) }
    }

    /**
     * Like [downloadMissingPhotos] but suspends until the download pass completes, then returns the
     * up-to-date driveFileId → cache-URI map. Used by the backup export to ensure Drive-only photos
     * are pulled local *before* bundling, so a cold cache can't silently produce a photo-less backup.
     */
    suspend fun ensurePhotosCached(entries: List<TravelEntry>): Map<String, String> =
        withContext(Dispatchers.IO) {
            downloadMissing(entries)
            _cachedPhotoUris.value
        }

    /** Shared download body for [downloadMissingPhotos] (fire-and-forget) and [ensurePhotosCached]
     *  (awaited). Downloads Drive photos that have an ID but no accessible local file into
     *  cacheDir/drive_photos/ and folds them into [_cachedPhotoUris]. */
    private suspend fun downloadMissing(entries: List<TravelEntry>) = withContext(Dispatchers.IO) {
        val needed = entries.flatMap { entry ->
            entry.driveFileIds.filterIndexed { i, id ->
                id.isNotEmpty() &&
                    !_cachedPhotoUris.value.containsKey(id) &&
                    (entry.photoUris.getOrNull(i)?.let { !isUriAccessible(it) } ?: true)
            }
        }.distinct()

        val neededVideos = entries.flatMap { entry ->
            entry.videoFileIds.filterIndexed { i, id ->
                id.isNotEmpty() &&
                    !_cachedPhotoUris.value.containsKey(id) &&
                    (entry.videoUris.getOrNull(i)?.let { !isUriAccessible(it) } ?: true)
            }
        }.distinct()

        if (needed.isEmpty() && neededVideos.isEmpty()) return@withContext

        val drive = runCatching { getDriveService() }.getOrNull() ?: return@withContext
        // ConcurrentHashMap: the download loops below write to this from multiple coroutines
        // (bounded by downloadSemaphore) at once.
        val newEntries = java.util.concurrent.ConcurrentHashMap<String, String>()

        val downloadSemaphore = Semaphore(DRIVE_DOWNLOAD_CONCURRENCY)

        if (needed.isNotEmpty()) {
            val cacheDir = JavaFile(context.cacheDir, "drive_photos").apply { mkdirs() }
            coroutineScope {
                needed.map { fileId ->
                    async {
                        downloadSemaphore.withPermit {
                            val cacheFile = JavaFile(cacheDir, "$fileId.jpg")
                            if (!cacheFile.exists()) {
                                runCatching {
                                    val out = ByteArrayOutputStream()
                                    drive.files().get(fileId).executeMediaAndDownloadTo(out)
                                    cacheFile.writeBytes(out.toByteArray())
                                }
                            }
                            if (cacheFile.exists()) newEntries[fileId] = Uri.fromFile(cacheFile).toString()
                        }
                    }
                }.awaitAll()
            }
        }

        // Videos share the same driveFileId → cache-URI map (keys are distinct Drive IDs, so photos
        // and videos never collide), cached under cacheDir/drive_videos/.
        if (neededVideos.isNotEmpty()) {
            val videoCacheDir = JavaFile(context.cacheDir, ImageStorage.DRIVE_VIDEOS).apply { mkdirs() }
            coroutineScope {
                neededVideos.map { fileId ->
                    async {
                        downloadSemaphore.withPermit {
                            val cacheFile = JavaFile(videoCacheDir, "$fileId.mp4")
                            if (!cacheFile.exists()) {
                                runCatching {
                                    cacheFile.outputStream().use { out ->
                                        drive.files().get(fileId).executeMediaAndDownloadTo(out)
                                    }
                                }.onFailure { cacheFile.delete() }
                            }
                            if (cacheFile.exists()) newEntries[fileId] = Uri.fromFile(cacheFile).toString()
                        }
                    }
                }.awaitAll()
            }
        }

        if (newEntries.isNotEmpty()) {
            _cachedPhotoUris.value = _cachedPhotoUris.value + newEntries
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
            fun TravelEntry.pendingPhotos() =
                photoUris.isNotEmpty() &&
                    (driveFileIds.size < photoUris.size || driveFileIds.any { it.isEmpty() })
            fun TravelEntry.pendingVideos() =
                videoUris.isNotEmpty() &&
                    (videoFileIds.size < videoUris.size || videoFileIds.any { it.isEmpty() })

            val pending = entries.filter { it.pendingPhotos() || it.pendingVideos() }
            if (pending.isEmpty()) {
                _syncState.value = DrivePhotoSyncState.Synced
                downloadMissingPhotos(entries)
                return@launch
            }

            // "Photos" here counts photos + videos — one progress unit per media item.
            val totalPhotos = pending.sumOf { e ->
                (e.photoUris.size - e.driveFileIds.count { it.isNotEmpty() }) +
                    (e.videoUris.size - e.videoFileIds.count { it.isNotEmpty() })
            }.coerceAtLeast(1)
            _syncState.value = DrivePhotoSyncState.Syncing(0, totalPhotos)

            var uploaded = 0
            var failed = 0
            var firstError: String? = null

            pending.forEach { entry ->
                val prevSynced = entry.driveFileIds.count { it.isNotEmpty() } +
                    entry.videoFileIds.count { it.isNotEmpty() }
                val result = runCatching {
                    val photoIds = if (entry.pendingPhotos()) uploadEntryPhotos(entry) else entry.driveFileIds
                    val videoIds = if (entry.pendingVideos()) uploadEntryVideos(entry) else entry.videoFileIds
                    photoIds to videoIds
                }.onFailure { e ->
                    Log.e("DrivePhotoSync", "Upload failed for entry ${entry.id}", e)
                    val msg = friendlyDriveError(e)
                    if (firstError == null) firstError = msg
                    _errors.trySend(msg)
                    failed += (entry.photoUris.size + entry.videoUris.size) - prevSynced
                    return@forEach
                }.getOrThrow()
                val (newIds, newVideoIds) = result

                val nowSynced = newIds.count { it.isNotEmpty() } + newVideoIds.count { it.isNotEmpty() }
                uploaded += (nowSynced - prevSynced).coerceAtLeast(0)
                failed += ((entry.photoUris.size + entry.videoUris.size) - nowSynced).coerceAtLeast(0)

                if (newIds != entry.driveFileIds || newVideoIds != entry.videoFileIds) {
                    runCatching {
                        onEntryUpdated(entry.copy(driveFileIds = newIds, videoFileIds = newVideoIds))
                    }
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
