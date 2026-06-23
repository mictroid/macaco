# Macaco — MapScreen / JournalViewModel: Fix Camera Zooming to Single Location

The map camera positions itself on the first geocoding result rather than fitting all locations,
because the camera effect fires as soon as `geocodedLocations` becomes non-empty. Touches
`JournalViewModel.kt` and `MapScreen.kt`.

Context: the v1 brief (`docs/DONE/code-brief-map-default-position.md`) added the loading scrim
to hide the default ocean position. This v2 fixes a regression introduced by that fix: the camera
now fires too early — on the first geocoded location — and locks out all subsequent results.

---

## Root cause

`geocodeLocations()` in the ViewModel runs geocoding sequentially and only adds a location to
`_geocodedLocations` on *success*. Failed lookups are silently skipped — no entry is ever written
for them. This means `geocodedLocations.size` grows from 0 to N one entry at a time, stopping
below `locations.size` if any lookups fail.

The camera `LaunchedEffect` in `MapScreen` keys on `geocodedLocations` and fires on every
new entry:

```kotlin
// CURRENT — fires as soon as any result arrives
LaunchedEffect(mapLoaded, geocodedLocations) {
    if (mapLoaded && !cameraPositioned && geocodedLocations.isNotEmpty()) {
        // ... positions camera, sets cameraPositioned = true
    }
}
```

When the very first location geocodes, `geocodedLocations.size == 1` → the `latlngs.size == 1`
branch runs → camera zooms to that single point at level 5 → `cameraPositioned = true`. All
subsequent results are ignored. The user sees the map locked onto whichever location happened to
geocode first.

---

## Fix 1: add `geocodingComplete` flag to ViewModel

Add a `StateFlow<Boolean>` that is `false` while geocoding is in progress and `true` once the
loop has finished (whether all succeeded, some failed, or the list was empty).

**File:** `ui/viewmodel/JournalViewModel.kt`

```kotlin
// Add alongside _geocodedLocations (around line 153):
private val _geocodingComplete = MutableStateFlow(false)
val geocodingComplete: StateFlow<Boolean> = _geocodingComplete.asStateFlow()

// Replace the existing geocodeLocations() function:
fun geocodeLocations(context: Context, locations: List<String>) {
    if (locations.isEmpty()) {
        _geocodingComplete.value = true
        return
    }
    _geocodingComplete.value = false
    viewModelScope.launch(Dispatchers.IO) {
        val geocoder = Geocoder(context)
        locations.forEach { loc ->
            if (loc !in _geocodedLocations.value) {
                try {
                    @Suppress("DEPRECATION")
                    val results = geocoder.getFromLocationName(loc, 1)
                    results?.firstOrNull()?.let { addr ->
                        _geocodedLocations.update { it + (loc to Pair(addr.latitude, addr.longitude)) }
                    }
                } catch (_: Exception) { }
            }
        }
        _geocodingComplete.value = true
    }
}
```

---

## Fix 2: gate camera animation on `geocodingComplete`, not first result

Change the camera `LaunchedEffect` to key on `geocodingComplete` instead of the live
`geocodedLocations` map. The effect then fires exactly once — when all geocoding attempts are
done — rather than once per result.

**File:** `ui/screens/MapScreen.kt`

```kotlin
// Add alongside the other collectAsState calls (near line 89):
val geocodingComplete by viewModel.geocodingComplete.collectAsState()

// Replace the existing camera LaunchedEffect:
//   was: LaunchedEffect(mapLoaded, geocodedLocations) {
//   now: LaunchedEffect(mapLoaded, geocodingComplete) {
LaunchedEffect(mapLoaded, geocodingComplete) {
    if (mapLoaded && !cameraPositioned && geocodingComplete && geocodedLocations.isNotEmpty()) {
        // Exclude Null Island (0.0, 0.0) — geocoding failures land there and drag the
        // bounds south to equatorial Africa.
        val nullFiltered = geocodedLocations.values
            .map { LatLng(it.first, it.second) }
            .filter { !(it.latitude == 0.0 && it.longitude == 0.0) }
        if (nullFiltered.isEmpty()) return@LaunchedEffect

        // Outlier rejection: drop any point >30° from the geographic median, catching a
        // location name that geocodes to the wrong continent. Fall back to the null-filtered
        // set if the places genuinely span >30°.
        val medianLat = nullFiltered.map { it.latitude }.sorted()[nullFiltered.size / 2]
        val medianLng = nullFiltered.map { it.longitude }.sorted()[nullFiltered.size / 2]
        val latlngs = nullFiltered.filter { pt ->
            Math.abs(pt.latitude - medianLat) <= 30.0 && Math.abs(pt.longitude - medianLng) <= 30.0
        }.ifEmpty { nullFiltered }

        val update = if (latlngs.size == 1) {
            CameraUpdateFactory.newLatLngZoom(latlngs[0], 5f)
        } else {
            val bounds = LatLngBounds.builder().apply { latlngs.forEach { include(it) } }.build()
            CameraUpdateFactory.newLatLngBounds(bounds, 80)
        }

        // newLatLngBounds throws if the map hasn't completed a layout pass (happens when
        // returning to this screen). Give it a frame, then retry once before giving up — and
        // always clear the scrim so a failed move can't strand the user on the default position.
        delay(100)
        try {
            cameraPositionState.move(update)
        } catch (_: Exception) {
            delay(400)
            runCatching { cameraPositionState.move(update) }
        }
        cameraPositioned = true
    }
}
```

The rest of `MapScreen.kt` is unchanged — the scrim condition, timeout fallback, and markers are
all unaffected.

---

## Scope notes

- **Cached results (returning to map screen):** `geocodingComplete` persists in the ViewModel
  across recompositions. If the user reopens the map with the same entries, `geocodingComplete`
  is already `true` and `geocodedLocations` is already populated, so the camera positions
  immediately once `mapLoaded` fires. Correct behaviour.
- **New entries added while map is open:** `LaunchedEffect(locations)` re-calls
  `geocodeLocations`, which resets `geocodingComplete` to `false` then back to `true`. Since
  `cameraPositioned` is still `true` at that point, the camera does not jump. Correct behaviour.
- **All locations fail to geocode:** `geocodingComplete` becomes `true`, but
  `geocodedLocations.isNotEmpty()` is false → the effect returns early. The 8-second
  `revealTimedOut` scrim fallback already handles this case.
- **Single location in journal:** the `latlngs.size == 1` path still zooms to level 5 on that
  one point. This is intentional — there is nothing else to fit.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `geocodingComplete: StateFlow<Boolean>`, reset to `false` at start of `geocodeLocations`, set to `true` when loop finishes | `ui/viewmodel/JournalViewModel.kt` |
| 2 | Collect `geocodingComplete`; change camera `LaunchedEffect` key from `geocodedLocations` to `geocodingComplete`; add `geocodingComplete` guard | `ui/screens/MapScreen.kt` |
