# Macaco — Adventure Reel: Auto-generate a shareable video from a trip's photos

# Macaco — Adventure Reel: Auto-generate a shareable video from a trip's photos

New premium feature. Adds a "Create Reel" icon button to each `TripHeader` in
`JournalListScreen`. Tapping it collects all photos from that trip's entries, renders them
into a Ken Burns slideshow video (9:16, 720×1280, H.264, 30 fps), and opens the Android
share sheet. The generated MP4 lives in `cacheDir` and is deleted after sharing.

Touches: `JournalListScreen.kt`, `JournalViewModel.kt`, new `AdventureReelEncoder.kt`,
`strings.xml` ×11 languages.

---

## Feature overview

```
TripHeader  ──tap 🎬──▶  ReelProgressDialog (generating…)
                               │
                    AdventureReelEncoder (coroutine)
                    ┌──────────────────────────────┐
                    │  for each photo (sorted by    │
                    │  entry dateMillis):            │
                    │    decode bitmap               │
                    │    render 90 Ken Burns frames  │
                    │    + 15-frame crossfade        │
                    │    → MediaCodec H.264 surface  │
                    └──────────────────────────────┘
                               │
                         MediaMuxer → cacheDir/reel_<tripName>.mp4
                               │
                         Android share sheet
                         (FileProvider URI, video/mp4)
```

---

## Change 1 — TripHeader: add "Create Reel" button (premium-gated)

```kotlin
// BEFORE — JournalListScreen.kt line ~1173
private fun TripHeader(tripName: String, entryCount: Int) {
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
    }
}

// AFTER — add onCreateReel callback + reel icon button
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
```

Wire `onCreateReel` at the live call site `item(key="trip-header-$trip") { TripHeader(...) }`
(JournalListScreen.kt:628) — pass `onCreateReel = { viewModel.startReel(trip, sectionEntries) }`.
The trip's entries are `sectionEntries` at that call site.
Pass `isPurchased` down from the collected ViewModel state (note: the whole list is already
behind the login+purchase gate in NavGraph, so `isPurchased` will always be true here —
the guard is harmless but redundant).

Import: `androidx.compose.material.icons.filled.Videocam`

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`

---

## Change 2 — ReelProgressDialog: show generation progress

A simple `AlertDialog` that blocks interaction while the reel is generating. Shown when
`reelState` is `Generating`; dismissed automatically when done.

```kotlin
// ADD new composable to JournalListScreen.kt

@Composable
private fun ReelProgressDialog(
    tripName: String,
    progress: Float,        // 0f..1f
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
```

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`

---

## Change 3 — JournalViewModel: reel state + trigger

```kotlin
// ADD to JournalViewModel.kt

sealed class ReelState {
    object Idle : ReelState()
    data class Generating(val tripName: String, val progress: Float) : ReelState()
    data class Ready(val tripName: String, val uri: Uri) : ReelState()
    data class Error(val message: String) : ReelState()
}

// _reelState backed by a private MutableStateFlow; exposed as val reelState
private val _reelState = MutableStateFlow<ReelState>(ReelState.Idle)
val reelState: StateFlow<ReelState> = _reelState.asStateFlow()

// Holds the encode coroutine so cancelReel() can actually stop it.
private var reelEncoderJob: Job? = null

fun startReel(tripName: String, entries: List<TravelEntry>) {
    val photos = entries
        .sortedBy { it.dateMillis }
        .flatMap { entry ->
            // Prefer local MediaStore URI; fall back to Drive cache URI.
            entry.photoUris.ifEmpty {
                entry.driveFileIds.mapNotNull { id ->
                    cachedDrivePhotos.value[id]
                }
            }
        }
        .filter { it.isNotBlank() }

    if (photos.isEmpty()) {
        _reelState.value = ReelState.Error("No photos found for this trip.")
        return
    }

    // Assign to reelEncoderJob so cancelReel() can cancel the coroutine.
    reelEncoderJob = viewModelScope.launch(Dispatchers.IO) {
        _reelState.value = ReelState.Generating(tripName, 0f)
        // JournalViewModel uses manual DI — appContext is injected at construction (line 38),
        // not getApplication() (this is a plain ViewModel, not AndroidViewModel).
        val result = AdventureReelEncoder(appContext).encode(
            photoUris = photos,
            outputName = "reel_${tripName.replace(" ", "_")}.mp4",
            onProgress = { p -> _reelState.value = ReelState.Generating(tripName, p) }
        )
        _reelState.value = result.fold(
            onSuccess = { uri -> ReelState.Ready(tripName, uri) },
            onFailure = { e -> ReelState.Error(e.message ?: "Reel generation failed.") }
        )
    }
}

fun cancelReel() {
    reelEncoderJob?.cancel()
    reelEncoderJob = null
    _reelState.value = ReelState.Idle
}

fun reelConsumed() { _reelState.value = ReelState.Idle }
```

File: `app/src/main/java/com/houseofmmminq/macaco/ui/JournalViewModel.kt`

---

## Change 4 — AdventureReelEncoder: MediaCodec pipeline

New file. This is the core of the feature. Runs on a background coroutine (called from
`viewModelScope.launch(Dispatchers.IO)`).

```kotlin
// NEW FILE: app/src/main/java/com/houseofmmminq/macaco/data/sync/AdventureReelEncoder.kt

package com.houseofmmminq.macaco.data.sync

import android.content.Context
import android.graphics.*
import android.media.*
import android.net.Uri
import android.view.Surface
import androidx.core.content.FileProvider
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * Encodes a list of photo URIs into a 9:16 (720×1280) H.264 MP4 slideshow with Ken Burns
 * pan/zoom animation and cross-fade transitions between photos.
 *
 * Pipeline: decode bitmap → render frames to MediaCodec InputSurface via Canvas
 *           → MediaMuxer writes MP4 to cacheDir.
 *
 * Caller must run this on a background dispatcher (IO or Default).
 */
class AdventureReelEncoder(private val context: Context) {

    companion object {
        private const val WIDTH  = 720
        private const val HEIGHT = 1280
        private const val FPS    = 30
        private const val BITRATE = 2_000_000       // 2 Mbps — good quality, ~15 MB/min
        private const val PHOTO_FRAMES  = 90        // 3 s per photo at 30 fps
        private const val FADE_FRAMES   = 15        // 0.5 s crossfade
        private const val MIME = "video/avc"
    }

    suspend fun encode(
        photoUris: List<String>,
        outputName: String,
        onProgress: (Float) -> Unit
    ): Result<Uri> = runCatching {
        val outFile = File(context.cacheDir, outputName).also { it.delete() }
        val totalPhotos = photoUris.size
        // Total frames: each photo has PHOTO_FRAMES, plus FADE_FRAMES overlap between consecutive.
        val totalFrames = totalPhotos * PHOTO_FRAMES + (totalPhotos - 1) * FADE_FRAMES

        // ── Configure encoder ─────────────────────────────────────────────────────────────────
        val format = MediaFormat.createVideoFormat(MIME, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MIME)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface: Surface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerTrack = -1
        var muxerStarted = false
        var frameIndex = 0L
        val bufferInfo = MediaCodec.BufferInfo()

        // ── Render helper ─────────────────────────────────────────────────────────────────────
        // Each call draws one frame onto the encoder's input surface.
        fun renderFrame(bitmap: Bitmap, alpha: Float, frameInPhoto: Int) {
            val canvas: Canvas = inputSurface.lockHardwareCanvas()
                ?: inputSurface.lockCanvas(null)
            try {
                canvas.drawColor(Color.BLACK)
                // Ken Burns: linear pan from top-left crop to bottom-right crop.
                // Scale the bitmap to fill the canvas (center-crop), then offset slightly.
                val t = frameInPhoto.toFloat() / PHOTO_FRAMES          // 0..1
                val bw = bitmap.width.toFloat()
                val bh = bitmap.height.toFloat()
                // Fill scale — fit the shorter dimension, overflow the longer.
                val scale = maxOf(WIDTH / bw, HEIGHT / bh) * 1.08f    // 8% extra for pan room
                val scaledW = bw * scale
                val scaledH = bh * scale
                val maxDx = (scaledW - WIDTH) / 2f
                val maxDy = (scaledH - HEIGHT) / 2f
                val dx = WIDTH / 2f - scaledW / 2f + maxDx * (0.5f - t * 0.5f)
                val dy = HEIGHT / 2f - scaledH / 2f + maxDy * (0.5f - t * 0.5f)
                val paint = Paint().apply {
                    this.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
                    isFilterBitmap = true
                }
                canvas.save()
                canvas.translate(dx, dy)
                canvas.scale(scale, scale)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
                canvas.restore()
            } finally {
                inputSurface.unlockCanvasAndPost(canvas)
            }
        }

        // ── Drain helper ──────────────────────────────────────────────────────────────────────
        fun drainEncoder(endOfStream: Boolean) {
            if (endOfStream) encoder.signalEndOfInputStream()
            while (true) {
                val idx = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> { if (!endOfStream) break else continue }
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxerTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start(); muxerStarted = true
                    }
                    idx >= 0 -> {
                        val buf = encoder.getOutputBuffer(idx) ?: run {
                            encoder.releaseOutputBuffer(idx, false); return
                        }
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            encoder.releaseOutputBuffer(idx, false)
                        } else if (muxerStarted && bufferInfo.size > 0) {
                            bufferInfo.presentationTimeUs =
                                frameIndex * 1_000_000L / FPS
                            muxer.writeSampleData(muxerTrack, buf, bufferInfo)
                            encoder.releaseOutputBuffer(idx, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        } else {
                            encoder.releaseOutputBuffer(idx, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }
        }

        // ── Main render loop ──────────────────────────────────────────────────────────────────
        try {
            var prevBitmap: Bitmap? = null
            var framesRendered = 0

            for ((photoIdx, uriString) in photoUris.withIndex()) {
                coroutineContext.ensureActive()   // respect cancellation

                val bitmap = loadBitmap(uriString) ?: continue

                // Crossfade from previous photo into this one.
                if (prevBitmap != null) {
                    for (f in 0 until FADE_FRAMES) {
                        coroutineContext.ensureActive()
                        val alpha = f.toFloat() / FADE_FRAMES
                        renderFrame(prevBitmap, 1f - alpha, PHOTO_FRAMES - 1)
                        renderFrame(bitmap, alpha, 0)
                        drainEncoder(false)
                        frameIndex++
                        framesRendered++
                        onProgress(framesRendered.toFloat() / totalFrames)
                    }
                    prevBitmap.recycle()
                }

                // Main photo display.
                for (f in 0 until PHOTO_FRAMES) {
                    coroutineContext.ensureActive()
                    renderFrame(bitmap, 1f, f)
                    drainEncoder(false)
                    frameIndex++
                    framesRendered++
                    onProgress(framesRendered.toFloat() / totalFrames)
                }

                prevBitmap = bitmap
            }
            prevBitmap?.recycle()
            drainEncoder(true)
        } finally {
            encoder.stop(); encoder.release()
            if (muxerStarted) muxer.stop()
            muxer.release()
            inputSurface.release()
        }

        // Return a FileProvider URI so the share sheet can read the file from other apps.
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
    }

    /**
     * Decodes the photo at [uriString] subsampled to fit within 1080px on its longest edge.
     * Returns null if the URI is unreadable or the format is unsupported.
     */
    private fun loadBitmap(uriString: String): Bitmap? {
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Separate null-check so inJustDecodeBounds returning null doesn't short-circuit.
        val streamForBounds = resolver.openInputStream(uri) ?: return null
        streamForBounds.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        val rawLongest = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
        if (rawLongest <= 0) return null
        var sample = 1
        while (rawLongest / sample > 1080) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching {
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        }.getOrNull()
    }
}
```

File: `app/src/main/java/com/houseofmmminq/macaco/data/sync/AdventureReelEncoder.kt`

---

## Change 5 — FileProvider: declare the reel output path

The live `file_paths.xml` only declares a `files-path` entry — there is no `cache-path`.
Add one so `FileProvider` can serve the generated MP4 from `cacheDir`:

```xml
<!-- res/xml/file_paths.xml — ADD this entry -->
<cache-path name="reel_output" path="." />
```

Also verify the `FileProvider` authority in `AndroidManifest.xml` is
`${applicationId}.fileprovider` — that is the authority used in `AdventureReelEncoder`
(`context.packageName + ".fileprovider"`). If the manifest uses a different authority string,
update `AdventureReelEncoder` to match.

File: `app/src/main/res/xml/file_paths.xml`

---

## Change 6 — Wire ReelState in JournalListScreen

Collect `reelState` from the ViewModel and handle each state:

```kotlin
// In JournalListScreen composable body — add alongside other collected state
val reelState by viewModel.reelState.collectAsState()
val context = LocalContext.current

// Handle Ready state: launch share sheet, then reset
LaunchedEffect(reelState) {
    if (reelState is ReelState.Ready) {
        val ready = reelState as ReelState.Ready
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, ready.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(shareIntent,
                context.getString(R.string.reel_share_chooser))
        )
        viewModel.reelConsumed()
    }
}

// Show progress dialog when generating
if (reelState is ReelState.Generating) {
    val gen = reelState as ReelState.Generating
    ReelProgressDialog(
        tripName = gen.tripName,
        progress = gen.progress,
        onCancel = { viewModel.cancelReel() }
    )
}

// Show error snackbar when failed (reuse existing snackbarHostState)
LaunchedEffect(reelState) {
    if (reelState is ReelState.Error) {
        snackbarHostState.showSnackbar((reelState as ReelState.Error).message)
        viewModel.reelConsumed()
    }
}
```

Also pass `isPurchased` and `onCreateReel` to `TripHeader` calls in the `LazyColumn` trip
section — `isPurchased` is already collected from `viewModel.isPurchased`.

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`

---

## New strings

| Key | EN value |
|-----|----------|
| `reel_create_button` | Create Adventure Reel |
| `reel_generating_title` | Creating your Reel… |
| `reel_generating_subtitle` | Building "%s" |
| `reel_share_chooser` | Share Adventure Reel |
| `reel_no_photos_error` | No photos found for this trip |

Add to `strings.xml` and translate into all 11 supported languages
(values-de, values-es, values-fr, values-it, values-ja, values-nl, values-pl, values-pt,
values-sv, values-zh-rCN).

---

## Scope

- **In:** Trip header reel button (premium-gated), progress dialog, encoder, share sheet.
- **Out:** Background music — v2 scope. Silent reels are a complete, shippable v1.
- **Out:** Save-to-gallery option — v2. For now the MP4 is ephemeral (cacheDir); share it and it's gone.
- **Out:** Entries without a `tripName` — no reel button for ungrouped entries.
- **Out:** Custom reel ordering, duration controls — v2.
- **No changes to TravelEntry, Firestore, or Drive sync.**

**⚠️ Ship alone.** MediaCodec input-surface behaviour is device-dependent and this is a new
encode pipeline. Do not batch with other changes — ship as a single vc so any device-specific
codec or OOM failure is immediately attributable to this feature.

## Verification

1. Assign a `tripName` to 3+ entries that have photos.
2. Open Journal List — confirm the 🎬 icon appears in the trip header (premium account only).
3. Tap it — confirm the progress dialog appears and advances smoothly.
4. After completion, confirm the Android share sheet opens with a video file.
5. Play the shared video — confirm Ken Burns motion, crossfade transitions, and correct ordering (oldest entry first).
6. Confirm cancel (✕ in dialog) stops generation cleanly without a crash.
7. Test with a trip whose entries have no photos — confirm the error snackbar appears instead of the dialog.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add 🎬 button to `TripHeader`, premium-gated | `JournalListScreen.kt` |
| 2 | Add `ReelProgressDialog` composable | `JournalListScreen.kt` |
| 3 | Add `ReelState` sealed class + `startReel` / `cancelReel` / `reelConsumed` to ViewModel | `JournalViewModel.kt` |
| 4 | New `AdventureReelEncoder` — MediaCodec H.264 pipeline with Ken Burns + crossfade | `AdventureReelEncoder.kt` |
| 5 | Declare `cacheDir` in FileProvider `file_paths.xml` if not already present | `file_paths.xml` |
| 6 | Collect `reelState`, show dialog, launch share sheet on Ready | `JournalListScreen.kt` |
| 7 | New string keys ×11 languages | `strings.xml` |
