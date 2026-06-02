package com.example.myapplication.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wanderlog_prefs")

class PreferencesManager(private val context: Context) {

    private val KEY_PURCHASED = booleanPreferencesKey("is_purchased")
    private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
    private val KEY_PROFILE_PHOTO = stringPreferencesKey("profile_photo_uri")
    private val KEY_APP_THEME = stringPreferencesKey("app_theme")
    private val KEY_THEME_IMAGE = stringPreferencesKey("theme_image_uri")
    private val KEY_REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
    private val KEY_REMINDER_INTERVAL = intPreferencesKey("reminder_interval_days")

    companion object {
        const val DEFAULT_REMINDER_INTERVAL_DAYS = 4
    }

    // Emits null until DataStore first loads, then false/true (never null after first load)
    val isPurchased: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_PURCHASED] ?: false }

    val isDarkMode: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_DARK_MODE] ?: false }

    suspend fun setPurchased(value: Boolean) {
        context.dataStore.edit { it[KEY_PURCHASED] = value }
    }

    suspend fun setDarkMode(value: Boolean) {
        context.dataStore.edit { it[KEY_DARK_MODE] = value }
    }

    // As with the theme image, only file:// URIs (copied into our own storage) survive restarts;
    // ignore any legacy transient content:// value so it can't render a broken/unreadable photo.
    val profilePhotoUri: Flow<String?> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_PROFILE_PHOTO]?.takeIf { it.startsWith("file://") } }

    suspend fun setProfilePhotoUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri != null) prefs[KEY_PROFILE_PHOTO] = uri
            else prefs.remove(KEY_PROFILE_PHOTO)
        }
    }

    val appThemeKey: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_APP_THEME] ?: "wanderlog" }

    suspend fun setAppThemeKey(key: String) {
        context.dataStore.edit { it[KEY_APP_THEME] = key }
    }

    // Only file:// URIs (copied into our own storage) are usable across restarts. Older builds
    // saved transient content:// Photo Picker URIs whose read grant is revoked on relaunch; treat
    // those as "no image" so a stale value can't break the background on launch.
    val themeImageUri: Flow<String?> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_THEME_IMAGE]?.takeIf { it.startsWith("file://") } }

    suspend fun setThemeImageUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri != null) prefs[KEY_THEME_IMAGE] = uri
            else prefs.remove(KEY_THEME_IMAGE)
        }
    }

    val remindersEnabled: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_REMINDERS_ENABLED] ?: false }

    val reminderIntervalDays: Flow<Int> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_REMINDER_INTERVAL] ?: DEFAULT_REMINDER_INTERVAL_DAYS }

    suspend fun setRemindersEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_REMINDERS_ENABLED] = value }
    }

    suspend fun setReminderIntervalDays(days: Int) {
        context.dataStore.edit { it[KEY_REMINDER_INTERVAL] = days }
    }
}
