package com.houseofmmminq.macaco.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.houseofmmminq.macaco.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.houseofmmminq.macaco.data.PreferencesManager
import com.houseofmmminq.macaco.data.auth.AuthRepository
import com.houseofmmminq.macaco.data.billing.BillingManager
import com.houseofmmminq.macaco.data.model.TravelEntry
import com.houseofmmminq.macaco.data.model.UserProfile
import com.houseofmmminq.macaco.data.storage.CloudEntrySync
import com.houseofmmminq.macaco.data.storage.LegacyEntryMigration
import com.houseofmmminq.macaco.data.sync.AdventureReelEncoder
import com.houseofmmminq.macaco.data.sync.ReelPhotoMeta
import com.houseofmmminq.macaco.util.VideoTranscoder
import java.io.File
import com.houseofmmminq.macaco.data.sync.DrivePhotoSync
import com.houseofmmminq.macaco.data.sync.DrivePhotoSyncState
import com.houseofmmminq.macaco.data.sync.JournalBackup
import com.houseofmmminq.macaco.ui.theme.AppTheme
import com.houseofmmminq.macaco.ui.theme.MapTheme
import com.houseofmmminq.macaco.util.ImageStorage
import com.houseofmmminq.macaco.util.ReminderScheduler
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class JournalViewModel(
    private val appContext: Context,
    private val cloudEntrySync: CloudEntrySync,
    private val preferencesManager: PreferencesManager,
    private val authRepository: AuthRepository,
    private val billingManager: BillingManager,
    private val drivePhotoSync: DrivePhotoSync
) : ViewModel() {

    val entries: StateFlow<List<TravelEntry>> = cloudEntrySync.entries

    /** One-shot error messages from Firestore sync and Drive backup — shown as snackbars. */
    val syncErrors: Flow<String> = merge(cloudEntrySync.errors, drivePhotoSync.errors)

    val driveSyncState: StateFlow<DrivePhotoSyncState> = drivePhotoSync.syncState
    val cachedDrivePhotos: StateFlow<Map<String, String>> = drivePhotoSync.cachedPhotoUris

    // Local file backup/restore (premium feature). Stateless helper — built from appContext.
    private val journalBackup = JournalBackup(appContext)

    /** Progress of an in-flight backup import, or null when no import is running.
     *  [current]/[total] are MB while [phase] is DOWNLOADING (total = -1 if size unknown) and
     *  entry counts while RESTORING. */
    data class ImportProgress(
        val phase: JournalBackup.ImportPhase,
        val current: Int,
        val total: Int
    )

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    /** Kind of backup operation currently running, or null when idle. */
    enum class BackupBusy { EXPORT, IMPORT }

    private val _backupBusy = MutableStateFlow<BackupBusy?>(null)
    val backupBusy: StateFlow<BackupBusy?> = _backupBusy.asStateFlow()

    /** Runs the export in viewModelScope so leaving Settings can't cancel it mid-write. */
    fun exportBackupInBackground(
        dest: android.net.Uri,
        compact: Boolean,
        onDone: (Result<JournalBackup.ExportResult>) -> Unit
    ) {
        if (_backupBusy.value != null) return
        _backupBusy.value = BackupBusy.EXPORT
        viewModelScope.launch {
            val result = exportBackup(dest, compact)
            _backupBusy.value = null
            onDone(result)
        }
    }

    /** Runs the import in viewModelScope so leaving Settings can't cancel it mid-restore. */
    fun importBackupInBackground(
        src: android.net.Uri,
        onDone: (Result<Int>) -> Unit
    ) {
        if (_backupBusy.value != null) return
        _backupBusy.value = BackupBusy.IMPORT
        viewModelScope.launch {
            val result = importBackup(src)
            _backupBusy.value = null
            onDone(result)
        }
    }

    // Tags currently filtering the journal list (empty = show all). Lifted here so the entry
    // detail screen can set it (tap a tag → list filtered by that tag).
    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedTags: StateFlow<Set<String>> = _selectedTags.asStateFlow()

    /**
     * Entries after applying the active tag filter — the single source of truth for what the user
     * sees. Used by both the journal list and the entry-detail swipe pager, so swiping in the
     * detail screen stays within the same filtered set (never lands on a filtered-out entry).
     */
    val visibleEntries: StateFlow<List<TravelEntry>> =
        combine(entries, selectedTags) { list, tags ->
            if (tags.isEmpty()) list else list.filter { entry -> entry.tags.any { it in tags } }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // null = loading, false = not purchased, true = purchased (sourced from RevenueCat)
    val isPurchased: StateFlow<Boolean?> = billingManager.isPremium

    val priceLabel: StateFlow<String> = billingManager.priceLabel
    val offerings = billingManager.offerings
    val currentPlanId = billingManager.currentPlanId
    val manageableSubscription = billingManager.manageableSubscription
    val currentBasePlanId = billingManager.currentBasePlanId

    /** State of an Adventure Reel render (premium): a shareable slideshow video from a trip's photos. */
    sealed class ReelState {
        object Idle : ReelState()
        data class Generating(val tripName: String, val progress: Float) : ReelState()
        data class Ready(val tripName: String, val uri: Uri) : ReelState()
        data class Error(val message: String) : ReelState()
    }

    private val _reelState = MutableStateFlow<ReelState>(ReelState.Idle)
    val reelState: StateFlow<ReelState> = _reelState.asStateFlow()

    // Holds the encode coroutine so cancelReel() can actually stop it.
    private var reelEncoderJob: Job? = null

    fun startReel(tripName: String, entries: List<TravelEntry>) {
        reelEncoderJob = viewModelScope.launch(Dispatchers.IO) {
            _reelState.value = ReelState.Generating(tripName, 0f)

            // Build the ordered frame list on the IO thread so video first-frame extraction
            // (MediaMetadataRetriever, via VideoTranscoder) is safe to call here. Video frames are
            // written to temp JPEGs in cacheDir and deleted after the encode finishes.
            val tempFrameFiles = mutableListOf<File>()
            val reelPhotos = entries
                .sortedBy { it.dateMillis }
                .flatMap { entry ->
                    val cache = cachedDrivePhotos.value
                    // Branding overlay: "Location · Mon YYYY" (null when the entry has no location).
                    val dateStr = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
                        .format(java.util.Date(entry.dateMillis))
                    val overlayText =
                        if (entry.location.isNotBlank()) "${entry.location} · $dateStr" else null

                    // Resolve media in display order. Entries with videos carry a mediaOrder
                    // (photos + videos interleaved); legacy/photo-only entries (mediaOrder empty)
                    // fall back to photos-only in photoUris order, unchanged from before.
                    val orderedItems: List<Pair<String, String>> = if (entry.mediaOrder.isNotEmpty()) {
                        entry.mediaOrder.mapNotNull { uri ->
                            when {
                                uri in entry.photoUris -> uri to "photo"
                                uri in entry.videoUris -> uri to "video"
                                else -> null
                            }
                        }
                    } else {
                        val count = maxOf(entry.photoUris.size, entry.driveFileIds.size)
                        (0 until count).mapNotNull { i ->
                            entry.photoUris.getOrNull(i)?.let { it to "photo" }
                        }
                    }

                    orderedItems.mapNotNull { (uri, type) ->
                        val resolvedUri: String? = if (type == "photo") {
                            // Prefer the Drive-cached copy when one exists (it only exists when the
                            // local URI was unreadable, e.g. after a reinstall), else the local URI.
                            val idx = entry.photoUris.indexOf(uri)
                            val driveId = entry.driveFileIds.getOrNull(idx)
                            (driveId?.takeIf { it.isNotEmpty() }?.let { cache[it] } ?: uri)
                                .takeIf { it.isNotBlank() }
                        } else {
                            // Video: extract the first frame → temp JPEG → file:// URI. Resolve the
                            // playable source the same way photos do (Drive-cache fallback).
                            val vIdx = entry.videoUris.indexOf(uri)
                            val vDriveId = entry.videoFileIds.getOrNull(vIdx)
                            val playable = vDriveId?.takeIf { it.isNotEmpty() }?.let { cache[it] } ?: uri
                            val bitmap = VideoTranscoder.getFirstFrame(
                                appContext, android.net.Uri.parse(playable)
                            )
                            if (bitmap == null) null else {
                                val tempFile = File(
                                    appContext.cacheDir, "reel_vf_${entry.id}_${uri.hashCode()}.jpg"
                                )
                                tempFile.outputStream().use { out ->
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                                }
                                bitmap.recycle()
                                tempFrameFiles += tempFile
                                android.net.Uri.fromFile(tempFile).toString()
                            }
                        }
                        resolvedUri?.let { ReelPhotoMeta(uri = it, overlayText = overlayText) }
                    }
                }

            if (reelPhotos.isEmpty()) {
                tempFrameFiles.forEach { it.delete() }
                _reelState.value = ReelState.Error(appContext.getString(R.string.reel_no_photos_error))
                return@launch
            }

            // Plain ViewModel (manual DI) — appContext is injected at construction, not getApplication().
            val result = AdventureReelEncoder(appContext).encode(
                photos = reelPhotos,
                outputName = "reel_${tripName.replace(" ", "_")}.mp4",
                onProgress = { p -> _reelState.value = ReelState.Generating(tripName, p) }
            )
            // Clean up temp video-frame files regardless of the encode outcome.
            tempFrameFiles.forEach { it.delete() }
            _reelState.value = result.fold(
                onSuccess = { uri -> ReelState.Ready(tripName, uri) },
                onFailure = { e ->
                    when {
                        // User cancelled via cancelReel(): encode's runCatching swallowed the
                        // CancellationException. cancelReel already set Idle — keep it, and
                        // never surface cancellation as an error.
                        e is kotlinx.coroutines.CancellationException -> ReelState.Idle
                        // Our own guard messages (e.g. no-photos) are already user-facing.
                        e is IllegalStateException && !e.message.isNullOrBlank() ->
                            ReelState.Error(e.message!!)
                        // Anything else: friendly localized copy, never raw exception text.
                        else -> ReelState.Error(appContext.getString(R.string.reel_error_generic))
                    }
                }
            )
        }
    }

    fun cancelReel() {
        reelEncoderJob?.cancel()
        reelEncoderJob = null
        _reelState.value = ReelState.Idle
    }

    fun reelConsumed() { _reelState.value = ReelState.Idle }

    val isDarkMode: StateFlow<Boolean> = preferencesManager.isDarkMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val currentUser: StateFlow<UserProfile?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val profilePhotoUri: StateFlow<String?> = preferencesManager.profilePhotoUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val appTheme: StateFlow<AppTheme> = preferencesManager.appThemeKey
        .map { AppTheme.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.WANDERLOG)

    val mapTheme: StateFlow<MapTheme> = preferencesManager.mapThemeKey
        .map { MapTheme.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MapTheme.DARK)

    val themeImageUri: StateFlow<String?> = preferencesManager.themeImageUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val remindersEnabled: StateFlow<Boolean> = preferencesManager.remindersEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val reminderIntervalDays: StateFlow<Int> = preferencesManager.reminderIntervalDays
        .stateIn(viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_REMINDER_INTERVAL_DAYS)

    val appLockEnabled: StateFlow<Boolean> = preferencesManager.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // User-added emoji moods, shown alongside the preset MOODS in the entry mood picker.
    val customMoods: StateFlow<List<String>> = preferencesManager.customMoods
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addCustomMood(emoji: String) {
        viewModelScope.launch { preferencesManager.addCustomMood(emoji) }
    }

    val coverHintCount: StateFlow<Int> = preferencesManager.coverHintCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun incrementCoverHintCount() {
        viewModelScope.launch { preferencesManager.incrementCoverHintCount() }
    }

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    fun lock() { _isAppLocked.value = true }
    fun unlock() { _isAppLocked.value = false }

    // Set just before launching one of our own activity-for-result flows (photo picker, camera,
    // voice input) that legitimately backgrounds the app, so the return trip doesn't trip the
    // re-lock timer. Consumed once on the next resume.
    @Volatile private var suppressAutoLockOnce = false
    fun suppressAutoLockOnce() { suppressAutoLockOnce = true }
    /** Returns whether the next auto-lock should be skipped, clearing the flag. */
    fun consumeSuppressAutoLock(): Boolean = suppressAutoLockOnce.also { suppressAutoLockOnce = false }

    // null = DataStore loading; false = first install (show onboarding); true = already seen
    val onboardingComplete: StateFlow<Boolean?> = preferencesManager.onboardingComplete
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun completeOnboarding() {
        viewModelScope.launch { preferencesManager.setOnboardingComplete() }
    }

    // location string → (lat, lon); populated lazily when MapScreen is open.
    private val _geocodedLocations = MutableStateFlow<Map<String, Pair<Double, Double>>>(emptyMap())
    val geocodedLocations: StateFlow<Map<String, Pair<Double, Double>>> = _geocodedLocations.asStateFlow()

    // False while a geocoding pass is in flight, true once the loop finishes (all succeeded, some
    // failed, or the list was empty). The map camera gates on this so it fits *all* locations once
    // geocoding is done, rather than locking onto whichever result arrived first.
    private val _geocodingComplete = MutableStateFlow(false)
    val geocodingComplete: StateFlow<Boolean> = _geocodingComplete.asStateFlow()

    fun geocodeLocations(context: Context, locations: List<String>) {
        if (locations.isEmpty()) {
            _geocodingComplete.value = true
            return
        }
        _geocodingComplete.value = false
        viewModelScope.launch(Dispatchers.IO) {
            val geocoder = Geocoder(context)
            locations.forEach { loc ->
                if (loc !in _geocodedLocations.value) {
                    try {
                        @Suppress("DEPRECATION")
                        val results = geocoder.getFromLocationName(loc, 1)
                        results?.firstOrNull()?.let { addr ->
                            _geocodedLocations.update { it + (loc to Pair(addr.latitude, addr.longitude)) }
                        }
                    } catch (_: Exception) { }
                }
            }
            _geocodingComplete.value = true
        }
    }

    init {
        // Cold-start lock: if App Lock is enabled, come up locked. The warm-resume re-lock in
        // NavGraph only fires while the same process survives in the background; an OEM
        // background-kill (common on Samsung) would otherwise relaunch the app unlocked and bypass
        // the lock. Runs once per process (ViewModel survives config changes), before the gate that
        // also requires login + purchase, so the lock screen shows as soon as those are satisfied.
        viewModelScope.launch {
            if (preferencesManager.appLockEnabled.first()) _isAppLocked.value = true
        }
        // Once a user is signed in, import any leftover entries from the legacy on-device store
        // into their cloud account (one-time; the migration renames the file when done).
        viewModelScope.launch {
            var lastUid: String? = null
            authRepository.currentUser.collect { user ->
                // On any account change (including sign-out), drop the Drive sync's per-account
                // cached folder id + photo cache so backups don't target the previous account.
                if (user?.uid != lastUid) {
                    drivePhotoSync.onAccountChanged()
                    // On sign-out, clear the locally stored profile photo so the next account that
                    // signs in starts with a clean slate. setProfilePhotoUri(null) removes the
                    // DataStore key; if no custom photo has ever been set this is a no-op.
                    if (user == null) {
                        preferencesManager.setProfilePhotoUri(null)
                    }
                    lastUid = user?.uid
                }
                if (user != null) LegacyEntryMigration.run(appContext, cloudEntrySync)
            }
        }
        // When the entry list changes, download any Drive photos missing on this device.
        viewModelScope.launch {
            cloudEntrySync.entries.collect { entryList ->
                drivePhotoSync.downloadMissingPhotos(entryList)
            }
        }
    }

    fun saveEntry(entry: TravelEntry) {
        viewModelScope.launch {
            // On edit, free the files for any photos/videos the user dropped from the entry.
            entries.value.find { it.id == entry.id }?.let { old ->
                ImageStorage.delete(appContext, old.photoUris - entry.photoUris.toSet())
                ImageStorage.delete(appContext, old.videoUris - entry.videoUris.toSet())
            }
            cloudEntrySync.save(entry)
            // Upload new photos AND videos to Drive in ONE background pass with ONE merged save.
            // Two separate launches used to race: each saved latest.copy(<its>FileIds), and the
            // later writer could read a `latest` predating the earlier save, reverting its IDs to
            // "" — which re-uploaded those files on the next save (duplicates in Drive).
            if (entry.photoUris.isNotEmpty() || entry.videoUris.isNotEmpty()) {
                launch {
                    val newPhotoIds =
                        if (entry.photoUris.isNotEmpty()) drivePhotoSync.uploadEntryPhotosOrReport(entry)
                        else entry.driveFileIds
                    val newVideoIds =
                        if (entry.videoUris.isNotEmpty()) drivePhotoSync.uploadEntryVideosOrReport(entry)
                        else entry.videoFileIds
                    if (newPhotoIds != entry.driveFileIds || newVideoIds != entry.videoFileIds) {
                        // Merge into the LATEST entry — the user may have edited it while the
                        // upload ran. Ids are positional to the media lists captured at upload
                        // start; merge each list only if it's unchanged since then.
                        val latest = entries.value.find { it.id == entry.id } ?: entry
                        val photosUnchanged = latest.photoUris == entry.photoUris
                        val videosUnchanged = latest.videoUris == entry.videoUris
                        if ((photosUnchanged && newPhotoIds != latest.driveFileIds) ||
                            (videosUnchanged && newVideoIds != latest.videoFileIds)
                        ) {
                            cloudEntrySync.save(
                                latest.copy(
                                    driveFileIds = if (photosUnchanged) newPhotoIds else latest.driveFileIds,
                                    videoFileIds = if (videosUnchanged) newVideoIds else latest.videoFileIds
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            entries.value.find { it.id == id }?.let {
                ImageStorage.delete(appContext, it.photoUris)
                ImageStorage.delete(appContext, it.videoUris)
            }
            cloudEntrySync.delete(id)
        }
    }

    fun deleteAccount(password: String? = null, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onComplete(authRepository.deleteAccount(password)) }
    }

    fun purchase(activity: Activity, pkg: com.revenuecat.purchases.Package, onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch { onResult(billingManager.purchase(activity, pkg)) }
    }

    fun restorePurchase(onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch { onResult(billingManager.restore()) }
    }

    fun toggleDarkMode() {
        viewModelScope.launch { preferencesManager.setDarkMode(!isDarkMode.value) }
    }

    fun signInWithGoogleIdToken(idToken: String, onResult: (Result<UserProfile>) -> Unit) {
        viewModelScope.launch { onResult(authRepository.signInWithGoogleIdToken(idToken)) }
    }

    fun signInWithEmail(email: String, password: String, onResult: (Result<UserProfile>) -> Unit) {
        viewModelScope.launch { onResult(authRepository.signInWithEmail(email, password)) }
    }

    fun createAccount(email: String, password: String, displayName: String, onResult: (Result<UserProfile>) -> Unit) {
        viewModelScope.launch { onResult(authRepository.createAccount(email, password, displayName)) }
    }

    fun sendPasswordResetEmail(email: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onResult(authRepository.sendPasswordResetEmail(email)) }
    }

    fun setProfilePhoto(uri: String) {
        viewModelScope.launch { preferencesManager.setProfilePhotoUri(uri) }
    }

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch { preferencesManager.setAppThemeKey(theme.key) }
    }

    fun setMapTheme(theme: MapTheme) {
        viewModelScope.launch { preferencesManager.setMapThemeKey(theme.key) }
    }

    fun setThemeImage(uri: String?) {
        viewModelScope.launch { preferencesManager.setThemeImageUri(uri) }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setRemindersEnabled(enabled)
            if (enabled) ReminderScheduler.schedule(appContext, reminderIntervalDays.value)
            else ReminderScheduler.cancel(appContext)
        }
    }

    fun setReminderIntervalDays(days: Int) {
        viewModelScope.launch {
            preferencesManager.setReminderIntervalDays(days)
            if (remindersEnabled.value) ReminderScheduler.schedule(appContext, days)
        }
    }

    fun setAppLockEnabled(value: Boolean) {
        viewModelScope.launch { preferencesManager.setAppLockEnabled(value) }
    }

    /** Toggle a tag in the list filter. */
    fun toggleTagFilter(tag: String) {
        _selectedTags.value = _selectedTags.value.let { if (tag in it) it - tag else it + tag }
    }

    /** Replace the list filter with a single tag (used when tapping a tag on an entry). */
    fun setTagFilter(tag: String) {
        _selectedTags.value = setOf(tag)
    }

    fun clearTagFilter() {
        _selectedTags.value = emptySet()
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    fun isDriveConnected(): Boolean = drivePhotoSync.isDriveConnected()

    /** Resets Drive sync state to Idle and clears the per-account folder cache. Call after the user
     *  reconnects their Google account so a stale error banner (e.g. an expired-sign-in message)
     *  clears immediately rather than lingering until the next successful sync. */
    fun resetDriveSyncState() {
        drivePhotoSync.onAccountChanged()
    }

    fun syncPhotosToGoogleDrive() {
        drivePhotoSync.syncAll(entries.value) { updated ->
            // `updated` was built from the entry list captured at sync start; merge only the
            // Drive-ID lists into the live entry so a mid-sync user edit isn't reverted. Each
            // list merges independently — ids are positional to the media list captured at start.
            val latest = entries.value.find { it.id == updated.id } ?: updated
            val photosUnchanged = latest.photoUris == updated.photoUris
            val videosUnchanged = latest.videoUris == updated.videoUris
            if (photosUnchanged || videosUnchanged) {
                cloudEntrySync.save(
                    latest.copy(
                        driveFileIds = if (photosUnchanged) updated.driveFileIds else latest.driveFileIds,
                        videoFileIds = if (videosUnchanged) updated.videoFileIds else latest.videoFileIds
                    )
                )
            }
        }
    }

    fun refreshDriveDownloads() {
        drivePhotoSync.downloadMissingPhotos(entries.value)
    }

    /** Writes a backup zip (entries + photo bytes) to the user-picked [dest]. [compact] re-encodes
     *  photos at JPEG 80% for a smaller file.
     *
     *  Entry photos can live only in the volatile Drive cache (cleared on app update), so before
     *  bundling we await a Drive download of any photo that isn't readable locally and substitute
     *  the freshly-cached copy. That stops a cold cache from silently producing a photo-less backup;
     *  anything still unreadable is reported via [JournalBackup.ExportResult.photosSkipped]. */
    suspend fun exportBackup(
        dest: android.net.Uri,
        compact: Boolean = false
    ): Result<JournalBackup.ExportResult> {
        val all = entries.value
        val cached = if (drivePhotoSync.isDriveConnected()) {
            runCatching { drivePhotoSync.ensurePhotosCached(all) }
                .getOrDefault(drivePhotoSync.cachedPhotoUris.value)
        } else {
            drivePhotoSync.cachedPhotoUris.value
        }
        // The cache only holds photos that weren't readable locally, so substituting "when cached"
        // touches exactly the photos that need it and leaves accessible local photos untouched.
        val resolved = if (cached.isEmpty()) all else all.map { entry ->
            val newUris = entry.photoUris.mapIndexed { i, uri ->
                val driveId = entry.driveFileIds.getOrNull(i)
                if (!driveId.isNullOrEmpty()) cached[driveId] ?: uri else uri
            }
            // Same substitution for videos — ensurePhotosCached/downloadMissing caches them in the
            // same map, so a Drive-only video (dead local URI after reinstall) still exports.
            val newVideoUris = entry.videoUris.mapIndexed { i, uri ->
                val driveId = entry.videoFileIds.getOrNull(i)
                if (!driveId.isNullOrEmpty()) cached[driveId] ?: uri else uri
            }
            entry.copy(photoUris = newUris, videoUris = newVideoUris)
        }
        return journalBackup.exportTo(dest, resolved, compact)
    }

    /** Restores entries from a backup zip at [src], upserting each into the cloud store. Publishes
     *  phase/byte progress to [importProgress] so the UI can show a determinate bar, then clears it. */
    suspend fun importBackup(src: android.net.Uri): Result<Int> {
        _importProgress.value = null
        return journalBackup.importFrom(
            src = src,
            onEntry = { cloudEntrySync.save(it) },
            onProgress = { phase, current, total ->
                _importProgress.value = ImportProgress(phase, current, total)
            }
        ).also { _importProgress.value = null }
    }

    class Factory(
        private val appContext: Context,
        private val cloudEntrySync: CloudEntrySync,
        private val preferencesManager: PreferencesManager,
        private val authRepository: AuthRepository,
        private val billingManager: BillingManager,
        private val drivePhotoSync: DrivePhotoSync
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            JournalViewModel(appContext, cloudEntrySync, preferencesManager, authRepository, billingManager, drivePhotoSync) as T
    }
}
