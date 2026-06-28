# Macaco — MapScreen: Map camera v10 — Mercator zoom + diagnostic logging

Replaces v9's `newLatLngBounds` SDK call with direct Mercator math and adds logging so any
future failure is visible. Touches `MapScreen.kt` only.

---

## Background: why v9 still fails

v9 combined the correct antimeridian-safe center (v8's largest-gap algorithm) with
`CameraUpdateFactory.newLatLngBounds(bounds, width, height, padding)` for exact zoom. On
device (A53, vc43) the map still does not zoom out far enough to show all pins.

Two possible causes, both silently hidden by `runCatching`:

1. **`newLatLngBounds` under-zooms for > 180° spans.** The SDK may have an internal edge case
   when the bounding box spans more than half the globe (the user's 4 locations span 209°).
2. **`cameraPositionState.move()` throws and is swallowed.** If the call throws, `isSuccess`
   is false, `cameraPositioned` stays false, the scrim drops after 8 s, and the map sits at
   the default world view — which also looks like "wrong zoom."

**Fix:** Replace the `newLatLngBounds` call with direct Mercator pixel math (the same
calculation the SDK does internally, but applied explicitly so no SDK edge case can interfere),
and add `Log.d/e` so every failure is visible in Logcat.

---

## Change 1 — Replace `newLatLngBounds` with Mercator zoom math

The center computation (largest-gap algorithm) is **correct and unchanged** — keep every line
from `val lats = ...` through `val lngSpan = 360.0 - largestGap`. Only replace what comes
after that.

```kotlin
// BEFORE — v9: MapScreen.kt lines ~185-196
val lngSpan = 360.0 - largestGap
val neLng = arcStart + lngSpan
val bounds = if (neLng <= 180.0) {
    // Non-crossing box, e.g. arcStart=-69, neLng=140 → literal 209° span.
    LatLngBounds(LatLng(latMin, arcStart), LatLng(latMax, neLng))
} else {
    // Antimeridian-crossing, e.g. arcStart=178, neLng=203 → NE.lng = 203-360 = -157
    LatLngBounds(LatLng(latMin, arcStart), LatLng(latMax, neLng - 360.0))
}
val padding = (context.resources.displayMetrics.density * 48).toInt() // 48dp inset
CameraUpdateFactory.newLatLngBounds(bounds, mapSizePx.width, mapSizePx.height, padding)

// AFTER — v10: compute center + zoom directly; no LatLngBounds needed
val lngSpan = 360.0 - largestGap
// Antimeridian-correct longitude center (v8 logic, unchanged)
var lngCenter = arcStart + lngSpan / 2.0
if (lngCenter > 180.0) lngCenter -= 360.0
if (lngCenter < -180.0) lngCenter += 360.0
val latCenter = (latMin + latMax) / 2.0

// Mercator zoom math — bypasses newLatLngBounds which under-zooms for spans > 180°.
// At zoom z: 1 longitude degree = 256·2^z / 360 px (linear).
//            1 Mercator radian  = 256·2^z / (2π) px (latitude is log-compressed).
// Solve for z in each dimension; take the smaller (most zoomed-out) value.
val paddingPx = (context.resources.displayMetrics.density * 32).toInt() // 32dp each side
val usableW = (mapSizePx.width  - 2 * paddingPx).coerceAtLeast(1)
val usableH = (mapSizePx.height - 2 * paddingPx).coerceAtLeast(1)
val lngZoom = ln(usableW * 360.0 / (256.0 * lngSpan)) / ln(2.0)
val mercY    = { deg: Double -> ln(tan(Math.PI / 4.0 + deg * Math.PI / 360.0)) }
val mercSpan = (mercY(latMax) - mercY(latMin)).coerceAtLeast(0.001)
val latZoom  = ln(usableH * 2.0 * Math.PI / (256.0 * mercSpan)) / ln(2.0)
val zoom     = minOf(lngZoom, latZoom).toFloat().coerceIn(1f, 18f)

Log.d("MapCamera", "v10: lngSpan=${"%.1f".format(lngSpan)}° " +
    "lngZ=${"%.2f".format(lngZoom)} latZ=${"%.2f".format(latZoom)} " +
    "→zoom=$zoom map=${mapSizePx.width}×${mapSizePx.height}px " +
    "center=(${"%+.1f".format(latCenter)},${"%+.1f".format(lngCenter)})")

CameraUpdateFactory.newLatLngZoom(LatLng(latCenter, lngCenter), zoom)
```

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`

---

## Change 2 — Surface errors from `cameraPositionState.move()`

```kotlin
// BEFORE — v9: MapScreen.kt lines ~202-203
val moved = runCatching { cameraPositionState.move(update) }.isSuccess
if (moved) cameraPositioned = true

// AFTER — v10: log the failure so it is visible in Logcat
val moveResult = runCatching { cameraPositionState.move(update) }
if (moveResult.isFailure) {
    Log.e("MapCamera", "v10: move() threw — camera not positioned", moveResult.exceptionOrNull())
}
val moved = moveResult.isSuccess
if (moved) cameraPositioned = true
```

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`

---

## Change 3 — Update imports

Remove `LatLngBounds` (no longer used). Add `Log`, `ln`, `tan`.

```kotlin
// REMOVE — no longer needed after v10
import com.google.android.gms.maps.model.LatLngBounds

// ADD
import android.util.Log
import kotlin.math.ln
import kotlin.math.tan
```

`Math.PI` is from `java.lang.Math` (always available, no import needed).

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`

---

## Scope

- **In:** zoom calculation and move() error surfacing in the multi-pin branch. Logging stays
  in until the map is confirmed stable (no user-visible change — debug log only).
- **Out:** single-location path (`newLatLngZoom(..., 6f)`) — unchanged and working.
- **Out:** center algorithm (largest-gap logic) — correct in v8/v9/v10, not touched.
- **Out:** `mapSizePx` / `onSizeChanged` / `cameraPositioned` scrim gate — unchanged.
- **No new strings. No ViewModel changes. No navigation changes.**

## Verification

After `assembleDebug`, open the Adventures map with 4+ globe-spanning pins and run:
```
adb logcat -s MapCamera
```
Expected output (Argentina/Iceland/Germany/Japan example):
```
v10: lngSpan=209.0° lngZ=2.45 latZ=3.87 →zoom=2.45 map=1080×1994px center=(+5.0,+35.5)
v10: move() result: success (no error line = good)
```
If `zoom` is above 3.5 for a globe-spanning set, or if the `move() threw` line appears,
report the full Logcat line — that is the next diagnostic.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Replace `newLatLngBounds` SDK call with direct Mercator zoom math | `MapScreen.kt` |
| 2 | Surface `move()` exceptions via `Log.e` instead of silently swallowing | `MapScreen.kt` |
| 3 | Remove `LatLngBounds` import; add `Log`, `ln`, `tan` | `MapScreen.kt` |
