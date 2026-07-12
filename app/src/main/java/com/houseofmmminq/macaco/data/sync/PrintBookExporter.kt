package com.houseofmmminq.macaco.data.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.res.ResourcesCompat
import androidx.exifinterface.media.ExifInterface
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.data.model.TravelEntry
import com.houseofmmminq.macaco.util.ImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a user-selected set of entries into a print-ready A4 PDF: cover → first page → one
 * page per photo (captioned with the entry's title/location/date) → a branded Macaco outro page
 * with a QR code, mirroring AdventureReelEncoder's outro card. Entirely on-device via
 * PdfDocument — no server, no third-party print API (see docs/code-brief-print-book-export.md
 * Scope for why that's deliberate).
 */
class PrintBookExporter(private val context: Context) {

    data class BookConfig(
        val title: String,
        val coverPhotoUri: String?,
        val firstPagePhotoUri: String?,
        val firstPageCaption: String,
        // Already filtered/ordered by the caller (PrintExportScreen) — this class doesn't
        // know about trips, locations, or tags, it just lays out what it's given.
        val entries: List<TravelEntry>,
        // Drive-file-id → readable local (cached) URI, so entry photos whose on-device original
        // is gone still render from their Drive copy — same resolution the rest of the app uses.
        val cachedDrivePhotos: Map<String, String> = emptyMap(),
    )

    /** Prefer the Drive-cached copy (keyed by driveFileId) when present, else the raw local URI —
     *  mirrors PrintExportScreen.printDisplayUri / EntryDetailScreen so the book shows the same
     *  photo the user sees in-app, even after the local original is deleted. */
    private fun resolvePhotoUri(entry: TravelEntry, index: Int, cached: Map<String, String>): String? =
        entry.driveFileIds.getOrNull(index)?.takeIf { it.isNotEmpty() }?.let { cached[it] }
            ?: entry.photoUris.getOrNull(index)

    data class ExportResult(val pagesWritten: Int, val photosSkipped: Int)

    private companion object {
        private const val PAGE_WIDTH_PT = 595   // A4 210mm @ 72pt/in
        private const val PAGE_HEIGHT_PT = 842  // A4 297mm @ 72pt/in
        private const val MARGIN_PT = 36f       // 0.5"
        private const val TARGET_DPI = 300
        private const val CAPTION_BAR_HEIGHT_PT = 96f

        // Same brand tokens as AdventureReelEncoder's outro card (SplashTealMid / SplashGoldBright
        // in ui/screens/SplashScreen.kt) — re-declared as raw ARGB because this is a
        // android.graphics.Canvas render target, not Compose; keep in sync if the Splash palette
        // ever changes. Everything in PrintExportScreen.kt (the actual Compose UI) must still use
        // MaterialTheme.colorScheme tokens — this hardcoding is scoped to page rendering only.
        private val OUTRO_BG = Color.rgb(0x0A, 0x4A, 0x58)
        private val OUTRO_GOLD = Color.rgb(0xF0, 0xC8, 0x40)
    }

    private val placeholderPaint = Paint().apply { color = Color.rgb(0xE0, 0xE0, 0xE0) }
    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val coverScrimPaint = Paint().apply { color = Color.argb(160, 0, 0, 0) }
    private val coverTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 26f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val captionBarPaint = Paint().apply { color = Color.argb(200, 0, 0, 0) }
    private val captionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 16f; typeface = Typeface.DEFAULT_BOLD
    }
    private val captionSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255); textSize = 11f
    }
    private val firstPageCaptionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 14f; textAlign = Paint.Align.CENTER
    }
    private val outroTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = OUTRO_GOLD; textSize = 32f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val outroTaglinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(196, 0xF0, 0xC8, 0x40); textSize = 13f; textAlign = Paint.Align.CENTER
    }
    private val outroCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val outroCtaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 13f; textAlign = Paint.Align.CENTER
    }

    suspend fun export(dest: Uri, config: BookConfig): Result<ExportResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pdf = PdfDocument()
                var photosSkipped = 0
                var pageNumber = 1
                fun newPage() = pdf.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, pageNumber++).create()
                )

                // Cover
                newPage().also { page ->
                    val bmp = drawFullBleedPhoto(page.canvas, config.coverPhotoUri)
                    if (config.coverPhotoUri != null && bmp == null) photosSkipped++
                    drawCoverTitle(page.canvas, config.title)
                    pdf.finishPage(page)   // materializes the page — only now is it safe to recycle
                    bmp?.recycle()
                }

                // First page (intro/dedication — user-selected photo + optional caption)
                newPage().also { page ->
                    val bmp = drawFullBleedPhoto(page.canvas, config.firstPagePhotoUri)
                    if (config.firstPagePhotoUri != null && bmp == null) photosSkipped++
                    if (config.firstPageCaption.isNotBlank()) {
                        page.canvas.drawText(
                            config.firstPageCaption,
                            PAGE_WIDTH_PT / 2f, PAGE_HEIGHT_PT - 60f, firstPageCaptionPaint
                        )
                    }
                    pdf.finishPage(page)
                    bmp?.recycle()
                }

                // Content — one page per photo; entries with no photos still get one captioned
                // placeholder page so the entry isn't silently dropped from the book.
                config.entries.forEach { entry ->
                    val photos = if (entry.photoUris.isEmpty()) listOf(null)
                        else entry.photoUris.indices.map { resolvePhotoUri(entry, it, config.cachedDrivePhotos) }
                    photos.forEachIndexed { i, uri ->
                        newPage().also { page ->
                            val bmp = drawFullBleedPhoto(page.canvas, uri)
                            if (uri != null && bmp == null) photosSkipped++
                            if (i == 0) drawEntryCaption(page.canvas, entry)
                            pdf.finishPage(page)
                            bmp?.recycle()
                        }
                    }
                }

                // Branded outro
                newPage().also { page ->
                    drawBrandedOutro(page.canvas)
                    pdf.finishPage(page)
                }

                context.contentResolver.openOutputStream(dest)?.use { pdf.writeTo(it) }
                    ?: error("Couldn't open the destination file.")
                pdf.close()
                ExportResult(pagesWritten = pageNumber - 1, photosSkipped = photosSkipped)
            }
        }

    /**
     * Decodes [uriString] EXIF-corrected, subsampled so its pixel dimensions are never smaller
     * than [targetW]×[targetH] (a floor, not exact) — enough for ~300 DPI at full-page size
     * without decoding at full camera resolution. Returns null only if genuinely unreadable
     * (revoked grant, deleted media, Drive-only photo not cached locally).
     */
    private fun decodeForPrint(uriString: String, targetW: Int, targetH: Int): Bitmap? = runCatching {
        // The WHOLE body is guarded: openInputStream *throws* FileNotFoundException (not null) for a
        // stale/unknown media URI — e.g. a photo whose on-device original was deleted and now only
        // exists as a Drive-backed copy. Letting that propagate would abort the entire export and
        // leave a 0-byte PDF; instead we swallow it here so the caller draws a placeholder for just
        // that one photo and the rest of the book still renders.
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // decodeStream returns null by design when inJustDecodeBounds is set (it only fills
        // outWidth/outHeight), so guard on whether the STREAM opened, not on the decode's return
        // value — otherwise every photo bails here and the book renders all-placeholder. Matches
        // JournalBackup.compressToBytes's proven pattern.
        val boundsStream = resolver.openInputStream(uri) ?: return@runCatching null
        boundsStream.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        if (rawW <= 0 || rawH <= 0) return@runCatching null

        var sampleSize = 1
        while (rawW / (sampleSize * 2) >= targetW && rawH / (sampleSize * 2) >= targetH) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: return@runCatching null

        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { s ->
                ExifInterface(s).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        ImageStorage.applyExifOrientation(bitmap, orientation)
    }.getOrNull()

    /**
     * Draws [uriString] full-bleed across the whole page, center-cropped (same intent as
     * Compose's ContentScale.Crop). Draws a neutral placeholder fill and returns null if the
     * photo is null or unreadable. On success returns the decoded [Bitmap] so the caller can
     * recycle it *after* `finishPage` — the PdfDocument page canvas only holds a reference to the
     * bitmap until the page is finished, so recycling here (before finishPage) would crash the
     * whole export with "Canvas: trying to use a recycled bitmap".
     */
    private fun drawFullBleedPhoto(canvas: Canvas, uriString: String?): Bitmap? {
        val pageW = PAGE_WIDTH_PT.toFloat()
        val pageH = PAGE_HEIGHT_PT.toFloat()
        val targetW = (PAGE_WIDTH_PT / 72f * TARGET_DPI).toInt()
        val targetH = (PAGE_HEIGHT_PT / 72f * TARGET_DPI).toInt()
        val bitmap = uriString?.let { decodeForPrint(it, targetW, targetH) }
        if (bitmap == null) {
            canvas.drawRect(0f, 0f, pageW, pageH, placeholderPaint)
            return null
        }
        canvas.save()
        canvas.clipRect(0f, 0f, pageW, pageH)
        val scale = maxOf(pageW / bitmap.width, pageH / bitmap.height)
        val scaledW = bitmap.width * scale
        val scaledH = bitmap.height * scale
        val left = (pageW - scaledW) / 2f
        val top = (pageH - scaledH) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + scaledW, top + scaledH), photoPaint)
        canvas.restore()
        return bitmap
    }

    private fun drawCoverTitle(canvas: Canvas, title: String) {
        val pageW = PAGE_WIDTH_PT.toFloat()
        val pageH = PAGE_HEIGHT_PT.toFloat()
        canvas.drawRect(0f, pageH - 180f, pageW, pageH, coverScrimPaint)
        canvas.drawText(title, pageW / 2f, pageH - 90f, coverTitlePaint)
    }

    /** Bottom caption bar: entry title + "location · date" (date-only when location is blank).
     *  Description is intentionally omitted here — see Scope, long entries would overflow a
     *  photo caption bar; a text-only page for the full description is a fast-follow, not v1. */
    private fun drawEntryCaption(canvas: Canvas, entry: TravelEntry) {
        val pageW = PAGE_WIDTH_PT.toFloat()
        val pageH = PAGE_HEIGHT_PT.toFloat()
        canvas.drawRect(0f, pageH - CAPTION_BAR_HEIGHT_PT, pageW, pageH, captionBarPaint)
        val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(entry.dateMillis))
        val subtitle = if (entry.location.isNotBlank()) "${entry.location} · $dateStr" else dateStr
        canvas.drawText(entry.title, MARGIN_PT, pageH - CAPTION_BAR_HEIGHT_PT + 34f, captionTitlePaint)
        canvas.drawText(subtitle, MARGIN_PT, pageH - CAPTION_BAR_HEIGHT_PT + 58f, captionSubtitlePaint)
    }

    /**
     * Branded end page: dark-teal background, monkey mark + wordmark, gold tagline, white-carded
     * QR code — same visual language as AdventureReelEncoder.drawOutroCard, static (no fade,
     * this is a print page not a video frame). Reuses the *existing* tracked short-link QR asset
     * (res/drawable-nodpi/reel_qr_code.png → AppActions.REEL_SHARE_URL) rather than minting a new
     * one — see Scope if a distinct print-attribution campaign link is wanted later.
     */
    private fun drawBrandedOutro(canvas: Canvas) {
        val pageW = PAGE_WIDTH_PT.toFloat()
        val pageH = PAGE_HEIGHT_PT.toFloat()
        canvas.drawColor(OUTRO_BG)

        val logo = runCatching {
            val drawable = ResourcesCompat.getDrawable(
                context.resources, R.drawable.ic_launcher_foreground, context.theme
            ) ?: return@runCatching null
            val size = 120
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bm ->
                drawable.setBounds(0, 0, size, size)
                drawable.draw(Canvas(bm))
            }
        }.getOrNull()
        logo?.let { canvas.drawBitmap(it, (pageW - it.width) / 2f, pageH * 0.22f, photoPaint) }

        canvas.drawText("macaco", pageW / 2f, pageH * 0.40f, outroTitlePaint)
        canvas.drawText(
            context.getString(R.string.reel_outro_tagline), pageW / 2f, pageH * 0.425f, outroTaglinePaint
        )

        val qr = runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val raw = BitmapFactory.decodeResource(context.resources, R.drawable.reel_qr_code, opts)
                ?: return@runCatching null
            val target = 150
            Bitmap.createScaledBitmap(raw, target, target, true).also { if (it !== raw) raw.recycle() }
        }.getOrNull()
        qr?.let {
            val cardPad = 14f
            val cardSize = it.width + cardPad * 2
            val cardLeft = (pageW - cardSize) / 2f
            val cardTop = pageH * 0.50f
            canvas.drawRoundRect(
                RectF(cardLeft, cardTop, cardLeft + cardSize, cardTop + cardSize), 16f, 16f, outroCardPaint
            )
            canvas.drawBitmap(it, cardLeft + cardPad, cardTop + cardPad, null)
            canvas.drawText(
                context.getString(R.string.reel_outro_cta), pageW / 2f, cardTop + cardSize + 26f, outroCtaPaint
            )
        }
    }
}
