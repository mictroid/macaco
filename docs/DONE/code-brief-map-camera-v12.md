# Macaco — MapScreen: Map camera v12 — diagnose SDK zoom clamp + globe-spanning UX

Adds one diagnostic log line and a user-facing message for the globe-spanning case.
Touches `MapScreen.kt` only.

---

## Background: what v11 confirmed and what remains

v11 fixed the density factor in the Mercator zoom formula. Based on the v11 comment at
line 177–182, the camera math now correctly computes zoom ≈ 1.2 for the user's 4-pin
globe-spanning set (Argentina −69°, Iceland −22°, Germany +13°, Japan +140° → 209° span).

**The remaining problem is a hard physics constraint, not a math bug.**

The Google Maps SDK silently clamps `move()` to a minimum zoom determined by the map's
*height in physical pixels*: it will not zoom out far enough to render past the poles
(which would show gray borders). On a full-height portrait A53 (map area ≈ 1900px tall),
this floor is approximately zoom 2.0. At zoom 2.0 on the A53 (1080px wide, density 2.625):

```
visible longitude = (1080 / (256 × 2.625 × 2²)) × 360° ≈ 144°
```

The user's pin set spans 209°. 209 > 144, so **no zoom level allowed by the SDK on a
portrait phone can frame all 4 pins simultaneously**. This is not fixable with more zoom
math — it is the Mercator projection's pole constraint.

v11 confirmed this on A53/vc43 via Logcat: requested zoom 1.2 → SDK applied ~2.0.

**What this brief does:**
1. Log the *actual applied zoom* after `move()` so the clamp is visible in Logcat.
2. Detect when clamping occurred (computed zoom < applied zoom by > 0.2).
3. Show a one-time subtitle update on the map header: "Swipe to see all pins" — so the
   user understands the map is correct but their trip is wider than the screen.

---

## Change 1 — Log the actual applied zoom after `move()`

```kotlin
// BEFORE — MapScreen.kt lines ~234-239
val moveResult = runCatching { cameraPositionState.move(update) }
if (moveResult.isFailure) {
    Log.e("MapCamera", "v11: move() threw — camera not positioned", moveResult.exceptionOrNull())
}
val moved = moveResult.isSuccess
if (moved) cameraPositioned = true

// AFTER — v12: also log the zoom the SDK actually applied
val moveResult = runCatching { cameraPositionState.move(update) }
if (moveResult.isFailure) {
    Log.e("MapCamera", "v12: move() threw — camera not positioned", moveResult.exceptionOrNull())
}
val moved = moveResult.isSuccess
if (moved) {
    cameraPositioned = true
    // Read back what the SDK actually applied — may differ from `zoom` if the SDK
    // clamped it (portrait Mercator floor: ~zoom 2.0 on a full-height phone).
    val appliedZoom = cameraPositionState.position.zoom
    Log.d("MapCamera", "v12: applied zoom=$appliedZoom (requested=$zoom) " +
        "clamp=${if (appliedZoom > zoom + 0.2f) "YES — SDK floor active" else "no"}")
    globeSpanning = appliedZoom > zoom + 0.2f
}
```

`globeSpanning` is a new `var` declared alongside `cameraPositioned`:

```kotlin
// ADD alongside the existing cameraPositioned declaration (line ~143)
var cameraPositioned by remember { mutableStateOf(false) }
var globeSpanning  by remember { mutableStateOf(false) }
```

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`

---

## Change 2 — Show "Swipe to see all pins" subtitle when globe-spanning

The map header already shows "X of Y locations mapped" as a subtitle. When `globeSpanning`
is true, append a second line so the user knows to pan.

Find the subtitle `Text` in the map header (the "X of Y locations mapped" line) and add a
conditional line below it:

```kotlin
// AFTER the existing "X of Y locations mapped" Text — inside the header Column/Box
if (globeSpanning) {
    Text(
        stringResource(R.string.map_globe_spanning_hint),
        style = MaterialTheme.typography.labelSmall,
        color = SplashGold.copy(alpha = 0.75f),
        letterSpacing = 0.5.sp
    )
}
```

New string key (add to all 11 language files):

| Key | EN value |
|-----|----------|
| `map_globe_spanning_hint` | Swipe to see all pins |

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`
File: `app/src/main/res/values/strings.xml` (+ all 10 language variants)

---

## Scope

- **In:** Post-move applied-zoom log; `globeSpanning` flag; header hint text.
- **Out:** Any further attempt to lower the SDK zoom floor — the Mercator pole constraint
  is fundamental and cannot be overridden. Do not add more zoom math.
- **Out:** Changing the camera position or center — both are correct in v11.
- **Out:** Single-location or small-span paths — unchanged.

---

## Verification

After `assembleDebug`, open the Adventures map with the 4 globe-spanning pins and run:

```
adb logcat -s MapCamera
```

Expected output:
```
v11: lngSpan=209.0° density=2.62 lngZ=1.22 latZ=4.xx →zoom=1.22 map=1080×1900px center=(+7.5,+35.2)
v12: applied zoom=2.0 (requested=1.22) clamp=YES — SDK floor active
```

- If `clamp=YES` appears: the globe-spanning hint should be visible in the header. ✓
- If `clamp=no` appears: all pins should be visible on screen and no hint shown. ✓
- If the `v11:` line does not appear at all: `move()` may be firing before `mapSizePx`
  is set — check the `mapSizePx.width > 0` guard and the `cameraPositioned` flag sequence.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `globeSpanning` flag; log applied zoom after `move()`; detect SDK clamp | `MapScreen.kt` |
| 2 | Show "Swipe to see all pins" subtitle in header when `globeSpanning = true` | `MapScreen.kt` |
| 3 | New string `map_globe_spanning_hint` ×11 languages | `strings.xml` |
