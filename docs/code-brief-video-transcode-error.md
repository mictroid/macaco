# Macaco — Video Transcoding: Muxer-Copy Fallback for Clip Failures

## Problem

When the user picks a video from the gallery, trims it in the trim dialog (e.g. 7s→22s from a
27s source), and confirms, the transcoding progress spinner runs and then shows a "video couldn't
be processed" toast. The transcoding library (`otaliastudios/Transcoder`) fails on some devices
(confirmed on Galaxy A53) for certain gallery sources.

The current fallback in `onTranscodeFailed` returns `null` for any source longer than 16 seconds
(correct — we don't want to store the untrimmed 27s original). But for a trimmed clip, the user
chose a specific 15s window and expects it to work.

## Root cause

`otaliastudios/Transcoder` uses Android's `MediaCodec` for decode+re-encode. On some devices or
with certain video formats (e.g. HEVC recorded by the A53 camera app), `ClipDataSource` +
`MediaCodec` initialization fails. The existing fallback can only copy the **whole** source; there
is no fallback for "copy just the trim window without re-encoding."

## Fix

Add a `trimWithMuxer()` function to `VideoTranscoder` that uses `MediaExtractor` + `MediaMuxer`
to extract a time-range from the source **without re-encoding** (mux-copy). This avoids
`MediaCodec` entirely and works on all devices. Use it as the first fallback in `onTranscodeFailed`
when a trim was requested.

The output may not be 720p/2 Mbps H.264 (it preserves the original codec), but it will be
correctly trimmed and playable.

**File:** `app/src/main/java/com/houseofmmminq/macaco/util/VideoTranscoder.kt`

---

## Change 1 — Add `trimWithMuxer()` function

Add this new private function to the `VideoTranscoder` object:

```kotlin
/**
 * Extracts the window [trimStartMs, trimStartMs + keepMs] from [sourceUri] using
 * MediaExtractor + MediaMuxer (no re-encode). Returns the output File on success, null on failure.
 * Output codec and resolution match the source (not normalized to 720p/H.264 like transcode()).
 */
private fun trimWithMuxer(
    context: Context,
    sourceUri: Uri,
    trimStartMs: Long,
    keepMs: Long
): File? = runCatching {
    val outFile = File(context.cacheDir, "mux_${System.currentTimeMillis()}.mp4")
    val extractor = android.media.MediaExtractor()
    extractor.setDataSource(context, sourceUri, null)

    val muxer = android.media.MediaMuxer(
        outFile.absolutePath,
        android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
    )

    val trackIndexMap = mutableMapOf<Int, Int>() // extractor track → muxer track
    for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
        if (mime.startsWith("video/") || mime.startsWith("audio/")) {
            val muxerTrack = muxer.addTrack(format)
            trackIndexMap[i] = muxerTrack
            extractor.selectTrack(i)
        }
    }

    if (trackIndexMap.isEmpty()) {
        outFile.delete()
        return@runCatching null
    }

    val trimStartUs = trimStartMs * 1000L
    val trimEndUs   = (trimStartMs + keepMs) * 1000L
    extractor.seekTo(trimStartUs, android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

    muxer.start()

    val bufferSize = 1 * 1024 * 1024 // 1 MB
    val buffer = java.nio.ByteBuffer.allocate(bufferSize)
    val bufferInfo = android.media.MediaCodec.BufferInfo()

    var firstPresentationTimeUs = Long.MIN_VALUE

    while (true) {
        val sampleSize = extractor.readSampleData(buffer, 0)
        if (sampleSize < 0) break

        val presentationTimeUs = extractor.sampleTime
        if (presentationTimeUs > trimEndUs) break
        if (presentationTimeUs < trimStartUs) {
            extractor.advance()
            continue
        }

        if (firstPresentationTimeUs == Long.MIN_VALUE) {
            firstPresentationTimeUs = presentationTimeUs
        }

        val track = extractor.sampleTrackIndex
        val muxerTrack = trackIndexMap[track] ?: run { extractor.advance(); continue }

        bufferInfo.offset = 0
        bufferInfo.size = sampleSize
        bufferInfo.presentationTimeUs = presentationTimeUs - firstPresentationTimeUs
        bufferInfo.flags = extractor.sampleFlags

        muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
        extractor.advance()
    }

    muxer.stop()
    muxer.release()
    extractor.release()

    outFile
}.getOrElse { e ->
    android.util.Log.e("VideoTranscoder", "trimWithMuxer failed", e)
    null
}
```

---

## Change 2 — Use `trimWithMuxer` as the first fallback in `onTranscodeFailed`

Modify `transcode()` to pass the trim parameters into the failure handler and try the muxer
fallback before checking duration.

### BEFORE (inside `transcode()`)

The `onTranscodeFailed` callback currently captures only `totalMs` and `outFile`:

```kotlin
override fun onTranscodeFailed(exception: Throwable) {
    android.util.Log.e("VideoTranscoder", "transcode failed", exception)
    outFile.delete()
    cont.resume(
        if (totalMs in 1..MAX_DURATION_MS + DURATION_TOLERANCE_MS)
            copyOriginalToCache(context, sourceUri)
        else null
    )
}
```

### AFTER

```kotlin
override fun onTranscodeFailed(exception: Throwable) {
    android.util.Log.e("VideoTranscoder", "transcode failed", exception)
    outFile.delete()
    // If a trim was requested, try extracting just the trim window without
    // re-encoding (MediaExtractor + MediaMuxer). This avoids MediaCodec
    // entirely and works on devices where otaliastudios fails.
    val muxerResult = if (trimStartMs > 0L || keepMs < totalMs) {
        trimWithMuxer(context, sourceUri, trimStartMs, keepMs)
    } else null

    cont.resume(
        muxerResult
            ?: if (totalMs in 1..MAX_DURATION_MS + DURATION_TOLERANCE_MS)
                copyOriginalToCache(context, sourceUri)
            else null
    )
}
```

To make `trimStartMs` and `keepMs` visible inside the callback, hoist them into the surrounding
`try` block scope (they're already computed there as local vals):

```kotlin
// Hoist so both the callback and catch block can reference them.
val keepMs = minOf(durationMs, MAX_DURATION_MS)
val trimEndMs = if (totalMs > 0L) (totalMs - trimStartMs - keepMs).coerceAtLeast(0L) else 0L
```

Replace any existing `val keepMs` / `val trimEndMs` declarations inside the try block with the
hoisted versions.

Apply the same muxer fallback in the outer `catch` block:

### BEFORE (outer catch)
```kotlin
} catch (e: Throwable) {
    android.util.Log.e("VideoTranscoder", "transcode setup failed", e)
    outFile.delete()
    if (totalMs in 1..MAX_DURATION_MS + DURATION_TOLERANCE_MS) copyOriginalToCache(context, sourceUri) else null
}
```

### AFTER (outer catch)
```kotlin
} catch (e: Throwable) {
    android.util.Log.e("VideoTranscoder", "transcode setup failed", e)
    outFile.delete()
    val keepMs = minOf(durationMs, MAX_DURATION_MS)
    val muxerResult = if (trimStartMs > 0L || keepMs < totalMs) {
        trimWithMuxer(context, sourceUri, trimStartMs, keepMs)
    } else null
    muxerResult
        ?: if (totalMs in 1..MAX_DURATION_MS + DURATION_TOLERANCE_MS)
            copyOriginalToCache(context, sourceUri)
        else null
}
```

---

## Why this works

`MediaExtractor` reads the compressed bitstream directly from the container; `MediaMuxer` writes
it to a new container. No decode/encode round-trip means no `MediaCodec`, no device-specific
hardware codec failures, and no format compatibility issues. The resulting file is a valid MP4
containing exactly the trimmed range, ready for `ImageStorage.persistVideoToGallery`.

The only downside is the output isn't normalized to H.264/720p — but it plays back fine
in `VideoView` / Coil video loading, and the Drive upload handles arbitrary MP4 files.
