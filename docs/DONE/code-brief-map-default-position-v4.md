# Macaco — MapScreen: Fix Camera Not Fitting All Locations (newLatLngBounds Premature Throw)

`CameraUpdateFactory.newLatLngBounds(bounds, 80)` is called before the map has completed a layout
pass, causing it to throw silently. `cameraPositioned` is never set true, the scrim drops after the
8-second timeout, and the map shows its default position instead of fitting all the user's places.
Touches `ui/screens/MapScreen.kt` only.

---

## Fix 1: compute newLatLngBounds inside the try block, after the layout delay

**Problem:** The `update` variable (which calls `CameraUpdateFactory.newLatLngBounds(...)`) is
computed BEFORE `delay(100)`. `newLatLngBounds` requires the map to have completed a layout pass
to know the viewport size — if it hasn't, it throws `IllegalStateException`. That exception
escapes the `LaunchedEffect` silently (there is no outer catch), so `cameraPositioned` is never
set to `true`, the scrim waits the full 8-second `revealTimedOut` timeout, then drops to show the
map at its uninitialised default position (whichever region it last rendered).

**Fix:** Move the `update` computation (including the `newLatLngBounds` call) to inside the `try`
block, after `delay(100)`. A local lambda `buildUpdate()` avoids duplicating the logic in the retry.

Find the `LaunchedEffect(mapLoaded, geocodingComplete)` block in `MapScreen.kt`:

```kotlin
// BEFORE — newLatLngBounds computed before delay, throws early if map not laid out:
LaunchedEffect(mapLoaded, geocodingComplete) {
    if (mapLoaded && !cameraPositioned && geocodingComplete && geocodedLocations.isNotEmpty()) {
        val latlngs = geocodedLocations.values
            .map { LatLng(it.first, it.second) }
            .filter { !(it.latitude == 0.0 && it.longitude == 0.0) }
        if (latlngs.isEmpty()) return@LaunchedEffect
        val update = if (latlngs.size == 1) {             // ← computed here, before delay
            CameraUpdateFactory.newLatLngZoom(latlngs[0], 5f)
        } else {
            val bounds = LatLngBounds.builder().apply { latlngs.forEach { include(it) } }.build()
            CameraUpdateFactory.newLatLngBounds(bounds, 80)  // ← throws if map not laid out
        }
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

// AFTER — update computed inside try, after the layout delay:
LaunchedEffect(mapLoaded, geocodingComplete) {
    if (mapLoaded && !cameraPositioned && geocodingComplete && geocodedLocations.isNotEmpty()) {
        // Only fit to locations that belong to current entries (geocodedLocations is an
        // append-only cache; stale entries from deleted/edited entries are excluded here).
        val latlngs = locations
            .mapNotNull { geocodedLocations[it] }
            .map { LatLng(it.first, it.second) }
            .filter { !(it.latitude == 0.0 && it.longitude == 0.0) }
        if (latlngs.isEmpty()) return@LaunchedEffect

        // Build the camera update. newLatLngBounds requires a completed map layout pass —
        // compute it inside the try block, after the delay, to avoid a premature throw.
        fun buildUpdate(): CameraUpdate = if (latlngs.size == 1) {
            CameraUpdateFactory.newLatLngZoom(latlngs[0], 5f)
        } else {
            val bounds = LatLngBounds.builder().apply { latlngs.forEach { include(it) } }.build()
            CameraUpdateFactory.newLatLngBounds(bounds, 80)
        }

        delay(100)
        try {
            cameraPositionState.move(buildUpdate())
        } catch (_: Exception) {
            delay(400)
            runCatching { cameraPositionState.move(buildUpdate()) }
        }
        cameraPositioned = true
    }
}
```

Note the `latlngs` computation also changes: it now iterates `locations` (the current entry
location strings) and looks them up in `geocodedLocations`, rather than iterating
`geocodedLocations.values` directly. This ensures stale locations from deleted or edited entries
are not included in the bounds calculation. `locations` is already in scope in `MapScreen` as
`val locations by viewModel.uniqueLocations.collectAsState()` (or equivalent — verify the exact
variable name in the file).

`CameraUpdate` is already imported (`com.google.android.gms.maps.CameraUpdate`). No other imports
needed. No string changes.

**File:** `ui/screens/MapScreen.kt`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Move `buildUpdate()` (including `newLatLngBounds`) inside the `try` block after `delay(100)` so it doesn't throw before the map has had a layout pass | `ui/screens/MapScreen.kt` |
| 2 | Filter `latlngs` from `locations` (current entries) rather than `geocodedLocations.values` (append-only cache including stale data) | `ui/screens/MapScreen.kt` |
