# Macaco — Adventures Map: Camera positions to most recent entry (v6)

Replaces the bounds-fitting zoom-table in `MapScreen.kt` with a simpler strategy that
centers on the most recently written entry's location at zoom 6.

---

## Change — Replace `buildUpdate()` with most-recent-location approach

**Problem:** The current v5 algorithm computes the center of *all* geocoded locations and
derives a zoom level from the geographic span. This works fine when all entries are in the
same country, but the moment a user has entries on two continents (e.g., Europe + Americas)
the lng-span exceeds 90° and the zoom table returns `2f` — a world-level view where every
pin is a tiny dot. The map opens looking like Google Earth instead of "where you've been."

The user reported the map "was working a few vc back." The fitting attempts (v3 → v4 → v5)
all tried to show every pin at once and each introduced its own failure mode. The
cross-continental zoom regression is the last one.

**Fix:** Discard the bounds-fitting approach entirely. Instead, sort entries by `createdAt`
descending and fly the camera to the first entry that has a geocoded location, at zoom 6
(~country level). This answers the natural question when opening Adventures: "where was I
last?" All other pins are still visible; the user can pinch-out to see the world view at
any time.

```kotlin
// BEFORE — MapScreen.kt, LaunchedEffect(mapLoaded, geocodingComplete), inner block
// (the entire body of the if-guard, after the closing brace of the guard condition)

        val latlngs = locations
            .mapNotNull { geocodedLocations[it] }
            .map { LatLng(it.first, it.second) }
            .filter { !(it.latitude == 0.0 && it.longitude == 0.0) }
        if (latlngs.isEmpty()) return@LaunchedEffect

        // Build the camera update without newLatLngBounds, which requires the map to be
        // fully laid out (throws on slow/tablet devices even after delay). Instead, compute
        // a center + approximate zoom from the bounds directly — newLatLngZoom never throws.
        fun buildUpdate(): CameraUpdate {
            if (latlngs.size == 1) return CameraUpdateFactory.newLatLngZoom(latlngs[0], 5f)
            val bounds = LatLngBounds.builder().apply { latlngs.forEach { include(it) } }.build()
            val center = bounds.center
            val latSpan = bounds.northeast.latitude - bounds.southwest.latitude
            val lngSpan = bounds.northeast.longitude - bounds.southwest.longitude
            val zoom = when (maxOf(latSpan, lngSpan)) {
                in 0.0..0.5   -> 10f
                in 0.5..2.0   -> 8f
                in 2.0..8.0   -> 6f
                in 8.0..20.0  -> 5f
                in 20.0..50.0 -> 4f
                in 50.0..90.0 -> 3f
                else          -> 2f
            }
            return CameraUpdateFactory.newLatLngZoom(center, zoom)
        }

        delay(100)
        val moved = runCatching { cameraPositionState.move(buildUpdate()) }.isSuccess
        if (moved) cameraPositioned = true

// AFTER — replace the entire block above with:

        // Center on the most recently written entry that has a geocoded location.
        // Fitting ALL locations looks great for same-country trips but degrades to
        // a useless zoom-2 world map the moment two continents are involved.
        // "Where was I last?" is the more useful default; users can pinch out to
        // see all pins. newLatLngZoom never throws regardless of map layout state.
        val target = entries
            .sortedByDescending { it.createdAt }
            .mapNotNull { entry ->
                val loc = entry.location.trim().ifBlank { null } ?: return@mapNotNull null
                geocodedLocations[loc]?.let { (lat, lon) ->
                    LatLng(lat, lon).takeIf { !(it.latitude == 0.0 && it.longitude == 0.0) }
                }
            }
            .firstOrNull() ?: return@LaunchedEffect

        val moved = runCatching {
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(target, 6f))
        }.isSuccess
        if (moved) cameraPositioned = true
```

**Import cleanup (optional but tidy):**
After this change `CameraUpdate` (used only as the `buildUpdate()` return type) and
`LatLngBounds` (used only inside `buildUpdate()`) are no longer referenced. Remove their
import lines if no other code in the file uses them:

```kotlin
// Remove these two imports if present and unused:
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.CameraUpdate
```

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`

---

## Scope

- **In:** Replace the `latlngs` + `buildUpdate()` + `delay(100)` block with the
  `target`-based single-call approach above. No other logic changes.
- **Out:** The 8-second `revealTimedOut` safety net, the scrim visibility condition, marker
  rendering, the geocoding `LaunchedEffect` — all untouched.
- **No string changes.** No ViewModel changes. Single-file edit.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Replace bounds-fitting + zoom table with `entries.sortedByDescending { createdAt }` → first geocoded location → `newLatLngZoom(target, 6f)` | `MapScreen.kt` |
