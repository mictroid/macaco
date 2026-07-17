# Macaco — Weather chip: persist `weatherIsFahrenheit` through the Firestore mapper

Fixes the vc72 weather-unit-by-location feature being a silent no-op: the new
`TravelEntry.weatherIsFahrenheit` field is set correctly at fetch time but is **dropped on every
Firestore write and never read back**, so the UI always sees `null` and falls back to °C — even
for US-location entries. Touches only `CloudEntrySync.kt` (two one-line additions).

---

## Background (read first)

Entries persist through exactly one path: `CloudEntrySync`, which hand-writes both directions of
the Firestore mapping — `TravelEntry.toMap()` for writes and the field-by-field constructor call
in `startListening()`'s snapshot mapper for reads. The UI's `entries` StateFlow is fed
*exclusively* from those snapshots (writes surface back via the listener, including local-cache
pending writes), so any field missing from the mapper is invisible to the whole app even on the
device that set it.

The original brief (`docs/DONE/code-brief-weather-unit-by-location.md`) changed `WeatherLookup`,
`TravelEntry`, `JournalViewModel`, and `EntryDetailScreen` — but not `CloudEntrySync`, so the flag
never round-trips. The re-fetch guard in `JournalViewModel.saveEntry`
(`if (latest.weatherCode == null)`) means an entry whose weather already exists never fetches
again — affected entries don't self-heal, which is why the mapper fix must ship rather than
waiting for a re-fetch.

Repro (current, deterministic): create an entry located "New York" with a past date → weather chip
appears → it renders °C. Expected after this fix: °F.

## Change 1 — write the flag in `toMap()`

**Problem:** `toMap()` ends at `weatherTempMaxC`; `weatherIsFahrenheit` is never written to the
entry document.

**Fix:** add the field, matching the property name exactly (the doc key is the contract for
Change 2).

```kotlin
// BEFORE — CloudEntrySync.kt, end of toMap()
        "mediaOrder" to mediaOrder,
        "weatherCode" to weatherCode,
        "weatherTempMaxC" to weatherTempMaxC
    )
```

```kotlin
// AFTER
        "mediaOrder" to mediaOrder,
        "weatherCode" to weatherCode,
        "weatherTempMaxC" to weatherTempMaxC,
        "weatherIsFahrenheit" to weatherIsFahrenheit
    )
```

**File:** `app/src/main/java/com/houseofmmminq/macaco/data/storage/CloudEntrySync.kt`

## Change 2 — read the flag in the snapshot mapper

**Problem:** the `TravelEntry(...)` construction in `startListening()` never reads the field, so
even a document that *has* it (post-Change-1) would map back to `null`.

**Fix:** read it with `getBoolean` — nullable by design, `null` for pre-feature entries (the
`?: false` metric fallback in `EntryDetailScreen` already handles that).

```kotlin
// BEFORE — CloudEntrySync.kt, startListening() snapshot mapper, last two fields
                            weatherCode = doc.getLong("weatherCode")?.toInt(),
                            weatherTempMaxC = doc.getDouble("weatherTempMaxC")
                        )
```

```kotlin
// AFTER
                            weatherCode = doc.getLong("weatherCode")?.toInt(),
                            weatherTempMaxC = doc.getDouble("weatherTempMaxC"),
                            weatherIsFahrenheit = doc.getBoolean("weatherIsFahrenheit")
                        )
```

**File:** `app/src/main/java/com/houseofmmminq/macaco/data/storage/CloudEntrySync.kt`

**Scope notes:**

- The widget's own mapper (`OnThisDayWidgetProvider.fetchHighlight`) deliberately reads only the
  fields it renders (title/location/date/photo) — no change needed there.
- Entries that fetched weather **while vc72 was live** lost their flag permanently (the fetch
  guard won't re-run for them); they'll keep the °C fallback. Accepted — same posture as the
  original brief's no-backfill scope note for pre-feature entries, and the vc72 open-testing
  population is a day old. Do not add a backfill.

## Verification

`assembleDebug`, then on-device:

1. Create an entry located "New York" (or any US city) with a past date → wait for the weather
   chip → it reads **°F**.
2. Force-stop and reopen the app (snapshot re-read from cache/server) → still °F.
3. Create an entry located "Berlin" → chip reads **°C**.
4. An old entry with pre-existing weather still renders (°C fallback), no crash.

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Write `"weatherIsFahrenheit" to weatherIsFahrenheit` in `toMap()` | `data/storage/CloudEntrySync.kt` |
| 2 | Read `weatherIsFahrenheit = doc.getBoolean("weatherIsFahrenheit")` in the snapshot mapper | `data/storage/CloudEntrySync.kt` |
