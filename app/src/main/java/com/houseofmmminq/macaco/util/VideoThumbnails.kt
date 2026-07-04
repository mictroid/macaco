package com.houseofmmminq.macaco.util

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
