# Macaco — Adventure Reel: Include Video First Frames

When entries contain videos (mixed with photos via `mediaOrder`), the Adventure Reel should
include the first frame of each video clip as a still slide, positioned in the correct
`mediaOrder` sequence. Entries with no videos are unaffected.

**No changes to `AdventureReelEncoder.kt`** — it already handles any URI that
`contentResolver.openInputStream()` resolves, including internal `file://` cache paths.

**File changed:** `app/src/main/java/com/houseofmmminq/macaco/ui/viewmodel/JournalViewModel.kt`

---

## What changes in `startReel()`

Currently the function builds `reelPhotos` synchronously on the calling thread (photos only),
then launches an IO coroutine to encode. This brief:

1. Moves the `reelPhotos` assembly **inside** the IO coroutine so video first-frame extraction
   (`MediaMetadataRetriever`) runs off the main thread.
2. Extends the assembly to respect `mediaOrder` when non-empty, interleaving video first
   frames with photos in user-defined order.
3. For each video URI: extracts the first frame → compresses to a temp JPEG in `cacheDir` →
   passes the `file://` URI to `ReelPhotoMeta`. Temp files are deleted when encoding finishes.

---

## Replacement for `startReel()` (~line 147)

### BEFORE
```kotlin
fun startReel(tripName: String, entries: List<TravelEntry>) {
    val reelPhotos = entries
        .sortedBy { it.dateMillis }
        .flatMap { entry ->
            val cache = cachedDrivePhotos.value
            val count = maxOf(entry.photoUris.size, entry.driveFileIds.size)
            val uris = (0 until count).mapNotNull { i ->
                entry.driveFileIds.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { cache[it] }
                    ?: entry.photoUris.getOrNull(i)
            }.filter { it.isNotBlank() }
            val dateStr = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
                .format(java.util.Date(entry.dateMillis))
            val overlayText =
                if (entry.location.isNotBlank()) "${entry.location} · $dateStr" else null
            uris.map { uri -> ReelPhotoMeta(uri = uri, overlayText = overlayText) }
        }

    if (reelPhotos.isEmpty()) {
        _reelState.value = ReelState.Error(appContext.getString(R.string.reel_no_photos_error))
        return
    }

    reelEncoderJob = viewModelScope.launch(Dispatchers.IO) {
        _reelState.value = ReelState.Generating(tripName, 0f)
        val result = AdventureReelEncoder(appContext).encode(
            photos = reelPhotos,
            outputName = "reel_${tripName.replace(" ", "_")}.mp4",
            onProgress = { p -> _reelState.value = ReelState.Generating(tripName, p) }
        )
        _reelState.value = result.fold(
            onSuccess = { uri -> ReelState.Ready(tripName, uri) },
            onFailure = { e ->
                when {
```

### AFTER
```kotlin
fun startReel(tripName: String, entries: List<TravelEntry>) {
    reelEncoderJob = viewModelScope.launch(Dispatchers.IO) {
        _reelState.value = ReelState.Generating(tripName, 0f)

        // Build the ordered frame list on the IO thread so first-frame extraction
        // (MediaMetadataRetriever) is safe to call here.
        val tempFrameFiles = mutableListOf<File>()
        val reelPhotos = entries
            .sortedBy { it.dateMillis }
            .flatMap { entry ->
                val cache = cachedDrivePhotos.value
                val dateStr = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
                    .format(java.util.Date(entry.dateMillis))
                val overlayText =
                    if (entry.location.isNotBlank()) "${entry.location} · $dateStr" else null

                // Resolve media in display order.
                // New entries use mediaOrder (photos + videos interleaved by user).
                // Legacy entries (mediaOrder empty) fall back to photos-only, unchanged.
                val orderedItems: List<Pair<String, String>> = if (entry.mediaOrder.isNotEmpty()) {
                    entry.mediaOrder.mapNotNull { uri ->
                        when {
                            uri in entry.photoUris -> uri to "photo"
                            uri in entry.videoUris -> uri to "video"
                            else -> null
                        }
                    }
                } else {
                    val count = maxOf(entry.photoUris.size, entry.driveFileIds.size)
                    (0 until count).mapNotNull { i ->
                        val resolved =
                            entry.driveFileIds.getOrNull(i)?.takeIf { it.isNotEmpty() }
                                ?.let { cache[it] }
                                ?: entry.photoUris.getOrNull(i)
                        resolved?.takeIf { it.isNotBlank() }?.let { it to "photo" }
                    }
                }

                orderedItems.mapNotNull { (uri, type) ->
                    val resolvedUri: String? = if (type == "photo") {
                        // Existing Drive-cache resolution: prefer cached copy if present.
                        val idx = entry.photoUris.indexOf(uri)
                        val driveId = entry.driveFileIds.getOrNull(idx)
                        driveId?.takeIf { it.isNotEmpty() }?.let { cache[it] } ?: uri
                    } else {
                        // Video: extract first frame → temp JPEG → file:// URI.
                        val bitmap = VideoTranscoder.getFirstFrame(
                            appContext, android.net.Uri.parse(uri)
                        ) ?: return@mapNotNull null
                        val tempFile = File(
                            appContext.cacheDir,
                            "reel_vf_${entry.id}_${uri.hashCode()}.jpg"
                        )
                        tempFile.outputStream().use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        bitmap.recycle()
                        tempFrameFiles += tempFile
                        android.net.Uri.fromFile(tempFile).toString()
                    }
                    resolvedUri?.let { ReelPhotoMeta(uri = it, overlayText = overlayText) }
                }
            }

        if (reelPhotos.isEmpty()) {
            tempFrameFiles.forEach { it.delete() }
            _reelState.value = ReelState.Error(appContext.getString(R.string.reel_no_photos_error))
            return@launch
        }

        val result = AdventureReelEncoder(appContext).encode(
            photos = reelPhotos,
            outputName = "reel_${tripName.replace(" ", "_")}.mp4",
            onProgress = { p -> _reelState.value = ReelState.Generating(tripName, p) }
        )
        // Clean up temp video frame files regardless of encode outcome.
        tempFrameFiles.forEach { it.delete() }
        _reelState.value = result.fold(
            onSuccess = { uri -> ReelState.Ready(tripName, uri) },
            onFailure = { e ->
                when {
```

Note: the `onFailure` branch continues unchanged from the original — only paste through the
closing braces to match the existing structure. The change ends after `tempFrameFiles.forEach`.

---

## Required import

Add to the import block if not already present:

```kotlin
import com.houseofmmminq.macaco.util.VideoTranscoder
```

---

## Behaviour summary

| Entry type | Reel output |
|---|---|
| Photos only, no `mediaOrder` | Unchanged — Drive-cached photos in `photoUris` order |
| Photos + videos, `mediaOrder` present | Photos and video first frames in `mediaOrder` sequence |
| Videos only (no photos), `mediaOrder` present | Video first frames only |
| All entries have no media | Existing `reel_no_photos_error` error state |

Temp JPEG files are written to `cacheDir` during encoding and deleted immediately after
`encode()` returns, whether it succeeds or fails. Cancellation via `cancelReel()` is safe:
`viewModelScope.launch` cancellation propagates into `encode()`, and `invokeOnCompletion`
is not needed because the delete call sits right after the `encode()` return in the same
coroutine — it runs even if `encode()` throws (the outer `runCatching` in the encoder
catches the exception and returns a `Result.failure`, so `result` is always non-null here).
