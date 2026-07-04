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
    // Recorded "15 s" clips report slightly over (container overhead); accept up to +1 s.
    private const val DURATION_TOLERANCE_MS = 1_000L

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
        // Hoisted above the try so both the failure callback and the catch block can consult it
        // when deciding whether the fallback-to-original is allowed (see the duration guard below).
        val totalMs = getDurationMs(context, sourceUri)
        try {
            val keepMs = minOf(durationMs, MAX_DURATION_MS)

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
                            // User cancelled — no tile wanted, so no fallback.
                            outFile.delete()
                            cont.resume(null)
                        }
                        override fun onTranscodeFailed(exception: Throwable) {
                            // Some devices' MediaCodec can't decode+re-encode the picked source
                            // (e.g. the Galaxy S8+/Exynos/API28 fails with "Failed to stop the
                            // muxer"; some A53 HEVC clips fail ClipDataSource+MediaCodec init).
                            android.util.Log.e("VideoTranscoder", "transcode failed", exception)
                            outFile.delete()
                            // First try extracting just the wanted window with MediaExtractor +
                            // MediaMuxer (no re-encode, so no MediaCodec) — this preserves the user's
                            // trim on devices where the re-encode fails. Only fall back to copying the
                            // whole ORIGINAL when no trim applies AND it fits the 15 s cap (storing a
                            // long clip whole would blow the gallery + Drive size budget).
                            val muxerResult = if (trimStartMs > 0L || keepMs < totalMs)
                                trimWithMuxer(context, sourceUri, trimStartMs, keepMs) else null
                            cont.resume(
                                muxerResult
                                    ?: if (totalMs in 1..MAX_DURATION_MS + DURATION_TOLERANCE_MS)
                                        copyOriginalToCache(context, sourceUri)
                                    else null
                            )
                        }
                    })
                    .transcode()
                cont.invokeOnCancellation { future.cancel(true) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            outFile.delete()
            throw e
        } catch (e: Throwable) {
            // Any synchronous setup failure (bad trim values, unreadable source, muxer init, …).
            // Try the no-re-encode muxer trim first (works where MediaCodec setup failed), then fall
            // back to the whole original — but only if it fits the 15 s cap, so a failed setup on a
            // long clip can't smuggle a full-length original into storage/Drive.
            android.util.Log.e("VideoTranscoder", "transcode setup failed", e)
            outFile.delete()
            val keepMs = minOf(durationMs, MAX_DURATION_MS)
            val muxerResult = if (trimStartMs > 0L || keepMs < totalMs)
                trimWithMuxer(context, sourceUri, trimStartMs, keepMs) else null
            muxerResult
                ?: if (totalMs in 1..MAX_DURATION_MS + DURATION_TOLERANCE_MS)
                    copyOriginalToCache(context, sourceUri)
                else null
        }
    }

    /**
     * Copies [sourceUri]'s raw bytes into a cacheDir `.mp4` so [ImageStorage.persistVideoToGallery]
     * can store it unchanged. Used as a fallback when this device's transcoder can't process the
     * source. Returns null if the source is genuinely unreadable (then the caller drops the tile).
     */
    private fun copyOriginalToCache(context: Context, sourceUri: Uri): File? = runCatching {
        val fallback = File(context.cacheDir, "original_${System.currentTimeMillis()}.mp4")
        val ok = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            fallback.outputStream().use { input.copyTo(it) }
            true
        } ?: false
        if (ok) fallback else { fallback.delete(); null }
    }.getOrNull()

    /**
     * Extracts the window [trimStartMs, trimStartMs + keepMs] from [sourceUri] using
     * MediaExtractor + MediaMuxer (no re-encode). Returns the output File on success, null on failure.
     * Output codec/resolution match the source (not normalized to 720p/H.264 like transcode()), but
     * it avoids MediaCodec entirely so it works on devices where the re-encoding transcode fails.
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
            muxer.release()
            extractor.release()
            outFile.delete()
            return@runCatching null
        }

        val trimStartUs = trimStartMs * 1000L
        val trimEndUs = (trimStartMs + keepMs) * 1000L
        extractor.seekTo(trimStartUs, android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        muxer.start()

        val buffer = java.nio.ByteBuffer.allocate(1 * 1024 * 1024) // 1 MB
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

            val muxerTrack = trackIndexMap[extractor.sampleTrackIndex] ?: run { extractor.advance(); continue }

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
