package com.example.myapplication.ui.viewmodel

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.PreferencesManager
import com.example.myapplication.data.auth.AuthRepository
import com.example.myapplication.data.billing.BillingManager
import com.example.myapplication.data.model.TravelEntry
import com.example.myapplication.data.model.UserProfile
import com.example.myapplication.data.storage.CloudEntrySync
import com.example.myapplication.data.storage.LegacyEntryMigration
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.util.ImageStorage
import com.example.myapplication.util.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JournalViewModel(
    private val appContext: Context,
    private val cloudEntrySync: CloudEntrySync,
    private val preferencesManager: PreferencesManager,
    private val authRepository: AuthRepository,
    private val billingManager: BillingManager
) : ViewModel() {

    val entries: StateFlow<List<TravelEntry>> = cloudEntrySync.entries

    /** One-shot sync error messages (snapshot/save/delete failures) for the UI to show. */
    val syncErrors: Flow<String> = cloudEntrySync.errors

    // Tags currently filtering the journal list (empty = show all). Lifted here so the entry
    // detail screen can set it (tap a tag → list filtered by that tag).
    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedTags: StateFlow<Set<String>> = _selectedTags.asStateFlow()

    // null = loading, false = not purchased, true = purchased (sourced from RevenueCat)
    val isPurchased: StateFlow<Boolean?> = billingManager.isPremium

    val priceLabel: StateFlow<String> = billingManager.priceLabel

    val isDarkMode: StateFlow<Boolean> = preferencesManager.isDarkMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val currentUser: StateFlow<UserProfile?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val profilePhotoUri: StateFlow<String?> = preferencesManager.profilePhotoUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val appTheme: StateFlow<AppTheme> = preferencesManager.appThemeKey
        .map { AppTheme.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.WANDERLOG)

    val themeImageUri: StateFlow<String?> = preferencesManager.themeImageUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val remindersEnabled: StateFlow<Boolean> = preferencesManager.remindersEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val reminderIntervalDays: StateFlow<Int> = preferencesManager.reminderIntervalDays
        .stateIn(viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_REMINDER_INTERVAL_DAYS)

    init {
        // Once a user is signed in, import any leftover entries from the legacy on-device store
        // into their cloud account (one-time; the migration renames the file when done).
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null) LegacyEntryMigration.run(appContext, cloudEntrySync)
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
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            entries.value.find { it.id == id }?.let { ImageStorage.delete(appContext, it.photoUris) }
            cloudEntrySync.delete(id)
        }
    }

    fun purchase(activity: Activity, onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch { onResult(billingManager.purchase(activity)) }
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

    fun signInWithApple(context: Context, onResult: (Result<UserProfile>) -> Unit) {
        viewModelScope.launch { onResult(authRepository.signInWithApple(context)) }
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

    class Factory(
        private val appContext: Context,
        private val cloudEntrySync: CloudEntrySync,
        private val preferencesManager: PreferencesManager,
        private val authRepository: AuthRepository,
        private val billingManager: BillingManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            JournalViewModel(appContext, cloudEntrySync, preferencesManager, authRepository, billingManager) as T
    }
}
