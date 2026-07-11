# Macaco — Print Book PDF Export (v1: self-print / share-out)

New feature: users assemble a selection of entries — by trip, by location, or a custom pick —
into an A4, print-ready PDF "book" with a custom cover, a custom first page, and a branded
Macaco outro page (mirrors the Adventure Reel outro card — read `docs/DONE/code-brief-adventure-reel-outro-card.md`
first for that pattern). The PDF is generated entirely on-device via `android.graphics.pdf.PdfDocument`
— no new Gradle dependency — then saved via SAF and optionally handed to the share sheet so the
user can print at home or upload it to any third-party print service themselves. This is
deliberately scoped to *not* include any print-fulfillment API integration (Peecho or otherwise);
see Scope.

Five files touched, one new: `data/model/TravelEntry.kt`, `data/sync/PrintBookExporter.kt` (NEW),
`ui/screens/PrintExportScreen.kt` (NEW), `ui/viewmodel/JournalViewModel.kt`,
`ui/navigation/Screen.kt`, `ui/navigation/NavGraph.kt`, `ui/screens/SettingsScreen.kt`.

---

## Phase 1 — Promote trip/location helpers to shared extensions

**Problem:** `NavGraph.kt` already has private `List<TravelEntry>.toLocationSuggestions()` and
`.toTripSuggestions()` extensions (used for the location/trip autocomplete on the entry editor).
The new selection screen needs the exact same two lists — trip and location "select by" groups
— so this logic should move to `TravelEntry.kt` alongside `tagsByFrequency()` rather than being
duplicated.

**Fix:** move both functions to `data/model/TravelEntry.kt`, drop `private`, rename to match the
existing naming style (`tagsByFrequency()` → `tripNames()` / `locations()`), and update
`NavGraph.kt`'s two call sites to use them.

```kotlin
// data/model/TravelEntry.kt — ADD below tagsByFrequency()

/** Distinct trip names from these entries, most-recent trip first (by latest entry in the trip). */
fun List<TravelEntry>.tripNames(): List<String> =
    mapNotNull { it.tripName?.trim()?.ifBlank { null } }
        .distinct()
        .sorted()

/** Distinct, non-blank locations from these entries, alphabetical. */
fun List<TravelEntry>.locations(): List<String> =
    mapNotNull { it.location.trim().ifBlank { null } }
        .distinct()
        .sorted()
```

```kotlin
// ui/navigation/NavGraph.kt — BEFORE
/** Distinct, non-blank locations from existing entries, for the location autocomplete. */
private fun List<TravelEntry>.toLocationSuggestions(): List<String> =
    mapNotNull { it.location.trim().ifBlank { null } }
        .distinct()
        .sorted()

/** Distinct trip names from existing entries, most-recently-used first. */
private fun List<TravelEntry>.toTripSuggestions(): List<String> =
    mapNotNull { it.tripName?.trim()?.ifBlank { null } }
        .distinct()
        .sorted()

// AFTER — delete both functions above entirely, add the import, and update call sites
import com.houseofmmminq.macaco.data.model.locations
import com.houseofmmminq.macaco.data.model.tripNames
```

Search this file for `.toLocationSuggestions()` and `.toTripSuggestions()` and replace both call
sites with `.locations()` and `.tripNames()`.

**Files:** `data/model/TravelEntry.kt`, `ui/navigation/NavGraph.kt`.

---

## Phase 2 — `PrintBookExporter`: PDF generation (NEW FILE)

A4 portrait at 595×842 pt (`PdfDocument.PageInfo` is always specified in points, 1/72"). Photos
are decoded and subsampled to roughly 300 DPI *at the size they're actually drawn* — for a
full-bleed page that's ~2481×3508 px — reusing the same bounds-first-decode +
`ImageStorage.applyExifOrientation` pattern `JournalBackup.compressToBytes` already uses, so
camera rotation is preserved and nothing decodes at full resolution just to be downscaled.

```kotlin
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
    )

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
                    if (!drawFullBleedPhoto(page.canvas, config.coverPhotoUri)) photosSkipped++
                    drawCoverTitle(page.canvas, config.title)
                    pdf.finishPage(page)
                }

                // First page (intro/dedication — user-selected photo + optional caption)
                newPage().also { page ->
                    if (!drawFullBleedPhoto(page.canvas, config.firstPagePhotoUri)) photosSkipped++
                    if (config.firstPageCaption.isNotBlank()) {
                        page.canvas.drawText(
                            config.firstPageCaption,
                            PAGE_WIDTH_PT / 2f, PAGE_HEIGHT_PT - 60f, firstPageCaptionPaint
                        )
                    }
                    pdf.finishPage(page)
                }

                // Content — one page per photo; entries with no photos still get one captioned
                // placeholder page so the entry isn't silently dropped from the book.
                config.entries.forEach { entry ->
                    val photos = entry.photoUris.ifEmpty { listOf(null) }
                    photos.forEachIndexed { i, uri ->
                        newPage().also { page ->
                            if (uri != null && !drawFullBleedPhoto(page.canvas, uri)) photosSkipped++
                            if (i == 0) drawEntryCaption(page.canvas, entry)
                            pdf.finishPage(page)
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
    private fun decodeForPrint(uriString: String, targetW: Int, targetH: Int): Bitmap? {
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        var sampleSize = 1
        while (rawW / (sampleSize * 2) >= targetW && rawH / (sampleSize * 2) >= targetH) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        }.getOrNull() ?: return null

        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { s ->
                ExifInterface(s).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        return ImageStorage.applyExifOrientation(bitmap, orientation)
    }

    /**
     * Draws [uriString] full-bleed across the whole page, center-cropped (same intent as
     * Compose's ContentScale.Crop). Draws a neutral placeholder fill and returns false if the
     * photo is null or unreadable — caller counts this toward `photosSkipped`.
     */
    private fun drawFullBleedPhoto(canvas: Canvas, uriString: String?): Boolean {
        val pageW = PAGE_WIDTH_PT.toFloat()
        val pageH = PAGE_HEIGHT_PT.toFloat()
        val targetW = (PAGE_WIDTH_PT / 72f * TARGET_DPI).toInt()
        val targetH = (PAGE_HEIGHT_PT / 72f * TARGET_DPI).toInt()
        val bitmap = uriString?.let { decodeForPrint(it, targetW, targetH) }
        if (bitmap == null) {
            canvas.drawRect(0f, 0f, pageW, pageH, placeholderPaint)
            return false
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
        bitmap.recycle()
        return true
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
```

**File:** `data/sync/PrintBookExporter.kt` (new).

---

## Phase 3 — Selection & customization screen (NEW FILE)

Three ways into the same underlying multi-select checklist, as requested: pick **By Trip** or
**By Location** to bulk-select that group's entries as a starting point (still individually
togglable after), or **Custom** to start from nothing and check entries one at a time.

```kotlin
package com.houseofmmminq.macaco.ui.screens

// … standard Compose/Material3 imports, following the pattern already used in JournalListScreen.kt

private enum class SelectionMode { TRIP, LOCATION, CUSTOM }

@Composable
fun PrintExportScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val context = LocalContext.current

    var mode by remember { mutableStateOf(SelectionMode.CUSTOM) }
    var scopeValue by remember { mutableStateOf<String?>(null) }   // chosen trip name / location
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var title by remember { mutableStateOf("") }
    var coverPhotoUri by remember { mutableStateOf<String?>(null) }
    var firstPagePhotoUri by remember { mutableStateOf<String?>(null) }
    var firstPageCaption by remember { mutableStateOf("") }

    val selectedEntries = remember(entries, selectedIds) {
        entries.filter { it.id in selectedIds }
            .sortedBy { it.dateMillis }
    }
    val availablePhotos = remember(selectedEntries) {
        selectedEntries.flatMap { it.photoUris }
    }

    // Bulk-select when a trip/location is chosen; still just sets the starting checklist state —
    // individual EntryRow checkboxes below can still add/remove entries afterward.
    fun applyScope(newMode: SelectionMode, value: String?) {
        mode = newMode
        scopeValue = value
        selectedIds = when (newMode) {
            SelectionMode.TRIP -> entries.filter { it.tripName == value }.map { it.id }.toSet()
            SelectionMode.LOCATION -> entries.filter { it.location == value }.map { it.id }.toSet()
            SelectionMode.CUSTOM -> selectedIds
        }
        if (title.isBlank()) title = value ?: context.getString(R.string.print_default_title)
    }

    // ── Layout ──────────────────────────────────────────────────────────────
    // 1. Mode chips: By Trip / By Location / Custom — same FilterChip styling as TagFilterRow.
    // 2. Mode == TRIP/LOCATION: a dropdown/LazyRow of entries.tripNames() / entries.locations()
    //    (Phase 1 extensions) — selecting one calls applyScope(mode, value).
    // 3. Checklist: LazyColumn of all entries (thumbnail + title + date + Checkbox bound to
    //    selectedIds), reusing EntryCard's thumbnail-loading logic from JournalListScreen.kt
    //    where practical rather than re-implementing photo loading.
    // 4. Once selectedEntries.isNotEmpty(): a "Cover & first page" section — a LazyRow of
    //    availablePhotos thumbnails; tapping one while "Cover" / "First page" toggle (two
    //    FilterChips) is active assigns coverPhotoUri / firstPagePhotoUri. Default coverPhotoUri
    //    to availablePhotos.firstOrNull() and firstPagePhotoUri to availablePhotos.getOrNull(1)
    //    ?: availablePhotos.firstOrNull() via LaunchedEffect(availablePhotos) the first time each
    //    is null, so the user always has a sane default without having to pick.
    // 5. OutlinedTextField for title, OutlinedTextField for firstPageCaption (optional).
    // 6. "Create PDF" button, enabled when selectedEntries.isNotEmpty() && title.isNotBlank().

    // Export state mirrors ReelState's shape: Idle / Generating / Ready / Error — see Phase 4.
    val exportState by viewModel.printExportState.collectAsState()

    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { dest ->
        if (dest != null) {
            viewModel.exportPrintBook(
                dest = dest,
                config = PrintBookExporter.BookConfig(
                    title = title,
                    coverPhotoUri = coverPhotoUri,
                    firstPagePhotoUri = firstPagePhotoUri,
                    firstPageCaption = firstPageCaption,
                    entries = selectedEntries
                )
            )
        }
    }

    // "Create PDF" button's onClick:
    // createDocLauncher.launch("${title.ifBlank { "macaco-book" }}.pdf")

    // On exportState becoming Ready(uri): show a snackbar/dialog with two actions —
    // "Share" (ACTION_SEND, type "application/pdf", EXTRA_STREAM = uri,
    // FLAG_GRANT_READ_URI_PERMISSION — same shape as the reel share intent in
    // JournalListScreen.kt, no FileProvider needed since SAF's CreateDocument Uri is already a
    // grantable content:// Uri) and "Done" (onBack).
}
```

This is a new, fairly large screen — follow `JournalListScreen.kt`'s existing composable-splitting
style (small private `@Composable` functions per section) rather than one long body. Reuse
`EntryCard`'s photo-loading approach (it already handles `cachedDrivePhotos` fallback for
Drive-only photos) for both the checklist thumbnails and the cover/first-page picker so
Drive-synced-but-not-locally-cached photos don't silently fail to show as pickable.

**File:** `ui/screens/PrintExportScreen.kt` (new).

---

## Phase 4 — ViewModel wiring

```kotlin
// ui/viewmodel/JournalViewModel.kt — ADD near the ReelState section

import com.houseofmmminq.macaco.data.sync.PrintBookExporter

// Stateless helper, same shape as journalBackup — built from appContext.
private val printBookExporter = PrintBookExporter(appContext)

sealed class PrintExportState {
    object Idle : PrintExportState()
    object Generating : PrintExportState()
    data class Ready(val uri: Uri, val photosSkipped: Int) : PrintExportState()
    data class Error(val message: String) : PrintExportState()
}

private val _printExportState = MutableStateFlow<PrintExportState>(PrintExportState.Idle)
val printExportState: StateFlow<PrintExportState> = _printExportState.asStateFlow()

fun exportPrintBook(dest: Uri, config: PrintBookExporter.BookConfig) {
    viewModelScope.launch(Dispatchers.IO) {
        _printExportState.value = PrintExportState.Generating
        val result = printBookExporter.export(dest, config)
        _printExportState.value = result.fold(
            onSuccess = { PrintExportState.Ready(dest, it.photosSkipped) },
            onFailure = { e ->
                PrintExportState.Error(e.message ?: appContext.getString(R.string.print_export_error_generic))
            }
        )
    }
}

fun printExportConsumed() { _printExportState.value = PrintExportState.Idle }
```

**File:** `ui/viewmodel/JournalViewModel.kt`.

---

## Phase 5 — Navigation + Settings entry point

```kotlin
// ui/navigation/Screen.kt — ADD
object PrintExport : Screen("print_export")
```

```kotlin
// ui/navigation/NavGraph.kt — ADD inside the NavHost, alongside the other composable(...) blocks
composable(Screen.PrintExport.route) {
    PrintExportScreen(
        viewModel = viewModel,
        onBack = { navController.popBackStack() }
    )
}
```

`SettingsScreen.kt` gets a new premium-gated section directly below the existing
**Backup & Restore** card (same file, same `premium = isPurchased == true` gate already used
there — see the `settings_backup_premium_required` pattern at line ~967). Add a new
`onPrintBook: () -> Unit` parameter to `SettingsScreen`'s signature, following the same pattern
as its other navigation callbacks, and wire it from `NavGraph.kt`'s
`composable(Screen.Settings.route)` block to `navController.navigate(Screen.PrintExport.route)`.

**Files:** `ui/navigation/Screen.kt`, `ui/navigation/NavGraph.kt`, `ui/screens/SettingsScreen.kt`.

---

## Localization

`reel_outro_tagline` and `reel_outro_cta` are reused as-is (identical text, already translated
in all 11 languages) — do not duplicate them. New keys needed, all 11 languages
(`values/` default plus `values-de/es/fr/it/ja/nl/pl/pt/sv/zh-rCN/`):

| Key | EN value |
|-----|----------|
| `print_book_title` | Print Book |
| `print_book_subtitle` | Turn your entries into a print-ready PDF |
| `print_premium_required` | A Premium feature — unlock it from Subscription |
| `print_default_title` | My Travels |
| `print_select_by_trip` | By trip |
| `print_select_by_location` | By location |
| `print_select_custom` | Custom |
| `print_cover_photo` | Cover photo |
| `print_first_page_photo` | First page |
| `print_first_page_caption_hint` | Add a short caption (optional) |
| `print_book_title_hint` | Book title |
| `print_create_pdf` | Create PDF |
| `print_export_generating` | Building your book… |
| `print_export_done` | Your book is ready (%1$d pages) |
| `print_export_done_warn` | Your book is ready, but %1$d photo(s) couldn't be included |
| `print_export_error_generic` | Couldn't create the PDF. Please try again. |
| `print_export_share` | Share |
| `print_no_entries_selected` | Select at least one entry to continue |

---

## Scope

- **In:** A4 portrait PDF, cover + first page (both user-photo-selectable, cover with a title
  overlay), one page per photo captioned with entry title/location/date, a branded Macaco outro
  page reusing the existing Adventure Reel QR asset and tracked link, selection by trip, by
  location, or custom multi-select, SAF save + share-sheet handoff. Premium-gated, matching the
  existing Backup & Restore feature.
- **Out — no print-fulfillment API integration.** This brief deliberately stops at "hand the user
  a PDF." Wiring a print-on-demand API (e.g. Peecho) — merchant key handling, a payment
  processor, shipping-address collection, order tracking — is a separate initiative with its own
  privacy-policy/ToS/backend implications and is not part of this brief.
- **Out:** entry description text is not included on photo pages (would overflow the caption
  bar) — a dedicated text-only "story page" per entry is a reasonable fast-follow, not v1.
- **Out:** A4 landscape / other trim sizes — v1 is portrait only; swapping
  `PAGE_WIDTH_PT`/`PAGE_HEIGHT_PT` is a small follow-up if a landscape "coffee table" layout is
  wanted later.
- **Out:** a dedicated print-campaign QR/tracked URL (separate UTM attribution from the Adventure
  Reel share funnel) — v1 reuses the existing `reel_qr_code.png` asset and `REEL_SHARE_URL` since
  minting a new tracked QR is a Cowork/Python task (regenerating a PNG), not something Code should
  do; flag this as a fast-follow if per-feature attribution turns out to matter.
- **Out:** a per-trip quick-launch shortcut (e.g. a "Print" button next to each `TripHeader`'s
  "Create Reel" button) — v1's only entry point is Settings, matching Backup & Restore's pattern.

---

## Verification

1. From Settings, open Print Book (only reachable when premium). Confirm the section shows the
   locked subtitle and no button when `isPurchased != true`.
2. Select **By Trip**, choose a trip with 2+ entries and mixed photo counts (including an entry
   with zero photos). Confirm the checklist pre-selects exactly that trip's entries and stays
   editable.
3. Assign a cover photo and first-page photo from two different entries' photos; leave the
   caption blank on one run, filled on another. Generate the PDF via `CreateDocument` and open it
   in a PDF viewer: page 1 is the cover with the title overlay, page 2 is the first page with (or
   without) the caption, content pages show one photo each with the correct entry
   title/location/date caption bar, zero-photo entries produce one placeholder-fill captioned
   page instead of being silently dropped, and the last page is the branded outro with a scannable
   QR resolving to the Play Store listing.
4. Confirm a Drive-only photo (synced but not locally cached) either downloads via
   `cachedDrivePhotos` before export or is cleanly counted in `photosSkipped` — never a crash or a
   blank/corrupt page.
5. Trigger the post-export "Share" action and confirm a PDF-capable target (Gmail, Drive, a PDF
   viewer) receives a valid, openable file.
6. Repeat with **By Location** and **Custom** selection modes.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `tripNames()` / `locations()` promoted to shared extensions | `TravelEntry.kt`, `NavGraph.kt` |
| 2 | `PrintBookExporter`: A4 PDF generation, cover/first/content/outro pages | `PrintBookExporter.kt` (new) |
| 3 | `PrintExportScreen`: trip/location/custom selection + cover/first-page picker | `PrintExportScreen.kt` (new) |
| 4 | `exportPrintBook()` + `PrintExportState` | `JournalViewModel.kt` |
| 5 | New route + Settings entry point (premium-gated) | `Screen.kt`, `NavGraph.kt`, `SettingsScreen.kt` |
| — | 18 new string keys × 11 languages (2 reused from Adventure Reel) | `strings.xml` × 11 |
