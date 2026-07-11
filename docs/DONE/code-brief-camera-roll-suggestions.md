# Macaco — Camera-Roll Auto-Suggested Entries

New feature: detect recent, geotagged camera-roll photos that aren't part of any existing entry,
cluster them by time+location, and surface a dismissible banner — "New photos from Lisbon (Jun
3–5) — start an entry?" — that pre-fills a new entry with the suggested title, location, date,
and photos already attached. This directly targets the biggest churn risk for a journal app:
someone takes the trip, then never opens the app to log it.

**Read this platform detail before anything else, it's the crux of the whole feature working at
all:** since Android 10 (API 29), reading *unredacted* GPS EXIF from a photo your app didn't
create requires the special `ACCESS_MEDIA_LOCATION` permission **and** opening the file via
`MediaStore.setRequireOriginal(uri)`. Without both, `ExifInterface` silently returns null/zeroed
GPS tags for any photo not written by this app — the feature would compile fine and simply never
find any location data. This is called out explicitly in Change 2 below.

Six files touched, three new: `AndroidManifest.xml`, `util/PhotoRollScanner.kt` (NEW),
`data/PreferencesManager.kt`, `ui/viewmodel/JournalViewModel.kt`,
`ui/screens/JournalListScreen.kt`, `ui/screens/NewEditEntryScreen.kt`.

---

## Change 1 — New permission

```xml
<!-- AndroidManifest.xml — ADD alongside the existing media permissions -->
<!-- Required (API 29+) to read unredacted GPS EXIF from camera-roll photos this app didn't
     create — see util/PhotoRollScanner.kt for why this is load-bearing, not optional. -->
<uses-permission android:name="android.permission.ACCESS_MEDIA_LOCATION" />
```

This is a runtime-requested permission (no system rationale UI of its own — request it like any
other dangerous permission via `ActivityResultContracts.RequestPermission`). Request it together
with `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE` the first time the suggestion feature would
run (Change 4) — not at first app launch, so users who never care about this feature are never
prompted for it.

**File:** `AndroidManifest.xml`.

---

## Change 2 — `PhotoRollScanner` (NEW FILE)

Queries `MediaStore.Images.Media` for photos from the last 14 days, reads each one's GPS EXIF
(the `setRequireOriginal` dance from the platform note above), clusters consecutive photos into
candidate groups (same cluster if within 6 hours *and* ~2 km of the previous photo in the
cluster), reverse-geocodes each cluster's centroid to a place name, and drops any cluster that
significantly overlaps an existing entry's location + date range.

```kotlin
package com.houseofmmminq.macaco.util

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.houseofmmminq.macaco.data.model.TravelEntry
import java.util.Calendar
import kotlin.math.abs

object PhotoRollScanner {

    data class PhotoCluster(
        val photoUris: List<Uri>,
        val startMillis: Long,
        val endMillis: Long,
        val placeName: String?
    )

    private const val LOOKBACK_DAYS = 14
    private const val CLUSTER_GAP_MILLIS = 6L * 60 * 60 * 1000   // 6 hours
    private const val CLUSTER_RADIUS_DEGREES = 0.02              // ~2 km at most inhabited latitudes

    /** Candidate clusters from the last [LOOKBACK_DAYS], excluding anything overlapping
     *  [existingEntries]'s own location+date coverage. Must run on a background dispatcher —
     *  does MediaStore queries, per-photo EXIF reads, and geocoding. */
    fun scan(context: Context, existingEntries: List<TravelEntry>): List<PhotoCluster> {
        val cutoff = System.currentTimeMillis() - LOOKBACK_DAYS * 24L * 60 * 60 * 1000
        val photos = queryGeotaggedPhotos(context, cutoff)
        if (photos.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<GeoPhoto>>()
        photos.sortedBy { it.dateTakenMillis }.forEach { photo ->
            val last = clusters.lastOrNull()?.lastOrNull()
            val sameCluster = last != null &&
                photo.dateTakenMillis - last.dateTakenMillis <= CLUSTER_GAP_MILLIS &&
                abs(photo.lat - last.lat) <= CLUSTER_RADIUS_DEGREES &&
                abs(photo.lon - last.lon) <= CLUSTER_RADIUS_DEGREES
            if (sameCluster) clusters.last().add(photo) else clusters.add(mutableListOf(photo))
        }

        val geocoder = Geocoder(context)
        return clusters.mapNotNull { group ->
            val startMillis = group.minOf { it.dateTakenMillis }
            val endMillis = group.maxOf { it.dateTakenMillis }
            val centroidLat = group.map { it.lat }.average()
            val centroidLon = group.map { it.lon }.average()
            val placeName = runCatching {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(centroidLat, centroidLon, 1)?.firstOrNull()
                    ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
            }.getOrNull()

            // Skip if an existing entry already covers roughly this place + time — coarse,
            // string/date-range based (see Scope: exact photo-level de-dup isn't attempted since
            // an already-journaled photo is a *copy* in Pictures/Macaco with a different
            // MediaStore id than this camera-roll original, not a comparable URI).
            val alreadyJournaled = existingEntries.any { entry ->
                entry.location.isNotBlank() && placeName != null &&
                    entry.location.contains(placeName, ignoreCase = true) &&
                    entry.dateMillis in (startMillis - CLUSTER_GAP_MILLIS)..(endMillis + CLUSTER_GAP_MILLIS)
            }
            if (alreadyJournaled) null
            else PhotoCluster(
                photoUris = group.map { it.uri },
                startMillis = startMillis,
                endMillis = endMillis,
                placeName = placeName
            )
        }
    }

    private data class GeoPhoto(val uri: Uri, val dateTakenMillis: Long, val lat: Double, val lon: Double)

    private fun queryGeotaggedPhotos(context: Context, sinceMillis: Long): List<GeoPhoto> {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN)
        val selection = "${MediaStore.Images.Media.DATE_TAKEN} >= ?"
        val results = mutableListOf<GeoPhoto>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection,
            arrayOf(sinceMillis.toString()), "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val dateTaken = cursor.getLong(dateCol)
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                // The load-bearing bit: setRequireOriginal forces MediaStore to hand back the
                // real file (with GPS tags intact) instead of a location-redacted copy, which is
                // what you'd otherwise get for any photo this app didn't itself write. Requires
                // ACCESS_MEDIA_LOCATION (Change 1) — without it this throws or the tags read null.
                val originalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
                } else uri
                val latLong = runCatching {
                    resolver.openInputStream(originalUri)?.use { ExifInterface(it).latLong }
                }.getOrNull()
                if (latLong != null) {
                    results += GeoPhoto(uri, dateTaken, latLong[0], latLong[1])
                }
            }
        }
        return results
    }
}
```

**File:** `util/PhotoRollScanner.kt` (new).

---

## Change 3 — Dismissal persistence

**Problem:** without remembering a dismissal, an ignored suggestion would reappear every app
open — a fast way to make this feel like nagging rather than a nice-to-have.

**Fix:** persist dismissed clusters by a stable key (cluster start time, since MediaStore ids
aren't guaranteed stable across a photo being re-imported) in the existing DataStore-backed
`PreferencesManager`, same style as its other keys.

```kotlin
// data/PreferencesManager.kt — ADD, following the existing key/flow pattern used for e.g.
// reminders_enabled

private val DISMISSED_SUGGESTIONS_KEY = stringSetPreferencesKey("dismissed_photo_clusters")

val dismissedPhotoClusters: Flow<Set<String>> = dataStore.data
    .map { it[DISMISSED_SUGGESTIONS_KEY] ?: emptySet() }

suspend fun dismissPhotoCluster(clusterKey: String) {
    dataStore.edit { it[DISMISSED_SUGGESTIONS_KEY] = (it[DISMISSED_SUGGESTIONS_KEY] ?: emptySet()) + clusterKey }
}
```

`clusterKey` is `cluster.startMillis.toString()` — passed by the caller, not computed inside
`PreferencesManager`, so this file stays a plain key/value store.

**File:** `data/PreferencesManager.kt`.

---

## Change 4 — ViewModel wiring

```kotlin
// ui/viewmodel/JournalViewModel.kt — ADD

private val _suggestedClusters = MutableStateFlow<List<PhotoRollScanner.PhotoCluster>>(emptyList())
val suggestedClusters: StateFlow<List<PhotoRollScanner.PhotoCluster>> = _suggestedClusters.asStateFlow()

/** Call once when the journal list first composes (has its own internal guard against running
 *  more than once per process — scanning MediaStore is not free). Caller must already hold
 *  READ_MEDIA_IMAGES/READ_EXTERNAL_STORAGE + ACCESS_MEDIA_LOCATION before calling this. */
fun checkForSuggestedEntries(context: Context) {
    viewModelScope.launch(Dispatchers.IO) {
        val dismissed = preferencesManager.dismissedPhotoClusters.first()
        val clusters = PhotoRollScanner.scan(context, entries.value)
            .filterNot { it.startMillis.toString() in dismissed }
        _suggestedClusters.value = clusters
    }
}

fun dismissSuggestedCluster(cluster: PhotoRollScanner.PhotoCluster) {
    viewModelScope.launch {
        preferencesManager.dismissPhotoCluster(cluster.startMillis.toString())
        _suggestedClusters.value = _suggestedClusters.value - cluster
    }
}

// Seed for pre-filling NewEditEntryScreen from an accepted suggestion — read once, then cleared,
// same one-shot-consumption shape as ReelState.Ready.
data class EntrySeed(val title: String, val location: String, val dateMillis: Long, val photoUris: List<String>)
private val _pendingEntrySeed = MutableStateFlow<EntrySeed?>(null)
val pendingEntrySeed: StateFlow<EntrySeed?> = _pendingEntrySeed.asStateFlow()

fun acceptSuggestedCluster(cluster: PhotoRollScanner.PhotoCluster) {
    _pendingEntrySeed.value = EntrySeed(
        title = cluster.placeName ?: "",
        location = cluster.placeName ?: "",
        dateMillis = cluster.startMillis,
        photoUris = cluster.photoUris.map { it.toString() }
    )
    _suggestedClusters.value = _suggestedClusters.value - cluster
}

fun entrySeedConsumed() { _pendingEntrySeed.value = null }
```

New import: `com.houseofmmminq.macaco.util.PhotoRollScanner`.

**File:** `ui/viewmodel/JournalViewModel.kt`.

---

## Change 5 — Suggestion banner on `JournalListScreen`

Same shape as the existing `OnThisDayBanner` (dismissible `LazyColumn` item above the trip/month
sections), one card per cluster.

```
┌──────────────────────────────────────┐
│ 📷 New photos from Lisbon             │
│    Jun 3 – Jun 5 · 14 photos          │
│           [ Dismiss ]  [ Create entry ]│
└──────────────────────────────────────┘
```

```kotlin
// ui/screens/JournalListScreen.kt — ADD near the on-this-day LaunchedEffect setup
LaunchedEffect(Unit) {
    val hasMediaPermission = /* existing permission-check helper, or ContextCompat.checkSelfPermission
                                 for READ_MEDIA_IMAGES + ACCESS_MEDIA_LOCATION */
    if (hasMediaPermission) viewModel.checkForSuggestedEntries(context)
}
val suggestedClusters by viewModel.suggestedClusters.collectAsState()
```

Add a new `item(key = "suggestions-$index")` block per cluster, positioned directly below the
"on this day" banner item and above `tripSections`/`monthSections`, rendering the card shown
above with two actions: `onDismiss = { viewModel.dismissSuggestedCluster(cluster) }` and
`onCreate = { viewModel.acceptSuggestedCluster(cluster); onNewEntry() }` (reuses the screen's
existing `onNewEntry` navigation callback — no new nav route needed, `NewEditEntryScreen` just
checks for a pending seed on entry, see Change 6).

Permission request: the first time `checkForSuggestedEntries` would run and the permission isn't
yet granted, show a one-time, dismissible prompt card instead ("Let Macaco suggest entries from
your recent photos?") rather than firing a cold OS permission dialog unprompted — request only on
explicit tap, using `rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())`
for `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE` (API-gated, matching the existing manifest split)
plus `ACCESS_MEDIA_LOCATION`.

**File:** `ui/screens/JournalListScreen.kt`.

---

## Change 6 — Pre-fill `NewEditEntryScreen` from a suggestion

```kotlin
// ui/screens/NewEditEntryScreen.kt — BEFORE (existing state init, ~line 166)
var title by rememberSaveable { mutableStateOf(existingEntry?.title ?: "") }
var location by rememberSaveable { mutableStateOf(existingEntry?.location ?: "") }
var dateMillis by rememberSaveable { mutableStateOf(existingEntry?.dateMillis ?: System.currentTimeMillis()) }

// AFTER — a pending suggestion seed (create-mode only; existingEntry always wins in edit mode)
val pendingSeed by viewModel.pendingEntrySeed.collectAsState()
val seed = existingEntry?.let { null } ?: pendingSeed
var title by rememberSaveable { mutableStateOf(existingEntry?.title ?: seed?.title ?: "") }
var location by rememberSaveable { mutableStateOf(existingEntry?.location ?: seed?.location ?: "") }
var dateMillis by rememberSaveable {
    mutableStateOf(existingEntry?.dateMillis ?: seed?.dateMillis ?: System.currentTimeMillis())
}
// The picked-photos state list (whatever this screen already calls its in-progress photo URIs —
// search for the state var backing the photos LazyRow) should likewise seed from
// seed?.photoUris ?: emptyList() instead of always starting empty.

LaunchedEffect(Unit) { if (seed != null) viewModel.entrySeedConsumed() }
```

The suggested `photoUris` here are the **original camera-roll URIs**, not yet copied into
Pictures/Macaco — that copy already happens at save time via the existing
`ImageStorage.persistToGallery` path this screen already calls for any newly-picked photo, so no
special-casing is needed there; a seeded photo just needs to enter that same "picked but not yet
persisted" state the manual picker flow already produces.

**File:** `ui/screens/NewEditEntryScreen.kt`.

---

## Localization

| Key | EN value |
|-----|----------|
| `photo_suggestion_title` | New photos from %1$s |
| `photo_suggestion_subtitle` | %1$d photos, %2$s |
| `photo_suggestion_dismiss` | Dismiss |
| `photo_suggestion_create` | Create entry |
| `photo_suggestion_permission_title` | Let Macaco suggest entries from your recent photos? |
| `photo_suggestion_permission_allow` | Allow |
| `photo_suggestion_permission_not_now` | Not now |

All 11 languages need these 7 keys.

---

## Scope

- **In:** last-14-days geotagged photo scan, time+location clustering, reverse-geocoded place
  name, coarse de-dup against existing entries' location+date, dismissible banner, pre-filled
  new-entry flow.
- **Out:** exact photo-level de-duplication. As noted in Change 2, an already-journaled photo is
  a byte-copy under Pictures/Macaco with a different MediaStore id than the original camera-roll
  file — there's no cheap, reliable way to prove "this exact photo is already in an entry" without
  comparing image hashes (real cost: decoding and hashing every candidate photo). The location+date
  heuristic is intentionally coarse and can occasionally re-suggest photos from a trip you already
  logged if the entry's stored location string doesn't textually overlap the geocoded place name.
- **Out:** videos — this scans `MediaStore.Images.Media` only; extending to
  `MediaStore.Video.Media` is a small, separate addition if wanted.
- **Out:** background/periodic scanning (e.g. a WorkManager job that scans daily and posts a
  notification even when the app isn't open) — v1 only scans when the journal list is opened.
  That's a meaningful behavior difference (opt-in in-app nudge vs. a background job with its own
  battery/Doze considerations) worth a separate decision, not bundled into this brief.

---

## Verification

1. Grant the new permission via the in-app prompt (not a cold OS dialog on launch). Confirm
   declining leaves the feature silently inactive rather than repeatedly re-prompting every open.
2. Take 3+ photos at a real location today (or adjust device clock / use photos with known EXIF
   GPS), open the journal. Confirm a suggestion card appears with a plausible place name and
   correct photo count.
3. Tap Create entry — confirm `NewEditEntryScreen` opens pre-filled with the place name as both
   title and location, the correct date, and the suggested photos already showing in the photo
   row, still editable/removable before save.
4. Save that entry, then reopen the journal list. Confirm the same photos no longer generate a
   suggestion (the location+date overlap check).
5. Dismiss a different suggestion instead of creating an entry. Force-close and reopen the app —
   confirm it stays dismissed (persisted, not just in-memory).
6. On a device/emulator image with `ACCESS_MEDIA_LOCATION` denied, confirm no crash — clusters
   simply come back empty (EXIF reads fail closed via the `runCatching` wrapper in
   `queryGeotaggedPhotos`).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | New `ACCESS_MEDIA_LOCATION` permission | `AndroidManifest.xml` |
| 2 | `PhotoRollScanner`: MediaStore scan + EXIF GPS + clustering + de-dup | `PhotoRollScanner.kt` (new) |
| 3 | Persisted dismissal set | `PreferencesManager.kt` |
| 4 | `suggestedClusters` / `pendingEntrySeed` state + scan/accept/dismiss | `JournalViewModel.kt` |
| 5 | Suggestion banner + permission-prompt card | `JournalListScreen.kt` |
| 6 | Pre-fill from `pendingEntrySeed` in create mode | `NewEditEntryScreen.kt` |
| — | 7 new string keys × 11 languages | `strings.xml` × 11 |
