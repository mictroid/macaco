# Brief: Map Camera Fixes

**Priority:** High  
**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`

## Problems

1. **Africa/Null Island bug** — One or more location strings geocode to `(0.0, 0.0)` (the "Null Island"
   coordinate in the Gulf of Guinea). This point is silently included in `geocodedLocations` and pulled
   into `LatLngBounds`, forcing the camera to zoom out to show both Europe and equatorial Africa.
   Confirmed via screenshot: "7 of 7 locations mapped" with all visible markers in Western Europe, but
   the map spans from UK to South Africa.

2. **North Atlantic bug on return** — When navigating back to MapScreen, `cameraPositioned` resets to
   `false` (it's `remember { mutableStateOf(false) }`). On re-entering the screen, `onMapLoaded` fires
   and triggers the camera-positioning `LaunchedEffect`. `CameraUpdateFactory.newLatLngBounds()` requires
   the map to have completed a layout pass — if called too early, it throws `IllegalStateException`.
   This exception propagates and silently cancels the LaunchedEffect coroutine before
   `cameraPositioned = true` is reached. The scrim then drops after the 8-second `revealTimedOut`,
   revealing the default North Atlantic position.

3. **Single-location zoom too tight** — When `latlngs.size == 1`, the camera uses zoom level `8f`
   (street-level). Should be `5f` (city/region overview).

## Changes

### 1. Filter Null Island and outlier coordinates before building bounds

In `LaunchedEffect(mapLoaded, geocodedLocations)`, before building the `LatLngBounds` or choosing the
single-location update, filter the `latlngs` list:

```kotlin
// Step 1: exclude Null Island (geocoding failures that return 0.0, 0.0)
val nullFiltered = geocodedLocations.values
    .map { LatLng(it.first, it.second) }
    .filter { !(it.latitude == 0.0 && it.longitude == 0.0) }

if (nullFiltered.isEmpty()) return@LaunchedEffect

// Step 2: outlier rejection — exclude any point >30° from the geographic median
//         This catches cases where a location name geocodes to the wrong continent.
val medianLat = nullFiltered.map { it.latitude }.sorted()[nullFiltered.size / 2]
val medianLng = nullFiltered.map { it.longitude }.sorted()[nullFiltered.size / 2]
val latlngs = nullFiltered.filter { pt ->
    Math.abs(pt.latitude - medianLat) <= 30.0 && Math.abs(pt.longitude - medianLng) <= 30.0
}.ifEmpty { nullFiltered } // fall back to null-filtered set if all points are spread >30°
```

Use `latlngs` (filtered) for all subsequent camera logic.

### 2. Add delay + try-catch around camera move to fix North Atlantic on return

Replace the bare `cameraPositionState.move(update)` call with:

```kotlin
delay(100) // give the map time to complete its first layout pass
try {
    cameraPositionState.move(update)
} catch (_: Exception) {
    // map not yet laid out; try once more after a longer delay
    delay(400)
    runCatching { cameraPositionState.move(update) }
}
cameraPositioned = true // always clear scrim, even if move failed
```

`cameraPositioned = true` must be set AFTER both attempts so the scrim drops regardless.

### 3. Change single-location zoom from 8f to 5f

```kotlin
// Before:
CameraUpdateFactory.newLatLngZoom(latlngs[0], 8f)

// After:
CameraUpdateFactory.newLatLngZoom(latlngs[0], 5f)
```

## Final LaunchedEffect shape (after all changes)

```kotlin
LaunchedEffect(mapLoaded, geocodedLocations) {
    if (!mapLoaded || cameraPositioned || geocodedLocations.isEmpty()) return@LaunchedEffect

    val nullFiltered = geocodedLocations.values
        .map { LatLng(it.first, it.second) }
        .filter { !(it.latitude == 0.0 && it.longitude == 0.0) }
    if (nullFiltered.isEmpty()) return@LaunchedEffect

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

    delay(100)
    try {
        cameraPositionState.move(update)
    } catch (_: Exception) {
        delay(400)
        runCatching { cameraPositionState.move(update) }
    }
    cameraPositioned = true
}
```

No other changes needed.
