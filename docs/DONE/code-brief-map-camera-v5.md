# Macaco — Adventures Map: Camera Position Fix (v5)

The Adventures map still shows the default world view on tablet after geocoding completes. The v4
fix moved `buildUpdate()` inside the try block and added a 400ms retry, but this is not enough.

## Root cause

`CameraUpdateFactory.newLatLngBounds(bounds, padding)` internally calls `map.getProjection()`
which requires the map view to have been measured and laid out. If the map hasn't finished its
layout pass, it throws `IllegalStateException("Map size can't be 0")`. On a slower tablet, even
500ms (100ms + 400ms retry) is not enough.

The critical bug is that `cameraPositioned = true` is set **unconditionally** after the try/catch:

```kotlin
// v4 code — MapScreen.kt lines 163–170
delay(100)
try {
    cameraPositionState.move(buildUpdate())    // throws on tablet
} catch (_: Exception) {
    delay(400)
    runCatching { cameraPositionState.move(buildUpdate()) }  // also throws
}
cameraPositioned = true  // ← runs regardless — scrim drops, camera stuck at world view
```

When both attempts throw, `cameraPositioned` is set to `true`, the scrim drops, and the map shows
the default `LatLng(20.0, 0.0)` world view permanently (until the user navigates away and back).

## Fix

Replace `newLatLngBounds` with a layout-independent calculation: compute the bounds' center
and estimate the zoom from the lat/lng span. `newLatLngZoom(center, zoom)` never throws —
it only stores values; no map measurement is needed.

Also gate `cameraPositioned = true` on whether the move actually succeeded so a genuine failure
doesn't permanently hide the scrim (the 8s timeout in the separate `LaunchedEffect` still ensures
the spinner can't trap the user forever).

```kotlin
// BEFORE — MapScreen.kt lines 150–170
            // Build the camera update. newLatLngBounds requires a completed map layout pass —
            // compute it inside the try block, after the delay, to avoid a premature throw that
            // would escape the LaunchedEffect silently and strand the scrim until its timeout.
            fun buildUpdate(): CameraUpdate = if (latlngs.size == 1) {
                CameraUpdateFactory.newLatLngZoom(latlngs[0], 5f)
            } else {
                val bounds = LatLngBounds.builder().apply { latlngs.forEach { include(it) } }.build()
                CameraUpdateFactory.newLatLngBounds(bounds, 80)
            }

            // Give the map a frame to lay out, then retry once before giving up — and always
            // clear the scrim so a failed move can't strand the user on the default
            // North-Atlantic position behind the spinner.
            delay(100)
            try {
                cameraPositionState.move(buildUpdate())
            } catch (_: Exception) {
                delay(400)
                runCatching { cameraPositionState.move(buildUpdate()) }
            }
            cameraPositioned = true

// AFTER — replace the entire block above with:
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

            // newLatLngZoom never throws, but keep the gate so a hypothetical future failure
            // doesn't clear the scrim with the camera stuck at the default world view.
            delay(100)
            val moved = runCatching { cameraPositionState.move(buildUpdate()) }.isSuccess
            if (moved) cameraPositioned = true
            // If not moved, the 8-second revealTimedOut in the sibling LaunchedEffect will
            // eventually drop the scrim so the user is never permanently trapped.
```

No new imports needed — `LatLngBounds`, `LatLng`, `CameraUpdateFactory`, and `CameraUpdate` are
already imported.

**File:** `ui/screens/MapScreen.kt`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Replace `newLatLngBounds` with manual center+zoom (layout-independent) | `ui/screens/MapScreen.kt` |
| 2 | Gate `cameraPositioned = true` on move success | `ui/screens/MapScreen.kt` |

No string resources, no ViewModel changes, no other files.
