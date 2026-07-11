package com.houseofmmminq.macaco.data.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.data.model.YearRecap
import java.io.File

/**
 * Renders a [YearRecap] into a branded, shareable 1080×1920 PNG — same visual language as the
 * Adventure Reel outro card and the Print Book's branded outro page (dark-teal background, gold
 * wordmark, white-carded QR). Written to cacheDir and returned as a FileProvider content:// URI,
 * same mechanism already used for camera temp files and Adventure Reel output
 * (res/xml/file_paths.xml already exposes the whole cacheDir via the `reel_output` cache-path —
 * no manifest/xml change needed here).
 */
class YearRecapRenderer(private val context: Context) {

    private companion object {
        private const val W = 1080
        private const val H = 1920
        private val BG = Color.rgb(0x0A, 0x4A, 0x58)   // SplashTealMid — see PrintBookExporter's note
        private val GOLD = Color.rgb(0xF0, 0xC8, 0x40) // SplashGoldBright
    }

    fun render(recap: YearRecap): Uri? = runCatching {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG)

        val yearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD; textSize = 160f; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 44f; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val statValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 72f; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val statLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255); textSize = 28f; textAlign = Paint.Align.CENTER
        }
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD; textSize = 34f; textAlign = Paint.Align.CENTER
        }

        canvas.drawText(recap.year.toString(), W / 2f, 320f, yearPaint)
        canvas.drawText(context.getString(R.string.year_recap_title), W / 2f, 400f, titlePaint)

        // Four-stat grid: entries / trips / locations / media.
        val stats = listOf(
            recap.entryCount.toString() to context.getString(R.string.profile_memories),
            recap.tripCount.toString() to context.getString(R.string.profile_trips),
            recap.locationCount.toString() to context.getString(R.string.profile_locations),
            recap.mediaCount.toString() to context.getString(R.string.profile_media)
        )
        val gridTop = 560f
        val cellW = W / 2f
        val cellH = 220f
        stats.forEachIndexed { i, (value, label) ->
            val cx = cellW * (i % 2) + cellW / 2f
            val cy = gridTop + cellH * (i / 2)
            canvas.drawText(value, cx, cy, statValuePaint)
            canvas.drawText(label, cx, cy + 44f, statLabelPaint)
        }

        // Highlights: top mood / top tag / busiest month, whichever are non-null.
        var highlightY = gridTop + cellH * 2 + 80f
        listOfNotNull(
            recap.topMood?.let { context.getString(R.string.year_recap_top_mood, it) },
            recap.topTag?.let { context.getString(R.string.year_recap_top_tag, it) },
            recap.busiestMonth?.let { context.getString(R.string.year_recap_busiest_month, it) }
        ).forEach { line ->
            canvas.drawText(line, W / 2f, highlightY, highlightPaint)
            highlightY += 56f
        }

        // Branded bottom band — logo + QR, same assets as AdventureReelEncoder/PrintBookExporter.
        val logo = runCatching {
            val drawable = ResourcesCompat.getDrawable(
                context.resources, R.drawable.ic_launcher_foreground, context.theme
            ) ?: return@runCatching null
            val size = 130
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
                drawable.setBounds(0, 0, size, size); drawable.draw(Canvas(it))
            }
        }.getOrNull()
        logo?.let { canvas.drawBitmap(it, (W - it.width) / 2f, H - 560f, null) }
        canvas.drawText("macaco", W / 2f, H - 400f, titlePaint)

        val qr = runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val raw = BitmapFactory.decodeResource(context.resources, R.drawable.reel_qr_code, opts)
                ?: return@runCatching null
            Bitmap.createScaledBitmap(raw, 200, 200, true).also { if (it !== raw) raw.recycle() }
        }.getOrNull()
        qr?.let {
            val pad = 16f
            val size = it.width + pad * 2
            val left = (W - size) / 2f
            val top = H - 320f
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawRoundRect(RectF(left, top, left + size, top + size), 20f, 20f, cardPaint)
            canvas.drawBitmap(it, left + pad, top + pad, null)
        }

        val outFile = File(context.cacheDir, "year_recap_${recap.year}.png")
        outFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }.getOrNull()
}
