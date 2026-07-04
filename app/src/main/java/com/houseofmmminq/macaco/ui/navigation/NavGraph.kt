package com.houseofmmminq.macaco.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.data.model.TravelEntry
import com.houseofmmminq.macaco.data.model.tagsByFrequency
import com.houseofmmminq.macaco.ui.screens.MapScreen
import com.houseofmmminq.macaco.ui.screens.AppLockScreen
import com.houseofmmminq.macaco.ui.screens.OnboardingScreen
import com.houseofmmminq.macaco.ui.screens.EntryDetailScreen
import com.houseofmmminq.macaco.ui.screens.HelpAboutScreen
import com.houseofmmminq.macaco.ui.screens.JournalListScreen
import com.houseofmmminq.macaco.ui.screens.LoginScreen
import com.houseofmmminq.macaco.ui.screens.NewEditEntryScreen
import com.houseofmmminq.macaco.ui.screens.ProfileScreen
import com.houseofmmminq.macaco.ui.screens.PurchaseScreen
import com.houseofmmminq.macaco.ui.screens.SettingsScreen
import com.houseofmmminq.macaco.ui.screens.SplashScreen
import com.houseofmmminq.macaco.ui.screens.SubscriptionInfoScreen
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel

/** Distinct, non-blank locations from existing entries, for the location autocomplete. */
private fun List<TravelEntry>.toLocationSuggestions(): List<String> =
    mapNotNull { it.location.trim().ifBlank { null } }
        .distinct()
        .sorted()

/** Distinct trip names from existing entries, most-recently-used first. */
private fun List<TravelEntry>.toTripSuggestions(): List<String> =
    mapNotNull { it.tripName?.trim()?.ifBlank { null } }
        .distinct()
        .sorted()

// Re-lock after this many ms in the background.
private const val LOCK_TIMEOUT_MS = 30_000L

// Entries a signed-in user can create before the paywall appears at the next creation.
private const val FREE_ENTRY_LIMIT = 3

@Composable
fun NavGraph(
    viewModel: JournalViewModel,
    openNewEntry: Boolean = false,
    onOpenNewEntryConsumed: () -> Unit = {}
) {
    // All state collected unconditionally so Compose hooks are always called in the same order.
    val onboardingComplete by viewModel.onboardingComplete.collectAsState()
    var showSplash by rememberSaveable { mutableStateOf(true) }
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
                    // Skip the re-lock when we left for our own picker/camera/voice flow — otherwise
                    // a long photo selection (> timeout) locks the user out mid-entry.
                    val suppressed = viewModel.consumeSuppressAutoLock()
                    if (!suppressed && pauseTime[0] > 0 && elapsed > LOCK_TIMEOUT_MS && appLockEnabled) {
                        viewModel.lock()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Wait for DataStore to read the onboarding flag before showing anything.
    if (onboardingComplete == null) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    // First install — show onboarding once, then proceed to splash.
    if (onboardingComplete == false) {
        OnboardingScreen(onComplete = { viewModel.completeOnboarding() })
        return
    }

    // Branded launch screen. Survives config changes so it isn't replayed on rotation, but shows
    // on each cold start (after onboarding).
    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
        return
    }

    // Show the lock screen over everything when the journal is locked (only after login).
    if (appLockEnabled && isAppLocked && currentUser != null) {
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

        // Logged in — full journal. Premium is enforced per-feature (backup, reel) and at
        // entry creation beyond the free limit (see goToNewEntry), not as an app-wide wall.
        else -> {
            val navController = rememberNavController()

            val entryCount = viewModel.entries.collectAsState().value.size
            // isPurchased == false is the only state that gates; null (still loading) lets the
            // user through — worst case one extra free entry, never a wrongly-blocked premium user.
            val goToNewEntry: () -> Unit = {
                if (isPurchased == false && entryCount >= FREE_ENTRY_LIMIT) {
                    navController.navigate(Screen.Paywall.route)
                } else {
                    navController.navigate(Screen.NewEntry.route)
                }
            }

            // Deep link from the journal-reminder notification: jump straight to the new-entry
            // screen. Only reachable here (signed-in + purchased), so the gates are respected.
            LaunchedEffect(openNewEntry) {
                if (openNewEntry) {
                    goToNewEntry()
                    onOpenNewEntryConsumed()
                }
            }
            val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
            val tabRoutes = setOf(Screen.JournalList.route, Screen.Adventures.route, Screen.Profile.route)

            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (currentRoute in tabRoutes) {
                        MacacoBottomNavBar(navController = navController, currentRoute = currentRoute)
                    }
                }
            ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.JournalList.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.JournalList.route) {
                    JournalListScreen(
                        viewModel = viewModel,
                        onNewEntry = goToNewEntry,
                        onEntryClick = { id ->
                            navController.navigate(Screen.EntryDetail.createRoute(id))
                        },
                        onProfile = { navController.navigateToTab(Screen.Profile.route) }
                    )
                }

                composable(Screen.Adventures.route) {
                    MapScreen(
                        viewModel = viewModel,
                        onEntryClick = { id -> navController.navigate(Screen.EntryDetail.createRoute(id)) }
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
                        tagSuggestions = entries.tagsByFrequency(),
                        tripSuggestions = entries.toTripSuggestions(),
                        customMoods = viewModel.customMoods.collectAsState().value,
                        onAddCustomMood = { viewModel.addCustomMood(it) },
                        onSuppressAutoLock = { viewModel.suppressAutoLockOnce() }
                    )
                }

                composable(
                    route = Screen.EntryDetail.route,
                    arguments = listOf(navArgument("entryId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("entryId") ?: return@composable
                    // Swipe through the same tag-filtered set the list shows, not all entries.
                    val entries by viewModel.visibleEntries.collectAsState()
                    val cachedDrivePhotos by viewModel.cachedDrivePhotos.collectAsState()
                    val coverHintCount by viewModel.coverHintCount.collectAsState()
                    // Tracks that onDelete has already fired a popBackStack(), so the
                    // entries-change guard below doesn't fire a second one. On slower devices
                    // (S8, Android 8.1) the composable survives an extra recomposition frame after
                    // the pop, during which Firestore's local cache drops the entry and the guard
                    // would otherwise pop again → past the NavHost root → blank screen.
                    var isBeingDeleted by remember { mutableStateOf(false) }
                    if (!isBeingDeleted && entries.none { it.id == id }) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                        return@composable
                    }
                    EntryDetailScreen(
                        entries = entries,
                        initialEntryId = id,
                        onEdit = { entryId -> navController.navigate(Screen.EditEntry.createRoute(entryId)) },
                        onDelete = { entryId ->
                            // Block the entries-change guard before popping (see isBeingDeleted).
                            isBeingDeleted = true
                            navController.popBackStack()
                            viewModel.deleteEntry(entryId)
                        },
                        onBack = { navController.popBackStack() },
                        onTagClick = { tag ->
                            viewModel.setTagFilter(tag)
                            navController.popBackStack(Screen.JournalList.route, inclusive = false)
                        },
                        onSaveEntry = { viewModel.saveEntry(it) },
                        onSuppressAutoLock = { viewModel.suppressAutoLockOnce() },
                        cachedDrivePhotos = cachedDrivePhotos,
                        coverHintCount = coverHintCount,
                        onIncrementCoverHintCount = { viewModel.incrementCoverHintCount() }
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
                        tagSuggestions = entries.tagsByFrequency(),
                        tripSuggestions = entries.toTripSuggestions(),
                        customMoods = viewModel.customMoods.collectAsState().value,
                        onAddCustomMood = { viewModel.addCustomMood(it) },
                        onSuppressAutoLock = { viewModel.suppressAutoLockOnce() }
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
                        },
                        onSubscription = { navController.navigate(Screen.Subscription.route) },
                        onSettings = { navController.navigate(Screen.Settings.route) },
                        onHelp = { navController.navigate(Screen.HelpAbout.route) },
                        onDeleteAccount = { password, callback -> viewModel.deleteAccount(password, callback) }
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
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Paywall.route) {
                    // After a successful purchase the entitlement flips; pop back into the journal.
                    LaunchedEffect(isPurchased) {
                        if (isPurchased == true) navController.popBackStack()
                    }
                    PurchaseScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        showFreeLimitNote = true
                    )
                }

                composable(Screen.HelpAbout.route) {
                    HelpAboutScreen(onBack = { navController.popBackStack() })
                }
            }
            } // Scaffold
        }
    }
}

private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val tabRoutes = setOf(Screen.JournalList.route, Screen.Adventures.route, Screen.Profile.route)

@Composable
private fun MacacoBottomNavBar(navController: NavController, currentRoute: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.primary) {
        val itemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.secondaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f),
            selectedTextColor = MaterialTheme.colorScheme.secondaryContainer,
            unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f),
            indicatorColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.20f)
        )
        NavigationBarItem(
            selected = currentRoute == Screen.JournalList.route,
            onClick = { navController.navigateToTab(Screen.JournalList.route) },
            colors = itemColors,
            icon = {
                Icon(
                    if (currentRoute == Screen.JournalList.route) Icons.Filled.AutoStories
                    else Icons.Outlined.AutoStories,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(R.string.nav_journal)) }
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Adventures.route,
            onClick = { navController.navigateToTab(Screen.Adventures.route) },
            colors = itemColors,
            icon = {
                Icon(
                    if (currentRoute == Screen.Adventures.route) Icons.Filled.Explore
                    else Icons.Outlined.Explore,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(R.string.drawer_adventures)) }
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Profile.route,
            onClick = { navController.navigateToTab(Screen.Profile.route) },
            colors = itemColors,
            icon = {
                Icon(
                    if (currentRoute == Screen.Profile.route) Icons.Filled.Person
                    else Icons.Outlined.Person,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(R.string.common_profile)) }
        )
    }
}
