# Macaco — Video Clips in Entries

Adds 15-second compressed video clips to journal entries. Videos live in the same reorderable
media row as photos, backed up to Google Drive alongside them. Users can record directly from
the entry editor (system camera, auto-stops at 15 s) or pick from the gallery (scrub-to-trim
if longer than 15 s). Playback is inline in the entry detail screen via ExoPlayer.

**Files touched:** `build.gradle` (app), `AndroidManifest.xml`, `TravelEntry.kt`,
`ImageStorage.kt`, `VideoTranscoder.kt` (NEW), `NewEditEntryScreen.kt`,
`EntryDetailScreen.kt`, `DrivePhotoSync.kt`, `JournalBackup.kt`, `HelpAboutScreen.kt`,
`strings.xml`.

Implement in the order of the phases below. Each phase builds on the previous.

---

## Phase 1 — Foundation

### 1a — Gradle dependencies (`app/build.gradle`)

Add to the `dependencies {}` block:

```kotlin
// Video transcoding — H.264 720p + AAC 96kbps, supports trim, works API 21+
implementation("com.otaliastudios:transcoder:0.10.4")

// ExoPlayer (Media3) — video playback in EntryDetailScreen
implementation("androidx.media3:media3-exoplayer:1.3.1")
implementation("androidx.media3:media3-ui:1.3.1")
```

### 1b — Permissions (`AndroidManifest.xml`)

Add alongside the existing media permissions:

```xml
<!-- In-app video recording needs the microphone (audio track is kept). -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- Video gallery pick on API 33+. -->
<uses-permission
    android:name="android.permission.READ_MEDIA_VIDEO"
    android:minSdkVersion="33" />
```

Also add a `<queries>` block inside `<manifest>` so the video capture intent resolves
(required on API 30+ package-visibility rules):

```xml
<queries>
    <intent>
        <action android:name="android.media.action.VIDEO_CAPTURE" />
    </intent>
</queries>
```

### 1c — Data model (`TravelEntry.kt`)

Add three new fields with default empty values so all existing Firestore documents
deserialise cleanly with no migration:

```kotlin
// BEFORE
@Serializable
data class TravelEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val location: String,
    val dateMillis: Long,
    val description: String,
    val mood: String,
    val photoUris: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val driveFileIds: List<String> = emptyList(),
    val tripName: String? = null
)

// AFTER
@Serializable
data class TravelEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val location: String,
    val dateMillis: Long,
    val description: String,
    val mood: String,
    val photoUris: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val driveFileIds: List<String> = emptyList(),
    val tripName: String? = null,
    // ── Video support (added post-vc49) ─────────────────────────────────────
    // videoUris: content:// URIs in Movies/Macaco (parallel to videoFileIds).
    val videoUris: List<String> = emptyList(),
    // videoFileIds: Drive file IDs parallel to videoUris; "" = not uploaded yet.
    val videoFileIds: List<String> = emptyList(),
    // mediaOrder: all media URIs (photos + videos) in user-defined display order.
    // Empty on old entries = backward-compatible: photos displayed first, then videos.
    val mediaOrder: List<String> = emptyList(),
)
```

### 1d — ImageStorage additions (`ImageStorage.kt`)

Add two new functions after `newCameraTempUri`. Also add the two new constants at the bottom.

```kotlin
/**
 * Creates an empty .mp4 temp file the system camera can record into, returned as a
 * FileProvider content:// URI. After recording, transcode then call persistVideoToGallery.
 * Temp files live in filesDir/video_temp/ — call clear(context, VIDEO_TEMP) when done.
 */
fun newVideoTempUri(context: Context): android.net.Uri? = runCatching {
    val dir = File(context.filesDir, VIDEO_TEMP).apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.mp4")
    file.createNewFile()
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

/**
 * Copies [transcodedFile] (the .mp4 output from VideoTranscoder) into the device's shared
 * Movies/Macaco collection and returns the content:// URI, or null on failure.
 * Uses the same IS_PENDING pattern as persistToGallery for API 29+.
 */
fun persistVideoToGallery(context: Context, transcodedFile: File): String? = runCatching {
    val resolver = context.contentResolver
    val name = "macaco_${System.currentTimeMillis()}.mp4"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Macaco")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
        ) ?: return null
        val ok = resolver.openOutputStream(uri)?.use { out ->
            transcodedFile.inputStream().use { it.copyTo(out) }; true
        } ?: false
        if (!ok) { runCatching { resolver.delete(uri, null, null) }; return null }
        values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri.toString()
    } else {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Macaco"
        ).also { it.mkdirs() }
        val file = File(dir, name)
        transcodedFile.copyTo(file, overwrite = true)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            @Suppress("DEPRECATION")
            put(MediaStore.Video.Media.DATA, file.absolutePath)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        (uri ?: android.net.Uri.fromFile(file)).toString()
    }
}.getOrNull()
```

Add at the bottom of the constants block:
```kotlin
const val VIDEO_TEMP = "video_temp"
const val DRIVE_VIDEOS = "drive_videos"   // used in DrivePhotoSync
```

Also extend the existing `delete()` function to handle video URIs — it already handles
`content://` URIs generically so no change needed. However, add a matching clear call to
the `VIDEO_TEMP` constant so callers can clean up after recording.

### 1e — VideoTranscoder utility (NEW FILE: `util/VideoTranscoder.kt`)

```kotlin
package com.houseofmmminq.macaco.util

import android.content.Context
import android.net.Uri
import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderListener
import com.otaliastudios.transcoder.source.ClipDataSource
import com.otaliastudios.transcoder.source.UriDataSource
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

object VideoTranscoder {

    const val MAX_DURATION_MS = 15_000L

    /**
     * Transcodes [sourceUri] to H.264 720p / 2 Mbps + AAC 96 kbps.
     *
     * If [trimStartMs] > 0 the clip is trimmed starting at that offset.
     * [durationMs] caps the output — always ≤ MAX_DURATION_MS.
     * Progress is reported 0f→1f via [onProgress].
     *
     * Returns the output [File] (in cacheDir) on success, null on failure.
     * Must be called from a coroutine; runs on IO dispatcher internally.
     */
    suspend fun transcode(
        context: Context,
        sourceUri: Uri,
        trimStartMs: Long = 0L,
        durationMs: Long = MAX_DURATION_MS,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        val outFile = File(context.cacheDir, "transcode_${System.currentTimeMillis()}.mp4")
        val clampedDuration = minOf(durationMs, MAX_DURATION_MS)

        val dataSource = if (trimStartMs > 0L) {
            ClipDataSource(UriDataSource(context, sourceUri), trimStartMs * 1000L, clampedDuration * 1000L)
        } else {
            ClipDataSource(UriDataSource(context, sourceUri), 0L, clampedDuration * 1000L)
        }

        val videoStrategy = DefaultVideoStrategy.Builder()
            .addResizer(com.otaliastudios.transcoder.resize.AtMostResizer(720))
            .bitRate(2_000_000L)
            .build()

        val audioStrategy = DefaultAudioStrategy.Builder()
            .channels(DefaultAudioStrategy.CHANNELS_AS_INPUT)
            .sampleRate(DefaultAudioStrategy.SAMPLE_RATE_AS_INPUT)
            .bitRate(96_000L)
            .build()

        suspendCancellableCoroutine { cont ->
            val future = Transcoder.into(outFile.absolutePath)
                .addDataSource(dataSource)
                .setVideoTrackStrategy(videoStrategy)
                .setAudioTrackStrategy(audioStrategy)
                .setListener(object : TranscoderListener {
                    override fun onTranscodeProgress(progress: Double) {
                        onProgress(progress.toFloat())
                    }
                    override fun onTranscodeCompleted(successCode: Int) {
                        cont.resume(outFile)
                    }
                    override fun onTranscodeCanceled() {
                        outFile.delete()
                        cont.resume(null)
                    }
                    override fun onTranscodeFailed(exception: Throwable) {
                        outFile.delete()
                        cont.resume(null)
                    }
                })
                .transcode()
            cont.invokeOnCancellation { future.cancel(true) }
        }
    }

    /** Returns the duration of [uri] in milliseconds, or 0 on failure. */
    fun getDurationMs(context: Context, uri: Uri): Long = runCatching {
        android.media.MediaMetadataRetriever().use { r ->
            r.setDataSource(context, uri)
            r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)

    /** Returns a [Bitmap] of the first frame, or null on failure. Used for thumbnails. */
    fun getFirstFrame(context: Context, uri: Uri): android.graphics.Bitmap? = runCatching {
        android.media.MediaMetadataRetriever().use { r ->
            r.setDataSource(context, uri)
            r.getFrameAtTime(0L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
    }.getOrNull()
}
```

---

## Phase 2 — Capture, Pick & Trim (NewEditEntryScreen.kt)

### 2a — New state variables

Add after the existing `driveIds` state variable block:

```kotlin
// ── Video state ──────────────────────────────────────────────────────────────
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

// Trim dialog state — shown when a picked video is longer than 15 s
var videoToTrim by remember { mutableStateOf<Uri?>(null) }
var videoToDuration by remember { mutableStateOf(0L) }

// Transcoding progress — null when idle, 0f→1f when active
var transcodingProgress by remember { mutableStateOf<Float?>(null) }

val coroutineScope = rememberCoroutineScope()
```

Add a helper that returns the current display list (in mediaOrder if populated, else
photos-then-videos):

```kotlin
// Derived display list: uri → "photo" or "video"
val displayMedia: List<Pair<String, String>> = remember(mediaOrder, photoUris, videoUris) {
    if (mediaOrder.isEmpty()) {
        photoUris.map { it to "photo" } + videoUris.map { it to "video" }
    } else {
        mediaOrder.mapNotNull { uri ->
            when {
                uri in photoUris -> uri to "photo"
                uri in videoUris -> uri to "video"
                else -> null
            }
        }
    }
}
```

### 2b — Launcher: video recording

Add after `cameraLauncher`:

```kotlin
val videoRecordLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    val captured = pendingVideoUri
    ImageStorage.clear(context, ImageStorage.VIDEO_TEMP)
    pendingVideoUriString = null
    if (result.resultCode == android.app.Activity.RESULT_OK && captured != null) {
        transcodingProgress = 0f
        coroutineScope.launch {
            // Recorded clip is ≤ 15 s so no trim needed — transcode directly.
            val outFile = VideoTranscoder.transcode(
                context, captured, onProgress = { transcodingProgress = it }
            )
            transcodingProgress = null
            outFile?.let { file ->
                ImageStorage.persistVideoToGallery(context, file)?.let { stored ->
                    file.delete()
                    sessionAdded = sessionAdded + stored
                    videoUris = videoUris + stored
                    videoFileIds = videoFileIds + ""
                    mediaOrder = mediaOrder + stored
                }
            }
        }
    }
}

val launchVideoRecord = {
    ImageStorage.newVideoTempUri(context)?.let { uri ->
        pendingVideoUriString = uri.toString()
        onSuppressAutoLock()
        val intent = android.content.Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(android.provider.MediaStore.EXTRA_DURATION_LIMIT, 15)
            putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
        }
        videoRecordLauncher.launch(intent)
    }
}
```

### 2c — Launcher: video gallery pick

Add after `launchVideoRecord`:

```kotlin
// Max 3 videos total per entry.
val MAX_VIDEOS = 3

val videoPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_VIDEOS)
) { uris ->
    val canAdd = MAX_VIDEOS - videoUris.size
    if (canAdd <= 0) return@rememberLauncherForActivityResult
    uris.take(canAdd).forEach { uri ->
        val durationMs = VideoTranscoder.getDurationMs(context, uri)
        if (durationMs <= VideoTranscoder.MAX_DURATION_MS) {
            // Short enough — transcode immediately without a trim UI.
            transcodingProgress = 0f
            coroutineScope.launch {
                val outFile = VideoTranscoder.transcode(
                    context, uri, onProgress = { transcodingProgress = it }
                )
                transcodingProgress = null
                outFile?.let { file ->
                    ImageStorage.persistVideoToGallery(context, file)?.let { stored ->
                        file.delete()
                        sessionAdded = sessionAdded + stored
                        videoUris = videoUris + stored
                        videoFileIds = videoFileIds + ""
                        mediaOrder = mediaOrder + stored
                    }
                }
            }
        } else {
            // Longer clip — ask user to trim before transcoding.
            videoToTrim = uri
            videoToDuration = durationMs
        }
    }
}
```

### 2d — Trim dialog composable (add near top of file as a private function)

```kotlin
/**
 * Shown when a gallery-picked video is > 15 seconds. Lets the user choose
 * which 15-second window to keep by dragging a slider.
 */
@Composable
private fun VideoTrimDialog(
    sourceUri: Uri,
    durationMs: Long,
    onTrimConfirmed: (trimStartMs: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val maxStart = (durationMs - VideoTranscoder.MAX_DURATION_MS).coerceAtLeast(0L)
    var trimStartMs by remember { mutableStateOf(0L) }

    // First-frame thumbnail.
    val thumbnail = remember(sourceUri) {
        VideoTranscoder.getFirstFrame(context, sourceUri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.video_trim_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Thumbnail preview
                if (thumbnail != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Text(
                    text = stringResource(
                        R.string.video_trim_window,
                        formatMmSs(trimStartMs),
                        formatMmSs(trimStartMs + VideoTranscoder.MAX_DURATION_MS)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = trimStartMs.toFloat() / maxStart.coerceAtLeast(1L),
                    onValueChange = { trimStartMs = (it * maxStart).toLong() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onTrimConfirmed(trimStartMs) }) {
                Text(stringResource(R.string.video_trim_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

private fun formatMmSs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
```

Show the trim dialog in the composable body, and transcode on confirm:

```kotlin
// In the main composable body, after the existing showDatePicker dialog:
val trimUri = videoToTrim
if (trimUri != null) {
    VideoTrimDialog(
        sourceUri = trimUri,
        durationMs = videoToDuration,
        onTrimConfirmed = { startMs ->
            videoToTrim = null
            transcodingProgress = 0f
            coroutineScope.launch {
                val outFile = VideoTranscoder.transcode(
                    context, trimUri, trimStartMs = startMs,
                    onProgress = { transcodingProgress = it }
                )
                transcodingProgress = null
                outFile?.let { file ->
                    ImageStorage.persistVideoToGallery(context, file)?.let { stored ->
                        file.delete()
                        sessionAdded = sessionAdded + stored
                        videoUris = videoUris + stored
                        videoFileIds = videoFileIds + ""
                        mediaOrder = mediaOrder + stored
                    }
                }
            }
        },
        onDismiss = { videoToTrim = null }
    )
}

// Transcoding overlay — full-screen scrim + circular progress
val progress = transcodingProgress
if (progress != null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
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
```

Note: the transcoding overlay Box should be placed OUTSIDE the Scaffold (wrap both in a `Box(Modifier.fillMaxSize())`) so it covers the top app bar too.

### 2e — Source dialog: add Video options

Rename `showPhotoSourceDialog` references are unchanged; instead add a second dialog
`showVideoSourceDialog` that mirrors the photo one:

```kotlin
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
                    Icon(Icons.Filled.Videocam, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.new_entry_video_record),
                        style = MaterialTheme.typography.bodyLarge)
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
                    Icon(Icons.Filled.VideoLibrary, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.new_entry_video_gallery),
                        style = MaterialTheme.typography.bodyLarge)
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
```

Add `Icons.Filled.Videocam` and `Icons.Filled.VideoLibrary` to the import list.

### 2f — Replace the photos LazyRow with unified media LazyRow

The current `item { SectionLabel(...) ... LazyRow(...) { itemsIndexed(photoUris) ... } }` in the
LazyColumn must be replaced with a unified version that renders both photos and videos.

The section label changes to `R.string.new_entry_media_label` (rename from `new_entry_photos_label`
or add a new key — see strings section).

The drag reorder logic is the same but now operates on `mediaOrder`; photos and videos both move freely.

```kotlin
item {
    SectionLabel(stringResource(R.string.new_entry_media_label))
    Spacer(Modifier.height(8.dp))

    val slotPx = with(LocalDensity.current) { 88.dp.toPx() }
    var draggingUri by remember { mutableStateOf<String?>(null) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    val liveDisplay by rememberUpdatedState(displayMedia)

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 16.dp)
    ) {
        // Each media item (photo or video) as a 80×80 dp tile
        itemsIndexed(displayMedia, key = { _, pair -> pair.first }) { index, (uri, type) ->
            val isDragging = draggingUri == uri
            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationX = if (isDragging) dragOffsetX else 0f
                        val s = if (isDragging) 1.08f else 1f; scaleX = s; scaleY = s
                    }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggingUri = uri; dragOffsetX = 0f },
                            onDragEnd = { draggingUri = null; dragOffsetX = 0f },
                            onDragCancel = { draggingUri = null; dragOffsetX = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX += dragAmount.x
                                // Reorder in mediaOrder; fall back to derived index if mediaOrder empty.
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
                // Shared remove badge
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    if (type == "photo") {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Video tile: thumbnail + play overlay
                        VideoThumbnailTile(uri = uri)
                    }
                }
                // Remove badge (× circle top-right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable {
                            if (type == "photo") {
                                val idx = photoUris.indexOf(uri)
                                if (idx >= 0) {
                                    photoUris = photoUris.toMutableList().also { it.removeAt(idx) }
                                    driveIds = driveIds.toMutableList().also { it.removeAt(idx) }
                                    if (uri in sessionAdded) {
                                        ImageStorage.delete(context, listOf(uri))
                                        sessionAdded = sessionAdded - uri
                                    }
                                }
                            } else {
                                val idx = videoUris.indexOf(uri)
                                if (idx >= 0) {
                                    videoUris = videoUris.toMutableList().also { it.removeAt(idx) }
                                    videoFileIds = videoFileIds.toMutableList().also { it.removeAt(idx) }
                                    if (uri in sessionAdded) {
                                        ImageStorage.delete(context, listOf(uri))
                                        sessionAdded = sessionAdded - uri
                                    }
                                }
                            }
                            mediaOrder = mediaOrder.toMutableList().also { it.remove(uri) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(12.dp))
                }
            }
        }

        // + Photo button (always present while < max photos)
        item {
            AddMediaButton(
                icon = Icons.Filled.PhotoCamera,
                label = stringResource(R.string.new_entry_add_photo_short),
                onClick = { showPhotoSourceDialog = true }
            )
        }

        // + Video button (present while < 3 videos)
        if (videoUris.size < MAX_VIDEOS) {
            item {
                AddMediaButton(
                    icon = Icons.Filled.Videocam,
                    label = stringResource(R.string.new_entry_add_video_short),
                    onClick = { showVideoSourceDialog = true }
                )
            }
        }
    }
}
```

Add these two private composables near the bottom of the file:

```kotlin
@Composable
private fun VideoThumbnailTile(uri: String) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        VideoTranscoder.getFirstFrame(context, Uri.parse(uri))
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Icon(Icons.Filled.PlayCircle, contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun AddMediaButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

Add `Icons.Filled.PlayCircle`, `Icons.Filled.VideoLibrary` to the import list.
Add `import androidx.compose.ui.graphics.asImageBitmap`.

### 2g — TravelEntry.copy() on Save

Update the `onSave(TravelEntry(...))` call in the top bar to include the new fields:

```kotlin
onSave(
    TravelEntry(
        id = existingEntry?.id ?: UUID.randomUUID().toString(),
        title = title.trim(),
        location = location.trim(),
        dateMillis = dateMillis,
        description = description.trim(),
        mood = mood,
        photoUris = photoUris,
        tags = tags,
        createdAt = existingEntry?.createdAt ?: System.currentTimeMillis(),
        driveFileIds = driveIds,
        tripName = tripName.trim().ifBlank { null },
        videoUris = videoUris,
        videoFileIds = videoFileIds,
        mediaOrder = if (mediaOrder.isEmpty() && videoUris.isEmpty()) emptyList()
                     else if (mediaOrder.isEmpty()) photoUris + videoUris
                     else mediaOrder
    )
)
```

Also extend the `cancel` lambda to clean up session-added videos:

```kotlin
val cancel = {
    ImageStorage.delete(context, sessionAdded)  // existing — deletes both photos and videos
    onBack()
}
```

This already works since `sessionAdded` tracks all URIs (photos and videos) together.

---

## Phase 3 — Playback (EntryDetailScreen.kt)

### 3a — Unified media list derivation

At the top of `EntryDetailScreen` composable, where photos are currently used for display,
derive the unified display list from the entry:

```kotlin
// Unified ordered media list: uri → "photo" or "video"
val displayMedia: List<Pair<String, String>> = remember(entry) {
    if (entry.mediaOrder.isEmpty()) {
        entry.photoUris.map { it to "photo" } + entry.videoUris.map { it to "video" }
    } else {
        entry.mediaOrder.mapNotNull { uri ->
            when {
                uri in entry.photoUris -> uri to "photo"
                uri in entry.videoUris -> uri to "video"
                else -> null
            }
        }
    }
}
```

### 3b — Video playback composable

Add this private composable to `EntryDetailScreen.kt` (or a new `VideoPlayerDialog.kt`
component in `ui/components/`):

```kotlin
/**
 * Shows a video as a thumbnail tile. Tapping opens a full-screen dialog with ExoPlayer.
 * The player is released when the dialog closes.
 */
@Composable
fun VideoEntryTile(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showPlayer by remember { mutableStateOf(false) }

    val thumbnail = remember(uri) { VideoTranscoder.getFirstFrame(context, Uri.parse(uri)) }

    Box(modifier = modifier.clickable { showPlayer = true }, contentAlignment = Alignment.Center) {
        if (thumbnail != null) {
            androidx.compose.foundation.Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Icon(Icons.Filled.PlayCircle, contentDescription = stringResource(R.string.video_play_cd),
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(40.dp))
    }

    if (showPlayer) {
        Dialog(
            onDismissRequest = { showPlayer = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center
            ) {
                val player = remember {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(uri))
                        prepare(); playWhenReady = true
                    }
                }
                DisposableEffect(Unit) { onDispose { player.release() } }

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                IconButton(
                    onClick = { showPlayer = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close),
                        tint = Color.White)
                }
            }
        }
    }
}
```

Required imports (add to EntryDetailScreen.kt):
```kotlin
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
```

### 3c — Replace photo-only display with unified media display

In the section of `EntryDetailScreen` that renders the photo list/pager, replace references
to `entry.photoUris` with `displayMedia`. For each item:
- If type == `"photo"`: render `AsyncImage` (existing behaviour, with Drive-cached URI fallback)
- If type == `"video"`: render `VideoEntryTile(uri)`

The Drive-cached URI lookup currently happens for photos via `cachedDrivePhotos`. Add the
same lookup for videos: the ViewModel's `cachedDrivePhotos` map can be reused (it maps
original URI → cached URI) — just extend `DrivePhotoSync` to populate it for videos too
(Phase 4).

---

## Phase 4 — Drive Sync & Backup

### 4a — DrivePhotoSync: extend for videos

`DrivePhotoSync.kt` currently has `uploadEntryPhotos(entry)` and `downloadMissingPhotos(entry)`.
Add parallel functions for videos. The logic is identical except:
- URIs come from `entry.videoUris` / `entry.videoFileIds`
- Downloaded files go to `cacheDir/drive_videos/` (constant: `ImageStorage.DRIVE_VIDEOS`)
- MIME type is `"video/mp4"`

```kotlin
/**
 * Uploads any video in [entry.videoUris] that has no Drive file ID yet.
 * Returns the entry copy with [videoFileIds] filled in.
 * Mirrors uploadEntryPhotos exactly.
 */
suspend fun uploadEntryVideos(entry: TravelEntry): TravelEntry { /* ... same pattern ... */ }

/**
 * Downloads any video that has a Drive file ID but no readable local URI.
 * Saves to cacheDir/drive_videos/<driveId>.mp4 and populates cachedDrivePhotos
 * (the same map photos use — keys are original URIs, values are cached local URIs).
 */
suspend fun downloadMissingVideos(entry: TravelEntry) { /* ... same pattern ... */ }
```

Wire `uploadEntryVideos` into `syncAll` alongside `uploadEntryPhotos`, and call
`downloadMissingVideos` in the auto-download triggered by the entry list change in
`JournalViewModel`.

### 4b — JournalBackup: do NOT bundle video bytes

**Videos are excluded from the local backup zip.** At up to 4 MB per 15-second clip and
3 clips per entry, bundling video bytes would make the zip unmanageable for users with many
entries (e.g. 50 entries × 3 videos × 4 MB = 600 MB). Videos already live on the user's
personal Google Drive and can always be re-fetched from there.

**Export**: No change needed. `videoUris`, `videoFileIds`, and `mediaOrder` are already
serialised in `backup.json` as `@Serializable` fields — no extra handling required.
Video bytes are not written to the zip.

**Import**: Read the video fields from `backup.json` as-is. Do **not** clear `videoFileIds`
to `""` (the way `driveFileIds` is cleared to force photo re-upload). Videos must keep their
existing Drive IDs so `downloadMissingVideos()` can re-fetch them on the next sync pass —
exactly as it does when the user installs on a new device.

**In practice this means the only code to verify in `JournalBackup.kt`** is that the import
path does not clear `videoFileIds`. If the existing import logic clears `driveFileIds` for
photos (which it does), make sure the analogous step is NOT applied to `videoFileIds`.

---

## Phase 5 — Help & About

### 5a — HelpAboutScreen.kt: rename Photos section + add Video FAQ

Rename the existing `help_section_photos` section to `help_section_media` (covers both),
and add two new video FAQ entries to that section:

```kotlin
// BEFORE (HelpAboutScreen.kt ~line 73)
FaqSection(
    R.string.help_section_photos,
    listOf(
        R.string.help_faq_q_photos to R.string.help_faq_a_photos,
        R.string.help_faq_drive_connect_q to R.string.help_faq_drive_connect_a,
        R.string.help_faq_reorder_photos_q to R.string.help_faq_reorder_photos_a,
        R.string.help_faq_cover_q to R.string.help_faq_cover_a,
        R.string.help_faq_q_backup to R.string.help_faq_a_backup,
    )
),

// AFTER
FaqSection(
    R.string.help_section_media,
    listOf(
        R.string.help_faq_q_photos to R.string.help_faq_a_photos,
        R.string.help_faq_video_add_q to R.string.help_faq_video_add_a,
        R.string.help_faq_video_length_q to R.string.help_faq_video_length_a,
        R.string.help_faq_drive_connect_q to R.string.help_faq_drive_connect_a,
        R.string.help_faq_reorder_photos_q to R.string.help_faq_reorder_photos_a,
        R.string.help_faq_cover_q to R.string.help_faq_cover_a,
        R.string.help_faq_q_backup to R.string.help_faq_a_backup,
    )
),
```

### 5b — strings.xml: new and changed keys

New UI strings (add in all 11 supported languages; EN values below — translate accordingly):

| Key | EN value |
|-----|----------|
| `new_entry_media_label` | Photos & Videos |
| `new_entry_add_video` | Add Video |
| `new_entry_add_video_short` | Video |
| `new_entry_add_photo_short` | Photo |
| `new_entry_video_record` | Record clip (15 s) |
| `new_entry_video_gallery` | Choose from gallery |
| `video_trim_title` | Choose your 15-second clip |
| `video_trim_window` | %1$s → %2$s |
| `video_trim_confirm` | Use this clip |
| `video_processing` | Compressing video… |
| `video_play_cd` | Play video |
| `help_section_media` | Photos & Videos |
| `help_faq_video_add_q` | How do I add a video to an entry? |
| `help_faq_video_add_a` | Tap the Video button in the entry editor and choose "Record clip" to capture up to 15 seconds directly from your camera, or "Choose from gallery" to pick an existing video. Gallery videos longer than 15 seconds can be trimmed to the moment you want before saving. You can add up to 3 videos per entry. |
| `help_faq_video_length_q` | How long can a video be, and how much space does it use? |
| `help_faq_video_length_a` | Videos are limited to 15 seconds. macaco automatically compresses each clip to around 4 MB (720p, H.264), so your storage and Drive quota stay under control. Videos are backed up to Google Drive alongside your photos as long as Drive is connected in Settings. |

The existing `new_entry_photos_label` key can remain for now (used in other possible
references) — the LazyRow section label is replaced by `new_entry_media_label`.

---

## Summary

| # | Change | File(s) |
|---|--------|---------|
| 1 | Add 3 Gradle dependencies (transcoder, media3) | `build.gradle` |
| 2 | Add RECORD_AUDIO + READ_MEDIA_VIDEO permissions + queries block | `AndroidManifest.xml` |
| 3 | Add `videoUris`, `videoFileIds`, `mediaOrder` fields (default empty) | `TravelEntry.kt` |
| 4 | Add `newVideoTempUri`, `persistVideoToGallery`, VIDEO_TEMP constant | `ImageStorage.kt` |
| 5 | New `VideoTranscoder` — transcode/trim, getDurationMs, getFirstFrame | `VideoTranscoder.kt` (NEW) |
| 6 | Video state, record/pick/trim launchers, unified media LazyRow | `NewEditEntryScreen.kt` |
| 7 | Unified media display + VideoEntryTile + ExoPlayer dialog | `EntryDetailScreen.kt` |
| 8 | `uploadEntryVideos` + `downloadMissingVideos` | `DrivePhotoSync.kt` |
| 9 | Verify video fields NOT cleared on import (no video bytes in zip) | `JournalBackup.kt` |
| 10 | Photos section → Media section + 2 new video FAQ entries | `HelpAboutScreen.kt` |
| 11 | New UI strings + FAQ strings (11 languages) | `strings.xml` |
