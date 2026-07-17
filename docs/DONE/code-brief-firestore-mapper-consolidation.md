# Macaco — Data layer: one shared Firestore mapper for `TravelEntry`

Consolidates the three hand-written copies of the Firestore ↔ `TravelEntry` mapping into a single
shared pair, so a new model field can only ever be forgotten in one place. Touches
`CloudEntrySync.kt`, `OnThisDayWidgetProvider.kt`, and adds one new file. No behavior change
intended — this is the structural fix behind the vc72 `weatherIsFahrenheit` field-drop bug
(`code-brief-weather-unit-firestore-persistence.md`), which existed because the write mapper, the
read mapper, and the widget's read mapper are maintained by hand in parallel.

**Prerequisite:** implement `code-brief-weather-unit-firestore-persistence.md` FIRST (or fold it
in here) — the AFTER code below already includes `weatherIsFahrenheit` in both directions.

**Background (read first):** entries persist exclusively through Firestore documents shaped by
these mappers. `CloudEntrySync.toMap()` writes, `startListening()`'s snapshot mapper reads, and
`OnThisDayWidgetProvider.fetchHighlight()` keeps a third, deliberately-partial read copy (its
comment says "kept in sync manually"). All three must agree on field names; today nothing enforces
that.

## Change 1 — new shared mapper file

**Problem:** no single source of truth for the document shape.

**Fix:** add `data/storage/TravelEntryFirestoreMapper.kt` with both directions. The read side is
copied verbatim from `CloudEntrySync.startListening()` (it is the most complete mapper), the write
side from `CloudEntrySync.toMap()` — plus the `weatherIsFahrenheit` field from the prerequisite
brief.

```kotlin
// NEW FILE — app/src/main/java/com/houseofmmminq/macaco/data/storage/TravelEntryFirestoreMapper.kt
package com.houseofmmminq.macaco.data.storage

import com.google.firebase.firestore.DocumentSnapshot
import com.houseofmmminq.macaco.data.model.TravelEntry

/**
 * The single source of truth for the Firestore document shape of a TravelEntry.
 * Every write (CloudEntrySync.save) and every read (CloudEntrySync snapshot listener,
 * OnThisDayWidgetProvider's out-of-process query) MUST go through this pair — a field
 * added to TravelEntry that should sync must be added in BOTH functions here, nowhere else.
 * (The vc72 weatherIsFahrenheit no-op happened because these mappings lived in three places.)
 */
fun TravelEntry.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "title" to title,
    "location" to location,
    "dateMillis" to dateMillis,
    "description" to description,
    "mood" to mood,
    "photoUris" to photoUris,
    "tags" to tags,
    "createdAt" to createdAt,
    "driveFileIds" to driveFileIds,
    "tripName" to tripName,
    "videoUris" to videoUris,
    "videoFileIds" to videoFileIds,
    "mediaOrder" to mediaOrder,
    "weatherCode" to weatherCode,
    "weatherTempMaxC" to weatherTempMaxC,
    "weatherIsFahrenheit" to weatherIsFahrenheit
)

/** Returns null for a document missing the required title field (mirrors the old mapNotNull). */
fun DocumentSnapshot.toTravelEntry(): TravelEntry? = runCatching {
    TravelEntry(
        id = getString("id") ?: id,
        title = getString("title") ?: return@runCatching null,
        location = getString("location") ?: "",
        dateMillis = getLong("dateMillis") ?: 0L,
        description = getString("description") ?: "",
        mood = getString("mood") ?: "",
        photoUris = (get("photoUris") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        tags = (get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        createdAt = getLong("createdAt") ?: 0L,
        driveFileIds = (get("driveFileIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        tripName = getString("tripName"),
        videoUris = (get("videoUris") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        videoFileIds = (get("videoFileIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        mediaOrder = (get("mediaOrder") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        weatherCode = getLong("weatherCode")?.toInt(),
        weatherTempMaxC = getDouble("weatherTempMaxC"),
        weatherIsFahrenheit = getBoolean("weatherIsFahrenheit")
    )
}.getOrNull()
```

**File:** `app/src/main/java/com/houseofmmminq/macaco/data/storage/TravelEntryFirestoreMapper.kt` (new)

## Change 2 — `CloudEntrySync` uses the shared pair

**Problem:** `startListening()` inlines the field-by-field construction; `save()` uses the private
`toMap()`.

**Fix:** replace both with calls to the shared functions; delete the private `toMap()` entirely.

```kotlin
// BEFORE — startListening(), snapshot mapping
                _entries.value = snapshot.documents.mapNotNull { doc ->
                    runCatching {
                        TravelEntry(
                            id = doc.getString("id") ?: doc.id,
                            // ... full field-by-field construction ...
                        )
                    }.getOrNull()
                }
```

```kotlin
// AFTER
                _entries.value = snapshot.documents.mapNotNull { it.toTravelEntry() }
```

```kotlin
// BEFORE — save()
            .set(entry.toMap())
```

```kotlin
// AFTER
            .set(entry.toFirestoreMap())
```

Then delete `private fun TravelEntry.toMap(): Map<String, Any?> = mapOf(...)` at the bottom of the
file.

**File:** `app/src/main/java/com/houseofmmminq/macaco/data/storage/CloudEntrySync.kt`

## Change 3 — widget uses the shared read mapper

**Problem:** `OnThisDayWidgetProvider.fetchHighlight()` keeps a third, partial copy (it currently
reads only title/location/date/photo fields — enough for rendering, but it's still a manually-
synced duplicate).

**Fix:** replace the inline construction with `doc.toTravelEntry()`. Mapping the full entry is
slightly more work per document but removes the duplication; the widget query is a one-shot
`get()`, not a listener, so the cost is negligible.

```kotlin
// BEFORE — fetchHighlight(), after the .get().await()
        // Same decode shape as CloudEntrySync.startListening — kept in sync manually since a
        // widget-process query can't share that private mapper (see the brief's Scope note).
        val entries = snapshot.documents.mapNotNull { doc ->
            runCatching {
                TravelEntry(
                    id = doc.getString("id") ?: doc.id,
                    title = doc.getString("title") ?: return@runCatching null,
                    location = doc.getString("location") ?: "",
                    dateMillis = doc.getLong("dateMillis") ?: 0L,
                    description = doc.getString("description") ?: "",
                    mood = doc.getString("mood") ?: "",
                    photoUris = (doc.get("photoUris") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    tags = (doc.get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    createdAt = doc.getLong("createdAt") ?: 0L
                )
            }.getOrNull()
        }
```

```kotlin
// AFTER
        // Shared mapper — see TravelEntryFirestoreMapper.kt (single source of truth for the
        // document shape; the old manually-synced partial copy is gone).
        val entries = snapshot.documents.mapNotNull { it.toTravelEntry() }
```

Add the import: `import com.houseofmmminq.macaco.data.storage.toTravelEntry`.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/widget/OnThisDayWidgetProvider.kt`

## Verification

`assembleDebug`, then on-device: journal list loads, save an edit (Firestore write round-trips),
weather chip unit still correct (proves `weatherIsFahrenheit` survived the refactor), widget still
renders an entry. Grep check: `grep -rn "getString(\"title\")" app/src/main/java` should match
only the shared mapper file.

## Summary

| # | Change | File |
|---|--------|------|
| 1 | New shared `toFirestoreMap()` / `toTravelEntry()` pair | `data/storage/TravelEntryFirestoreMapper.kt` (new) |
| 2 | Use shared pair; delete private `toMap()` + inline read mapping | `data/storage/CloudEntrySync.kt` |
| 3 | Replace partial widget mapper with `toTravelEntry()` | `ui/widget/OnThisDayWidgetProvider.kt` |
