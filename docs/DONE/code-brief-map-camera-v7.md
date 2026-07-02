# Macaco — MapScreen: Map Box height measurement bug (v7)

Fixes pins appearing behind the branded header on tablet (portrait) and any tall-header
configuration. The root cause is `Modifier.fillMaxSize()` on the map Box inside a Column —
it measures to the FULL Column height (not remaining height after the header), making
`mapSizePx.height` larger than the actually-visible map area. The camera center is then placed
too low, pushing northern pins above the top of the map Box and visually behind the header.
Touches one file: `ui/screens/MapScreen.kt`.

---

## Root cause

```
Column(fillMaxSize, height = 720dp) {
    Box  ← teal header, intrinsic height = 146dp, placed at y = 0
    Box  ← map, fillMaxSize → measured height = 720dp (!), placed at y = 146dp
}
```

Because `fillMaxSize()` fills the PARENT's max constraint (720dp), not the remaining space
(574dp), `mapSizePx.height` = 720dp. The camera's initial move centers the target lat/lng
at pixel y = 360dp from the top of the map Box. The map Box is placed at screen y = 146dp,
so the camera center appears at screen y = 146 + 360 = 506dp (below the true visible midpoint
of 146 + 287 = 433dp). Northern pins are consequently positioned above y = 0 in the map Box
coordinate system → they render at screen y < 146dp → inside/behind the header Box area.

The same incorrect `mapSizePx.height` also inflates the `arrowVerticalFraction()` offsets for
the east/west chevron buttons.

---

## Fix: Replace `fillMaxSize()` with `weight(1f).fillMaxWidth()`

`Modifier.weight(1f)` in a Column child sets the child's height to the REMAINING space after
all non-weighted siblings — exactly the map's truly visible height.

```kotlin
// BEFORE (MapScreen.kt ~line 500)
Box(modifier = Modifier
    .fillMaxSize()
    .onSizeChanged { mapSizePx = it }) {

// AFTER
Box(modifier = Modifier
    .weight(1f)
    .fillMaxWidth()
    .onSizeChanged { mapSizePx = it }) {
```

**Result:**
- `mapSizePx.height` = 720dp − 146dp = 574dp (true visible map height)
- Camera center at y = 287dp from map Box top = screen y = 146 + 287 = 433dp ✓
- Northern pins at ≤ 287dp above center appear at map Box y ≥ 0 = visible ✓
- The 32dp padding already added in the zoom math (`paddingPx`) ensures pins don't hug the edges

**Why header height varies:** The portrait header (monkey icon + "macaco" + "Adventures" +
location count + swipe hint) is ~146dp on tablet. The landscape compact header (slim row) is
~40dp. In both cases `weight(1f)` correctly measures whatever remains, so no hardcoded offsets
are needed.

**No other changes required.** `mapSizePx` is the only consumer of this measurement — the zoom
math, camera centering, and arrow vertical offsets all use it correctly once it holds the right
value.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Map Box: `fillMaxSize()` → `weight(1f).fillMaxWidth()` so `mapSizePx` measures visible map height | `MapScreen.kt` |
