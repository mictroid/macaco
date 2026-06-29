package com.houseofmmminq.macaco.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.houseofmmminq.macaco.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.houseofmmminq.macaco.data.model.TravelEntry
import com.houseofmmminq.macaco.ui.components.MacacoWatermarkBackground
import com.houseofmmminq.macaco.util.AppActions
import com.houseofmmminq.macaco.util.ImageStorage
import com.houseofmmminq.macaco.ui.theme.heroGradientColors
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun EntryDetailScreen(
    entries: List<TravelEntry>,
    initialEntryId: String,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    onTagClick: (String) -> Unit = {},
    onSaveEntry: (TravelEntry) -> Unit = {},
    onSuppressAutoLock: () -> Unit = {},
    cachedDrivePhotos: Map<String, String> = emptyMap(),
    coverHintCount: Int = 0,
    onIncrementCoverHintCount: () -> Unit = {}
) {
    val context = LocalContext.current
    // Swipe horizontally to move between entries. Opens on the tapped entry; the toolbar and the
    // delete dialog always act on whichever entry is currently in view.
    val initialIndex = remember(initialEntryId) {
        entries.indexOfFirst { it.id == initialEntryId }.coerceAtLeast(0)
    }
    val entriesPagerState = rememberPagerState(initialPage = initialIndex) { entries.size }
    // Set when the user deletes from this screen. onDelete (in NavGraph) already pops the back
    // stack, so once Firestore drops the entry and currentEntry goes null we must NOT pop again
    // here — a second pop falls past the list to a blank screen (the bug delete-blank-screen-v2
    // missed: this is a third pop trigger beyond the two NavGraph ones).
    var isDeleting by remember { mutableStateOf(false) }
    val currentEntry = entries.getOrNull(entriesPagerState.currentPage)
    if (currentEntry == null) {
        // List emptied out from under us (e.g. deleted from another device) — return to the list,
        // unless our own delete already initiated the pop.
        if (!isDeleting) {
            LaunchedEffect(Unit) { onBack() }
        }
        return
    }

    // Light haptic tick each time a swipe settles on a new entry. `drop(1)` skips the initial
    // (already-idle) state so opening the screen doesn't fire a tick.
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(entriesPagerState) {
        snapshotFlow { entriesPagerState.isScrollInProgress }
            .drop(1)
            .distinctUntilChanged()
            .collect { inProgress -> if (!inProgress) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    // Index of the photo to open the full-screen gallery at (null = gallery closed). Hoisted to the
    // screen so the overlay can cover everything, the app bar included.
    var galleryStartIndex by remember { mutableStateOf<Int?>(null) }

    // "+" add-photo tile picker. Persists picked images into the Macaco gallery (same as the edit
    // screen — the Photo Picker grant is temporary, so raw URIs wouldn't survive) then appends them
    // to the current entry and saves. rememberUpdatedState keeps the callback on the latest entry.
    val latestEntry = rememberUpdatedState(currentEntry)
    val addPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val stored = uris.mapNotNull { ImageStorage.persistToGallery(context, it) }
            if (stored.isNotEmpty()) {
                val e = latestEntry.value
                onSaveEntry(e.copy(photoUris = (e.photoUris + stored).distinct()))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.entry_detail_delete_title)) },
            text = {
                Text(stringResource(R.string.entry_detail_delete_message, currentEntry.title))
            },
            confirmButton = {
                TextButton(onClick = {
                    isDeleting = true
                    showDeleteDialog = false
                    onDelete(currentEntry.id)
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Orientation among entries — only worth showing once there's more than one.
                    if (entries.size > 1) {
                        AnimatedContent(
                            targetState = entriesPagerState.currentPage + 1,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                            },
                            label = "entryCounter"
                        ) { pageNumber ->
                            Text(
                                text = "$pageNumber / ${entries.size}",
                                style = MaterialTheme.typography.labelLarge,
                                // Match the header's back/share/edit/delete icons (onPrimary) so the
                                // counter is legible on the teal header across all themes.
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { shareEntry(context, currentEntry) }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.entry_detail_share_cd))
                    }
                    IconButton(onClick = { onEdit(currentEntry.id) }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.entry_detail_edit_cd))
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.entry_detail_delete_cd)
                        )
                    }
                },
                // Branded header: the active theme's primary colour with on-primary icons, so it
                // reads as a header band across all themes (not hardcoded teal).
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        HorizontalPager(
            state = entriesPagerState,
            // Reveals a sliver of the neighbouring entries so swiping reads as discoverable.
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { page ->
        val entry = entries[page]
        // How far this page is from the active one, in pages (0 = active, ±1 = adjacent).
        val pageOffset = entriesPagerState.currentPage - page + entriesPagerState.currentPageOffsetFraction
        val pageProximity = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
        val pageScale = lerp(start = 0.93f, stop = 1f, fraction = pageProximity)
        val pageAlpha = lerp(start = 0.75f, stop = 1f, fraction = pageProximity)

        // Each entry keeps its own scroll position, but lands back at the top whenever it becomes
        // the active page so swiping in never resumes mid-scroll.
        val listState = rememberLazyListState()
        var photoActionIndex by remember { mutableStateOf<Int?>(null) }
        var showCoverHint by remember { mutableStateOf(false) }
        LaunchedEffect(entry.id) {
            if (entry.photoUris.size > 1 && coverHintCount < 3) {
                showCoverHint = true
                onIncrementCoverHintCount()
                delay(4_000)
                showCoverHint = false
            }
        }
        LaunchedEffect(entriesPagerState.currentPage) {
            if (entriesPagerState.currentPage == page) {
                listState.scrollToItem(0)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pageScale
                    scaleY = pageScale
                    alpha = pageAlpha
                }
        ) {
            // Subtle macaco watermark behind the content when the entry has no description, so the
            // empty lower area doesn't read as dead space.
            if (entry.description.isBlank()) {
                MacacoWatermarkBackground(modifier = Modifier.matchParentSize())
            }
            val photoCount = maxOf(entry.photoUris.size, entry.driveFileIds.size)
            photoActionIndex?.let { idx ->
                PhotoActionSheet(
                    index = idx,
                    total = photoCount,
                    onSetCover = { onSaveEntry(entry.withCover(idx)) },
                    onMoveLeft = { onSaveEntry(entry.withSwapped(idx, idx - 1)) },
                    onMoveRight = { onSaveEntry(entry.withSwapped(idx, idx + 1)) },
                    onRemove = { onSaveEntry(entry.withRemoved(idx)) },
                    onDismiss = { photoActionIndex = null }
                )
            }
            // In landscape on phones (short screen) a full-width hero photo fills most of the
            // viewport and the title/text fall below the fold. For entries WITH photos, switch to
            // a two-panel Row (photo left, scrollable text right). Photoless entries and tablets
            // (tall screens) keep the portrait single-column layout below.
            val isLandscape = LocalConfiguration.current.screenHeightDp < 480
            if (isLandscape && photoCount > 0) {
                val launchAddPhoto = {
                    onSuppressAutoLock()
                    addPhotoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left panel: photo / collage fills the panel height.
                    Box(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                            .clipToBounds()
                            .graphicsLayer { translationX = pageOffset * size.width * 0.4f }
                    ) {
                        when {
                            photoCount == 1 -> JournalPhoto(
                                data = entry.displayPhotoUri(0, cachedDrivePhotos),
                                onClick = { galleryStartIndex = 0 },
                                onLongClick = { photoActionIndex = 0 },
                                modifier = Modifier.fillMaxSize()
                            )
                            else -> {
                                val rightCount = if (photoCount <= 4) photoCount - 1 else 2
                                val overflowStart = 1 + rightCount
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        JournalPhoto(
                                            data = entry.displayPhotoUri(0, cachedDrivePhotos),
                                            onClick = { galleryStartIndex = 0 },
                                            onLongClick = { photoActionIndex = 0 },
                                            modifier = Modifier.weight(0.65f).fillMaxHeight()
                                        )
                                        Column(
                                            modifier = Modifier.weight(0.35f).fillMaxHeight(),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            for (index in 1..rightCount) {
                                                JournalThumb(
                                                    data = entry.displayPhotoUri(index, cachedDrivePhotos),
                                                    onClick = { galleryStartIndex = index },
                                                    onLongClick = { photoActionIndex = index },
                                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                                )
                                            }
                                            if (photoCount <= 4) {
                                                AddPhotoTile(
                                                    onClick = launchAddPhoto,
                                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                    if (photoCount > 4) {
                                        Spacer(Modifier.height(2.dp))
                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            items(photoCount - overflowStart) { j ->
                                                val index = j + overflowStart
                                                JournalThumb(
                                                    data = entry.displayPhotoUri(index, cachedDrivePhotos),
                                                    onClick = { galleryStartIndex = index },
                                                    onLongClick = { photoActionIndex = index },
                                                    modifier = Modifier.size(64.dp)
                                                )
                                            }
                                            item {
                                                AddPhotoTile(
                                                    onClick = launchAddPhoto,
                                                    modifier = Modifier.size(64.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Right panel: text content, scrollable.
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                    ) {
                        item {
                            AnimatedVisibility(
                                visible = showCoverHint,
                                enter = fadeIn(tween(300)),
                                exit = fadeOut(tween(500))
                            ) {
                                Text(
                                    text = stringResource(R.string.entry_detail_cover_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp, bottom = 2.dp)
                                )
                            }
                        }
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    entry.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (entry.mood.isNotBlank()) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(entry.mood) },
                                            border = null,
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        )
                                    }
                                    if (entry.location.isNotBlank()) {
                                        AssistChip(
                                            onClick = { AppActions.openMapsSearch(context, entry.location) },
                                            label = { Text(entry.location) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.LocationOn,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = AssistChipDefaults.assistChipColors(
                                                labelColor = MaterialTheme.colorScheme.primary,
                                                leadingIconContentColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(formatDate(entry.dateMillis)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.DateRange,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        border = null,
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    )
                                }
                                if (!entry.tripName.isNullOrBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("✈️", fontSize = 14.sp)
                                        Text(
                                            entry.tripName!!,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                if (entry.description.isNotBlank()) {
                                    Text(
                                        entry.description,
                                        style = MaterialTheme.typography.bodyLarge,
                                        lineHeight = 28.sp
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                            .clickable(onClick = { onEdit(entry.id) })
                                            .padding(24.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.entry_detail_add_story),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (entry.tags.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        entry.tags.forEach { tag ->
                                            AssistChip(
                                                onClick = { onTagClick(tag) },
                                                label = { Text("#$tag") },
                                                border = null,
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item {
                // Parallax: the header moves at ~40% of the swipe offset, clipped so it never
                // bleeds past the page edge.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .graphicsLayer { translationX = pageOffset * size.width * 0.4f }
                ) {
                val configuration = LocalConfiguration.current
                val heroHeight = if (configuration.screenWidthDp >= 600) {
                    (configuration.screenHeightDp * 0.52f).dp
                } else {
                    260.dp
                }
                // Promote the photo at [index] to the cover (hero) and confirm with a toast.
                val setCover: (Int) -> Unit = { index ->
                    onSaveEntry(entry.withCover(index))
                    Toast.makeText(
                        context,
                        context.getString(R.string.entry_detail_set_cover),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                val launchAddPhoto = {
                    onSuppressAutoLock()
                    addPhotoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                when {
                    photoCount == 0 -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Brush.verticalGradient(heroGradientColors())),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(entry.mood.ifBlank { "🗺️" }, fontSize = 72.sp)
                    }

                    // Single photo — full-width hero.
                    photoCount == 1 -> JournalPhoto(
                        data = entry.displayPhotoUri(0, cachedDrivePhotos),
                        onClick = { galleryStartIndex = 0 },
                        onLongClick = { photoActionIndex = 0 },
                        modifier = Modifier.fillMaxWidth().height(heroHeight)
                    )

                    // Editorial collage: hero (left, ~65%) + stacked thumbnails on the right (~35%).
                    // The right column holds all remaining thumbnails when there are ≤4 photos (so a
                    // 4th photo never sits alone in an overflow row); for 5+ it caps at 2 and the
                    // rest flow into a horizontally scrollable overflow strip. A "+" tile to add more
                    // photos is the last slot (in the right column for ≤4, in the overflow row for 5+).
                    // Tap a photo → gallery at that index; long-press a thumbnail → set as cover.
                    else -> {
                        val rightCount = if (photoCount <= 4) photoCount - 1 else 2
                        val overflowStart = 1 + rightCount
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(heroHeight),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                JournalPhoto(
                                    data = entry.displayPhotoUri(0, cachedDrivePhotos),
                                    onClick = { galleryStartIndex = 0 },
                                    onLongClick = { photoActionIndex = 0 },
                                    modifier = Modifier.weight(0.65f).fillMaxHeight()
                                )
                                Column(
                                    modifier = Modifier.weight(0.35f).fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    for (index in 1..rightCount) {
                                        JournalThumb(
                                            data = entry.displayPhotoUri(index, cachedDrivePhotos),
                                            onClick = { galleryStartIndex = index },
                                            onLongClick = { photoActionIndex = index },
                                            modifier = Modifier.weight(1f).fillMaxWidth()
                                        )
                                    }
                                    // "+" lives here only when there's no overflow row (≤4 photos).
                                    if (photoCount <= 4) {
                                        AddPhotoTile(
                                            onClick = launchAddPhoto,
                                            modifier = Modifier.weight(1f).fillMaxWidth()
                                        )
                                    }
                                }
                            }
                            if (photoCount > 4) {
                                Spacer(Modifier.height(2.dp))
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    items(photoCount - overflowStart) { j ->
                                        val index = j + overflowStart
                                        JournalThumb(
                                            data = entry.displayPhotoUri(index, cachedDrivePhotos),
                                            onClick = { galleryStartIndex = index },
                                            onLongClick = { photoActionIndex = index },
                                            modifier = Modifier.size(80.dp)
                                        )
                                    }
                                    item {
                                        AddPhotoTile(
                                            onClick = launchAddPhoto,
                                            modifier = Modifier.size(80.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
                AnimatedVisibility(
                    visible = showCoverHint,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(500))
                ) {
                    Text(
                        text = stringResource(R.string.entry_detail_cover_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp)
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Metadata chips: mood · location · date. FlowRow wraps to a second line when
                    // the row runs out of width, so the date chip is never truncated.
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Mood + date are filled accent chips (secondaryContainer = the amber accent
                        // in the Macaco theme, matching the journal-list date pill and adapting per
                        // theme). Location keeps an outlined look, tinted in the primary colour.
                        if (entry.mood.isNotBlank()) {
                            AssistChip(
                                onClick = {},
                                label = { Text(entry.mood) },
                                border = null,
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                        if (entry.location.isNotBlank()) {
                            AssistChip(
                                onClick = { AppActions.openMapsSearch(context, entry.location) },
                                label = { Text(entry.location) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    labelColor = MaterialTheme.colorScheme.primary,
                                    leadingIconContentColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text(formatDate(entry.dateMillis)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            border = null,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                    if (!entry.tripName.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("✈️", fontSize = 14.sp)
                            Text(
                                entry.tripName!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    if (entry.description.isNotBlank()) {
                        Text(
                            entry.description,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 28.sp
                        )
                    } else {
                        // No description yet — invite the user to add one instead of leaving the
                        // lower half of the screen blank. Tapping opens the editor.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                .clickable(onClick = { onEdit(entry.id) })
                                .padding(24.dp)
                        ) {
                            Text(
                                stringResource(R.string.entry_detail_add_story),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Subtle branded fade so the empty lower area doesn't read as dead space.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                        )
                                    )
                                )
                        )
                    }

                    if (entry.tags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            entry.tags.forEach { tag ->
                                // Teal-tinted (primaryContainer) so tags read as a distinct group
                                // from the amber (secondaryContainer) mood/date chips above.
                                AssistChip(
                                    onClick = { onTagClick(tag) },
                                    label = { Text("#$tag") },
                                    border = null,
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
            }
            }

            // Soft fade into the background at the very bottom, giving the scroll visual closure
            // regardless of theme (uses the active background colour, not a hardcoded grey).
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                        )
                    )
            )
        }
        }
    }

        // Full-screen swipeable gallery over the current entry's photos, layered above the whole
        // screen (app bar included). Opens at the tapped index; tap a photo or the close button to
        // dismiss.
        galleryStartIndex?.let { startIndex ->
            val count = maxOf(currentEntry.photoUris.size, currentEntry.driveFileIds.size)
            val galleryPagerState = rememberPagerState(
                initialPage = startIndex.coerceIn(0, (count - 1).coerceAtLeast(0)),
                pageCount = { count }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                HorizontalPager(state = galleryPagerState, modifier = Modifier.fillMaxSize()) { page ->
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(currentEntry.displayPhotoUri(page, cachedDrivePhotos))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { galleryStartIndex = null }
                    )
                }
                if (count > 1) {
                    Text(
                        text = "${galleryPagerState.currentPage + 1} / $count",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 12.dp)
                    )
                }
                IconButton(
                    onClick = { galleryStartIndex = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Resolves the URI to display for the photo at [index]: prefers the cached Drive copy (downloaded
 * on this device), falling back to the local URI (which may be unreadable on a device that didn't
 * add the photo).
 */
private fun TravelEntry.displayPhotoUri(index: Int, cached: Map<String, String>): String? =
    driveFileIds.getOrNull(index)?.takeIf { it.isNotEmpty() }?.let { cached[it] }
        ?: photoUris.getOrNull(index)

/**
 * A copy of this entry with the photo at [index] moved to the front (the cover/hero). Moves the
 * parallel [TravelEntry.driveFileIds] entry too so the two lists stay aligned.
 */
private fun TravelEntry.withCover(index: Int): TravelEntry {
    if (index <= 0 || index >= photoUris.size) return this
    val photos = photoUris.toMutableList().apply { add(0, removeAt(index)) }
    val driveIds = driveFileIds.toMutableList().apply { if (index < size) add(0, removeAt(index)) }
    return copy(photoUris = photos, driveFileIds = driveIds)
}

private fun TravelEntry.withRemoved(index: Int): TravelEntry {
    if (index < 0 || index >= photoUris.size) return this
    val photos = photoUris.toMutableList().apply { removeAt(index) }
    val driveIds = driveFileIds.toMutableList().apply { if (index < size) removeAt(index) }
    return copy(photoUris = photos, driveFileIds = driveIds)
}

private fun TravelEntry.withSwapped(a: Int, b: Int): TravelEntry {
    if (a == b || a < 0 || b < 0 || a >= photoUris.size || b >= photoUris.size) return this
    val photos = photoUris.toMutableList().also { list -> val tmp = list[a]; list[a] = list[b]; list[b] = tmp }
    val driveIds = if (a < driveFileIds.size && b < driveFileIds.size) {
        driveFileIds.toMutableList().also { list -> val tmp = list[a]; list[a] = list[b]; list[b] = tmp }
    } else driveFileIds
    return copy(photoUris = photos, driveFileIds = driveIds)
}

/** A single journal photo, cropped to fill its slot, tappable to open the full-screen viewer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JournalPhoto(data: String?, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, modifier: Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(data).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick ?: {})
    )
}

/** A "+" tile matching the thumbnail slots, opening the photo picker to add photos to the entry. */
@Composable
private fun AddPhotoTile(onClick: () -> Unit, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = stringResource(R.string.entry_detail_add_photo),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
    }
}

/** A non-hero photo thumbnail: tap opens the gallery, long-press promotes it to the cover. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JournalThumb(data: String?, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(data).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoActionSheet(
    index: Int,
    total: Int,
    onSetCover: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            if (index > 0) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.entry_detail_photo_set_cover)) },
                    leadingContent = { Icon(Icons.Filled.Star, contentDescription = null) },
                    modifier = Modifier.clickable { onSetCover(); onDismiss() }
                )
            }
            if (index > 0) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.entry_detail_photo_move_left)) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null) },
                    modifier = Modifier.clickable { onMoveLeft(); onDismiss() }
                )
            }
            if (index < total - 1) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.entry_detail_photo_move_right)) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable { onMoveRight(); onDismiss() }
                )
            }
            HorizontalDivider()
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.entry_detail_photo_remove),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable { onRemove(); onDismiss() }
            )
        }
    }
}

private fun shareEntry(context: Context, entry: TravelEntry) {
    val dateStr = formatDate(entry.dateMillis)
    val shareText = buildString {
        appendLine(entry.title)

        // Location + date on one line.
        val locationLine = listOfNotNull(
            entry.location.takeIf { it.isNotBlank() },
            dateStr.takeIf { it.isNotBlank() }
        ).joinToString("  ·  ")
        if (locationLine.isNotBlank()) appendLine("📍 $locationLine")

        // Description snippet — capped so the share doesn't dwarf the rest of the message.
        if (entry.description.isNotBlank()) {
            appendLine()
            if (entry.description.length <= 300) {
                append(entry.description)
            } else {
                append(entry.description.take(300))
                append("…")
            }
        }

        // Soft app credit — organic word-of-mouth, not an advertisement.
        appendLine()
        appendLine()
        append("— shared from Macaco")
    }

    // Photos live in app-internal storage (file:// URIs), which other apps can't read directly.
    // Expose each through our FileProvider so the share target gets a readable content:// URI.
    val authority = "${context.packageName}.fileprovider"
    val shareUris = ArrayList<Uri>(
        entry.photoUris.mapNotNull { uriString ->
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "file" -> uri.path?.let { path ->
                    runCatching { FileProvider.getUriForFile(context, authority, File(path)) }.getOrNull()
                }
                else -> uri // already a content:// URI — pass through
            }
        }
    )

    val intent = when {
        // No photos — plain text share.
        shareUris.isEmpty() -> Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }

        // Single photo — image + caption together (one image never gets split).
        shareUris.size == 1 -> Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, shareUris[0])
            putExtra(Intent.EXTRA_TEXT, shareText)
            clipData = ClipData.newUri(context.contentResolver, "Macaco photo", shareUris[0])
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Multiple photos — omit EXTRA_TEXT so targets like WhatsApp group them into a single
        // album instead of one message per photo (a caption alongside multiple images pushes
        // WhatsApp onto its split-per-image path). The caption can't ride along in that case, so
        // copy it to the clipboard for the user to paste into the album caption field.
        else -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Macaco entry", shareText))
            Toast.makeText(
                context,
                "Caption copied — paste it into the photo caption",
                Toast.LENGTH_LONG
            ).show()
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
                // A ClipData listing every URI is what propagates the read grant to all of them.
                clipData = ClipData.newUri(context.contentResolver, "Macaco photos", shareUris[0]).apply {
                    for (i in 1 until shareUris.size) addItem(ClipData.Item(shareUris[i]))
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
    context.startActivity(Intent.createChooser(intent, "Share your memory"))
}
