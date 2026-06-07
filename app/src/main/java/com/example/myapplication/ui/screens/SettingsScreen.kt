package com.example.myapplication.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.R
import com.example.myapplication.data.auth.FirebaseConfig
import com.example.myapplication.data.sync.DrivePhotoSyncState
import com.example.myapplication.ui.screens.isBiometricAvailable
import com.example.myapplication.ui.screens.showBiometricPrompt
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.ui.theme.isLightTheme
import com.example.myapplication.ui.viewmodel.JournalViewModel
import com.example.myapplication.util.ImageStorage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val themeImageUri by viewModel.themeImageUri.collectAsState()
    val remindersEnabled by viewModel.remindersEnabled.collectAsState()
    val reminderIntervalDays by viewModel.reminderIntervalDays.collectAsState()
    val driveSyncState by viewModel.driveSyncState.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val context = LocalContext.current
    var lockErrorMessage by remember { mutableStateOf<String?>(null) }

    // Drive sign-in with DRIVE_FILE scope — used to connect Drive for photo backup.
    var driveConnected by remember { mutableStateOf(viewModel.isDriveConnected()) }
    val driveSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        driveConnected = viewModel.isDriveConnected()
        if (driveConnected) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.common_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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

            // ── Theme Color ───────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader(stringResource(R.string.settings_theme_color))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    Spacer(Modifier.height(10.dp))
                    Text(
                        appTheme.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── Background Image ──────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader(stringResource(R.string.settings_background_image))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (themeImageUri == null) 80.dp else 44.dp)
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
                        } else {
                            Text(
                                stringResource(R.string.settings_change_image),
                                style = MaterialTheme.typography.labelLarge,
                                color = pickerFg,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (themeImageUri == null) {
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

            // ── Google Drive Backup ───────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader(stringResource(R.string.settings_drive_backup))

            DriveBackupCard(
                connected = driveConnected,
                syncState = driveSyncState,
                onConnect = { driveSignInLauncher.launch(driveSignInClient.signInIntent) },
                onSyncNow = { viewModel.syncPhotosToGoogleDrive() }
            )

            // ── About ─────────────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader(stringResource(R.string.settings_about))

            val versionName = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull().orEmpty()
            }
            SettingsInfoRow(icon = Icons.Filled.Info, title = stringResource(R.string.common_version), value = stringResource(R.string.settings_version_value, versionName))

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ThemeSwatch(theme: AppTheme, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
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
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun DriveBackupCard(
    connected: Boolean,
    syncState: DrivePhotoSyncState,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit
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
                        if (connected) stringResource(R.string.settings_drive_connected) else stringResource(R.string.settings_drive_not_connected),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (connected) stringResource(R.string.settings_drive_connected_subtitle)
                        else stringResource(R.string.settings_drive_not_connected_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                OutlinedButton(
                    onClick = onSyncNow,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_drive_sync_now))
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
