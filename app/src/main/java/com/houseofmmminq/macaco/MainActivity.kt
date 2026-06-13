package com.houseofmmminq.macaco

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.houseofmmminq.macaco.ui.navigation.NavGraph
import com.houseofmmminq.macaco.ui.theme.WanderlogTheme
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel

class MainActivity : AppCompatActivity() {

    // Set when launched/re-launched from the journal-reminder notification's deep link. Read by the
    // Compose tree (NavGraph) once the journal is on screen, then consumed.
    private var openNewEntry by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash (teal brand colour + monkey) covers the cold-start window, then the
        // in-app Compose SplashScreen continues the branded poster.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.action == ACTION_NEW_ENTRY) openNewEntry = true
        val app = application as TravelJournalApp
        val factory = JournalViewModel.Factory(app.applicationContext, app.cloudEntrySync, app.preferencesManager, app.authRepository, app.billingManager, app.drivePhotoSync)
        setContent {
            val vm: JournalViewModel = viewModel(factory = factory)

            // Entry photos live in shared storage (Pictures/Wanderlog) so they survive uninstalls.
            // After a reinstall the app no longer owns those files, so it needs media-read permission
            // to display them again — request it once on launch.
            val context = LocalContext.current
            val mediaPermission = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_IMAGES
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Manifest.permission.READ_EXTERNAL_STORAGE
                else -> Manifest.permission.WRITE_EXTERNAL_STORAGE // <=28: grants the read+write storage group
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {}
            LaunchedEffect(Unit) {
                if (ContextCompat.checkSelfPermission(context, mediaPermission) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(mediaPermission)
                }
            }

            // Android 13+ needs the runtime POST_NOTIFICATIONS grant for journal reminders to show.
            val notificationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {}
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val isDarkMode by vm.isDarkMode.collectAsState()
            val appTheme by vm.appTheme.collectAsState()
            val themeImageUri by vm.themeImageUri.collectAsState()
            WanderlogTheme(
                appTheme = appTheme,
                darkTheme = isDarkMode,
                backgroundImageUri = themeImageUri
            ) {
                NavGraph(
                    viewModel = vm,
                    openNewEntry = openNewEntry,
                    onOpenNewEntryConsumed = { openNewEntry = false }
                )
            }
        }
    }

    // When the activity is already running (singleTop), the deep-link arrives here instead of onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_NEW_ENTRY) openNewEntry = true
    }

    companion object {
        const val ACTION_NEW_ENTRY = "com.houseofmmminq.macaco.ACTION_NEW_ENTRY"
    }
}
