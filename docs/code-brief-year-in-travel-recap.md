# Macaco — Year in Travel Recap (shareable annual stats card)

New feature: a "Year in Travel" screen, reached from Profile, that turns a year's worth of
entries into a Wrapped-style stat card (entries, trips, locations, media, top mood, top tag,
busiest month) — and a "Share" action that renders it as a branded PNG image with the same
QR/outro band as the Adventure Reel and the Print Book export, for growth via reshares. Entirely
client-side over data already in `entries` — no new collection, no backend. Five files touched,
two new: `data/model/TravelEntry.kt`, `data/sync/YearRecapRenderer.kt` (NEW),
`ui/screens/YearInTravelScreen.kt` (NEW), `ui/screens/ProfileScreen.kt`,
`ui/navigation/Screen.kt`, `ui/navigation/NavGraph.kt`.

---

## Change 1 — Year filtering + recap aggregation

**Problem:** `ProfileScreen`'s existing stats card (entries/trips/locations/media counts, ~line
474) is all-time only, computed inline in the composable. There's no per-year breakdown and no
"most common X" aggregation anywhere.

**Fix:** add shared extensions next to `tagsByFrequency()` / `tripNames()` / `locations()`: one
to filter entries to a calendar year, one to list which years have entries, and one to compute a
`YearRecap` from a given year's entries.

```kotlin
// data/model/TravelEntry.kt — ADD

/** This entry's calendar year (device default time zone, matching how dates are entered/shown
 *  everywhere else in the app). */
private fun TravelEntry.year(): Int =
    Calendar.getInstance().apply { timeInMillis = dateMillis }.get(Calendar.YEAR)

/** Entries whose date falls in [year]. */
fun List<TravelEntry>.inYear(year: Int): List<TravelEntry> = filter { it.year() == year }

/** Distinct years that have at least one entry, most recent first — populates the year picker. */
fun List<TravelEntry>.entryYears(): List<Int> = map { it.year() }.distinct().sortedDescending()

data class YearRecap(
    val year: Int,
    val entryCount: Int,
    val tripCount: Int,
    val locationCount: Int,
    val mediaCount: Int,
    val topMood: String?,
    val topTag: String?,
    val busiestMonth: String?
)

/** Aggregates [this] (already the whole journal — filters internally) into a [YearRecap] for
 *  [year]. Returns a recap with all-zero counts if the year has no entries, rather than null —
 *  the screen always has something to render, even if empty. */
fun List<TravelEntry>.toYearRecap(year: Int): YearRecap {
    val yearEntries = inYear(year)
    val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
    return YearRecap(
        year = year,
        entryCount = yearEntries.size,
        tripCount = yearEntries.mapNotNull { it.tripName?.trim()?.ifBlank { null } }.distinct().size,
        locationCount = yearEntries.mapNotNull { it.location.trim().ifBlank { null } }.distinct().size,
        mediaCount = yearEntries.sumOf { it.photoUris.size + it.videoUris.size },
        topMood = yearEntries.map { it.mood }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
        topTag = yearEntries.tagsByFrequency().firstOrNull(),
        busiestMonth = yearEntries
            .groupingBy { monthFormat.format(Date(it.dateMillis)) }
            .eachCount().maxByOrNull { it.value }?.key
    )
}
```

New imports needed in `TravelEntry.kt`: `java.text.SimpleDateFormat`.

`ProfileScreen`'s existing inline stats card is untouched — this doesn't replace it, it adds a
year-scoped view on top.

**File:** `data/model/TravelEntry.kt`.

---

## Change 2 — `YearRecapRenderer` (NEW FILE): branded shareable PNG

Same rendering approach as `AdventureReelEncoder`'s outro card and `PrintBookExporter`'s outro
page — plain `android.graphics.Canvas` onto a `Bitmap`, hardcoded ARGB brand tokens (this is a
raster render target, not Compose — see the existing precedent comment in
`PrintBookExporter.kt`), reusing the same `ic_launcher_foreground` logo and `reel_qr_code.png` /
`AppActions.REEL_SHARE_URL` tracked link. Output is a 1080×1920 portrait image — standard
share-sheet/story aspect ratio.

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
```

**File:** `data/sync/YearRecapRenderer.kt` (new).

---

## Change 3 — `YearInTravelScreen` (NEW FILE)

Year picker (only years with entries, from `entryYears()`) + the recap stats + a Share button.

```kotlin
package com.houseofmmminq.macaco.ui.screens

// … standard Compose/Material3 imports, following JournalListScreen.kt's conventions

@Composable
fun YearInTravelScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val context = LocalContext.current
    val years = remember(entries) { entries.entryYears() }
    var selectedYear by remember(years) { mutableStateOf(years.firstOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)) }
    val recap = remember(entries, selectedYear) { entries.toYearRecap(selectedYear) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.year_recap_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(24.dp)) {
            // Year selector: a row of FilterChips (years.size is typically small — a handful of
            // years of journaling — so a plain Row, not a dropdown, keeps every year one tap away.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                years.forEach { y ->
                    FilterChip(selected = y == selectedYear, onClick = { selectedYear = y }, label = { Text(y.toString()) })
                }
            }
            Spacer(Modifier.height(24.dp))
            // Stat grid + highlights mirroring ProfileScreen's existing stat card styling
            // (StatItem composable, secondaryContainer dividers) — reuse StatItem directly rather
            // than restyling; promote it from ProfileScreen.kt (drop `private`) if it's currently
            // file-scoped.
            // … render recap.entryCount / tripCount / locationCount / mediaCount via StatItem,
            // recap.topMood / topTag / busiestMonth as plain Text lines when non-null.
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val uri = YearRecapRenderer(context).render(recap) ?: return@Button
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.reel_share_caption, AppActions.REEL_SHARE_URL))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.year_recap_share_chooser)))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.year_recap_share))
            }
        }
    }
}
```

Rendering happens synchronously on the click — a single 1080×1920 bitmap draw is fast enough
(well under a frame budget's worth of perceptible delay) that this doesn't need a coroutine/
progress dialog the way the multi-second Adventure Reel encode does; if profiling later shows
otherwise, wrap the `render()` call in `withContext(Dispatchers.Default)`.

**File:** `ui/screens/YearInTravelScreen.kt` (new).

---

## Change 4 — Entry point from Profile

Add a new action tile to `ProfileScreen`'s existing 2-column action grid (the same grid that
already holds Settings/Help/Share/Rate/Subscription/Sign Out, ~line 556 onward) — follow the
existing `ProfileActionTile`-style composable used for those, with a new
`onYearInTravel: () -> Unit` parameter on `ProfileScreen`.

**File:** `ui/screens/ProfileScreen.kt`.

---

## Change 5 — Navigation wiring

```kotlin
// ui/navigation/Screen.kt — ADD
object YearInTravel : Screen("year_in_travel")
```

```kotlin
// ui/navigation/NavGraph.kt — ADD inside the NavHost
composable(Screen.YearInTravel.route) {
    YearInTravelScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
}
```

Wire `ProfileScreen`'s new `onYearInTravel` to `navController.navigate(Screen.YearInTravel.route)`.

**File:** `ui/navigation/Screen.kt`, `ui/navigation/NavGraph.kt`.

---

## Localization

`profile_memories` / `profile_trips` / `profile_locations` / `profile_media` and
`reel_share_caption` are reused as-is. New keys, all 11 languages:

| Key | EN value |
|-----|----------|
| `year_recap_action` | Year in Travel |
| `year_recap_title` | Year in Travel |
| `year_recap_top_mood` | Most common mood: %1$s |
| `year_recap_top_tag` | Most used tag: %1$s |
| `year_recap_busiest_month` | Busiest month: %1$s |
| `year_recap_share` | Share my year |
| `year_recap_share_chooser` | Share your year in travel |
| `year_recap_no_entries` | No entries yet for %1$d |

---

## Scope

- **In:** per-year stat recap (entries, trips, locations, media, top mood, top tag, busiest
  month), shareable as a single branded PNG reusing the Adventure Reel's QR/tracked-link asset.
- **Out:** a distinct tracked URL/QR for this feature's own attribution — same reasoning as the
  Print Book brief: minting a new QR PNG is a Cowork/Python task, not Code's; v1 reuses
  `reel_qr_code.png` / `REEL_SHARE_URL`.
- **Out:** distance traveled / countries visited as a distinct metric from "locations" — that
  would need real geocoding + country-level reverse lookup for every entry (cost/latency,
  and most entries store a free-text city/place name, not a coordinate) rather than the simple
  distinct-string count `locations` already does elsewhere in the app (Profile's existing stats
  card has the same limitation). A country-level rollup is a reasonable fast-follow once/if
  `geocodedLocations` is populated for the whole journal rather than just the entries visible on
  the map.
- **Out:** auto-surfacing this at year-end (e.g. a January notification) — v1 is pull-only,
  reached from Profile whenever the user wants it.

---

## Verification

1. Open Year in Travel with a journal spanning 2+ years. Confirm the year chips list exactly the
   years that have entries, most recent first, and switching years updates every stat.
2. Pick a year with zero trips (no entry has a `tripName`) and confirm the trip count area
   doesn't render as a distracting "0" the way `ProfileScreen`'s existing card *does* show 0 —
   decide and note in the PR which convention this follows; simplest is to match Profile's
   existing behavior (always show trips) for consistency across the two stat surfaces.
3. Pick a year with no mood/tag data at all — confirm the highlight lines are simply omitted
   (`listOfNotNull`), not rendered as "Most common mood: null".
4. Tap Share — confirm a PNG lands in a chooser target (e.g. Gmail/Drive) and opens correctly,
   with legible text at native resolution (check on both a small and a large-screen device, since
   this is a fixed 1080×1920 raster, not a scalable layout).
5. Confirm `ProfileScreen`'s existing all-time stats card is completely unchanged by this brief.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `inYear()` / `entryYears()` / `toYearRecap()` + `YearRecap` model | `TravelEntry.kt` |
| 2 | `YearRecapRenderer`: branded 1080×1920 PNG render | `YearRecapRenderer.kt` (new) |
| 3 | `YearInTravelScreen`: year picker + stats + share | `YearInTravelScreen.kt` (new) |
| 4 | New Profile action tile entry point | `ProfileScreen.kt` |
| 5 | New route + wiring | `Screen.kt`, `NavGraph.kt` |
| — | 8 new string keys × 11 languages (5 reused) | `strings.xml` × 11 |
