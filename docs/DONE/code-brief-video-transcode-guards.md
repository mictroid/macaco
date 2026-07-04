# Macaco — Video: Transcode Guards (cap the fallback, block input, queue multi-select)

Fixes QA V2/V5/V7 (`docs/qa-video-review-2026-07-04.md`): the transcode-failure fallback can
store an uncapped full-length original; the transcoding overlay doesn't block Save/back; and
multi-selecting videos mishandles trim + progress. Touches `VideoTranscoder.kt`,
`NewEditEntryScreen.kt`, `strings.xml` ×11.

---

## Change 1 — VideoTranscoder: fallback only for sources within the 15 s cap

**Problem:** On devices where MediaCodec transcoding fails (the S8-class fallback path),
`copyOriginalToCache` stores the ORIGINAL clip with no duration/size limit — a 10-minute 4K
gallery pick becomes hundreds of MB in MediaStore plus a full Drive upload. The 15-second
product cap is enforced only by the transcoder that just failed.

**Fix:** Allow the raw-copy fallback only when the source itself fits the cap (recorded clips
always do — `EXTRA_DURATION_LIMIT` caps them at 15 s). For longer sources, fail with null so
the caller can tell the user instead of silently storing the full clip.

```kotlin
// BEFORE (onTranscodeFailed, ~line 82)
                        override fun onTranscodeFailed(exception: Throwable) {
                            // Some devices' MediaCodec can't decode+re-encode the picked source
                            // (e.g. the Galaxy S8+/Exynos/API28 fails with "Failed to stop the
                            // muxer"). Rather than silently drop the video, fall back to storing the
                            // ORIGINAL clip unchanged so a tile always appears — no size reduction /
                            // no trim, but the feature works instead of no-op'ing.
                            android.util.Log.e("VideoTranscoder", "transcode failed — using original", exception)
                            outFile.delete()
                            cont.resume(copyOriginalToCache(context, sourceUri))
                        }

// AFTER
                        override fun onTranscodeFailed(exception: Throwable) {
                            // Some devices' MediaCodec can't decode+re-encode the picked source
                            // (e.g. the Galaxy S8+/Exynos/API28 fails with "Failed to stop the
                            // muxer"). Fall back to storing the ORIGINAL clip — but ONLY if it fits
                            // the 15 s cap (recorded clips always do). A longer source can't be
                            // trimmed on this device, and storing it whole would blow past the size
                            // budget (gallery + Drive), so fail and let the caller inform the user.
                            android.util.Log.e("VideoTranscoder", "transcode failed", exception)
                            outFile.delete()
                            cont.resume(
                                if (totalMs in 1..MAX_DURATION_MS + DURATION_TOLERANCE_MS)
                                    copyOriginalToCache(context, sourceUri)
                                else null
                            )
                        }
```

Apply the same guard in the synchronous `catch (e: Throwable)` setup-failure branch
(~line 99): `if (totalMs in 1..MAX_DURATION_MS + DURATION_TOLERANCE_MS) copyOriginalToCache(context, sourceUri) else null`.

Add the tolerance constant next to `MAX_DURATION_MS` (camera clips report e.g. 15_180 ms):

```kotlin
    const val MAX_DURATION_MS = 15_000L
    // Recorded "15 s" clips report slightly over (container overhead); accept up to +1 s.
    private const val DURATION_TOLERANCE_MS = 1_000L
```

(`totalMs` is already computed before the trim math — no new retrieval needed.)

**File:** `VideoTranscoder.kt`

---

## Change 2 — Edit screen: overlay consumes input; back blocked while transcoding

**Problem:** The full-screen transcoding scrim is a plain `Box` — taps pass straight through
to Save/fields beneath, and system back cancels the composition scope mid-transcode. Saving
during a transcode commits the entry without the video, silently.

**Fix:** Consume all pointer input on the scrim and swallow back presses while active.

```kotlin
// BEFORE (~line 934)
    // Transcoding overlay — full-screen scrim + circular progress, above the app bar.
    val progress = transcodingProgress
    if (progress != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {

// AFTER
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
```

(`BackHandler` and `pointerInput` are already imported in this file.)

**File:** `NewEditEntryScreen.kt`

---

## Change 3 — Queue multi-selected videos; sequential transcode; failure feedback

**Problem:** Picking several videos at once: (a) each >15 s video overwrites the single
`videoToTrim` slot — only the last gets the trim dialog, the rest vanish; (b) short videos
transcode in PARALLEL sharing one `transcodingProgress` — the first completion nulls it and
drops the overlay while others still run (which Change 2 would then block from saving, but the
progress is still wrong); (c) a null transcode result (now possible per Change 1) is silent.

**Fix:** Process picks sequentially through one queue; trim dialogs pop one at a time; toast
on failure.

```kotlin
// BEFORE (videoPicker, ~line 340)
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_VIDEOS)
    ) { uris ->
        val canAdd = MAX_VIDEOS - videoUris.size
        if (canAdd > 0) {
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
                    // Longer clip — ask the user to trim before transcoding.
                    videoToTrim = uri
                    videoToDuration = durationMs
                }
            }
        }
    }

// AFTER
    // Shared store-the-result step for every transcode path (record, pick, trim).
    // Toasts when the clip couldn't be processed (e.g. fallback refused an over-length source).
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
    // Long videos waiting for the trim dialog — head of the list is the one on screen.
    var trimQueue by remember { mutableStateOf<List<Pair<Uri, Long>>>(emptyList()) }

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
        // Short clips transcode SEQUENTIALLY in one coroutine so the shared progress
        // overlay stays up (and coherent) until the last one finishes.
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
```

Replace the single-slot trim dialog wiring (~line 904):

```kotlin
// BEFORE
    val trimUri = videoToTrim
    if (trimUri != null) {
        VideoTrimDialog(
            …
                videoToTrim = null
            …
            onDismiss = { videoToTrim = null }
        )
    }

// AFTER — one dialog per queue head; confirming/dismissing advances the queue.
    trimQueue.firstOrNull()?.let { (trimUri, trimDuration) ->
        VideoTrimDialog(
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
```

Delete the now-unused `videoToTrim` / `videoToDuration` state vars, and refactor the record
launcher (~line 300) and the existing trim-confirm body to call `storeTranscoded(outFile)`
instead of their inline copies (the record path keeps its `ImageStorage.clear(context,
ImageStorage.VIDEO_TEMP)` call).

Match `VideoTrimDialog`'s actual parameter names when wiring (`durationMs`, `onTrimConfirmed`,
`onDismiss` — verify against ~line 1399).

New string (×11):

| Key | EN value |
|-----|----------|
| `video_add_failed` | This video couldn't be processed on this device |

**File:** `NewEditEntryScreen.kt`, `res/values*/strings.xml`

---

## Scope notes

- `trimQueue` is `remember`, not saveable (a `Uri` queue of picker grants wouldn't survive
  process death anyway — the grants die with the process). Acceptable.
- V12 (picker allows selecting more than the free slots, overflow dropped) is half-covered by
  the toast pathway; a dedicated "only N more videos" message is optional — Code's call.

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Fallback-to-original only when source ≤ 15 s (+1 s tolerance); else null | `VideoTranscoder.kt` |
| 2 | Overlay consumes taps; `BackHandler` while transcoding | `NewEditEntryScreen.kt` |
| 3 | Trim queue, sequential short-clip transcodes, shared store step + failure toast | `NewEditEntryScreen.kt`, `strings.xml` ×11 |
