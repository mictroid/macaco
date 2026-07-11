# Macaco — Weather Stamp on Entries

New feature: entries automatically pick up the historical weather for their location and date,
shown as a small chip alongside the existing mood/location chips on `EntryDetailScreen`. Uses
Open-Meteo's free historical-weather API — no API key, so nothing secret has to live in the APK
(unlike the print/Peecho path discussed earlier, this needs no backend or key-holding proxy).
Five files touched, one new: `data/model/TravelEntry.kt`, `util/WeatherLookup.kt` (NEW),
`data/storage/CloudEntrySync.kt`, `ui/viewmodel/JournalViewModel.kt`,
`ui/screens/EntryDetailScreen.kt`.

---

## Change 1 — Two new optional fields on `TravelEntry`

**Problem:** there's nowhere to store fetched weather data on an entry.

**Fix:** store the raw WMO weather code and the day's max temperature in Celsius — raw, not a
pre-rendered string, so the display can still localize units (°C/°F) and icon/label text later
without re-fetching.

```kotlin
// data/model/TravelEntry.kt — BEFORE
    val mediaOrder: List<String> = emptyList(),
)

// AFTER
    val mediaOrder: List<String> = emptyList(),
    // ── Weather (added post-vc-print-book) ───────────────────────────────────
    // Fetched once, lazily, after an entry is first saved (see JournalViewModel.maybeFetchWeather).
    // null = not yet fetched, fetch failed, or the entry predates this feature. Never fetched for
    // future-dated entries (Open-Meteo's archive API is historical only).
    val weatherCode: Int? = null,
    val weatherTempMaxC: Double? = null,
)
```

Both fields default to `null` so every existing entry decodes unchanged.

**File:** `data/model/TravelEntry.kt`.

---

## Change 2 — `WeatherLookup` utility (NEW FILE)

Geocodes the entry's free-text `location` the same way `JournalViewModel.geocodeLocations`
already does (`Geocoder.getFromLocationName`, deprecated-but-functional, wrapped in
`@Suppress("DEPRECATION")`), then calls Open-Meteo's archive endpoint for that single date. Plain
`HttpURLConnection` + `org.json` — both built into Android, no new Gradle dependency, consistent
with the app's otherwise-minimal dependency set.

```kotlin
package com.houseofmmminq.macaco.util

import android.content.Context
import android.location.Geocoder
import com.houseofmmminq.macaco.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Historical weather for an entry's location + date, via Open-Meteo's free archive API
 * (https://open-meteo.com — no API key, no attribution required for non-commercial rate limits).
 * Deliberately narrow: one location, one date, no caching beyond what's persisted on the entry
 * itself (see TravelEntry.weatherCode / weatherTempMaxC).
 */
object WeatherLookup {

    private const val ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive"

    data class Result(val weatherCode: Int, val tempMaxC: Double)

    /** Returns null for a blank location, a future date (nothing to look up yet — Open-Meteo's
     *  archive API is historical only), a failed geocode, or any network/parse failure. Always
     *  call from a background dispatcher — this does blocking I/O. */
    suspend fun fetch(context: Context, location: String, dateMillis: Long): Result? =
        withContext(Dispatchers.IO) {
            if (location.isBlank() || dateMillis > System.currentTimeMillis()) return@withContext null
            val (lat, lon) = geocode(context, location) ?: return@withContext null
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dateMillis))
            val json = runCatching {
                val url = URL(
                    "$ARCHIVE_URL?latitude=$lat&longitude=$lon" +
                        "&start_date=$dateStr&end_date=$dateStr" +
                        "&daily=weathercode,temperature_2m_max&timezone=auto"
                )
                (url.openConnection() as HttpURLConnection).run {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    inputStream.bufferedReader().use { it.readText() }
                }
            }.getOrNull() ?: return@withContext null
            runCatching {
                val daily = JSONObject(json).getJSONObject("daily")
                Result(
                    weatherCode = daily.getJSONArray("weathercode").getInt(0),
                    tempMaxC = daily.getJSONArray("temperature_2m_max").getDouble(0)
                )
            }.getOrNull()
        }

    private fun geocode(context: Context, location: String): Pair<Double, Double>? = runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context).getFromLocationName(location, 1)?.firstOrNull()
            ?.let { it.latitude to it.longitude }
    }.getOrNull()

    /** Maps Open-Meteo's WMO weather code to an emoji + localized short label for the chip. */
    fun describe(context: Context, code: Int): Pair<String, String> = when (code) {
        0 -> "☀️" to context.getString(R.string.weather_clear)
        1, 2, 3 -> "⛅" to context.getString(R.string.weather_partly_cloudy)
        45, 48 -> "🌫️" to context.getString(R.string.weather_fog)
        51, 53, 55, 56, 57 -> "🌦️" to context.getString(R.string.weather_drizzle)
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️" to context.getString(R.string.weather_rain)
        71, 73, 75, 77, 85, 86 -> "❄️" to context.getString(R.string.weather_snow)
        95, 96, 99 -> "⛈️" to context.getString(R.string.weather_storm)
        else -> "🌡️" to context.getString(R.string.weather_unknown)
    }

    /** °C → the display string appropriate to the device locale (°F for a US locale, °C for
     *  everyone else) — matches how most weather apps localize without a settings toggle. */
    fun formatTemp(context: Context, tempC: Double): String {
        val useF = Locale.getDefault().country == "US"
        val value = if (useF) (tempC * 9 / 5 + 32) else tempC
        return context.getString(
            if (useF) R.string.weather_temp_f else R.string.weather_temp_c,
            value.toInt()
        )
    }
}
```

**File:** `util/WeatherLookup.kt` (new).

---

## Change 3 — Firestore read/write

```kotlin
// data/storage/CloudEntrySync.kt — inside the TravelEntry(...) construction in startListening(), ADD
                            weatherCode = doc.getLong("weatherCode")?.toInt(),
                            weatherTempMaxC = doc.getDouble("weatherTempMaxC"),
```

```kotlin
// data/storage/CloudEntrySync.kt — inside TravelEntry.toMap(), ADD
        "weatherCode" to weatherCode,
        "weatherTempMaxC" to weatherTempMaxC,
```

**File:** `data/storage/CloudEntrySync.kt`.

---

## Change 4 — Fetch weather in the background after save

**Problem:** nothing currently populates the new fields.

**Fix:** a narrow, independent background fetch inside `saveEntry` — same pattern as the existing
Drive-upload launch just above it (fire-and-forget, re-saves only the two new fields once the
lookup resolves), guarded so it only runs once per entry (skips if `weatherCode` is already set,
the location is blank, or the date is in the future).

```kotlin
// ui/viewmodel/JournalViewModel.kt — inside saveEntry(), immediately after the existing
// Drive-upload `if (entry.photoUris.isNotEmpty() || entry.videoUris.isNotEmpty()) { launch { ... } }`
// block (~line 407-426), ADD a sibling launch (still inside the outer viewModelScope.launch):

            // Weather: one lazy background fetch per entry, independent of the Drive upload
            // above — a failed/slow weather lookup must never block or race photo/video sync.
            if (entry.weatherCode == null && entry.location.isNotBlank() &&
                entry.dateMillis <= System.currentTimeMillis()
            ) {
                launch {
                    val result = WeatherLookup.fetch(appContext, entry.location, entry.dateMillis)
                        ?: return@launch
                    val latest = entries.value.find { it.id == entry.id } ?: entry
                    // Only patch in weather if nothing else already raced in a value (e.g. the
                    // user re-saved the entry again before this fetch returned).
                    if (latest.weatherCode == null) {
                        cloudEntrySync.save(
                            latest.copy(weatherCode = result.weatherCode, weatherTempMaxC = result.tempMaxC)
                        )
                    }
                }
            }
```

New import: `com.houseofmmminq.macaco.util.WeatherLookup`.

**File:** `ui/viewmodel/JournalViewModel.kt`.

---

## Change 5 — Weather chip on `EntryDetailScreen`

**Problem:** nowhere to see the fetched weather.

**Fix:** a third chip alongside the existing mood and location chips (`entry.mood.isNotBlank()` /
`entry.location.isNotBlank()` blocks, appearing twice in this file for the portrait/landscape
layouts — apply to both).

```
┌──────────────────────────────────┐
│  😊 Happy   📍 Lisbon   ☀️ 24°C   │   ← existing two chips + new weather chip
└──────────────────────────────────┘
```

```kotlin
// ui/screens/EntryDetailScreen.kt — BEFORE (appears twice, e.g. ~line 868 and ~578)
if (entry.location.isNotBlank()) {
    AssistChip(
        onClick = { AppActions.openMapsSearch(context, entry.location) },
        label = { Text(entry.location) },
        // ...
    )
}

// AFTER — add immediately after each of those two blocks
entry.weatherCode?.let { code ->
    val (icon, label) = WeatherLookup.describe(context, code)
    val tempLabel = entry.weatherTempMaxC?.let { WeatherLookup.formatTemp(context, it) }
    AssistChip(
        onClick = {},
        label = { Text(if (tempLabel != null) "$icon $tempLabel" else "$icon $label") },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    )
}
```

New import: `com.houseofmmminq.macaco.util.WeatherLookup`. Uses the same
`secondaryContainer`/`onSecondaryContainer` tokens as the mood chip (per the existing chip-style
convention), not new colors.

**File:** `ui/screens/EntryDetailScreen.kt`.

---

## Localization

| Key | EN value |
|-----|----------|
| `weather_clear` | Clear |
| `weather_partly_cloudy` | Partly cloudy |
| `weather_fog` | Foggy |
| `weather_drizzle` | Light rain |
| `weather_rain` | Rainy |
| `weather_snow` | Snowy |
| `weather_storm` | Stormy |
| `weather_unknown` | — |
| `weather_temp_c` | %1$d°C |
| `weather_temp_f` | %1$d°F |

All 11 languages need these 10 keys.

---

## Scope

- **In:** one historical weather lookup per entry (max temp + condition code), fetched lazily in
  the background after save, shown as a chip on `EntryDetailScreen`.
- **Out:** editing/overriding the fetched weather, showing weather for future-dated entries
  (Open-Meteo's archive API doesn't cover them — a forecast-API variant is a separate,
  narrower-window feature if wanted later), and backfilling weather for entries that existed
  before this ships (v1 only fetches going forward on save; a one-time backfill pass over
  existing entries is a reasonable fast-follow, not v1).
- **Out:** any new permission or account data — the geocode + weather call use only the entry's
  existing free-text `location` field, nothing new is collected from the user.

---

## Verification

1. Create a new entry with a real, past-dated location (e.g. "Paris" dated last month). Confirm
   no weather chip appears immediately, then appears within a few seconds once the background
   fetch resolves (may need to reopen the entry or rely on the existing `entries` StateFlow
   re-emitting after the patch-save).
2. Create an entry dated today with a nonsense location string (e.g. "Xyzzy123"). Confirm no
   crash and no chip — geocoding fails cleanly.
3. Create an entry dated in the future. Confirm no fetch is attempted at all (no chip, and no
   wasted network call — check via a breakpoint/log if needed).
4. Edit an entry that already has weather fetched (change its title only). Confirm the weather
   fields are preserved and it does not re-fetch (the `entry.weatherCode == null` guard).
5. Switch the device region to the US and confirm the chip shows °F; switch to any other region
   and confirm °C.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `weatherCode` / `weatherTempMaxC` fields | `TravelEntry.kt` |
| 2 | `WeatherLookup`: geocode + Open-Meteo archive call + icon/label/temp formatting | `WeatherLookup.kt` (new) |
| 3 | Firestore read/write of the two new fields | `CloudEntrySync.kt` |
| 4 | Background fetch-once-per-entry inside `saveEntry` | `JournalViewModel.kt` |
| 5 | Weather chip (portrait + landscape) | `EntryDetailScreen.kt` |
| — | 10 new string keys × 11 languages | `strings.xml` × 11 |
