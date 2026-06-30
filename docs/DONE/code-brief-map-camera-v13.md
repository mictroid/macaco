# Macaco — MapScreen: Map camera v13 — landscape header centering + off-screen pin indicators

Three changes, all in `MapScreen.kt`.

---

## Fix 1 — Center the landscape header Row

The compact landscape Row (triggered when `screenHeightDp < 480`) currently uses the default
`horizontalArrangement = Arrangement.Start`, so logo + text cluster to the left.
Change the Row to center its content and add the `globeSpanning` hint inline.

```kotlin
// BEFORE — line ~279
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(24.dp)
    )
    Spacer(Modifier.width(6.dp))
    Text(
        text = "macaco",
        color = SplashGoldBright,
        fontSize = 14.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 3.sp
    )
    Text(
        text = " · Adventures",
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.85f)
    )
    if (locations.isNotEmpty()) {
        val mappedCount = locations.count { it in geocodedLocations }
        Text(
            text = " · $mappedCount/${locations.size} mapped",
            color = SplashGold.copy(alpha = 0.70f),
            fontSize = 11.sp,
            fontFamily = MacacoFontFamily
        )
    }
}

// AFTER — add horizontalArrangement = Arrangement.Center and globeSpanning hint
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(24.dp)
    )
    Spacer(Modifier.width(6.dp))
    Text(
        text = "macaco",
        color = SplashGoldBright,
        fontSize = 14.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 3.sp
    )
    Text(
        text = " · Adventures",
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.85f)
    )
    if (locations.isNotEmpty()) {
        val mappedCount = locations.count { it in geocodedLocations }
        Text(
            text = " · $mappedCount/${locations.size} mapped",
            color = SplashGold.copy(alpha = 0.70f),
            fontSize = 11.sp,
            fontFamily = MacacoFontFamily
        )
    }
    // Globe-spanning hint — compact, dot-separated, same line
    if (globeSpanning) {
        Text(
            text = " · swipe to see all",
            style = MaterialTheme.typography.labelSmall,
            color = SplashGold.copy(alpha = 0.75f),
            letterSpacing = 0.5.sp
        )
    }
}
```

---

## Fix 2 — Off-screen pin indicators (edge arrows)

When `globeSpanning = true` the SDK has clamped the zoom and one or more pins fall
off the left or right edge of the screen. Show tappable chevron buttons at the map
edges so the user knows exactly where to pan and can tap to jump there.

### 2a — New state variables (add near the `globeSpanning` declaration, line ~143)

```kotlin
var globeSpanning   by remember { mutableStateOf(false) }
// Longitude of the off-screen pin furthest west; null = none off-screen to the west.
var offScreenWestLng  by remember { mutableStateOf<Double?>(null) }
// Longitude of the off-screen pin furthest east; null = none off-screen to the east.
var offScreenEastLng  by remember { mutableStateOf<Double?>(null) }
// Latitude center used for pan-to animations (kept in sync with lngCenter after move).
var mapLatCenter      by remember { mutableStateOf(0.0) }
// Applied zoom after move() — needed to keep the same zoom level when panning.
var mapAppliedZoom    by remember { mutableStateOf(0f) }
```

### 2b — Compute off-screen pins + reframe latitude after `move()` (inside the LaunchedEffect)

Replace the block immediately after `globeSpanning = appliedZoom > requestedZoom + 0.2f` with
the following. It does two things: (1) computes which pins are off-screen for the edge
chevrons, and (2) when clamped, does a second `move()` to recentre the latitude on only
the visible pins — eliminating the large empty-ocean gap caused by off-screen pins' latitudes
pulling the frame south.

```kotlin
mapAppliedZoom = appliedZoom
mapLatCenter   = latCenter
if (globeSpanning) {
    // Compute visible longitude window at the SDK-clamped zoom.
    val halfSpanDeg = (mapSizePx.width / (tile * 2.0.pow(appliedZoom.toDouble()))) * 360.0 / 2.0
    val westEdge = lngCenter - halfSpanDeg
    val eastEdge = lngCenter + halfSpanDeg

    offScreenWestLng = latlngs.map { it.longitude }.filter { it < westEdge }.minOrNull()
    offScreenEastLng = latlngs.map { it.longitude }.filter { it > eastEdge }.maxOrNull()

    // Latitude reframe: off-screen pins' latitudes skew the vertical centre, leaving a
    // large empty-ocean band. Recompute latCenter from visible pins only and do a second
    // move() — both moves happen under the scrim so the user sees only the final result.
    val visibleLatlngs = latlngs.filter { it.longitude in westEdge..eastEdge }
    val latCenterVisible = if (visibleLatlngs.isNotEmpty()) {
        (visibleLatlngs.minOf { it.latitude } + visibleLatlngs.maxOf { it.latitude }) / 2.0
    } else latCenter

    if (kotlin.math.abs(latCenterVisible - latCenter) > 0.5) {
        Log.d("MapCamera", "v13: lat reframe ${"%.1f".format(latCenter)}→${"%.1f".format(latCenterVisible)} (${visibleLatlngs.size} visible pins)")
        runCatching {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(LatLng(latCenterVisible, lngCenter), appliedZoom)
            )
        }
        mapLatCenter = latCenterVisible
    }
} else {
    offScreenWestLng = null
    offScreenEastLng = null
}
```

`2.0.pow(...)` and `kotlin.math.abs` require `kotlin.math.*` — add the import if not present.

### 2c — Coroutine scope for animations

Add near the top of the composable (alongside the `context` val):

```kotlin
val mapScope = rememberCoroutineScope()
```

### 2d — Overlay the edge indicators on the map Box

Inside the map `Box` (line ~362), after the loading scrim block and before the closing `}`:

```kotlin
// ── Off-screen pin chevrons ──────────────────────────────────────────────────
// Only shown when globeSpanning=true AND there are pins off that edge.
// Each button animates the camera to centre on the furthest off-screen pin in
// that direction, keeping the same zoom and latitude.
if (globeSpanning) {
    val westLng = offScreenWestLng
    val eastLng = offScreenEastLng

    if (westLng != null) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF0D3D38).copy(alpha = 0.80f))
                .clickable {
                    mapScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(mapLatCenter, westLng),
                                mapAppliedZoom
                            ),
                            durationMs = 600
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Pan to off-screen pin",
                tint = SplashGold,
                modifier = Modifier.size(22.dp)
            )
        }
    }

    if (eastLng != null) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF0D3D38).copy(alpha = 0.80f))
                .clickable {
                    mapScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(mapLatCenter, eastLng),
                                mapAppliedZoom
                            ),
                            durationMs = 600
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Pan to off-screen pin",
                tint = SplashGold,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
```

**Imports to add** (if not already present):
```kotlin
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import kotlinx.coroutines.launch
import kotlin.math.pow
```

---

## Scope

- **In:** Landscape header centering; `globeSpanning` hint in landscape row; west/east edge
  indicators when pins are off-screen.
- **Out:** Any further zoom math — the SDK floor is a confirmed physics limit (v12 Logcat).
- **Out:** Portrait header — already centered and already has the `globeSpanning` hint.
- **Out:** Single-pin or small-span map paths — `globeSpanning` stays false, no indicators shown.

---

## Verification

1. **Landscape header** — open Adventures in landscape; logo + text should be centered, not left-aligned.
2. **Globe-spanning hint in landscape** — with 6 globe-spanning entries: "· swipe to see all" appears inline in the landscape header row.
3. **Edge chevrons in portrait** — with globe-spanning entries: ◀ appears at the left map edge (Argentina off-screen) and ▶ at the right (Japan off-screen). Tapping each animates the camera to centre on that pin.
4. **Latitude reframe** — in portrait with the 6-entry set, the map should no longer show a large empty ocean band below Africa. The vertical centre should sit around the visible pins (Northern Europe / Iceland cluster), not pulled south by Argentina's latitude. Logcat should show: `v13: lat reframe +7.0→+52.0 (4 visible pins)` (approximate values).
5. **Small-span set** — with only European entries: no chevrons, no hint, map frames all pins normally.
