# Macaco — Map: One Directional Arrow Per Off-Screen Pin (v15)

Two improvements to the off-screen pin chevrons in `MapScreen.kt`:
1. Show **one arrow per off-screen location** instead of just one arrow per side.
2. Make each arrow **point in the actual visual direction** toward its pin (NW, W, SW, etc.)
   rather than always pointing horizontally left or right.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`

Example: viewed from Japan with entries in Europe (W), Iceland (NW), and Argentina (SW),
the user currently sees one `<` chevron. After this brief: three arrows — one tilted slightly
northwest toward Europe, one tilted more steeply northwest toward Iceland, one tilted southwest
toward Argentina. Tapping each navigates directly to that pin.

---

## Change 1 — Imports

Remove `ChevronLeft` and `ChevronRight`. Add `ArrowUpward` (the single rotatable icon) and
`atan2` for the bearing calculation.

```kotlin
// REMOVE these two lines (~line 35-36):
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight

// ADD:
import androidx.compose.material.icons.filled.ArrowUpward
import kotlin.math.atan2
```

If `graphicsLayer` (for `Modifier.graphicsLayer { rotationZ = ... }`) is not already imported,
add `import androidx.compose.ui.graphics.graphicsLayer`.

---

## Change 2 — State variables (~line 197)

Replace the four single-pin tracking variables with two lists — one per edge.

### BEFORE
```kotlin
var offScreenWestLng by remember { mutableStateOf<Double?>(null) }
var offScreenEastLng by remember { mutableStateOf<Double?>(null) }
var offScreenWestLat by remember { mutableStateOf<Double?>(null) }   // v14: pin latitude, for arrow y-offset + nav target
var offScreenEastLat by remember { mutableStateOf<Double?>(null) }
```

### AFTER
```kotlin
var offScreenWestPins by remember { mutableStateOf<List<LatLng>>(emptyList()) }
var offScreenEastPins by remember { mutableStateOf<List<LatLng>>(emptyList()) }
```

---

## Change 3 — Off-screen tracker LaunchedEffect (~line 389)

Replace the "nearest one per side" selection with "all off-screen pins per side".
The click target is now the specific pin the arrow points to, so we want every one, not just nearest.

### BEFORE
```kotlin
        // Nearest west: highest longitude that is still west of the viewport edge.
        val nearestWest = latlngs.filter { it.longitude < westEdge }.maxByOrNull { it.longitude }
        // Nearest east: lowest longitude that is still east of the viewport edge.
        val nearestEast = latlngs.filter { it.longitude > eastEdge }.minByOrNull { it.longitude }

        offScreenWestLng = nearestWest?.longitude
        offScreenWestLat = nearestWest?.latitude
        offScreenEastLng = nearestEast?.longitude
        offScreenEastLat = nearestEast?.latitude
```

### AFTER
```kotlin
        // All pins off each edge — each gets its own directional arrow.
        offScreenWestPins = latlngs.filter { it.longitude < westEdge }
        offScreenEastPins = latlngs.filter { it.longitude > eastEdge }
```

---

## Change 4 — Arrow rendering (~line 603)

Replace the two single-arrow `if` blocks (one west, one east) with two `forEach` loops.
Each arrow is an `ArrowUpward` icon rotated to the visual direction from the map centre to
the arrow's edge position. The bearing is computed from the screen-space vector — this
correctly represents what the user sees on the map (NW for Iceland, SW for Argentina, etc.).

### BEFORE
```kotlin
            // ── Off-screen pin chevrons ──────────────────────────────────────────────────
            // v14: shown whenever there is an off-screen pin on that edge ...
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
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
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
                        tint = MaterialTheme.colorScheme.onPrimary,
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
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End))
                        .padding(end = 8.dp)
                        .size(36.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
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
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
```

### AFTER
```kotlin
            // ── Off-screen pin arrows (v15) ──────────────────────────────────────────────
            // One arrow per off-screen pin. Each arrow is an ArrowUpward icon rotated to the
            // visual direction from the map centre to that arrow's edge position — so Iceland
            // tilts NW, Europe points W, Argentina tilts SW, etc.
            //
            // Bearing formula: from screen-centre (0.5, 0.5) to the arrow's edge position
            // (left edge x=0, right edge x=1, vertical = arrowVerticalFraction).
            // atan2(eastComponent, northComponent) → degrees clockwise from north for rotationZ.

            offScreenWestPins.forEach { pin ->
                val fraction = arrowVerticalFraction(
                    pinLat = pin.latitude,
                    camLat = cameraPositionState.position.target.latitude,
                    camZoom = cameraPositionState.position.zoom,
                    mapHeightPx = mapSizePx.height,
                    density = density
                )
                val yOffsetPx = ((fraction - 0.5f) * mapSizePx.height).roundToInt()
                // Arrow at left edge: east component = -0.5, north component = 0.5 - fraction
                val bearing = Math.toDegrees(
                    atan2(-0.5, (0.5f - fraction).toDouble())
                ).toFloat()
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(0, yOffsetPx) }
                        .padding(start = 8.dp)
                        .size(36.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            mapScope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(pin.latitude, pin.longitude), mapAppliedZoom
                                    ),
                                    durationMs = 600
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = stringResource(R.string.map_globe_spanning_hint),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = bearing }
                    )
                }
            }

            offScreenEastPins.forEach { pin ->
                val fraction = arrowVerticalFraction(
                    pinLat = pin.latitude,
                    camLat = cameraPositionState.position.target.latitude,
                    camZoom = cameraPositionState.position.zoom,
                    mapHeightPx = mapSizePx.height,
                    density = density
                )
                val yOffsetPx = ((fraction - 0.5f) * mapSizePx.height).roundToInt()
                // Arrow at right edge: east component = +0.5, north component = 0.5 - fraction
                val bearing = Math.toDegrees(
                    atan2(0.5, (0.5f - fraction).toDouble())
                ).toFloat()
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset { IntOffset(0, yOffsetPx) }
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End))
                        .padding(end = 8.dp)
                        .size(36.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            mapScope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(pin.latitude, pin.longitude), mapAppliedZoom
                                    ),
                                    durationMs = 600
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = stringResource(R.string.map_globe_spanning_hint),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = bearing }
                    )
                }
            }
```

---

## Bearing math, explained

The `arrowVerticalFraction` helper places each arrow at a vertical position on the screen edge
(0 = top, 1 = bottom). The bearing is derived from the vector from map centre (0.5, 0.5) to
the arrow's edge position:

| Pin direction | fraction | bearing |
|---|---|---|
| Due west | 0.5 | −90° = 270° (W) |
| Northwest (e.g. Iceland) | ~0.15 | ~−55° = 305° (NNW) |
| Southwest (e.g. Argentina) | ~0.85 | ~−125° = 235° (SW) |

`rotationZ` in Compose is clockwise, so negative values rotate counter-clockwise — a negative
bearing from `atan2` naturally produces the correct NW/SW tilt on the `ArrowUpward` icon.

---

## What is NOT changed

- `arrowVerticalFraction()` helper function — unchanged.
- `globeSpanning` logic and the "Swipe to see all pins" hint — unchanged.
- Map style, pin rendering, camera animation parameters — all unchanged.
- The `mapLatCenter` and `mapAppliedZoom` sync lines in the LaunchedEffect — unchanged.
- Antimeridian handling: still uses simple `-180..+180` longitude comparison (same as v14).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Remove `ChevronLeft`/`ChevronRight` imports; add `ArrowUpward`, `atan2` | `MapScreen.kt` |
| 2 | Replace 4 single-pin `offScreen*` vars with `offScreenWestPins`/`offScreenEastPins` lists | `MapScreen.kt` |
| 3 | Tracker: replace nearest-one-per-side selection with filter-all-per-side | `MapScreen.kt` |
| 4 | Rendering: replace two single-arrow `if` blocks with two `forEach` loops + rotation | `MapScreen.kt` |
