package com.example.myapplication.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.R
import com.example.myapplication.data.model.TravelEntry
import com.example.myapplication.data.model.onThisDayEntries
import com.example.myapplication.data.model.tagsByFrequency
import com.example.myapplication.ui.theme.heroGradientColors
import com.example.myapplication.ui.viewmodel.JournalViewModel
import com.example.myapplication.util.AppActions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalListScreen(
    viewModel: JournalViewModel,
    onNewEntry: () -> Unit,
    onEntryClick: (String) -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onSubscription: () -> Unit,
    onLogin: () -> Unit,
    onHelp: () -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val profilePhotoUri by viewModel.profilePhotoUri.collectAsState()
    val cachedDrivePhotos by viewModel.cachedDrivePhotos.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()

    val context = LocalContext.current
    // Toggle app lock from the drawer, reusing the same biometric-verify guard as Settings:
    // verify auth actually works before enabling, so a user can't lock themselves out.
    val toggleAppLock: (Boolean) -> Unit = { enable ->
        if (enable) {
            if (!isBiometricAvailable(context)) {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.settings_app_lock_unavailable),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } else {
                showBiometricPrompt(context) { viewModel.setAppLockEnabled(true) }
            }
        } else {
            viewModel.setAppLockEnabled(false)
        }
    }

    // Tag filter: tapping chips narrows the list to entries carrying any of the selected tags (OR).
    // State lives in the ViewModel so the detail screen can set it too.
    val selectedTags by viewModel.selectedTags.collectAsState()
    val allTags = remember(entries) { entries.tagsByFrequency() }
    val visibleEntries = remember(entries, selectedTags) {
        if (selectedTags.isEmpty()) entries
        else entries.filter { entry -> entry.tags.any { it in selectedTags } }
    }
    // Break the list into consecutive month/year runs so the LazyColumn can show a date header
    // before each run — adds structure without reordering the existing createdAt-desc list.
    val monthSections = remember(visibleEntries) {
        val out = mutableListOf<Pair<String, MutableList<TravelEntry>>>()
        visibleEntries.forEach { e ->
            val key = monthYear(e.dateMillis)
            if (out.isEmpty() || out.last().first != key) out.add(key to mutableListOf(e))
            else out.last().second.add(e)
        }
        out
    }

    // "On This Day" — entries from the same month+day in prior years.
    val onThisDayEntries = remember(entries) { entries.onThisDayEntries() }
    var onThisDayDismissed by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Surface cloud-sync failures (load/save/delete) as a snackbar.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.syncErrors.collect { snackbarHostState.showSnackbar(it) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // Brand-teal icon tint on every row so the menu feels designed, not default-grey.
            val drawerItemColors = NavigationDrawerItemDefaults.colors(
                unselectedIconColor = MaterialTheme.colorScheme.primary
            )
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                // Branded drawer header: the same splash teal fade + gold "macaco" wordmark as the
                // app header, with the monkey icon above the signed-in user's name.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(macacoBrandBackground())
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp)
                        )
                        Column(
                            modifier = Modifier.offset(y = (-8).dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "macaco",
                                color = SplashGoldBright,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = 5.sp
                            )
                            Text(
                                text = "Roam Freely. Forget Nothing.",
                                color = SplashGold.copy(alpha = 0.82f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (currentUser != null) currentUser!!.displayName else "Not signed in",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.common_profile)) },
                    selected = false,
                    colors = drawerItemColors,
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        onProfile()
                    }
                )

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.common_settings)) },
                    selected = false,
                    colors = drawerItemColors,
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        onSettings()
                    }
                )

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.common_subscription)) },
                    selected = false,
                    colors = drawerItemColors,
                    icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        onSubscription()
                    }
                )

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.settings_app_lock)) },
                    selected = false,
                    colors = drawerItemColors,
                    icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    badge = {
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { toggleAppLock(it) }
                        )
                    },
                    onClick = { toggleAppLock(!appLockEnabled) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                NavigationDrawerItem(
                    label = { Text(if (isDarkMode) stringResource(R.string.journal_list_switch_to_light) else stringResource(R.string.journal_list_switch_to_dark)) },
                    selected = false,
                    colors = drawerItemColors,
                    icon = {
                        Icon(
                            if (isDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = null
                        )
                    },
                    onClick = { viewModel.toggleDarkMode() }
                )

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_share_app)) },
                    selected = false,
                    colors = drawerItemColors,
                    icon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        AppActions.shareApp(context)
                    }
                )

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_rate_us)) },
                    selected = false,
                    colors = drawerItemColors,
                    icon = { Icon(Icons.Filled.StarRate, contentDescription = null) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        AppActions.requestReview(context)
                    }
                )

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_help)) },
                    selected = false,
                    colors = drawerItemColors,
                    icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        onHelp()
                    }
                )

                Spacer(Modifier.weight(1f))

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                if (currentUser != null) {
                    NavigationDrawerItem(
                        label = {
                            Text(stringResource(R.string.common_sign_out), color = MaterialTheme.colorScheme.error)
                        },
                        selected = false,
                    colors = drawerItemColors,
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                viewModel.signOut()
                            }
                        }
                    )
                } else {
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.common_sign_in)) },
                        selected = false,
                    colors = drawerItemColors,
                        icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        onClick = {
                            scope.launch { drawerState.close() }
                            onLogin()
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                // Branded header: the splash's deep-teal radial fade behind the icon, with the
                // gold "macaco" wordmark styled to match the splash. Kept compact; the drawer menu
                // and profile avatar ride the top so navigation stays reachable.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(macacoBrandBackground())
                        .statusBarsPadding()
                        .padding(bottom = 4.dp)
                ) {
                    // Menu and avatar ride the top edge; the brand block overlays them centered,
                    // so the icon sits flush at the top with no dead space above it.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.journal_list_menu_cd),
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (currentUser != null) {
                            if (profilePhotoUri != null) {
                                AsyncImage(
                                    model = profilePhotoUri,
                                    contentDescription = stringResource(R.string.common_profile),
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { onProfile() },
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .clickable { onProfile() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        currentUser!!.displayName.take(1).uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = SplashTealMid
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                        // Pull the wordmark up into the adaptive icon's bottom padding so it sits
                        // snug under the monkey, with the splash slogan and count beneath.
                        Column(
                            modifier = Modifier.offset(y = (-10).dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "macaco",
                                color = SplashGoldBright,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = 3.sp
                            )
                            Text(
                                text = "Roam Freely. Forget Nothing.",
                                color = SplashGold.copy(alpha = 0.82f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = 1.sp
                            )
                            if (entries.isNotEmpty()) {
                                val count = visibleEntries.size
                                val memoriesText = pluralStringResource(R.plurals.journal_list_memories, count, count)
                                Text(
                                    memoriesText + if (selectedTags.isNotEmpty()) " · ${stringResource(R.string.journal_list_filtered)}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SplashGold.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onNewEntry,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.common_new_entry)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // Faint teal wash from the top so the page isn't a flat slab behind the cards.
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            ) {
                if (onThisDayEntries.isNotEmpty() && !onThisDayDismissed) {
                    OnThisDayBanner(
                        entries = onThisDayEntries,
                        cachedDrivePhotos = cachedDrivePhotos,
                        onEntryClick = onEntryClick,
                        onDismiss = { onThisDayDismissed = true }
                    )
                }
                if (allTags.isNotEmpty()) {
                    TagFilterRow(
                        tags = allTags,
                        selected = selectedTags,
                        onToggle = { viewModel.toggleTagFilter(it) },
                        onClear = { viewModel.clearTagFilter() }
                    )
                }
                when {
                    entries.isEmpty() -> EmptyState(modifier = Modifier.fillMaxSize())
                    visibleEntries.isEmpty() -> NoMatchState(modifier = Modifier.fillMaxSize())
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        monthSections.forEach { (month, sectionEntries) ->
                            item(key = "header-$month") { MonthHeader(month) }
                            items(sectionEntries, key = { it.id }) { entry ->
                                EntryCard(
                                    entry = entry,
                                    cachedDrivePhotos = cachedDrivePhotos,
                                    selectedTags = selectedTags,
                                    onTagClick = { viewModel.toggleTagFilter(it) },
                                    onClick = { onEntryClick(entry.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagFilterRow(
    tags: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected.isNotEmpty()) {
            FilterChip(
                selected = false,
                onClick = onClear,
                label = { Text(stringResource(R.string.journal_list_filter_clear)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
        tags.forEach { tag ->
            FilterChip(
                selected = tag in selected,
                onClick = { onToggle(tag) },
                label = { Text("#$tag") }
            )
        }
    }
}

@Composable
private fun NoMatchState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔍", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.journal_list_no_match),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🗺️", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.journal_list_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.journal_list_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EntryPhotoArea(
    displayUris: List<String>,
    totalCount: Int,
    mood: String
) {
    val topCorners = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    if (displayUris.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(topCorners)
                .background(Brush.horizontalGradient(heroGradientColors())),
            contentAlignment = Alignment.Center
        ) {
            Text(mood.ifBlank { "🗺️" }, fontSize = 32.sp)
        }
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(topCorners)
    ) {
        when (displayUris.size) {
            1 -> AsyncImage(
                model = displayUris[0],
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            2 -> Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                displayUris.forEach { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            else -> Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Larger left photo
                AsyncImage(
                    model = displayUris[0],
                    contentDescription = null,
                    modifier = Modifier.weight(0.6f).fillMaxHeight(),
                    contentScale = ContentScale.Crop
                )
                // Two stacked on the right
                Column(
                    modifier = Modifier.weight(0.4f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AsyncImage(
                        model = displayUris[1],
                        contentDescription = null,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentScale = ContentScale.Crop
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = displayUris[2],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // "+N more" overlay when there are photos beyond the 3 shown
                        if (totalCount > 3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "+${totalCount - 3}",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryCard(
    entry: TravelEntry,
    cachedDrivePhotos: Map<String, String>,
    selectedTags: Set<String>,
    onTagClick: (String) -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Build display URIs for up to 3 photos, preferring Drive cache over local URIs.
            val totalPhotoCount = maxOf(entry.photoUris.size, entry.driveFileIds.size)
            val displayUris = (0 until minOf(totalPhotoCount, 3)).mapNotNull { i ->
                entry.driveFileIds.getOrNull(i)?.takeIf { it.isNotEmpty() }
                    ?.let { cachedDrivePhotos[it] }
                    ?: entry.photoUris.getOrNull(i)
            }
            EntryPhotoArea(
                displayUris = displayUris,
                totalCount = totalPhotoCount,
                mood = entry.mood
            )

            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (entry.tags.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        TagChips(
                            tags = entry.tags,
                            selectedTags = selectedTags,
                            onTagClick = onTagClick,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (entry.mood.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(entry.mood, fontSize = 14.sp)
                        }
                    }
                }

                // Location and date share one row to save vertical space; the location is
                // left-aligned (ellipsizing if long) and the date is pushed to the right.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (entry.location.isNotBlank()) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            entry.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SplashGold.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            formatDate(entry.dateMillis),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// Renders entry tags as small themed pill chips on a single row. Tapping a chip toggles
// that tag in the list filter; active filters are highlighted in the primary color. Chips
// that don't fit the constrained width are clipped rather than wrapping, keeping cards compact.
@Composable
private fun TagChips(
    tags: List<String>,
    selectedTags: Set<String>,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tags.forEach { tag ->
            val isSelected = tag in selectedTags
            Text(
                "#$tag",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                    .clickable { onTagClick(tag) }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun OnThisDayBanner(
    entries: List<TravelEntry>,
    cachedDrivePhotos: Map<String, String>,
    onEntryClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🗓️", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.journal_list_on_this_day),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.common_dismiss),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                entries.forEach { entry ->
                    OnThisDayEntryChip(
                        entry = entry,
                        cachedDrivePhotos = cachedDrivePhotos,
                        onClick = { onEntryClick(entry.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnThisDayEntryChip(
    entry: TravelEntry,
    cachedDrivePhotos: Map<String, String>,
    onClick: () -> Unit
) {
    val thumbnailUri = entry.driveFileIds.firstOrNull()
        ?.takeIf { it.isNotEmpty() }
        ?.let { cachedDrivePhotos[it] }
        ?: entry.photoUris.firstOrNull()

    val diff = run {
        val entryYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(entry.dateMillis)).toIntOrNull() ?: return@run 0
        val thisYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date()).toIntOrNull() ?: return@run 0
        thisYear - entryYear
    }
    val yearsAgo = if (diff > 0) pluralStringResource(R.plurals.journal_list_years_ago, diff, diff) else ""

    Card(
        onClick = onClick,
        modifier = Modifier.width(140.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            if (thumbnailUri != null) {
                AsyncImage(
                    model = thumbnailUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(Brush.horizontalGradient(heroGradientColors())),
                    contentAlignment = Alignment.Center
                ) {
                    Text(entry.mood.ifBlank { "🗺️" }, fontSize = 28.sp)
                }
            }
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    yearsAgo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.location.isNotBlank()) {
                    Text(
                        entry.location,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

internal fun formatDate(millis: Long): String =
    SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(millis))

internal fun monthYear(millis: Long): String =
    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(millis))

// Month/year run header: a small gold-uppercase label with a hairline rule trailing off to the
// right, breaking the list into chapters instead of one unbroken stream of cards.
@Composable
private fun MonthHeader(month: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            month.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = SplashGold
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = SplashGold.copy(alpha = 0.3f)
        )
    }
}
