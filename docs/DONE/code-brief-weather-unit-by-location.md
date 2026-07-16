# Macaco — Weather chip: temperature unit follows entry location, not device locale

Fixes the weather chip on `EntryDetailScreen.kt` always showing °F for users whose phone's
*display language* happens to be an English (US) variant, even when the entry (and the user)
is physically in a Celsius region. Touches `WeatherLookup.kt`, `TravelEntry.kt`, and
`JournalViewModel.kt`.

---

## Problem

`WeatherLookup.formatTemp()` currently decides °F vs °C from the device's language setting:

```kotlin
// WeatherLookup.kt (current)
fun formatTemp(context: Context, tempC: Double): String {
    val useF = Locale.getDefault().country == "US"
    val value = if (useF) (tempC * 9 / 5 + 32) else tempC
    return context.getString(
        if (useF) R.string.weather_temp_f else R.string.weather_temp_c,
        value.toInt()
    )
}
```

`Locale.getDefault().country` reflects the region tied to the phone's **selected display
language** (e.g. "English (United States)"), not the phone's physical location. A user living in
Berlin with their phone set to English (US) — a very common setup — gets °F on every entry,
regardless of where the trip happened. Confirmed via screenshot: a Berlin entry showing "76°F".

## Fix

`WeatherLookup` already geocodes the entry's `location` string to fetch weather (`geocode()` in
the same file) — it can return the country code from that same `Address` lookup at no extra
network cost. Decide the unit **once, at fetch time, from the entry's own location**, and persist
it on the entry (parallel to `weatherCode` / `weatherTempMaxC`) so the UI never needs to re-run
Geocoder synchronously inside Compose.

Imperial only for `"US"` — matches the existing rule, just re-scoped from device locale to
entry-location country.

### 1. `WeatherLookup.kt` — geocode also returns country, `Result` carries the unit

```kotlin
// BEFORE
data class Result(val weatherCode: Int, val tempMaxC: Double)

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
    // ... unchanged ...
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
```

```kotlin
// AFTER
data class Result(val weatherCode: Int, val tempMaxC: Double, val isFahrenheit: Boolean)

suspend fun fetch(context: Context, location: String, dateMillis: Long): Result? =
    withContext(Dispatchers.IO) {
        if (location.isBlank() || dateMillis > System.currentTimeMillis()) return@withContext null
        val geo = geocode(context, location) ?: return@withContext null
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dateMillis))
        val json = runCatching {
            val url = URL(
                "$ARCHIVE_URL?latitude=${geo.lat}&longitude=${geo.lon}" +
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
                tempMaxC = daily.getJSONArray("temperature_2m_max").getDouble(0),
                // Imperial only for the US — decided from where the *entry* is, not the phone's
                // display-language setting (that was the bug: a device set to English (US) but
                // physically in Berlin was always showing °F).
                isFahrenheit = geo.countryCode == "US"
            )
        }.getOrNull()
    }

private data class Geo(val lat: Double, val lon: Double, val countryCode: String?)

private fun geocode(context: Context, location: String): Geo? = runCatching {
    @Suppress("DEPRECATION")
    Geocoder(context).getFromLocationName(location, 1)?.firstOrNull()
        ?.let { Geo(it.latitude, it.longitude, it.countryCode) }
}.getOrNull()

/** Maps Open-Meteo's WMO weather code to an emoji + localized short label for the chip. */
fun describe(context: Context, code: Int): Pair<String, String> = when (code) {
    // ... unchanged ...
}

/** °C → the display string for the unit this entry was fetched with — [isFahrenheit] is decided
 *  once at fetch time from the entry's own location (see [fetch]), not the device's locale, so a
 *  Berlin entry reads in °C even on a phone whose display language is English (US). */
fun formatTemp(context: Context, tempC: Double, isFahrenheit: Boolean): String {
    val value = if (isFahrenheit) (tempC * 9 / 5 + 32) else tempC
    return context.getString(
        if (isFahrenheit) R.string.weather_temp_f else R.string.weather_temp_c,
        value.toInt()
    )
}
```

### 2. `TravelEntry.kt` — persist the unit alongside the other weather fields

```kotlin
// BEFORE
    // ── Weather (added post-vc-print-book) ───────────────────────────────────
    // Fetched once, lazily, after an entry is first saved (see JournalViewModel.saveEntry).
    // null = not yet fetched, fetch failed, or the entry predates this feature. Never fetched for
    // future-dated entries (Open-Meteo's archive API is historical only).
    val weatherCode: Int? = null,
    val weatherTempMaxC: Double? = null,
)
```

```kotlin
// AFTER
    // ── Weather (added post-vc-print-book) ───────────────────────────────────
    // Fetched once, lazily, after an entry is first saved (see JournalViewModel.saveEntry).
    // null = not yet fetched, fetch failed, or the entry predates this feature. Never fetched for
    // future-dated entries (Open-Meteo's archive API is historical only).
    val weatherCode: Int? = null,
    val weatherTempMaxC: Double? = null,
    // Decided once at fetch time from the entry's own location's country (WeatherLookup.fetch),
    // not the device locale. Null on entries fetched before this field existed or with no weather
    // — EntryDetailScreen falls back to metric (false) for those, since most locales are metric.
    val weatherIsFahrenheit: Boolean? = null,
)
```

### 3. `JournalViewModel.kt` — save the unit with the rest of the weather patch

```kotlin
// BEFORE
                    val latest = entries.value.find { it.id == entry.id } ?: entry
                    // Only patch in weather if nothing else already raced in a value (e.g. the
                    // user re-saved the entry again before this fetch returned).
                    if (latest.weatherCode == null) {
                        cloudEntrySync.save(
                            latest.copy(weatherCode = result.weatherCode, weatherTempMaxC = result.tempMaxC)
                        )
                    }
```

```kotlin
// AFTER
                    val latest = entries.value.find { it.id == entry.id } ?: entry
                    // Only patch in weather if nothing else already raced in a value (e.g. the
                    // user re-saved the entry again before this fetch returned).
                    if (latest.weatherCode == null) {
                        cloudEntrySync.save(
                            latest.copy(
                                weatherCode = result.weatherCode,
                                weatherTempMaxC = result.tempMaxC,
                                weatherIsFahrenheit = result.isFahrenheit
                            )
                        )
                    }
```

### 4. `EntryDetailScreen.kt` — pass the entry's own unit at both chip call sites (~line 609, ~line 911)

Both are the same one-line change, identical at each of the two call sites:

```kotlin
// BEFORE
val tempLabel = entry.weatherTempMaxC?.let { WeatherLookup.formatTemp(context, it) }
```

```kotlin
// AFTER
val tempLabel = entry.weatherTempMaxC?.let {
    WeatherLookup.formatTemp(context, it, entry.weatherIsFahrenheit ?: false)
}
```

**Scope note:** old entries that already have `weatherCode`/`weatherTempMaxC` from before this
brief will have `weatherIsFahrenheit == null` and will render in °C (the fallback) even if they
were originally a US location — they were never displaying correctly before either (device-locale
based), so this isn't a regression, and no backfill migration is in scope here.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `geocode()` returns country code too; `Result` gains `isFahrenheit`; `formatTemp()` takes the unit as a parameter instead of reading device locale | `WeatherLookup.kt` |
| 2 | New persisted field `weatherIsFahrenheit: Boolean?` | `TravelEntry.kt` |
| 3 | Save `weatherIsFahrenheit` alongside `weatherCode`/`weatherTempMaxC` | `JournalViewModel.kt` |
| 4 | Pass `entry.weatherIsFahrenheit ?: false` into `formatTemp()` at both chip render sites | `EntryDetailScreen.kt` |
