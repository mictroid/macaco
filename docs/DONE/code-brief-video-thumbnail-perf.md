# Macaco — Video: Async Thumbnails (stop extracting frames on the main thread)

Fixes QA V6 (`docs/qa-video-review-2026-07-04.md`): both video tiles run
`MediaMetadataRetriever` frame extraction synchronously during composition — 100 ms–1 s+ per
tile on the main thread, janking entry-open and re-running on every scroll-back. Touches a new
`util/VideoThumbnails.kt`, `EntryDetailScreen.kt`, `NewEditEntryScreen.kt`.

---

## Change 1 — Shared async thumbnail loader with an in-memory cache

**Fix:** One small helper: an LruCache keyed by URI plus a composable state producer that
extracts on `Dispatchers.IO`. Both tiles use it; scroll-backs hit the cache instead of
re-extracting.

```kotlin
// NEW FILE — app/src/main/java/com/houseofmmminq/macaco/util/VideoThumbnails.kt
package com.houseofmmminq.macaco.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * First-frame thumbnails for video tiles. Extraction (MediaMetadataRetriever) takes
 * 100 ms–1 s+, so it must never run on the main thread; results are memory-cached by URI so
 * scrolling back to a tile is instant and doesn't re-extract.
 */
object VideoThumbnails {

    // ~24 MB of thumbnails (tile-sized frames are ~1–4 MB each as ARGB_8888).
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Cached thumbnail for [uri], loading it on IO on first request. Null while loading/failed. */
    @Composable
    fun rememberThumbnail(uri: String): State<Bitmap?> {
        val context = LocalContext.current.applicationContext
        return produceState(initialValue = cache.get(uri), uri) {
            if (value == null) {
                value = withContext(Dispatchers.IO) {
                    VideoTranscoder.getFirstFrame(context, Uri.parse(uri))
                        ?.also { cache.put(uri, it) }
                }
            }
        }
    }
}
```

**File:** `util/VideoThumbnails.kt` (new)

---

## Change 2 — EntryDetailScreen.VideoEntryTile

```kotlin
// BEFORE (~line in VideoEntryTile)
    val thumbnail = remember(uri) { VideoTranscoder.getFirstFrame(context, Uri.parse(uri)) }

// AFTER
    val thumbnail by VideoThumbnails.rememberThumbnail(uri)
```

(Add `import com.houseofmmminq.macaco.util.VideoThumbnails` and
`androidx.compose.runtime.getValue`; the existing null-branch placeholder already doubles as
the loading state — no other changes. `VideoTranscoder` import stays for the player/duration
uses if any; remove if now unused.)

**File:** `EntryDetailScreen.kt`

---

## Change 3 — NewEditEntryScreen.VideoThumbnailTile

```kotlin
// BEFORE (~line 1461)
private fun VideoThumbnailTile(uri: String) {
    val context = LocalContext.current
    val bitmap = remember(uri) { VideoTranscoder.getFirstFrame(context, Uri.parse(uri)) }

// AFTER
private fun VideoThumbnailTile(uri: String) {
    val bitmap by VideoThumbnails.rememberThumbnail(uri)
```

(Same imports; drop the now-unused `context` val if nothing else in the function uses it.)

**File:** `NewEditEntryScreen.kt`

---

## Scope notes

- Cache is process-lifetime and never invalidated: fine, because stored video URIs are
  immutable (a new clip always gets a new MediaStore URI).
- Do NOT cache/persist to disk — the Drive download cache already keeps the videos local;
  thumbnails rebuild cheaply per process.

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `VideoThumbnails` LruCache + `rememberThumbnail` (IO-backed produceState) | `util/VideoThumbnails.kt` (new) |
| 2 | Detail tile uses async thumbnail | `EntryDetailScreen.kt` |
| 3 | Edit tile uses async thumbnail | `NewEditEntryScreen.kt` |
