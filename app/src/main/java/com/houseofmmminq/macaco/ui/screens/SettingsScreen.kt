package com.houseofmmminq.macaco.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.RadioButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.ui.components.MacacoBrandBlock
import com.houseofmmminq.macaco.data.auth.FirebaseConfig
import com.houseofmmminq.macaco.data.sync.DrivePhotoSyncState
import com.houseofmmminq.macaco.ui.screens.isBiometricAvailable
import com.houseofmmminq.macaco.ui.screens.showBiometricPrompt
import com.houseofmmminq.macaco.ui.theme.AppTheme
import com.houseofmmminq.macaco.ui.theme.MapTheme
import com.houseofmmminq.macaco.ui.theme.isLightTheme
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel
import com.houseofmmminq.macaco.util.ImageStorage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.launch

private data class AppLanguage(val code: String, val nativeName: String)

private val SUPPORTED_LANGUAGES = listOf(
    AppLanguage("",   "System default"),
    AppLanguage("en", "English"),
    AppLanguage("de", "Deutsch"),
    AppLanguage("fr", "Français"),
    AppLanguage("es", "Español"),
    AppLanguage("it", "Italiano"),
    AppLanguage("nl", "Nederlands"),
    AppLanguage("pt", "Português"),
    AppLanguage("pl", "Polski"),
    AppLanguage("sv", "Svenska"),
    AppLanguage("ja", "日本語"),
    AppLanguage("zh", "中文（简体）"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    onPrintBook: () -> Unit
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val mapTheme by viewModel.mapTheme.collectAsState()
    val themeImageUri by viewModel.themeImageUri.collectAsState()
    val remindersEnabled by viewModel.remindersEnabled.collectAsState()
    val reminderIntervalDays by viewModel.reminderIntervalDays.collectAsState()
    val driveSyncState by viewModel.driveSyncState.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val context = LocalContext.current
    var lockErrorMessage by remember { mutableStateOf<String?>(null) }

    val currentLanguageCode = remember {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) "" else locales[0]?.language ?: ""
    }
    var selectedLanguageCode by remember { mutableStateOf(currentLanguageCode) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    if (showLanguagePicker) {
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SUPPORTED_LANGUAGES.forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedLanguageCode = lang.code
                                    AppCompatDelegate.setApplicationLocales(
                                        if (lang.code.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                                        else LocaleListCompat.forLanguageTags(lang.code)
                                    )
                                    showLanguagePicker = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = lang.code == selectedLanguageCode,
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (lang.code.isEmpty()) stringResource(R.string.settings_language_system_default)
                                else lang.nativeName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguagePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Drive sign-in with DRIVE_FILE scope — used to connect Drive for photo backup.
    var driveConnected by remember { mutableStateOf(viewModel.isDriveConnected()) }
    val driveSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        driveConnected = viewModel.isDriveConnected()
        if (driveConnected) {
            // Clear any stale error banner (e.g. expired sign-in) the instant the user reconnects,
            // before the sync runs — otherwise it lingers until the next successful sync.
            viewModel.resetDriveSyncState()
            viewModel.refreshDriveDownloads()
            viewModel.syncPhotosToGoogleDrive()
        }
    }
    val driveSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(FirebaseConfig.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    // Enabling reminders needs the POST_NOTIFICATIONS runtime grant on Android 13+.
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.setRemindersEnabled(true) }
    fun enableReminders() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setRemindersEnabled(true)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // Copy into our own storage and persist a file:// URI so the background survives
            // relaunches (the Photo Picker grant is temporary). See ImageStorage.
            ImageStorage.persist(context, uri, ImageStorage.BACKGROUNDS, replaceExisting = true)
                ?.let { viewModel.setThemeImage(it) }
        }
    }

    // Local file backup/restore (premium). SAF picks the destination/source; results via Toast.
    val isPurchased by viewModel.isPurchased.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val backupBusyState by viewModel.backupBusy.collectAsState()
    // Covers only the SAF picker window (between button tap and picker result), before the
    // ViewModel-owned operation starts.
    var pickerPending by remember { mutableStateOf(false) }
    val backupBusy = backupBusyState != null || pickerPending
    // true = import is running; false = export is running. Used to tailor the overlay status text.
    val backupIsImport = backupBusyState == JournalViewModel.BackupBusy.IMPORT
    // A backup is writing to user-picked storage; swallow back presses so navigation can't
    // interrupt the overlay (the operation itself now survives in viewModelScope regardless).
    BackHandler(enabled = backupBusy) { }

    // Keep the screen on while a backup export or import is running. Large imports take several
    // minutes to download; without this the device screen timeout fires mid-operation.
    val activity = context as? android.app.Activity
    DisposableEffect(backupBusy) {
        if (backupBusy) {
            activity?.window?.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        onDispose {
            // Always clear the flag when backupBusy becomes false or the composable leaves.
            activity?.window?.clearFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    var showExportDialog by remember { mutableStateOf(false) }
    var pendingCompact by remember { mutableStateOf(false) }
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        pickerPending = false
        if (uri == null) return@rememberLauncherForActivityResult
        val appContext = context.applicationContext
        val isCompact = pendingCompact // capture before the background op
        viewModel.exportBackupInBackground(uri, compact = isCompact) { result ->
            Toast.makeText(
                appContext,
                result.fold(
                    { r ->
                        buildString {
                            append(
                                if (r.photosSkipped > 0) appContext.getString(
                                    R.string.settings_backup_export_done_warn, r.entries, r.photosSkipped
                                ) else appContext.getString(R.string.settings_backup_export_done, r.entries)
                            )
                            if (r.videosSkipped > 0) {
                                append(" ")
                                append(
                                    appContext.getString(
                                        R.string.settings_backup_export_videos_warn, r.videosSkipped
                                    )
                                )
                            }
                        }
                    },
                    { it.message ?: appContext.getString(R.string.settings_backup_failed) }
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        pickerPending = false
        if (uri == null) return@rememberLauncherForActivityResult
        val appContext = context.applicationContext
        viewModel.importBackupInBackground(uri) { result ->
            Toast.makeText(
                appContext,
                result.fold(
                    { appContext.getString(R.string.settings_backup_import_done, it) },
                    { it.message ?: appContext.getString(R.string.settings_backup_failed) }
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        // The branded teal header runs edge-to-edge under the status bar; opt out of the default
        // top inset and re-apply it inside the banner.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // In landscape on phones (short screen) the tall centered header + stacked appearance
            // cards push the lower settings off the fold; collapse the header and put Theme/Background
            // side-by-side. Tablets stay tall (height > 480dp) and keep the portrait layout.
            val isLandscape = LocalConfiguration.current.screenHeightDp < 480
            // Branded fixed header: splash teal radial with the back button, gold "macaco"
            // wordmark, and the "Settings" label — matching the profile and login headers.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(macacoBrandBackground())
                    .statusBarsPadding()
            ) {
                if (isLandscape) {
                    // ── Compact landscape: back button start, brand column (icon above text) centered ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 4.dp)
                                .size(40.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = Color.White
                            )
                        }
                        MacacoBrandBlock(
                            isLandscape = true,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Text(
                                text = " · " + stringResource(R.string.common_settings),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Color.White
                        )
                    }
                    MacacoBrandBlock(
                        isLandscape = false,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 2.dp, bottom = 10.dp)
                    ) {
                        Text(
                            stringResource(R.string.common_settings),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
            // ── Appearance ────────────────────────────────────────────────────
            SettingsSectionHeader(stringResource(R.string.settings_appearance))

            SettingsToggleRow(
                icon = if (isDarkMode) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                title = stringResource(R.string.settings_dark_mode),
                subtitle = if (isDarkMode) stringResource(R.string.settings_dark_mode_on) else stringResource(R.string.settings_dark_mode_off),
                checked = isDarkMode,
                onCheckedChange = { viewModel.toggleDarkMode() }
            )

            // ── Theme Color + Background Image ────────────────────────────────
            // Portrait: stacked (spacer + header + card each). Landscape: the two cards sit
            // side-by-side in a weight(1f) Row so the lower settings stay above the fold.
            // Extracted as composable lambdas so both layouts share one definition.
            val themeColorCard: @Composable () -> Unit = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(if (isLandscape) 12.dp else 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            AppTheme.entries.forEach { theme ->
                                ThemeSwatch(
                                    theme = theme,
                                    selected = appTheme == theme,
                                    onClick = {
                                        viewModel.setAppTheme(theme)
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(if (isLandscape) 6.dp else 10.dp))
                        Text(
                            appTheme.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            val backgroundImageCard: @Composable () -> Unit = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(if (isLandscape) 12.dp else 16.dp)) {
                        if (themeImageUri != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Current image thumbnail
                                Box(modifier = Modifier.size(72.dp)) {
                                    AsyncImage(
                                        model = themeImageUri,
                                        contentDescription = stringResource(R.string.settings_background_image),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.settings_custom_bg_active),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        stringResource(R.string.settings_custom_bg_tap_change),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Remove button
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .clickable {
                                            ImageStorage.clear(context, ImageStorage.BACKGROUNDS)
                                            viewModel.setThemeImage(null)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.settings_remove_image_cd),
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        // Pick / Change button. Light mode: vibrant accent; dark mode: container (as-is).
                        val pickerLight = isLightTheme()
                        val pickerBg = if (pickerLight) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.primaryContainer
                        val pickerFg = if (pickerLight) MaterialTheme.colorScheme.onPrimary
                                       else MaterialTheme.colorScheme.onPrimaryContainer
                        // Compact (44dp inline) when an image is set, or in landscape; generous
                        // 80dp icon-above-text tap target only in portrait with no image.
                        val pickerHeight = when {
                            themeImageUri != null -> 44.dp
                            isLandscape -> 44.dp
                            else -> 80.dp
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(pickerHeight)
                                .clip(RoundedCornerShape(10.dp))
                                .background(pickerBg)
                                .clickable {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (themeImageUri == null) {
                                if (isLandscape) {
                                    // Compact inline row in landscape.
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = null,
                                            tint = pickerFg,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            stringResource(R.string.settings_pick_bg),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = pickerFg
                                        )
                                    }
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = null,
                                            tint = pickerFg,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            stringResource(R.string.settings_pick_bg),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = pickerFg
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    stringResource(R.string.settings_change_image),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = pickerFg,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // The explanatory hint eats vertical space; skip it in landscape.
                        if (themeImageUri == null && !isLandscape) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.settings_bg_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ── Theme Color ───────────────────────────────────────────────
            // Always stacked (side-by-side landscape layout reverted); the outer Column's
            // spacedBy(4.dp) provides the inter-section gap.
            SettingsSectionHeader(stringResource(R.string.settings_theme_color))
            themeColorCard()

            // ── Background Image ──────────────────────────────────────────
            SettingsSectionHeader(stringResource(R.string.settings_background_image))
            backgroundImageCard()

            // ── Map ───────────────────────────────────────────────────────────
            SettingsSectionHeader(stringResource(R.string.settings_map))

            MapThemeCard(
                selected = mapTheme,
                onSelect = { viewModel.setMapTheme(it) }
            )

            // ── Language ──────────────────────────────────────────────────────
            SettingsSectionHeader(stringResource(R.string.settings_language))

            val displayedLanguage = SUPPORTED_LANGUAGES.find { it.code == selectedLanguageCode }
            SettingsClickRow(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.settings_language),
                value = if (displayedLanguage == null || displayedLanguage.code.isEmpty())
                    stringResource(R.string.settings_language_system_default)
                else displayedLanguage.nativeName,
                onClick = { showLanguagePicker = true }
            )

            // ── Notifications ─────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader(stringResource(R.string.settings_notifications))

            SettingsToggleRow(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.settings_reminders),
                subtitle = if (remindersEnabled) {
                    pluralStringResource(R.plurals.settings_reminder_interval, reminderIntervalDays, reminderIntervalDays)
                } else {
                    stringResource(R.string.settings_reminders_off)
                },
                checked = remindersEnabled,
                onCheckedChange = { checked ->
                    if (checked) enableReminders() else viewModel.setRemindersEnabled(false)
                }
            )
            if (remindersEnabled) {
                Spacer(Modifier.height(8.dp))
                ReminderCadenceCard(
                    selectedDays = reminderIntervalDays,
                    onSelect = { viewModel.setReminderIntervalDays(it) }
                )
            }

            // ── Privacy ───────────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader(stringResource(R.string.settings_privacy))

            SettingsToggleRow(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.settings_app_lock),
                subtitle = if (appLockEnabled) stringResource(R.string.settings_app_lock_on)
                           else stringResource(R.string.settings_app_lock_off),
                checked = appLockEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        if (!isBiometricAvailable(context)) {
                            lockErrorMessage = context.getString(R.string.settings_app_lock_unavailable)
                            return@SettingsToggleRow
                        }
                        // Verify auth works before enabling — prevents lockout on unsupported devices.
                        showBiometricPrompt(context) { viewModel.setAppLockEnabled(true) }
                    } else {
                        viewModel.setAppLockEnabled(false)
                    }
                }
            )
            if (lockErrorMessage != null) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        lockErrorMessage!!,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Opens the Android system "App info" screen so the user can review/adjust every
            // runtime permission (camera, location, photos/videos, notifications) in one place —
            // apps can't grant permissions silently, so the canonical pattern is to deep-link here.
            SettingsClickRow(
                icon = Icons.Filled.Security,
                title = stringResource(R.string.settings_permissions),
                value = stringResource(R.string.settings_permissions_subtitle),
                stackedValue = true,
                onClick = {
                    val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )

            // ── Google Drive Backup ───────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader(stringResource(R.string.settings_drive_backup))

            val connectedEmail = if (driveConnected) {
                GoogleSignIn.getLastSignedInAccount(context)?.email
            } else null

            DriveBackupCard(
                connected = driveConnected,
                connectedEmail = connectedEmail,
                syncState = driveSyncState,
                premium = isPurchased == true,
                onConnect = {
                    if (isPurchased == true) {
                        driveSignInLauncher.launch(driveSignInClient.signInIntent)
                    } else {
                        onNavigateToPaywall()
                    }
                },
                onSyncNow = { viewModel.syncPhotosToGoogleDrive() },
                onDisconnect = {
                    driveSignInClient.signOut().addOnCompleteListener {
                        driveConnected = false
                    }
                }
            )

            // ── Backup & restore to file ──────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader(stringResource(R.string.settings_backup_file))

            BackupFileCard(
                premium = isPurchased == true,
                busy = backupBusy,
                importProgress = importProgress,
                onExport = {
                    if (isPurchased == true) showExportDialog = true
                    else onNavigateToPaywall()
                },
                onImport = {
                    if (isPurchased == true) {
                        pickerPending = true
                        backupImportLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    } else onNavigateToPaywall()
                }
            )

            // Export quality must be chosen before the SAF picker opens (it can't be asked after).
            if (showExportDialog) {
                AlertDialog(
                    onDismissRequest = { showExportDialog = false },
                    title = { Text(stringResource(R.string.settings_backup_export_quality_title)) },
                    text = { Text(stringResource(R.string.settings_backup_export_quality_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showExportDialog = false
                            pendingCompact = true
                            pickerPending = true
                            backupExportLauncher.launch("macaco-backup-compact.zip")
                        }) {
                            Text(stringResource(R.string.settings_backup_export_compact))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showExportDialog = false
                            pendingCompact = false
                            pickerPending = true
                            backupExportLauncher.launch("macaco-backup.zip")
                        }) {
                            Text(stringResource(R.string.settings_backup_export_full))
                        }
                    }
                )
            }

            // ── Print Book (premium) ──────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader(stringResource(R.string.print_book_title))

            SettingsClickRow(
                icon = Icons.Filled.Print,
                title = stringResource(R.string.print_book_title),
                value = if (isPurchased == true) stringResource(R.string.print_book_subtitle)
                else stringResource(R.string.print_premium_required),
                onClick = { if (isPurchased == true) onPrintBook() else onNavigateToPaywall() },
                stackedValue = true
            )

            // ── Subscription ──────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader(stringResource(R.string.settings_subscription))

            // Restore is reachable here once past PurchaseScreen — needed after a reinstall or
            // device switch. Toast feedback matches how BackupFileCard reports its results.
            SettingsClickRow(
                icon = Icons.Filled.Restore,
                title = stringResource(R.string.settings_restore_purchase),
                value = "",
                onClick = {
                    viewModel.restorePurchase { result ->
                        result.fold(
                            onSuccess = { restored ->
                                Toast.makeText(
                                    context,
                                    if (restored) context.getString(R.string.settings_restore_success)
                                    else context.getString(R.string.settings_restore_not_found),
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            onFailure = {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_restore_error),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                }
            )

            // ── About ─────────────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader(stringResource(R.string.settings_about))

            val versionLabel = remember {
                runCatching {
                    val info = context.packageManager.getPackageInfo(context.packageName, 0)
                    val versionCode = PackageInfoCompat.getLongVersionCode(info)
                    "${info.versionName} ($versionCode)"
                }.getOrNull().orEmpty()
            }
            SettingsInfoRow(icon = Icons.Filled.Info, title = stringResource(R.string.common_version), value = stringResource(R.string.settings_version_value, versionLabel))

            Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Branded loading overlay — covers the entire screen during backup operations so a long
    // export/import shows clear feedback instead of a frozen or black-looking settings screen.
    if (backupBusy) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(macacoBrandBackground())
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp)
                )
                Text(
                    text = "macaco",
                    color = SplashGoldBright,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 5.sp
                )
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(4.dp))
                // Contextual status text: show import phase or generic export message.
                val statusText = if (backupIsImport) {
                    when (importProgress?.phase) {
                        com.houseofmmminq.macaco.data.sync.JournalBackup.ImportPhase.DOWNLOADING ->
                            stringResource(R.string.settings_backup_status_downloading)
                        com.houseofmmminq.macaco.data.sync.JournalBackup.ImportPhase.RESTORING ->
                            stringResource(R.string.settings_backup_status_restoring)
                        else -> stringResource(R.string.settings_backup_status_importing)
                    }
                } else {
                    stringResource(R.string.settings_backup_status_exporting)
                }
                Text(
                    text = statusText,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }
    }
}

@Composable
private fun ThemeSwatch(theme: AppTheme, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(theme.swatch)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun BackupFileCard(
    premium: Boolean,
    busy: Boolean,
    importProgress: JournalViewModel.ImportProgress?,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_backup_file_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (premium) stringResource(R.string.settings_backup_file_subtitle)
                        else stringResource(R.string.settings_backup_premium_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!premium) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (busy) {
                // Import publishes phase/byte progress; export (and the pre-progress window) just
                // shows an indeterminate bar.
                val progress = importProgress
                if (progress != null) {
                    val label = when (progress.phase) {
                        com.houseofmmminq.macaco.data.sync.JournalBackup.ImportPhase.DOWNLOADING ->
                            if (progress.total > 0)
                                stringResource(R.string.settings_import_downloading_mb, progress.current, progress.total)
                            else
                                stringResource(R.string.settings_import_downloading)
                        com.houseofmmminq.macaco.data.sync.JournalBackup.ImportPhase.RESTORING ->
                            stringResource(R.string.settings_import_restoring, progress.current, progress.total)
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (progress.total > 0) {
                        LinearProgressIndicator(
                            progress = { progress.current.toFloat() / progress.total },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onExport,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.settings_backup_export)) }
                OutlinedButton(
                    onClick = onImport,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.settings_backup_import)) }
            }
        }
    }
}

@Composable
private fun DriveBackupCard(
    connected: Boolean,
    connectedEmail: String?,
    syncState: DrivePhotoSyncState,
    premium: Boolean,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (connected) Icons.Filled.CloudUpload else Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (connected) stringResource(R.string.settings_drive_connected)
                        else stringResource(R.string.settings_drive_not_connected),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (connected && connectedEmail != null)
                            stringResource(R.string.settings_drive_connected_as, connectedEmail)
                        else if (connected)
                            stringResource(R.string.settings_drive_connected_subtitle)
                        else if (!premium)
                            stringResource(R.string.settings_drive_premium_required)
                        else
                            stringResource(R.string.settings_drive_not_connected_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!connected && !premium) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (syncState) {
                is DrivePhotoSyncState.Syncing -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { syncState.done.toFloat() / syncState.total.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            stringResource(R.string.settings_drive_uploading, syncState.done, syncState.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is DrivePhotoSyncState.Synced -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.settings_drive_all_synced),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                is DrivePhotoSyncState.Error -> {
                    Text(
                        syncState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }

            if (!connected) {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.settings_drive_connect))
                }
            } else if (syncState !is DrivePhotoSyncState.Syncing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onSyncNow,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_drive_sync_now))
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.settings_drive_disconnect))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapThemeCard(selected: MapTheme, onSelect: (MapTheme) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                stringResource(R.string.settings_map_style),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = theme == selected,
                        onClick = { onSelect(theme) },
                        label = { Text(stringResource(theme.labelRes)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderCadenceCard(selectedDays: Int, onSelect: (Int) -> Unit) {
    val options = listOf(1, 3, 4, 7, 14)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                stringResource(R.string.settings_remind_every),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { d ->
                    FilterChip(
                        selected = d == selectedDays,
                        onClick = { onSelect(d) },
                        label = { Text(if (d == 1) stringResource(R.string.settings_reminder_one_day) else stringResource(R.string.settings_reminder_days, d)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsClickRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    // True for rows whose value is a full descriptive sentence (e.g. App Permissions listing every
    // runtime permission) rather than a short inline value (e.g. Language: "System default").
    // Inline layout gives the value Text no width/line limit, so a long value squeezes the weighted
    // title down until it wraps mid-word. Stacked layout puts the value on its own full-width line
    // below the title instead — same idea as how BackupFileCard already renders longer descriptions.
    stackedValue: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick
    ) {
        if (stackedValue) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 40.dp) // 24dp icon + 16dp spacer — aligns under the title
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsInfoRow(icon: ImageVector, title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
