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
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.util.ImageStorage
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JournalViewModel(
    private val cloudEntrySync: CloudEntrySync,
    private val preferencesManager: PreferencesManager,
    private val authRepository: AuthRepository,
    private val billingManager: BillingManager
) : ViewModel() {

    val entries: StateFlow<List<TravelEntry>> = cloudEntrySync.entries

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

    fun saveEntry(entry: TravelEntry) {
        viewModelScope.launch {
            // On edit, free the files for any photos the user dropped from the entry.
            entries.value.find { it.id == entry.id }?.let { old ->
                ImageStorage.delete(old.photoUris - entry.photoUris.toSet())
            }
            cloudEntrySync.save(entry)
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            entries.value.find { it.id == id }?.let { ImageStorage.delete(it.photoUris) }
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

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    class Factory(
        private val cloudEntrySync: CloudEntrySync,
        private val preferencesManager: PreferencesManager,
        private val authRepository: AuthRepository,
        private val billingManager: BillingManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            JournalViewModel(cloudEntrySync, preferencesManager, authRepository, billingManager) as T
    }
}
