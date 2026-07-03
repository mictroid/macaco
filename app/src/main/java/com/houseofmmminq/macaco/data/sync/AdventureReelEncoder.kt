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
import com.houseofmmminq.macaco.R
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Metadata for one photo in the reel.
 * [overlayText] appears as a location/date pill at the bottom of the frame —
 * pass null to skip the overlay for that photo.
 */
data class ReelPhotoMeta(
    val uri: String,
    val overlayText: String? = null   // e.g. "Patagonia · Jun 2025"
)

/**
 * Encodes a list of photos into a 9:16 (720×1280) H.264 MP4 slideshow with Ken Burns
 * pan/zoom animation, ease-in-out cross-dissolve transitions, and a location/macaco branding
 * overlay.
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

    // Pre-allocated Paints — never allocate inside the render loop (GC stall risk on A53's
    // Exynos 1280). These are android.graphics.Paint writing pixels into the video frame, not
    // Compose theming, so the hardcoded colours are correct here. Only kenBurnsPaint.alpha
    // changes per frame; it's set inline before each drawBitmap.
    private val kenBurnsPaint = Paint().apply { isFilterBitmap = true }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(178, 7, 30, 38)   // macaco dark-teal at 70% opacity
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT
    }
    private val logoPaint = Paint().apply { alpha = 38 }       // ~15% opacity

    suspend fun encode(
        photos: List<ReelPhotoMeta>,
        outputName: String,
        onProgress: (Float) -> Unit
    ): Result<Uri> = runCatching {
        val outFile = File(context.cacheDir, outputName).also { it.delete() }
        val totalPhotos = photos.size
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
            // Coerce so the extended main loop (f runs up to PHOTO_FRAMES + FADE_FRAMES - 1) stays in [0,1].
            val t = (frameInPhoto.toFloat() / PHOTO_FRAMES).coerceAtMost(1f)
            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            // Fill scale: exactly enough to cover the frame on the short axis.
            val fillScale = maxOf(WIDTH / bw, HEIGHT / bh)
            // Animate from 1.00× to 1.04× — less crop than the old 1.08×, slow cinematic pull-in.
            val scale = fillScale * (1.00f + 0.04f * t)
            val scaledW = bw * scale
            val scaledH = bh * scale
            // Overflow room available for panning (0 if photo perfectly matches the frame ratio).
            val maxDx = (scaledW - WIDTH).coerceAtLeast(0f) / 2f
            val maxDy = (scaledH - HEIGHT).coerceAtLeast(0f) / 2f
            // Pan from upper-left offset toward centre as the photo plays (matches the pull-in).
            val dx = WIDTH / 2f - scaledW / 2f + maxDx * (1f - t)
            val dy = HEIGHT / 2f - scaledH / 2f + maxDy * (1f - t)
            kenBurnsPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(dx, dy)
            canvas.scale(scale, scale)
            canvas.drawBitmap(bitmap, 0f, 0f, kenBurnsPaint)
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
        val logoBitmap = loadLogoBitmap(sizePx = 48)
        try {
            var prevBitmap: Bitmap? = null
            var framesRendered = 0

            for ((photoIdx, meta) in photos.withIndex()) {
                coroutineContext.ensureActive()   // respect cancellation

                val bitmap = loadBitmap(meta.uri) ?: continue
                val prev = prevBitmap

                // Cross-dissolve from the previous photo into this one — both layers in ONE frame so
                // they actually blend (the old draft posted them as two separate frames, so the
                // second's drawColor(BLACK) erased the first and nothing ever crossfaded).
                if (prev != null) {
                    for (f in 0 until FADE_FRAMES) {
                        coroutineContext.ensureActive()
                        // Cosine ease-in-out: slow start, fast middle, slow end — no perceptual pop.
                        val alpha = (0.5f - 0.5f * cos(PI * f.toDouble() / FADE_FRAMES)).toFloat()
                        postFrame { canvas ->
                            drawKenBurns(canvas, prev, 1f, PHOTO_FRAMES - 1)
                            drawKenBurns(canvas, bitmap, alpha, f)
                            drawBranding(canvas, logoBitmap, meta.overlayText)
                        }
                        drainEncoder(false)
                        framesRendered++
                        onProgress((framesRendered.toFloat() / totalFrames).coerceAtMost(1f))
                    }
                    prev.recycle()
                }

                // Main photo display. For photos after the first, the preceding cross-dissolve
                // already advanced Ken Burns from t=0 to t=FADE_FRAMES/PHOTO_FRAMES, so start the
                // main loop at FADE_FRAMES to continue seamlessly instead of snapping back to t=0
                // (the visible jerk at every transition). For the first photo (no dissolve) f still
                // begins at 0. Total frames per photo is unchanged (dissolve 15 + main 90 = 105).
                val mainStart = if (prev != null) FADE_FRAMES else 0
                for (f in mainStart until PHOTO_FRAMES + mainStart) {
                    coroutineContext.ensureActive()
                    postFrame { canvas ->
                        drawKenBurns(canvas, bitmap, 1f, f)
                        drawBranding(canvas, logoBitmap, meta.overlayText)
                    }
                    drainEncoder(false)
                    framesRendered++
                    onProgress((framesRendered.toFloat() / totalFrames).coerceAtMost(1f))
                }

                prevBitmap = bitmap
            }
            prevBitmap?.recycle()
            check(framesRendered > 0) {
                context.getString(R.string.reel_no_photos_error)
            }
            drainEncoder(true)
        } finally {
            logoBitmap?.recycle()
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
     * Decodes the photo at [uriString] subsampled to fit within 1440px on its longest edge —
     * one sample-size of headroom above the 1280px video height, so most phone photos decode at
     * sample=2 rather than sample=4 (sharper). Returns null if the URI is unreadable.
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
        while (rawLongest / sample > 1440) sample *= 2
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

    /**
     * Loads the launcher foreground drawable as a Bitmap for use as a watermark.
     * Returns null if the drawable cannot be found or drawn.
     */
    private fun loadLogoBitmap(sizePx: Int): Bitmap? = runCatching {
        val drawable = androidx.core.content.res.ResourcesCompat.getDrawable(
            context.resources, R.drawable.ic_launcher_foreground, context.theme
        ) ?: return@runCatching null
        val bm = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(c)
        bm
    }.getOrNull()

    /**
     * Composites the branding layer onto [canvas]:
     *   - Semi-transparent location/date pill (bottom of frame) if [overlayText] is non-null.
     *   - macaco logo watermark (bottom-centre, ~15% opacity) if [logoBitmap] is non-null.
     *
     * Call this AFTER drawKenBurns so branding always sits on top.
     */
    private fun drawBranding(canvas: Canvas, logoBitmap: Bitmap?, overlayText: String?) {
        // ── Location pill ──────────────────────────────────────────────────────
        if (overlayText != null) {
            // Use the pre-allocated class fields — never allocate Paint inside the render loop.
            canvas.drawRoundRect(
                android.graphics.RectF(32f, 1152f, 688f, 1224f),
                24f, 24f,
                pillPaint
            )
            // Vertically centre text within the pill (pill midpoint y = 1188; baseline ≈ 1196).
            canvas.drawText(overlayText, 360f, 1196f, textPaint)
        }

        // ── Logo watermark (bottom-centre) ─────────────────────────────────────
        if (logoBitmap != null) {
            val logoX = ((WIDTH - 48) / 2).toFloat()   // = 336f
            val logoY = (HEIGHT - 48 - 8).toFloat()    // = 1224f → bottom edge 1272f
            canvas.drawBitmap(logoBitmap, logoX, logoY, logoPaint)
        }
    }
}
