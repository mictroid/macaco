package com.houseofmmminq.macaco.ui.screens

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.data.model.TravelEntry
import com.houseofmmminq.macaco.data.model.onThisDayEntries
import com.houseofmmminq.macaco.data.model.tagsByFrequency
import com.houseofmmminq.macaco.ui.components.MacacoBrandBlock
import com.houseofmmminq.macaco.ui.components.MacacoSnackbar
import com.houseofmmminq.macaco.ui.components.MacacoWatermarkBackground
import com.houseofmmminq.macaco.ui.theme.heroGradientColors
import com.houseofmmminq.macaco.ui.theme.macacoContentGutter
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel.ReelState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalListScreen(
    viewModel: JournalViewModel,
    onNewEntry: () -> Unit,
    onEntryClick: (String) -> Unit,
    onProfile: () -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val profilePhotoUri by viewModel.profilePhotoUri.collectAsState()
    val cachedDrivePhotos by viewModel.cachedDrivePhotos.collectAsState()
    val context = LocalContext.current
    // null = billing still loading; the whole list is already behind the purchase gate so this is
    // effectively always true here, but keep the explicit check so the reel button can't leak pre-gate.
    val isPurchased by viewModel.isPurchased.collectAsState()
    val reelState by viewModel.reelState.collectAsState()

    // Tag filter: tapping chips narrows the list to entries carrying any of the selected tags (OR).
    // State lives in the ViewModel so the detail screen can set it too.
    val selectedTags by viewModel.selectedTags.collectAsState()
    val allTags = remember(entries) { entries.tagsByFrequency() }
    // Filtered set is owned by the ViewModel so the entry-detail swipe pager shares it exactly.
    val visibleEntries by viewModel.visibleEntries.collectAsState()
    // Trips: when any visible entry has a tripName, switch to trip grouping.
    val hasTrips = remember(visibleEntries) { visibleEntries.any { !it.tripName.isNullOrBlank() } }

    // Trip sections: each named trip's entries, trips ordered by most recent entry first.
    val tripSections = remember(visibleEntries) {
        visibleEntries
            .filter { !it.tripName.isNullOrBlank() }
            .groupBy { it.tripName!! }
            .entries
            .sortedByDescending { (_, list) -> list.maxOf { it.dateMillis } }
            .map { (name, list) -> name to list.sortedByDescending { it.dateMillis } }
    }

    // Month sections: when trips are active, covers only entries with no trip; otherwise all entries.
    val monthSections = remember(visibleEntries, hasTrips) {
        val source = if (hasTrips) visibleEntries.filter { it.tripName.isNullOrBlank() } else visibleEntries
        // Group all entries of the same month into one section (keyed by year+month so the
        // section key is unique even when the source list isn't strictly date-ordered), then
        // order sections newest-first and entries within each section newest-first.
        source
            .groupBy { monthYear(it.dateMillis) }
            .entries
            .sortedByDescending { (_, list) -> list.maxOf { it.dateMillis } }
            .map { (month, list) -> month to list.sortedByDescending { it.dateMillis } }
    }

    // "On This Day" — entries from the same month+day in prior years.
    val onThisDayEntries = remember(entries) { entries.onThisDayEntries() }
    var onThisDayDismissed by remember { mutableStateOf(false) }

    // Hoisted list state drives the collapsing header: once the list scrolls away from the top,
    // the tall brand block gives way to the compact single-row header (also used in landscape).
    val listState = rememberLazyListState()
    val collapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 24
        }
    }

    // Surface cloud-sync failures (load/save/delete) as a snackbar.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.syncErrors.collect { snackbarHostState.showSnackbar(it) }
    }

    // Adventure Reel: when a render finishes, launch the share sheet; on error, snackbar it.
    LaunchedEffect(reelState) {
        when (val state = reelState) {
            is ReelState.Ready -> {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, state.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, context.getString(R.string.reel_share_chooser))
                )
                viewModel.reelConsumed()
            }
            is ReelState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.reelConsumed()
            }
            else -> Unit
        }
    }

    // Block with a progress dialog while a reel is generating.
    (reelState as? ReelState.Generating)?.let { gen ->
        ReelProgressDialog(
            tripName = gen.tripName,
            progress = gen.progress,
            onCancel = { viewModel.cancelReel() }
        )
    }

    Scaffold(
            topBar = {
                // Branded header: the splash's deep-teal radial fade behind the icon, with the
                // gold "macaco" wordmark styled to match the splash. The profile avatar rides the
                // top so Profile stays reachable (the drawer was retired — its items moved to
                // Profile). The tall centred brand block collapses to the compact single row both
                // in landscape (short screen) AND once the list scrolls (collapsed), animated.
                val isLandscape = LocalConfiguration.current.screenHeightDp < 480
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(macacoBrandBackground())
                        .statusBarsPadding()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .animateContentSize()
                ) {
                  // Collapsed (any orientation): the header reduces to a slim bar with just
                  // the macaco icon centred in the brand fade (the brand strip behind the
                  // status bar also stays). Not collapsed: portrait tall block / landscape
                  // compact row.
                  if (collapsed) {
                    // Icon keeps its full (uncollapsed) size; a bottom padding nudges it up so
                    // it falls nearer the centre of the brand fade.
                    MacacoBrandBlock(isLandscape = isLandscape, collapsed = true)
                  } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (isLandscape) 0.dp else 4.dp)
                            .animateContentSize()
                    ) {
                      if (isLandscape) {
                    // ── Compact landscape header: icon on its own centred row (matching
                    // Adventures & the portrait block), wordmark + count beneath it, avatar
                    // anchored to the top-end corner so Profile stays reachable. ──
                    Box(modifier = Modifier.fillMaxWidth()) {
                        MacacoBrandBlock(
                            isLandscape = true,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            if (entries.isNotEmpty()) {
                                val count = visibleEntries.size
                                val memoriesText = pluralStringResource(R.plurals.journal_list_memories, count, count)
                                Text(
                                    text = " · " + memoriesText +
                                        if (selectedTags.isNotEmpty()) " · ${stringResource(R.string.journal_list_filtered)}" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SplashGold.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(40.dp)
                                // Matches the list/tag-row gutter below, instead of a fixed 4dp —
                                // keeps the avatar aligned with content at every screen width.
                                .padding(end = macacoContentGutter()),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentUser != null) {
                                if (profilePhotoUri != null) {
                                    AsyncImage(
                                        model = profilePhotoUri,
                                        contentDescription = stringResource(R.string.common_profile),
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .clickable { onProfile() },
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
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
                    }
                  } else {
                    // The avatar rides the top edge; the brand block overlays it centered, so the
                    // icon sits flush at the top with no dead space above it. A 40dp leading spacer
                    // (matching the avatar anchor) keeps the row balanced now the hamburger is gone.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Matches the list/tag-row gutter below, instead of a fixed 4dp.
                            .padding(horizontal = macacoContentGutter()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.size(40.dp))
                        Spacer(Modifier.weight(1f))
                        if (currentUser != null) {
                            if (profilePhotoUri != null) {
                                AsyncImage(
                                    model = profilePhotoUri,
                                    contentDescription = stringResource(R.string.common_profile),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { onProfile() },
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
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

                    MacacoBrandBlock(
                        isLandscape = false,
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        // Slogan removed from this daily-open surface; it stays on the
                        // splash/login/purchase screens (the persuasion moments).
                        if (entries.isNotEmpty()) {
                            val count = visibleEntries.size
                            val memoriesText = pluralStringResource(R.plurals.journal_list_memories, count, count)
                            Text(
                                memoriesText + if (selectedTags.isNotEmpty()) " · ${stringResource(R.string.journal_list_filtered)}" else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = SplashGold.copy(alpha = 0.8f)
                            )
                        }
                    }
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
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    // Scaffold's contentWindowInsets is zeroed on this screen (hand-managed
                    // insets elsewhere), so the FAB needs its own — otherwise it renders behind
                    // the system nav bar in landscape (nav bar sits on the side edge there, not
                    // the bottom), as seen on the Galaxy A53.
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) { data -> MacacoSnackbar(data) } },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // safeDrawing (not just navigationBars) so BOTH the nav-bar side and the
                    // front-camera-cutout side get inset — matches the header (line 213) and
                    // Help & About's default Scaffold insets, so the two screens' content widths
                    // agree and entries never render behind the cutout.
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
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
                if (allTags.isNotEmpty()) {
                    TagFilterRow(
                        tags = allTags,
                        selected = selectedTags,
                        onToggle = { viewModel.toggleTagFilter(it) },
                        onClear = { viewModel.clearTagFilter() }
                    )
                }
                // Shared width rule (ContentWidth.kt): content capped at MacacoContentMaxWidth
                // and centred, so the journal list and Help & About line up on wide screens.
                val listHorizontalPadding = macacoContentGutter()
                when {
                    entries.isEmpty() -> EmptyState(modifier = Modifier.fillMaxSize())
                    visibleEntries.isEmpty() -> NoMatchState(modifier = Modifier.fillMaxSize())
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = listHorizontalPadding,
                            top = 10.dp,
                            end = listHorizontalPadding,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Banner is now the first LazyColumn item so the whole page
                        // is one scrollable unit — swipe up anywhere to reach entries.
                        if (onThisDayEntries.isNotEmpty() && !onThisDayDismissed) {
                            item(key = "on_this_day_banner") {
                                OnThisDayBanner(
                                    entries = onThisDayEntries,
                                    cachedDrivePhotos = cachedDrivePhotos,
                                    onEntryClick = onEntryClick,
                                    onDismiss = { onThisDayDismissed = true }
                                )
                            }
                        }
                        if (hasTrips) {
                            tripSections.forEach { (trip, sectionEntries) ->
                                item(key = "trip-header-$trip") {
                                    TripHeader(
                                        tripName = trip,
                                        entryCount = sectionEntries.size,
                                        isPurchased = isPurchased == true,
                                        onCreateReel = { viewModel.startReel(trip, sectionEntries) }
                                    )
                                }
                                items(sectionEntries, key = { it.id }) { entry ->
                                    EntryCard(
                                        entry = entry,
                                        cachedDrivePhotos = cachedDrivePhotos,
                                        selectedTags = selectedTags,
                                        onClick = { onEntryClick(entry.id) }
                                    )
                                }
                            }
                        }
                        monthSections.forEach { (month, sectionEntries) ->
                            item(key = "header-$month") { MonthHeader(month) }
                            items(sectionEntries, key = { it.id }) { entry ->
                                EntryCard(
                                    entry = entry,
                                    cachedDrivePhotos = cachedDrivePhotos,
                                    selectedTags = selectedTags,
                                    onClick = { onEntryClick(entry.id) }
                                )
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
    MacacoWatermarkBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
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
}

@Composable
private fun EntryPhotoArea(
    displayUris: List<String>,
    totalCount: Int,
    mood: String
) {
    val topCorners = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    // Taller photo area on tablets (sw600dp+) so the wide card isn't a squat landscape strip.
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val photoHeight = if (isTablet) 200.dp else 120.dp
    if (displayUris.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTablet) 120.dp else 72.dp)
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
            .height(photoHeight)
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
    onClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isDark) Modifier.border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            ),
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
                // Horizontal padding matches Help & About's cards (gutter + 16dp) so the two
                // list screens' content lines up on the left.
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Title expands to fill the row so the tags + mood are pushed to the right edge.
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (entry.tags.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        // Show the first two tags, then "+N" so the right cluster stays bounded.
                        val visibleTags = entry.tags.take(2)
                        val overflow = entry.tags.size - visibleTags.size
                        TagChips(
                            tags = visibleTags,
                            selectedTags = selectedTags
                        )
                        if (overflow > 0) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "+$overflow",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            formatDate(entry.dateMillis),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
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
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
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
            .padding(vertical = 8.dp),
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
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
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

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(140.dp)
            .then(
                if (isDark) Modifier.border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp)
                ) else Modifier
            ),
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

// Trip section header: a filled pill banner that opens each named-trip group in the list.
@Composable
private fun TripHeader(
    tripName: String,
    entryCount: Int,
    isPurchased: Boolean,
    onCreateReel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            tripName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f)
        )
        Text(
            pluralStringResource(R.plurals.journal_list_memories, entryCount, entryCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        if (isPurchased) {
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onCreateReel,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Filled.Videocam,
                    contentDescription = stringResource(R.string.reel_create_button),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// Blocking progress dialog shown while an Adventure Reel is being encoded.
@Composable
private fun ReelProgressDialog(
    tripName: String,
    progress: Float,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},   // not dismissible by back-tap while generating
        title = { Text(stringResource(R.string.reel_generating_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.reel_generating_subtitle, tripName),
                    style = MaterialTheme.typography.bodyMedium
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

// Month/year run header: a small gold-uppercase label with a hairline rule trailing off to the
// right, breaking the list into chapters instead of one unbroken stream of cards.
@Composable
private fun MonthHeader(month: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            month.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
        )
    }
}
