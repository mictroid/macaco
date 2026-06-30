package com.houseofmmminq.macaco.data.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.view.Surface
import androidx.core.content.FileProvider
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * Encodes a list of photo URIs into a 9:16 (720×1280) H.264 MP4 slideshow with Ken Burns
 * pan/zoom animation and cross-dissolve transitions between photos.
 *
 * Pipeline: decode bitmap → render frames to a MediaCodec input surface via Canvas
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
        private const val FADE_FRAMES   = 15        // 0.5 s cross-dissolve
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
        val totalFrames = (totalPhotos * PHOTO_FRAMES + (totalPhotos - 1) * FADE_FRAMES)
            .coerceAtLeast(1)

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
        // Monotonic count of frames actually written to the muxer — drives presentation time so the
        // PTS is evenly spaced regardless of how far the encoder lags behind the render loop. (Using
        // the live render counter here, as the original draft did, produced drifting/non-monotonic
        // timestamps because output buffers are dequeued well after their input frame was posted.)
        var muxedFrames = 0L
        val bufferInfo = MediaCodec.BufferInfo()

        // ── Draw helpers ──────────────────────────────────────────────────────────────────────
        // Paints one Ken Burns layer onto an already-locked canvas. Does NOT lock/post — so several
        // layers (e.g. a cross-dissolve) can be composited into a SINGLE posted frame.
        fun drawKenBurns(canvas: Canvas, bitmap: Bitmap, alpha: Float, frameInPhoto: Int) {
            val t = frameInPhoto.toFloat() / PHOTO_FRAMES          // 0..1
            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            // Fill scale — fit the shorter dimension, overflow the longer, with extra room to pan.
            val scale = maxOf(WIDTH / bw, HEIGHT / bh) * 1.08f
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
        }

        // Locks the surface once, clears to black, runs [draw] to composite the frame, then posts it.
        fun postFrame(draw: (Canvas) -> Unit) {
            val canvas = inputSurface.lockHardwareCanvas()
            try {
                canvas.drawColor(Color.BLACK)
                draw(canvas)
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
                        val buf = encoder.getOutputBuffer(idx)
                        if (buf == null) {
                            encoder.releaseOutputBuffer(idx, false)
                        } else if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            // Codec config (SPS/PPS) is consumed by addTrack, not muxed directly.
                            encoder.releaseOutputBuffer(idx, false)
                        } else if (muxerStarted && bufferInfo.size > 0) {
                            bufferInfo.presentationTimeUs = muxedFrames * 1_000_000L / FPS
                            muxer.writeSampleData(muxerTrack, buf, bufferInfo)
                            muxedFrames++
                            encoder.releaseOutputBuffer(idx, false)
                        } else {
                            encoder.releaseOutputBuffer(idx, false)
                        }
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
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
                val prev = prevBitmap

                // Cross-dissolve from the previous photo into this one — both layers in ONE frame so
                // they actually blend (the old draft posted them as two separate frames, so the
                // second's drawColor(BLACK) erased the first and nothing ever crossfaded).
                if (prev != null) {
                    for (f in 0 until FADE_FRAMES) {
                        coroutineContext.ensureActive()
                        val alpha = f.toFloat() / FADE_FRAMES
                        postFrame { canvas ->
                            drawKenBurns(canvas, prev, 1f, PHOTO_FRAMES - 1)
                            drawKenBurns(canvas, bitmap, alpha, f)
                        }
                        drainEncoder(false)
                        framesRendered++
                        onProgress((framesRendered.toFloat() / totalFrames).coerceAtMost(1f))
                    }
                    prev.recycle()
                }

                // Main photo display.
                for (f in 0 until PHOTO_FRAMES) {
                    coroutineContext.ensureActive()
                    postFrame { canvas -> drawKenBurns(canvas, bitmap, 1f, f) }
                    drainEncoder(false)
                    framesRendered++
                    onProgress((framesRendered.toFloat() / totalFrames).coerceAtMost(1f))
                }

                prevBitmap = bitmap
            }
            prevBitmap?.recycle()
            drainEncoder(true)
        } finally {
            runCatching { encoder.stop() }
            encoder.release()
            if (muxerStarted) runCatching { muxer.stop() }
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
