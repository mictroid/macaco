package com.houseofmmminq.macaco.ui.viewmodel

import android.app.Activity
import android.content.Context
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
import com.houseofmmminq.macaco.data.sync.DrivePhotoSync
import com.houseofmmminq.macaco.data.sync.DrivePhotoSyncState
import com.houseofmmminq.macaco.data.sync.JournalBackup
import com.houseofmmminq.macaco.ui.theme.AppTheme
import com.houseofmmminq.macaco.ui.theme.MapTheme
import com.houseofmmminq.macaco.util.ImageStorage
import com.houseofmmminq.macaco.util.ReminderScheduler
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
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

    fun geocodeLocations(context: Context, locations: List<String>) {
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
            // On edit, free the files for any photos the user dropped from the entry.
            entries.value.find { it.id == entry.id }?.let { old ->
                ImageStorage.delete(appContext, old.photoUris - entry.photoUris.toSet())
            }
            cloudEntrySync.save(entry)
            // Upload new photos to Drive in the background; persist updated IDs when done.
            if (entry.photoUris.isNotEmpty()) {
                launch {
                    val updatedIds = drivePhotoSync.uploadEntryPhotosOrReport(entry)
                    if (updatedIds != entry.driveFileIds) {
                        cloudEntrySync.save(entry.copy(driveFileIds = updatedIds))
                    }
                }
            }
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            entries.value.find { it.id == id }?.let { ImageStorage.delete(appContext, it.photoUris) }
            cloudEntrySync.delete(id)
        }
    }

    fun deleteAccount(onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onComplete(authRepository.deleteAccount()) }
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
            cloudEntrySync.save(updated)
        }
    }

    fun refreshDriveDownloads() {
        drivePhotoSync.downloadMissingPhotos(entries.value)
    }

    /** Writes a backup zip (entries + photo bytes) to the user-picked [dest]. [compact] re-encodes
     *  photos at JPEG 80% for a smaller file. */
    suspend fun exportBackup(dest: android.net.Uri, compact: Boolean = false): Result<Int> =
        journalBackup.exportTo(dest, entries.value, compact)

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
