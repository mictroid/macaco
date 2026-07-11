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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.houseofmmminq.macaco.ui.components.MacacoSnackbar
import com.houseofmmminq.macaco.ui.navigation.NavGraph
import com.houseofmmminq.macaco.ui.theme.WanderlogTheme
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel

class MainActivity : AppCompatActivity() {

    // Set when launched/re-launched from the journal-reminder notification's deep link. Read by the
    // Compose tree (NavGraph) once the journal is on screen, then consumed.
    private var openNewEntry by mutableStateOf(false)

    // Play flexible in-app update. The download runs in the background; once it finishes we flip
    // updateReady so the Compose tree can offer a "Restart" snackbar to apply it.
    private val appUpdateManager by lazy { AppUpdateManagerFactory.create(this) }
    private var updateReady by mutableStateOf(false)

    // checkForUpdate() runs on every onResume, but the flexible-update confirmation overlay itself
    // causes an onPause→onResume cycle — without this guard we'd start a second overlapping flow.
    // Reset in onStop() so a fresh foreground session re-checks.
    private var updateFlowStarted = false

    // Result of the flexible-update overlay. Nothing to do here — the download continues in the
    // background and the install-state listener tells us when it's ready.
    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { }

    private val installStateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) updateReady = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen() must run on every onCreate — it performs the theme swap from
        // Theme.Macaco.Splash (Theme.SplashScreen parent, non-AppCompat) to Theme.MyApplication
        // (AppCompat). Skipping it on recreations (locale change, rotation) leaves a non-AppCompat
        // theme active and crashes AppCompatActivity.super.onCreate(). The one-frame flash that
        // motivated the old savedInstanceState guard is no longer possible: both themes now share
        // android:windowBackground=@color/splash_background (teal, added vc48), so the swap is
        // seamless. On recreations the OS doesn't show the splash animation regardless.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.action == ACTION_NEW_ENTRY) openNewEntry = true
        val app = application as TravelJournalApp
        val factory = JournalViewModel.Factory(app.applicationContext, app.cloudEntrySync, app.preferencesManager, app.authRepository, app.billingManager, app.drivePhotoSync)
        setContent {
            val vm: JournalViewModel = viewModel(factory = factory)

            // Entry photos live in shared storage (Pictures/Macaco) so they survive uninstalls.
            // After a reinstall the app no longer owns those files, so it needs media-read permission
            // to display them again — request it once on launch.
            val context = LocalContext.current
            // API 33+ splits media access per type — request BOTH so reinstalled devices can
            // re-read entry photos (Pictures/Macaco) AND videos (Movies/Macaco). Below 33 the
            // legacy storage permissions cover both types.
            val mediaPermissions = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                else -> arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE) // <=28: read+write group
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {}
            LaunchedEffect(Unit) {
                val missing = mediaPermissions.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isNotEmpty()) {
                    permissionLauncher.launch(missing.toTypedArray())
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
                val snackbarHostState = remember { SnackbarHostState() }
                val updateMessage = stringResource(R.string.update_ready_message)
                val updateAction = stringResource(R.string.update_ready_action)
                LaunchedEffect(updateReady) {
                    if (updateReady) {
                        val result = snackbarHostState.showSnackbar(
                            message = updateMessage,
                            actionLabel = updateAction,
                            duration = SnackbarDuration.Indefinite
                        )
                        // Reset before acting so a later onResume can re-set it to true and show a
                        // fresh snackbar (LaunchedEffect only re-fires on a value change).
                        updateReady = false
                        if (result == SnackbarResult.ActionPerformed) {
                            // Do NOT call finish() here. On Samsung One UI it races with
                            // completeUpdate() — the activity is finishing when Play Core tries to
                            // attach the installer overlay, so the install aborts silently and the
                            // user has to tap "Restart" twice. The teal windowBackground
                            // (@color/splash_background in themes.xml) already covers the transition,
                            // so there's no black window to hide. Play relaunches us when done.
                            appUpdateManager.completeUpdate()
                                .addOnFailureListener { e ->
                                    // Rare. The updateReady=false reset above means the snackbar
                                    // re-shows only when the next onResume sees DOWNLOADED again.
                                    android.util.Log.e("MainActivity", "completeUpdate failed", e)
                                }
                        }
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    NavGraph(
                        viewModel = vm,
                        openNewEntry = openNewEntry,
                        onOpenNewEntryConsumed = { openNewEntry = false }
                    )
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                    ) { data -> MacacoSnackbar(data) }
                }
            }
        }
    }

    // When the activity is already running (singleTop), the deep-link arrives here instead of onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_NEW_ENTRY) openNewEntry = true
    }

    override fun onResume() {
        super.onResume()
        appUpdateManager.registerListener(installStateListener)
        checkForUpdate()
    }

    override fun onPause() {
        super.onPause()
        appUpdateManager.unregisterListener(installStateListener)
    }

    override fun onStop() {
        super.onStop()
        updateFlowStarted = false
    }

    private fun checkForUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            when {
                // A newer version is on Play — kick off a background (flexible) download.
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) &&
                    !updateFlowStarted -> {
                    updateFlowStarted = true
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        updateLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                    )
                }
                // Downloaded in a previous session but not yet applied — offer the restart prompt.
                info.installStatus() == InstallStatus.DOWNLOADED -> updateReady = true
            }
        }
    }

    companion object {
        const val ACTION_NEW_ENTRY = "com.houseofmmminq.macaco.ACTION_NEW_ENTRY"
    }
}
