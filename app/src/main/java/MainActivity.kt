package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.navigation.NavGraph
import com.example.myapplication.ui.theme.WanderlogTheme
import com.example.myapplication.ui.viewmodel.JournalViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

            val isDarkMode by vm.isDarkMode.collectAsState()
            val appTheme by vm.appTheme.collectAsState()
            val themeImageUri by vm.themeImageUri.collectAsState()
            WanderlogTheme(
                appTheme = appTheme,
                darkTheme = isDarkMode,
                backgroundImageUri = themeImageUri
            ) {
                NavGraph(viewModel = vm)
            }
        }
    }
}
