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
        try {
            val keepMs = minOf(durationMs, MAX_DURATION_MS)
            val totalMs = getDurationMs(context, sourceUri)

            // otaliastudios ClipDataSource(source, trimStartUs, trimEndUs) trims from BOTH ends
            // (start offset + amount off the tail), NOT (start, duration). To keep the window
            // [trimStartMs, trimStartMs+keepMs] we trim trimStartMs off the front and
            // (total - trimStartMs - keepMs) off the back. When nothing needs trimming (a clip that
            // already fits from 0), use the raw source so we never pass a negative/invalid trim value
            // (that threw IllegalArgumentException "Trim values cannot be negative" and crashed).
            val trimEndMs = if (totalMs > 0L) (totalMs - trimStartMs - keepMs).coerceAtLeast(0L) else 0L
            val base = UriDataSource(context, sourceUri)
            val dataSource = if (trimStartMs <= 0L && trimEndMs <= 0L) base
                else ClipDataSource(base, trimStartMs * 1000L, trimEndMs * 1000L)

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
                            android.util.Log.e("VideoTranscoder", "transcode failed", exception)
                            outFile.delete()
                            cont.resume(null)
                        }
                    })
                    .transcode()
                cont.invokeOnCancellation { future.cancel(true) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            outFile.delete()
            throw e
        } catch (e: Throwable) {
            // Any synchronous setup failure (bad trim values, unreadable source, muxer init, …)
            // degrades to "no video added" instead of crashing the app.
            android.util.Log.e("VideoTranscoder", "transcode setup failed", e)
            outFile.delete()
            null
        }
    }

    /** Returns the duration of [uri] in milliseconds, or 0 on failure. */
    fun getDurationMs(context: Context, uri: Uri): Long = runCatching {
        // MediaMetadataRetriever.close() (AutoCloseable) is API 29+; release() works from API 10,
        // so use a finally to stay safe on the API 24 min (and the API 28 S8+ test device).
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } finally {
            r.release()
        }
    }.getOrDefault(0L)

    /** Returns a [Bitmap] of the first frame, or null on failure. Used for thumbnails. */
    fun getFirstFrame(context: Context, uri: Uri): android.graphics.Bitmap? = runCatching {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            r.getFrameAtTime(0L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally {
            r.release()
        }
    }.getOrNull()
}
