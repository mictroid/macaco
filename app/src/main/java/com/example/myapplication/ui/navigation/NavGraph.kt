package com.example.myapplication.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.data.model.TravelEntry
import com.example.myapplication.data.model.tagsByFrequency
import com.example.myapplication.ui.screens.AppLockScreen
import com.example.myapplication.ui.screens.EntryDetailScreen
import com.example.myapplication.ui.screens.JournalListScreen
import com.example.myapplication.ui.screens.LoginScreen
import com.example.myapplication.ui.screens.NewEditEntryScreen
import com.example.myapplication.ui.screens.ProfileScreen
import com.example.myapplication.ui.screens.PurchaseScreen
import com.example.myapplication.ui.screens.SettingsScreen
import com.example.myapplication.ui.screens.SplashScreen
import com.example.myapplication.ui.screens.SubscriptionInfoScreen
import com.example.myapplication.ui.viewmodel.JournalViewModel

/** Distinct, non-blank locations from existing entries, for the location autocomplete. */
private fun List<TravelEntry>.toLocationSuggestions(): List<String> =
    mapNotNull { it.location.trim().ifBlank { null } }
        .distinct()
        .sorted()

// Re-lock after this many ms in the background.
private const val LOCK_TIMEOUT_MS = 30_000L

@Composable
fun NavGraph(viewModel: JournalViewModel) {
    // Branded launch screen. Survives config changes so it isn't replayed on rotation, but shows
    // on each cold start.
    var showSplash by rememberSaveable { mutableStateOf(true) }
    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
        return
    }

    val isPurchased by viewModel.isPurchased.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val isAppLocked by viewModel.isAppLocked.collectAsState()

    // Re-lock when the app returns from background after LOCK_TIMEOUT_MS.
    val lifecycleOwner = LocalLifecycleOwner.current
    val pauseTime = remember { longArrayOf(0L) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> pauseTime[0] = System.currentTimeMillis()
                Lifecycle.Event.ON_RESUME -> {
                    val elapsed = System.currentTimeMillis() - pauseTime[0]
                    if (pauseTime[0] > 0 && elapsed > LOCK_TIMEOUT_MS && appLockEnabled) {
                        viewModel.lock()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Show the lock screen over everything when the journal is locked (only after login+purchase).
    if (appLockEnabled && isAppLocked && currentUser != null && isPurchased == true) {
        AppLockScreen(onUnlocked = { viewModel.unlock() })
        return
    }

    when {
        // DataStore still loading — blank splash to avoid flicker
        isPurchased == null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }

        // Not logged in — login required; memories are not shown
        currentUser == null -> {
            LoginScreen(
                viewModel = viewModel,
                onBack = {} // no dismissal — login is required
            )
        }

        // Logged in but not yet purchased
        isPurchased == false -> {
            PurchaseScreen(viewModel = viewModel)
        }

        // Logged in and purchased — full journal
        else -> {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = Screen.JournalList.route
            ) {
                composable(Screen.JournalList.route) {
                    JournalListScreen(
                        viewModel = viewModel,
                        onNewEntry = { navController.navigate(Screen.NewEntry.route) },
                        onEntryClick = { id ->
                            navController.navigate(Screen.EntryDetail.createRoute(id))
                        },
                        onProfile = { navController.navigate(Screen.Profile.route) },
                        onSettings = { navController.navigate(Screen.Settings.route) },
                        onSubscription = { navController.navigate(Screen.Subscription.route) },
                        onLogin = { navController.navigate(Screen.Login.route) }
                    )
                }

                composable(Screen.NewEntry.route) {
                    val entries by viewModel.entries.collectAsState()
                    NewEditEntryScreen(
                        existingEntry = null,
                        onSave = { entry ->
                            viewModel.saveEntry(entry)
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() },
                        locationSuggestions = entries.toLocationSuggestions(),
                        tagSuggestions = entries.tagsByFrequency()
                    )
                }

                composable(
                    route = Screen.EntryDetail.route,
                    arguments = listOf(navArgument("entryId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("entryId") ?: return@composable
                    val entries by viewModel.entries.collectAsState()
                    val cachedDrivePhotos by viewModel.cachedDrivePhotos.collectAsState()
                    val entry = entries.find { it.id == id }
                    if (entry == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                        return@composable
                    }
                    EntryDetailScreen(
                        entry = entry,
                        onEdit = { navController.navigate(Screen.EditEntry.createRoute(id)) },
                        onDelete = {
                            viewModel.deleteEntry(id)
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() },
                        onTagClick = { tag ->
                            viewModel.setTagFilter(tag)
                            navController.popBackStack(Screen.JournalList.route, inclusive = false)
                        },
                        cachedDrivePhotos = cachedDrivePhotos
                    )
                }

                composable(
                    route = Screen.EditEntry.route,
                    arguments = listOf(navArgument("entryId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("entryId") ?: return@composable
                    val entries by viewModel.entries.collectAsState()
                    val entry = entries.find { it.id == id } ?: return@composable
                    NewEditEntryScreen(
                        existingEntry = entry,
                        onSave = { updated ->
                            viewModel.saveEntry(updated)
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() },
                        locationSuggestions = entries.toLocationSuggestions(),
                        tagSuggestions = entries.tagsByFrequency()
                    )
                }

                composable(Screen.Login.route) {
                    LoginScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Profile.route) {
                    ProfileScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        // On sign-out currentUser becomes null, NavGraph auto-shows LoginScreen
                        onSignOut = { navController.popBackStack() },
                        onLogin = {
                            navController.popBackStack()
                            navController.navigate(Screen.Login.route)
                        }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Subscription.route) {
                    SubscriptionInfoScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
