# Macaco — MapScreen: Dynamic reactive pin-navigation arrows

One file: `ui/screens/MapScreen.kt`.

Read `docs/DONE/code-brief-map-camera-v13.md` for prior context (edge chevrons introduced).
This brief fixes three bugs in the v13 chevron implementation and adds dynamic vertical
positioning.

---

## Fix 1 — Stale off-screen pin coordinates (root cause of longitude wraparound)

**Problem:** `offScreenWestLng` and `offScreenEastLng` are computed once in the initial
`LaunchedEffect(mapLoaded, geocodingComplete, mapSizePx)` and never updated after the user
presses a chevron. After navigating to Japan (lng ≈ +140°E), both values still hold the
coordinates from the original globe-fit framing. Pressing the left chevron then sends to the
old `offScreenWestLng` (which could be the USA), not to Europe.

**Fix:** Add a separate reactive `LaunchedEffect` keyed on `cameraPositionState.position`
(and `geocodedLocations`, `mapSizePx`) that recomputes the off-screen pin set whenever
the camera settles at a new position. Remove the off-screen computation from the initial
LaunchedEffect (leave only the camera-positioning logic there).

Also add two new state variables to track the **latitude** of the nearest off-screen pin
(needed for Fix 3 — vertical arrow positioning and correct navigation target):

### 1a — New state variables (add alongside the existing offScreenWestLng / offScreenEastLng)

```kotlin
// BEFORE (line ~161) — existing state, latitude not tracked
var offScreenWestLng by remember { mutableStateOf<Double?>(null) }
var offScreenEastLng by remember { mutableStateOf<Double?>(null) }

// AFTER — add two latitude companions
var offScreenWestLng by remember { mutableStateOf<Double?>(null) }
var offScreenEastLng by remember { mutableStateOf<Double?>(null) }
var offScreenWestLat by remember { mutableStateOf<Double?>(null) }   // NEW
var offScreenEastLat by remember { mutableStateOf<Double?>(null) }   // NEW
```

### 1b — Remove off-screen computation from the initial LaunchedEffect

In `LaunchedEffect(mapLoaded, geocodingComplete, mapSizePx)` (line ~173), delete the block
that writes to `offScreenWestLng` / `offScreenEastLng` (lines ~284–313). Keep everything
else in that block: the zoom math, `cameraPositionState.move()`, `globeSpanning`, and the
lat-reframe logic. The reactive LaunchedEffect (1c) will handle off-screen pins from now on.

```kotlin
// REMOVE from the initial LaunchedEffect — the reactive LaunchedEffect takes over:
if (globeSpanning) {
    val halfSpanDeg =
        (mapSizePx.width / (tile * 2.0.pow(appliedZoom.toDouble()))) * 360.0 / 2.0
    val westEdge = fitLngCenter - halfSpanDeg
    val eastEdge = fitLngCenter + halfSpanDeg

    offScreenWestLng = latlngs.map { it.longitude }.filter { it < westEdge }.minOrNull()
    offScreenEastLng = latlngs.map { it.longitude }.filter { it > eastEdge }.maxOrNull()
    // ... visible lat reframe logic (KEEP that — it's separate from arrow tracking)
} else {
    offScreenWestLng = null
    offScreenEastLng = null
}
```

### 1c — New reactive LaunchedEffect (add after the initial LaunchedEffect block)

```kotlin
// Reactive off-screen pin tracker. Runs whenever the camera settles at a new position,
// mapSizePx changes, or geocodedLocations gains new entries. Replaces the one-time
// computation that was in the initial LaunchedEffect — this version always reflects the
// current viewport, so chevrons work correctly after the user navigates to a new region.
//
// Navigation goes to the NEAREST off-screen pin (closest to the viewport edge), not the
// furthest. From Japan: nearest western pin = Europe, not USA.
//
// Antimeridian note: uses simple -180..+180 longitude comparison. Edge case where the
// viewport straddles the antimeridian is rare and handled by globeSpanning logic above.
LaunchedEffect(cameraPositionState.position, geocodedLocations, mapSizePx) {
    if (mapSizePx.width <= 0 || geocodedLocations.isEmpty()) return@LaunchedEffect

    val pos = cameraPositionState.position
    val camLng = pos.target.longitude
    val camZoom = pos.zoom
    val tile = 256.0 * density                                        // density already in scope
    val halfSpanDeg =
        (mapSizePx.width / (tile * 2.0.pow(camZoom.toDouble()))) * 360.0 / 2.0
    val westEdge = camLng - halfSpanDeg
    val eastEdge  = camLng + halfSpanDeg

    val latlngs = locations
        .mapNotNull { geocodedLocations[it] }
        .map { LatLng(it.first, it.second) }
        .filter { !(it.latitude == 0.0 && it.longitude == 0.0) }

    // Nearest west: highest longitude that is still west of the viewport edge.
    val nearestWest = latlngs.filter { it.longitude < westEdge }
        .maxByOrNull { it.longitude }
    // Nearest east: lowest longitude that is still east of the viewport edge.
    val nearestEast = latlngs.filter { it.longitude > eastEdge }
        .minByOrNull { it.longitude }

    offScreenWestLng = nearestWest?.longitude
    offScreenWestLat = nearestWest?.latitude
    offScreenEastLng = nearestEast?.longitude
    offScreenEastLat = nearestEast?.latitude

    // Keep mapLatCenter + mapAppliedZoom in sync for chevron navigation.
    mapLatCenter = pos.target.latitude
    mapAppliedZoom = camZoom
}
```

**Import needed** (if not already present):
```kotlin
import kotlin.math.pow
```

---

## Fix 2 — Decouple arrows from globeSpanning (fixes tablet landscape)

**Problem:** The chevrons are gated on `if (globeSpanning)`. On tablets in landscape,
`globeSpanning` may stay `false` (the SDK didn't need to clamp the zoom) even though pins
are off-screen — the wider viewport can show more, but not necessarily all. With the
reactive tracker (Fix 1), `offScreenWestLng` / `offScreenEastLng` are always up-to-date,
so gating on `globeSpanning` is no longer necessary.

**Fix:** Replace the `if (globeSpanning)` gate with direct null-checks on the arrow
longitude values.

```kotlin
// BEFORE (line ~512) — gated on globeSpanning
if (globeSpanning) {
    val westLng = offScreenWestLng
    val eastLng = offScreenEastLng

    if (westLng != null) { /* left arrow */ }
    if (eastLng != null) { /* right arrow */ }
}

// AFTER — always check, regardless of globeSpanning
val westLng = offScreenWestLng
val eastLng = offScreenEastLng

if (westLng != null) { /* left arrow — see Fix 3 */ }
if (eastLng != null) { /* right arrow — see Fix 3 */ }
```

---

## Fix 3 — Dynamic vertical arrow positioning + navigate to pin's actual latitude

**Problem:** Both chevrons are placed at `Alignment.CenterStart` / `Alignment.CenterEnd`,
always at the vertical midpoint of the map, regardless of where the off-screen pin is.
The navigation target uses `mapLatCenter` (the camera's current latitude) instead of the
off-screen pin's own latitude, so `newLatLngZoom` may center on the wrong latitude.

**Fix:**

1. Compute a vertical fraction (0.0 = top, 1.0 = bottom) mapping the off-screen pin's
   latitude to where it would appear on-screen. Clamp to [0.1, 0.9] so the arrow stays
   within safe tap area.
2. Offset the arrow button from the vertical midpoint of the map using that fraction.
3. Pass the pin's actual latitude (not `mapLatCenter`) to `newLatLngZoom`.

```
Before:  arrow always at vertical centre of the map edge
          ┌──────────────────┐
          │                  │
          │                  │
    ◀ ──► │   viewport       │ ◀── arrows fixed to midpoint
          │                  │
          │                  │
          └──────────────────┘

After:   arrow floats toward the off-screen pin's latitude
          ┌──────────────────┐
    ◀ ──► │  ← pin is up here│   ← left arrow follows pin latitude
          │                  │
          │   viewport       │
          │                  │
          │            pin ► │ ─► right arrow follows that pin's latitude
          └──────────────────┘
```

Add a helper function at the top of the composable (or as a file-level private fun) to
compute the vertical fraction:

```kotlin
import kotlin.math.ln
import kotlin.math.tan
import kotlin.math.PI

/**
 * Maps [pinLat] to a vertical fraction (0.0 = top, 1.0 = bottom) in the current viewport.
 * Uses Mercator projection so high-latitude positions are positioned correctly.
 * Returns a value clamped to [minFraction, maxFraction] to stay within safe tap area.
 */
private fun arrowVerticalFraction(
    pinLat: Double,
    camLat: Double,
    camZoom: Float,
    mapHeightPx: Int,
    density: Float,
    minFraction: Float = 0.1f,
    maxFraction: Float = 0.9f
): Float {
    if (mapHeightPx <= 0) return 0.5f
    val tile = 256.0 * density
    val mercPerPx = 2 * PI / (tile * 2.0.pow(camZoom.toDouble()))
    val halfMercSpan = mercPerPx * mapHeightPx / 2.0
    val merc = { lat: Double -> ln(tan(PI / 4 + lat * PI / 360)) }
    val fraction = 0.5 + (merc(camLat) - merc(pinLat)) / (2 * halfMercSpan)
    return fraction.toFloat().coerceIn(minFraction, maxFraction)
}
```

Replace the chevron Box composables (full replacement of the gated section from Fix 2):

```kotlin
// AFTER — dynamic arrows (replace the entire old globeSpanning block)

val westLng = offScreenWestLng
val westLat = offScreenWestLat
val eastLng = offScreenEastLng
val eastLat = offScreenEastLat

if (westLng != null && westLat != null) {
    val fraction = arrowVerticalFraction(
        pinLat = westLat,
        camLat = cameraPositionState.position.target.latitude,
        camZoom = cameraPositionState.position.zoom,
        mapHeightPx = mapSizePx.height,
        density = density
    )
    val yOffsetPx = ((fraction - 0.5f) * mapSizePx.height).roundToInt()
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .offset { IntOffset(0, yOffsetPx) }
            .padding(start = 8.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f))
            .clickable {
                mapScope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(westLat, westLng), mapAppliedZoom
                        ),
                        durationMs = 600
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = stringResource(R.string.map_globe_spanning_hint),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp)
        )
    }
}

if (eastLng != null && eastLat != null) {
    val fraction = arrowVerticalFraction(
        pinLat = eastLat,
        camLat = cameraPositionState.position.target.latitude,
        camZoom = cameraPositionState.position.zoom,
        mapHeightPx = mapSizePx.height,
        density = density
    )
    val yOffsetPx = ((fraction - 0.5f) * mapSizePx.height).roundToInt()
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .offset { IntOffset(0, yOffsetPx) }
            .padding(end = 8.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f))
            .clickable {
                mapScope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(eastLat, eastLng), mapAppliedZoom
                        ),
                        durationMs = 600
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = stringResource(R.string.map_globe_spanning_hint),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp)
        )
    }
}
```

**Note on colour:** The v13 arrows used `Color(0xFF0D3D38).copy(alpha = 0.80f)` (hardcoded
teal) with `SplashGold` tint. Replaced with `primaryContainer`/`onPrimaryContainer` tokens
so it adapts to all 7 themes. If the product preference is to keep the hardcoded teal + gold,
revert to `Color(0xFF0D3D38).copy(alpha = 0.80f)` background and `SplashGold` icon tint.

**Imports needed:**
```kotlin
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
```

---

## Scope

- **In:** Reactive off-screen pin tracking (fixes stale wraparound); nearest-pin navigation;
  dynamic vertical arrow positioning; tablet landscape arrow visibility (decoupled from
  globeSpanning).
- **Out:** Antimeridian viewport-straddling edge case (camera at ±180° with pins on both
  sides) — rare in practice, deferred.
- **Out:** Multiple simultaneous arrows (one per off-screen pin) — current design shows one
  per side (nearest); a future brief could add per-pin indicators.
- **Out:** Any changes to geocoding, zoom math, or lat-reframe logic — unchanged.

---

## Verification

1. Set up entries in Europe + Japan + USA. On initial load all three are globe-spanning.
2. Press right arrow → camera moves to Japan. Left arrow should now point at Europe
   (nearest western pin), not USA. Right arrow should disappear (no pins east of Japan).
3. Press left arrow from Japan → camera moves to Europe. Left arrow should now point at
   USA (nearest western pin from Europe). Right arrow should point back at Japan.
4. Cycle should work indefinitely without getting "stuck" on USA.
5. On tablet in landscape: if any pins fall off-screen (even without zoom clamping),
   arrows should appear.
6. Arrows should be vertically offset toward the off-screen pin's approximate latitude
   — not rigidly stuck to the map midpoint.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1a | Add `offScreenWestLat` / `offScreenEastLat` state variables | `MapScreen.kt` |
| 1b | Remove stale off-screen computation from initial LaunchedEffect | `MapScreen.kt` |
| 1c | New reactive LaunchedEffect keyed on `cameraPositionState.position` | `MapScreen.kt` |
| 2 | Remove `globeSpanning` gate; show arrows whenever off-screen pins exist | `MapScreen.kt` |
| 3 | `arrowVerticalFraction()` helper + dynamic `yOffsetPx` offset + navigate to pin lat | `MapScreen.kt` |
