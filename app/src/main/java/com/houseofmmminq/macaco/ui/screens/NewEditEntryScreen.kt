package com.houseofmmminq.macaco.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.data.model.TravelEntry
import com.houseofmmminq.macaco.ui.components.MacacoWatermarkBackground
import com.houseofmmminq.macaco.ui.components.VideoTrimDialog
import com.houseofmmminq.macaco.util.Cities
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel
import com.houseofmmminq.macaco.util.ImageStorage
import com.houseofmmminq.macaco.util.SUGGESTED_TAGS
import com.houseofmmminq.macaco.util.VideoThumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.houseofmmminq.macaco.util.VideoTranscoder
import java.util.Locale
import java.util.UUID

// Savers so the form's List/Set state survives process death (phone lock → low-memory kill →
// unlock recreates the Activity). Bundles can't serialise List/Set directly, so round-trip via List.
private val StringListSaver = listSaver<List<String>, String>(
    save = { it },
    restore = { it }
)

private val StringSetSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() }
)

private val MOODS = listOf(
    "🤩", // Amazed
    "😌", // Peaceful
    "🥳", // Celebrating
    "😍", // Loved it
    "🫶", // Grateful
    "🥹", // Moved / touched
    "🤠", // Adventurous
    "😤", // Challenged / tough day
    "😴", // Exhausted
    "🔥", // Thrilled / on fire
    "💫", // Magical
    "🌿", // In nature / grounded
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEditEntryScreen(
    existingEntry: TravelEntry?,
    onSave: (TravelEntry) -> Unit,
    onBack: () -> Unit,
    locationSuggestions: List<String> = emptyList(),
    tagSuggestions: List<String> = emptyList(),
    tripSuggestions: List<String> = emptyList(),
    customMoods: List<String> = emptyList(),
    onAddCustomMood: (String) -> Unit = {},
    onSuppressAutoLock: () -> Unit = {},
    // Pre-fill seed from an accepted camera-roll suggestion (create-mode only). onSeedConsumed
    // clears the one-shot seed once this screen has captured it into local state.
    seed: JournalViewModel.EntrySeed? = null,
    onSeedConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    // An existingEntry always wins in edit mode; the suggestion seed only applies to new entries.
    val effectiveSeed = if (existingEntry != null) null else seed

    // rememberSaveable so an in-progress draft survives process death (phone lock → low-memory kill).
    var title by rememberSaveable { mutableStateOf(existingEntry?.title ?: effectiveSeed?.title ?: "") }
    var location by rememberSaveable { mutableStateOf(existingEntry?.location ?: effectiveSeed?.location ?: "") }
    var dateMillis by rememberSaveable {
        mutableStateOf(existingEntry?.dateMillis ?: effectiveSeed?.dateMillis ?: System.currentTimeMillis())
    }
    var mood by rememberSaveable { mutableStateOf(existingEntry?.mood ?: "") }
    var description by rememberSaveable { mutableStateOf(existingEntry?.description ?: "") }
    var photoUris by rememberSaveable(stateSaver = StringListSaver) {
        mutableStateOf(existingEntry?.photoUris ?: emptyList())
    }
    // Parallel Drive-file ids, padded to photoUris length ("" = not uploaded). Every photo
    // add/remove/reorder below mutates BOTH lists so the pairing survives editing.
    var driveIds by rememberSaveable(stateSaver = StringListSaver) {
        mutableStateOf(
            List(existingEntry?.photoUris?.size ?: 0) { i ->
                existingEntry?.driveFileIds?.getOrNull(i) ?: ""
            }
        )
    }
    var tags by rememberSaveable(stateSaver = StringListSaver) {
        mutableStateOf(existingEntry?.tags ?: emptyList())
    }
    var tripName by rememberSaveable { mutableStateOf(existingEntry?.tripName ?: "") }
    var titleError by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    // Files we copied into storage this session. Any that aren't committed via Save (removed again,
    // or the screen is dismissed) must be deleted so picker copies don't leak. Committed-photo
    // cleanup on edit/delete happens in the ViewModel instead. Saved across process death so those
    // gallery copies don't become orphaned if the entry is later cancelled.
    var sessionAdded by rememberSaveable(stateSaver = StringSetSaver) {
        mutableStateOf(emptySet<String>())
    }

    // ── Video state ──────────────────────────────────────────────────────────────
    // Max 3 videos total per entry.
    val MAX_VIDEOS = 3
    var videoUris by rememberSaveable(stateSaver = StringListSaver) {
        mutableStateOf(existingEntry?.videoUris ?: emptyList())
    }
    var videoFileIds by rememberSaveable(stateSaver = StringListSaver) {
        mutableStateOf(
            List(existingEntry?.videoUris?.size ?: 0) { i ->
                existingEntry?.videoFileIds?.getOrNull(i) ?: ""
            }
        )
    }
    // mediaOrder defines the display sequence of all media (photos + videos combined).
    // If empty (new or legacy entry), derived as photos-first then videos.
    var mediaOrder by rememberSaveable(stateSaver = StringListSaver) {
        mutableStateOf(existingEntry?.mediaOrder ?: emptyList())
    }

    var showVideoSourceDialog by remember { mutableStateOf(false) }
    var pendingVideoUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingVideoUri: Uri? = pendingVideoUriString?.let(Uri::parse)

    // Long videos waiting for the trim dialog — head of the list is the one on screen. One dialog
    // pops per queue head, so multi-selecting several >15 s clips no longer drops all but the last.
    // `remember`, not saveable: a Uri queue of picker grants wouldn't survive process death anyway.
    var trimQueue by remember { mutableStateOf<List<Pair<Uri, Long>>>(emptyList()) }

    // Transcoding progress — null when idle, 0f→1f when active.
    var transcodingProgress by remember { mutableStateOf<Float?>(null) }

    val coroutineScope = rememberCoroutineScope()

    // Shared store-the-result step for every transcode path (record, pick, trim). Toasts when the
    // clip couldn't be processed (e.g. the fallback refused an over-length source per Change 1).
    val storeTranscoded: (java.io.File?) -> Unit = { file ->
        if (file == null) {
            android.widget.Toast.makeText(
                context, context.getString(R.string.video_add_failed), android.widget.Toast.LENGTH_LONG
            ).show()
        } else {
            ImageStorage.persistVideoToGallery(context, file)?.let { stored ->
                sessionAdded = sessionAdded + stored
                videoUris = videoUris + stored
                videoFileIds = videoFileIds + ""
                mediaOrder = mediaOrder + stored
            }
            file.delete()
        }
    }

    // Derived display list: uri → "photo" or "video". Follows mediaOrder for any uris it lists, then
    // appends anything not yet in mediaOrder (e.g. photos added via the photo picker, which don't
    // touch mediaOrder) so media is NEVER dropped from the row when mediaOrder is partial.
    val displayMedia: List<Pair<String, String>> = remember(mediaOrder, photoUris, videoUris) {
        val ordered = mediaOrder.mapNotNull { uri ->
            when {
                uri in photoUris -> uri to "photo"
                uri in videoUris -> uri to "video"
                else -> null
            }
        }
        val seen = ordered.map { it.first }.toSet()
        val leftover = photoUris.filter { it !in seen }.map { it to "photo" } +
            videoUris.filter { it !in seen }.map { it to "video" }
        ordered + leftover
    }

    val isDatePickerLandscape = LocalConfiguration.current.screenHeightDp < 480
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dateMillis,
        initialDisplayMode = if (isDatePickerLandscape) DisplayMode.Input else DisplayMode.Picker,
    )

    var showPhotoSourceDialog by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        // Copy each picked image into the shared Pictures collection so it survives both relaunches
        // (the Photo Picker grant is temporary) and uninstalls. See ImageStorage.persistToGallery.
        val stored = uris.mapNotNull { ImageStorage.persistToGallery(context, it) }
        sessionAdded = sessionAdded + stored
        // No .distinct(): persistToGallery always creates a fresh uniquely-named copy so
        // duplicates can't occur, and distinct() would break the parallel-list invariant.
        photoUris = photoUris + stored
        driveIds = driveIds + List(stored.size) { "" }
    }

    // Camera capture: the camera app writes into a FileProvider temp file, which we then copy into
    // the shared gallery (same destination as picked photos) so it's Drive-syncable and persists.
    // Saveable as a string: the camera app backgrounds us and the OS may kill the process;
    // without this the captured photo is lost on return.
    var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingCameraUri: android.net.Uri? = pendingCameraUriString?.let(android.net.Uri::parse)
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val captured = pendingCameraUri
        if (success && captured != null) {
            ImageStorage.persistToGallery(context, captured)?.let { stored ->
                sessionAdded = sessionAdded + stored
                photoUris = photoUris + stored
                driveIds = driveIds + ""
            }
        }
        ImageStorage.clear(context, ImageStorage.CAMERA_TEMP)
        pendingCameraUriString = null
    }
    val launchCamera = {
        ImageStorage.newCameraTempUri(context)?.let { uri ->
            pendingCameraUriString = uri.toString()
            onSuppressAutoLock()
            cameraLauncher.launch(uri)
        }
    }

    // Seed photos from an accepted camera-roll suggestion. The seed carries the ORIGINAL
    // camera-roll URIs, which (unlike the picker/camera flows above) haven't been copied into
    // Pictures/Macaco yet — so persist them the same way here so they survive relaunch/uninstall
    // and are Drive-syncable, then attach. Runs once; the seed is then consumed so it can't refire
    // (photoUris is rememberSaveable, so a later process-death restore won't re-add).
    LaunchedEffect(Unit) {
        if (effectiveSeed != null) {
            val stored = withContext(Dispatchers.IO) {
                effectiveSeed.photoUris.mapNotNull {
                    ImageStorage.persistToGallery(context, android.net.Uri.parse(it))
                }
            }
            if (stored.isNotEmpty()) {
                sessionAdded = sessionAdded + stored
                photoUris = photoUris + stored
                driveIds = driveIds + List(stored.size) { "" }
            }
            onSeedConsumed()
        }
    }

    // Video recording: system camera writes into a FileProvider temp .mp4 (≤15s via the duration
    // limit extra), which we transcode then copy into the shared Movies collection.
    val videoRecordLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val captured = pendingVideoUri
        pendingVideoUriString = null
        if (result.resultCode == Activity.RESULT_OK && captured != null) {
            transcodingProgress = 0f
            coroutineScope.launch {
                // Recorded clip is ≤ 15 s so no trim needed — transcode directly.
                val outFile = VideoTranscoder.transcode(
                    context, captured, onProgress = { transcodingProgress = it }
                )
                transcodingProgress = null
                ImageStorage.clear(context, ImageStorage.VIDEO_TEMP)
                storeTranscoded(outFile)
            }
        } else {
            ImageStorage.clear(context, ImageStorage.VIDEO_TEMP)
        }
    }

    val launchVideoRecord = {
        ImageStorage.newVideoTempUri(context)?.let { uri ->
            pendingVideoUriString = uri.toString()
            onSuppressAutoLock()
            val intent = Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE).apply {
                putExtra(android.provider.MediaStore.EXTRA_DURATION_LIMIT, 15)
                putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
                // The camera app must be granted write access to our FileProvider temp URI, or it
                // can't save the clip (TakePicture grants this internally; a manual intent must not).
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            videoRecordLauncher.launch(intent)
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_VIDEOS)
    ) { uris ->
        val canAdd = MAX_VIDEOS - videoUris.size
        if (canAdd <= 0) return@rememberLauncherForActivityResult
        val (short, long) = uris.take(canAdd).partition { uri ->
            VideoTranscoder.getDurationMs(context, uri) <= VideoTranscoder.MAX_DURATION_MS
        }
        // Long clips queue for one-at-a-time trim dialogs.
        trimQueue = trimQueue + long.map { it to VideoTranscoder.getDurationMs(context, it) }
        // Short clips transcode SEQUENTIALLY in one coroutine so the shared progress overlay stays
        // up (and coherent) until the last one finishes.
        if (short.isNotEmpty()) {
            transcodingProgress = 0f
            coroutineScope.launch {
                short.forEach { uri ->
                    val outFile = VideoTranscoder.transcode(
                        context, uri, onProgress = { transcodingProgress = it }
                    )
                    storeTranscoded(outFile)
                }
                transcodingProgress = null
            }
        }
    }

    // Speech-to-text: result is appended to the description with a space separator.
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!text.isNullOrEmpty()) {
                description = if (description.isBlank()) text else "$description $text"
            }
        }
    }

    // Dismissing without Save: drop any files added this session, since they were never committed.
    val cancel = {
        ImageStorage.delete(context, sessionAdded)
        onBack()
    }
    BackHandler(onBack = cancel)

    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = { Text(stringResource(R.string.new_entry_add_photo)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoSourceDialog = false
                                launchCamera()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.new_entry_photo_take), style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoSourceDialog = false
                                onSuppressAutoLock()
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.new_entry_photo_choose), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoSourceDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showVideoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showVideoSourceDialog = false },
            title = { Text(stringResource(R.string.new_entry_add_video)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showVideoSourceDialog = false
                                if (videoUris.size < MAX_VIDEOS) launchVideoRecord()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.new_entry_video_record), style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showVideoSourceDialog = false
                                if (videoUris.size < MAX_VIDEOS) {
                                    onSuppressAutoLock()
                                    videoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.new_entry_video_gallery), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showVideoSourceDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showDatePicker) {
        // Brand the picker: surface background + macaco teal (primary) accents, so it stops
        // looking like a foreign Material default (surfaceContainerHigh lavender).
        val pickerColors = DatePickerDefaults.colors(
            containerColor            = MaterialTheme.colorScheme.surface,
            headlineContentColor      = MaterialTheme.colorScheme.primary,
            weekdayContentColor       = MaterialTheme.colorScheme.primary,
            selectedDayContainerColor = MaterialTheme.colorScheme.primary,
            selectedDayContentColor   = MaterialTheme.colorScheme.onPrimary,
            todayContentColor         = MaterialTheme.colorScheme.primary,
            todayDateBorderColor      = MaterialTheme.colorScheme.primary,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMillis = it }
                    showDatePicker = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.common_cancel)) }
            },
            colors = pickerColors
        ) {
            DatePicker(
                state = datePickerState,
                colors = pickerColors,
                // Hide the mode-toggle icon in landscape/short screens: the M3 calendar grid
                // overflows there (this is the v1 fix's initialDisplayMode = Input), but without
                // this the toggle icon still lets the user switch back into the broken grid.
                // Portrait is unaffected — toggle stays available, both modes still reachable.
                showModeToggle = !isDatePickerLandscape
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existingEntry == null) stringResource(R.string.common_new_entry) else stringResource(R.string.common_edit_entry),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = cancel) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (title.isBlank()) {
                                titleError = true
                            } else {
                                // Reconcile every media list to the unified display order, so
                                // photoUris (which the detail collage indexes) reflects any
                                // drag-reorder and the parallel id lists stay aligned. mediaOrder is
                                // only persisted when videos are present (else the detail view shows
                                // photos in photoUris order and an empty video strip).
                                val order = displayMedia
                                val finalPhotos = order.filter { it.second == "photo" }.map { it.first }
                                val finalDriveIds = finalPhotos.map { p ->
                                    driveIds.getOrElse(photoUris.indexOf(p)) { "" }
                                }
                                val finalVideos = order.filter { it.second == "video" }.map { it.first }
                                val finalVideoIds = finalVideos.map { v ->
                                    videoFileIds.getOrElse(videoUris.indexOf(v)) { "" }
                                }
                                onSave(
                                    TravelEntry(
                                        id = existingEntry?.id ?: UUID.randomUUID().toString(),
                                        title = title.trim(),
                                        location = location.trim(),
                                        dateMillis = dateMillis,
                                        description = description.trim(),
                                        mood = mood,
                                        photoUris = finalPhotos,
                                        tags = tags,
                                        createdAt = existingEntry?.createdAt ?: System.currentTimeMillis(),
                                        driveFileIds = finalDriveIds,
                                        tripName = tripName.trim().ifBlank { null },
                                        videoUris = finalVideos,
                                        videoFileIds = finalVideoIds,
                                        mediaOrder = if (finalVideos.isEmpty()) emptyList()
                                                     else order.map { it.first }
                                    )
                                )
                            }
                        }
                    ) {
                        Text(stringResource(R.string.common_save), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        MacacoWatermarkBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
        // Wider side gutters on tablets (sw600dp+) so the form fields don't stretch full-width.
        val formHorizontalPadding =
            if (LocalConfiguration.current.screenWidthDp >= 600) 100.dp else 16.dp
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = formHorizontalPadding),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Media row (photos + videos, reorderable together)
            item {
                SectionLabel(stringResource(R.string.new_entry_media_label))
                Spacer(Modifier.height(8.dp))
                // Media first, the + buttons last, with trailing padding so the final tile is never
                // clipped. Long-press a tile to drag it left/right and reorder; tapping × removes it.
                // Each uri is a stable LazyRow key, so the dragged composable (and its active pointer
                // gesture) travels with its tile as the list reorders under it.
                val slotPx = with(LocalDensity.current) { 88.dp.toPx() } // 80.dp item + 8.dp spacing
                var draggingUri by remember { mutableStateOf<String?>(null) }
                var dragOffsetX by remember { mutableStateOf(0f) }
                // Read the live display list inside the long-lived drag closure without stale capture.
                val liveDisplay by rememberUpdatedState(displayMedia)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 16.dp)
                ) {
                    // + Photo / + Video first so they're always visible without scrolling past the
                    // media. They're not URIs and never enter mediaOrder, so their position has no
                    // effect on drag-reorder (which only touches the media tiles below).
                    item {
                        AddMediaButton(
                            icon = Icons.Filled.PhotoCamera,
                            label = stringResource(R.string.new_entry_add_photo_short),
                            onClick = { showPhotoSourceDialog = true }
                        )
                    }

                    if (videoUris.size < MAX_VIDEOS) {
                        item {
                            AddMediaButton(
                                icon = Icons.Filled.Videocam,
                                label = stringResource(R.string.new_entry_add_video_short),
                                onClick = { showVideoSourceDialog = true }
                            )
                        }
                    }

                    itemsIndexed(displayMedia, key = { _, pair -> pair.first }) { index, (uri, type) ->
                        val isDragging = draggingUri == uri
                        Box(
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationX = if (isDragging) dragOffsetX else 0f
                                    val s = if (isDragging) 1.08f else 1f
                                    scaleX = s
                                    scaleY = s
                                }
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { draggingUri = uri; dragOffsetX = 0f },
                                        onDragEnd = { draggingUri = null; dragOffsetX = 0f },
                                        onDragCancel = { draggingUri = null; dragOffsetX = 0f },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetX += dragAmount.x
                                            // Reorder in mediaOrder; fall back to the derived order if empty.
                                            val currentOrder = if (mediaOrder.isEmpty())
                                                liveDisplay.map { it.first } else mediaOrder
                                            val from = currentOrder.indexOf(uri)
                                            if (from < 0) return@detectDragGesturesAfterLongPress
                                            if (dragOffsetX > slotPx / 2 && from < currentOrder.lastIndex) {
                                                mediaOrder = currentOrder.toMutableList()
                                                    .also { it.add(from + 1, it.removeAt(from)) }
                                                dragOffsetX -= slotPx
                                            } else if (dragOffsetX < -slotPx / 2 && from > 0) {
                                                mediaOrder = currentOrder.toMutableList()
                                                    .also { it.add(from - 1, it.removeAt(from)) }
                                                dragOffsetX += slotPx
                                            }
                                        }
                                    )
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                if (type == "photo") {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    VideoThumbnailTile(uri = uri)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(44.dp)
                                    .clickable {
                                        if (type == "photo") {
                                            val idx = photoUris.indexOf(uri)
                                            if (idx >= 0) {
                                                photoUris = photoUris.toMutableList().also { it.removeAt(idx) }
                                                driveIds = driveIds.toMutableList().also { it.removeAt(idx) }
                                            }
                                        } else {
                                            val idx = videoUris.indexOf(uri)
                                            if (idx >= 0) {
                                                videoUris = videoUris.toMutableList().also { it.removeAt(idx) }
                                                videoFileIds = videoFileIds.toMutableList().also { it.removeAt(idx) }
                                            }
                                        }
                                        mediaOrder = mediaOrder.toMutableList().also { it.remove(uri) }
                                        // If added this session it was never committed, so delete now.
                                        // Pre-existing media is cleaned up by the ViewModel once saved.
                                        if (uri in sessionAdded) {
                                            ImageStorage.delete(context, listOf(uri))
                                            sessionAdded = sessionAdded - uri
                                        }
                                    },
                                contentAlignment = Alignment.TopEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.new_entry_remove_photo_cd),
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                if (displayMedia.isEmpty()) {
                    HintRow(Icons.Filled.PhotoCamera, stringResource(R.string.new_entry_hint_photos))
                }
            }

            // Title
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleError = false },
                    label = { Text(stringResource(R.string.new_entry_title_label)) },
                    placeholder = { Text(stringResource(R.string.new_entry_title_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = titleError,
                    supportingText = if (titleError) {
                        { Text(stringResource(R.string.new_entry_title_required)) }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background
                    )
                )
            }

            // Location (with autocomplete from previously used locations)
            item {
                LocationField(
                    value = location,
                    onValueChange = { location = it },
                    suggestions = locationSuggestions
                )
            }

            // Trip (optional — groups this entry with others from the same named trip)
            item {
                TripField(
                    value = tripName,
                    onValueChange = { tripName = it },
                    suggestions = tripSuggestions
                )
            }

            // Date picker
            item {
                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.new_entry_date_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatDate(dateMillis),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Mood selector
            item {
                SectionLabel(stringResource(R.string.new_entry_mood_label))
                Spacer(Modifier.height(8.dp))
                MoodSelector(
                    selectedMood = mood,
                    customMoods = customMoods,
                    onMoodSelected = { mood = it },
                    onAddCustomMood = onAddCustomMood
                )
            }

            // Tags
            item {
                SectionLabel(stringResource(R.string.new_entry_tags_label))
                Spacer(Modifier.height(8.dp))
                TagsField(
                    tags = tags,
                    onTagsChange = { tags = it },
                    suggestions = tagSuggestions
                )
                if (tags.isEmpty()) {
                    HintRow(null, stringResource(R.string.new_entry_hint_tags))
                }
            }

            // Suggested tag chips — tap to add; already-added chips are de-emphasised
            item {
                SuggestedTagsRow(
                    currentTags = tags,
                    onAdd = { raw ->
                        val tag = normalizeTag(raw)
                        if (tag.isNotEmpty() && tag !in tags) tags = tags + tag
                    }
                )
            }

            // Description — last so the keyboard rising on it never hides the fields below;
            // imePadding() on the LazyColumn shrinks the scroll area by the keyboard height.
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.new_entry_description_label)) },
                    placeholder = { Text(stringResource(R.string.new_entry_description_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    maxLines = Int.MAX_VALUE,
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.new_entry_dictate_prompt))
                                // Match dictation language to the in-app locale (set via
                                // AppCompatDelegate.setApplicationLocales), not just the system default.
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault().toLanguageTag())
                            }
                            onSuppressAutoLock()
                            speechLauncher.launch(intent)
                        }) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = stringResource(R.string.new_entry_dictate_cd),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background
                    )
                )
                if (description.isEmpty()) {
                    HintRow(Icons.Filled.Mic, stringResource(R.string.new_entry_hint_story))
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
        }
    }

    // Trim dialog — one per queue head; confirming/dismissing advances the queue so multiple
    // long picks are each trimmed in turn instead of all-but-the-last being dropped.
    trimQueue.firstOrNull()?.let { (trimUri, trimDuration) ->
        VideoTrimDialog(
            sourceUri = trimUri,
            durationMs = trimDuration,
            onTrimConfirmed = { startMs ->
                trimQueue = trimQueue.drop(1)
                transcodingProgress = 0f
                coroutineScope.launch {
                    val outFile = VideoTranscoder.transcode(
                        context, trimUri, trimStartMs = startMs,
                        onProgress = { transcodingProgress = it }
                    )
                    transcodingProgress = null
                    storeTranscoded(outFile)
                }
            },
            onDismiss = { trimQueue = trimQueue.drop(1) }
        )
    }

    // Transcoding overlay — full-screen scrim + circular progress, above the app bar.
    // Consumes ALL input (taps + back) so Save can't commit a half-processed entry.
    val progress = transcodingProgress
    BackHandler(enabled = progress != null) { /* swallow while transcoding */ }
    if (progress != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(56.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.video_processing),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
    }
}

/**
 * Normalize raw tag text into an Instagram-style hashtag body: drop a leading '#', lowercase, and
 * keep only letters/digits/underscore. Returns "" if nothing usable remains.
 */
internal fun normalizeTag(raw: String): String =
    raw.trim().removePrefix("#").lowercase()
        .filter { it.isLetterOrDigit() || it == '_' }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TagsField(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    suggestions: List<String>
) {
    var input by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    fun commit(raw: String) {
        val tag = normalizeTag(raw)
        if (tag.isNotEmpty() && tag !in tags) onTagsChange(tags + tag)
        input = ""
        expanded = false
    }

    // Previously used tags come first (most-used order); preset suggestions fill the rest.
    // Tags already on this entry are hidden. When the field is empty the dropdown opens with
    // the most relevant tags immediately visible.
    val allSuggestions = remember(suggestions) {
        (suggestions + SUGGESTED_TAGS.map { normalizeTag(it) }).distinctBy { it }
    }
    val matches = remember(input, tags, allSuggestions) {
        val q = normalizeTag(input)
        val unused = allSuggestions.filterNot { it in tags }
        if (q.isEmpty()) unused.take(8)
        else unused.filter { it.startsWith(q) }.take(8)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Autocomplete dropdown of existing tags, mirroring the location field.
        ExposedDropdownMenuBox(
            expanded = expanded && matches.isNotEmpty(),
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    // A space or comma ends a tag, mirroring how hashtags are typed.
                    if (it.endsWith(" ") || it.endsWith(",")) commit(it)
                    else {
                        input = it
                        expanded = true
                    }
                },
                label = { Text(stringResource(R.string.new_entry_add_tag_label)) },
                placeholder = { Text(stringResource(R.string.new_entry_add_tag_placeholder)) },
                leadingIcon = { Text("#", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (input.isNotBlank()) {
                        IconButton(onClick = { commit(input) }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_entry_add_tag_cd))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit(input) }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    // Opaque fill so the empty-state watermark only shows in the gaps, not through fields.
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
            )
            ExposedDropdownMenu(
                expanded = expanded && matches.isNotEmpty(),
                onDismissRequest = { expanded = false }
            ) {
                matches.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text("#$suggestion") },
                        onClick = { commit(suggestion) }
                    )
                }
            }
        }

        // Chosen tags, each removable by tapping its ✕.
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { onTagsChange(tags - tag) },
                        label = { Text("#$tag") },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.new_entry_remove_tag_cd),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    // Bundled city list, loaded once off the main thread (see Cities).
    var cities by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        cities = withContext(Dispatchers.IO) { Cities.all(context) }
    }
    // As the user types, suggest their previously used locations plus matching world cities,
    // both by prefix ("Ber" → Berlin, Bern, Bergen). Past locations come first; an exact match
    // is dropped so the menu doesn't just echo what's already typed.
    val matches = remember(value, suggestions, cities) {
        val q = value.trim()
        if (q.isBlank()) emptyList()
        else {
            val past = suggestions.filter { Cities.matchesPrefix(it, q) }
            (past + Cities.search(cities, q, limit = 8))
                .distinctBy { it.lowercase() }
                .filterNot { it.equals(q, ignoreCase = true) }
                .take(6)
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && matches.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(stringResource(R.string.new_entry_location_label)) },
            placeholder = { Text(stringResource(R.string.new_entry_location_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded && matches.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            matches.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>
) {
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(value, suggestions) {
        val q = value.trim()
        if (q.isBlank()) suggestions.take(5)
        else suggestions
            .filter { it.contains(q, ignoreCase = true) }
            .filterNot { it.equals(q, ignoreCase = true) }
            .take(5)
    }

    ExposedDropdownMenuBox(
        expanded = expanded && matches.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(stringResource(R.string.new_entry_trip_label)) },
            placeholder = { Text(stringResource(R.string.new_entry_trip_placeholder)) },
            leadingIcon = {
                Text("✈️", fontSize = 16.sp, modifier = androidx.compose.ui.Modifier.padding(start = 4.dp))
            },
            trailingIcon = {
                if (value.isNotBlank()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.new_entry_trip_clear_cd))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background
            ),
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded && matches.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            matches.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    leadingIcon = { Text("✈️") },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SuggestedTagsRow(
    currentTags: List<String>,
    onAdd: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SUGGESTED_TAGS.forEach { label ->
            val normalized = normalizeTag(label)
            val alreadyAdded = normalized in currentTags
            Text(
                "#$label",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (alreadyAdded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (alreadyAdded) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                    .clickable(enabled = !alreadyAdded) { onAdd(label) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

/**
 * A "whispered guidance" empty-state hint: small primary-tinted text (optionally with a leading
 * icon) shown below a field only while it's empty. Theme-adaptive (primary at low alpha) rather than
 * a hardcoded brand colour, so it reads correctly under every app theme.
 */
@Composable
private fun HintRow(icon: ImageVector?, text: String) {
    // onSurfaceVariant (full opacity) over a translucent surface pill so hints stay legible on top
    // of the tiled macaco watermark on every theme, instead of fading into it.
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 6.dp, start = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun MoodSelector(
    selectedMood: String,
    customMoods: List<String>,
    onMoodSelected: (String) -> Unit,
    onAddCustomMood: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingEmoji by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    if (showAddDialog) {
        // Open the keyboard the moment the dialog appears so the user can switch straight to the
        // emoji panel without an extra tap on the field.
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        AlertDialog(
            onDismissRequest = { showAddDialog = false; pendingEmoji = "" },
            title = { Text(stringResource(R.string.mood_add_custom_title)) },
            text = {
                OutlinedTextField(
                    value = pendingEmoji,
                    onValueChange = { pendingEmoji = it },
                    placeholder = { Text(stringResource(R.string.mood_add_custom_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val emoji = pendingEmoji.trim()
                        if (emoji.isNotBlank()) {
                            onAddCustomMood(emoji)
                            onMoodSelected(emoji)
                        }
                        showAddDialog = false
                        pendingEmoji = ""
                    }
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; pendingEmoji = "" }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 8.dp)
    ) {
        // Preset moods
        items(MOODS) { m ->
            MoodChip(m, selectedMood == m) { onMoodSelected(if (selectedMood == m) "" else m) }
        }

        // User-added custom moods
        items(customMoods) { m ->
            MoodChip(m, selectedMood == m) { onMoodSelected(if (selectedMood == m) "" else m) }
        }

        // Add custom mood button — accent-tinted to match the selected chip style.
        item {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                    .clickable { showAddDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.mood_add_custom_cd),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun MoodChip(emoji: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                // Selected: Macaco gold — consistent with splash, nav bar, and brand moments.
                // Unselected: neutral surface so the emoji reads clearly at rest.
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 26.sp)
    }
}

@Composable
private fun VideoThumbnailTile(uri: String) {
    val bitmap = VideoThumbnails.rememberThumbnail(uri).value
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        Icon(
            Icons.Filled.PlayCircle,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun AddMediaButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
