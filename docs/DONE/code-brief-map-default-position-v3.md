# Macaco — MapScreen: Remove Outlier Rejection from Camera Fit

The v2 fix (`geocodingComplete` gate) is correctly implemented. The camera still zooms to a
single location because the outlier rejection block — which drops any point more than 30° from
the geographic median — eliminates all but one location for users with entries spread across
multiple continents.

File touched: `ui/screens/MapScreen.kt`.
Context: builds on `code-brief-map-default-position-v2.md` already in `docs/DONE/`.

---

## Root cause: outlier rejection too aggressive for global travel

With entries across Argentina, Iceland, Japan, and Germany the outlier filter computes:
- Median latitude ≈ 51° (Germany), median longitude ≈ 14° (Germany)
- Argentina (lat −50): |−50 − 51| = 101° > 30 → **dropped**
- Iceland (lng −22): |−22 − 14| = 36° > 30 → **dropped**
- Japan (lng 140): |140 − 14| = 126° > 30 → **dropped**
- Germany: passes → **only survivor**

`latlngs.size == 1` → camera zooms to level 5 on Germany. The feature was intended to catch
bad geocoding results (e.g. a city name geocoding to the wrong continent) but it is far too
narrow for any traveller who has visited more than one region of the world.

The Null Island filter (`!(lat == 0.0 && lng == 0.0)`) already handles the most common
geocoding failure. Outlier rejection provides no additional safety worth keeping.

---

## Fix: remove the outlier rejection block

Find the camera `LaunchedEffect` in `MapScreen.kt`. The outlier rejection block sits between
the Null Island filter and the `latlngs.size == 1` check:

```kotlin
// BEFORE:
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

// AFTER:
val latlngs = geocodedLocations.values
    .map { LatLng(it.first, it.second) }
    .filter { !(it.latitude == 0.0 && it.longitude == 0.0) }   // Null Island only
if (latlngs.isEmpty()) return@LaunchedEffect

val update = if (latlngs.size == 1) {
    CameraUpdateFactory.newLatLngZoom(latlngs[0], 5f)
} else {
    val bounds = LatLngBounds.builder().apply { latlngs.forEach { include(it) } }.build()
    CameraUpdateFactory.newLatLngBounds(bounds, 80)
}
```

The `nullFiltered` intermediate variable is eliminated; `latlngs` is built directly from
the Null Island filter. Everything below (the `delay(100)` retry block, `cameraPositioned = true`)
is unchanged.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Remove the geographic median / ±30° outlier rejection block; use Null Island filter only | `ui/screens/MapScreen.kt` |
