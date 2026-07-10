# Macaco — Adventures Tab: Fix the White "Flash" When Navigating to the Map

Diagnosed from a screen recording of the A53 (`Screen_Recording_20260710_184901_Macaco.mp4`,
frame-stepped with ffmpeg), not just from reading the source — see the breakdown below before
changing anything. Two concrete visual fixes, both touching only
`app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`.

## What the recording actually shows

Frame-stepping the recording at ~54fps around the tab switch (Journal → Adventures):

- **t≈1.0s:** user taps the Adventures tab; the Journal list cross-fades out (NavHost's default
  transition — not something this brief touches).
- **t≈1.15s to t≈2.17s (≈1 second):** screen shows a **plain, stark-white/pale blank panel** —
  MapScreen's loading scrim (`Box.background(MaterialTheme.colorScheme.background)` at ~line
  581-589) — with only a small `CircularProgressIndicator` in the middle. This is a hard,
  jarring color cut: the header above it is still the dark teal brand gradient, so there's an
  ugly seam, and the panel below it is a flat near-white that has nothing to do with the app's
  branding. **Two of the off-screen-pin chevron buttons (green circles with arrows) also render
  during this blank period**, before any map or pin is visible — see Change 2.
- **t≈2.17s:** the fully-formed, already-correctly-framed map hard-cuts into view. (Good news:
  there's no secondary jump/re-center after this — the "fit all pins" positioning itself is
  landing correctly and instantly once revealed. This part of the *positioning* logic, patched
  across the `map-camera-v*` / `map-default-position-v*` briefs, is not the problem here.)

So "flashes very briefly and quickly" is best read as: a ~1-second stark-white blank panel
sandwiched between two colored screens (the Journal list and the teal-and-map Adventures
screen), which reads as a "flash" even though it isn't a single-frame glitch. Two things make it
feel worse than a plain loading spinner should: (1) the scrim doesn't match the brand teal, so
it's a harsh color-block cut rather than a continuation of what's already on screen, and (2) the
chevron buttons popping in early adds motion/noise to what should just be a quiet loading state.

---

## Change 1 — Loading scrim: match the brand teal instead of cutting to plain white

**Problem:** The scrim's background is `MaterialTheme.colorScheme.background` (a near-white/pale
tone in the app's default theme), while the header directly above it is the dark teal
`macacoBrandBackground()` gradient. The hard seam between them is what reads as a "flash" —
the eye registers an abrupt cut to a completely different, brand-unrelated color, not just "a
spinner is showing."

**Fix:** Use the same `macacoBrandBackground()` brush the header already uses, so the scrim reads
as a continuation of the branded teal rather than a cut to blank white. Since the background gets
much darker, swap the spinner's color from `MaterialTheme.colorScheme.primary` (likely to wash
out against dark teal) to `SplashGoldBright` — the gold already used for every other accent in
this file (`SplashGold.copy(alpha = ...)` appears throughout the header trailing text) and in
`MacacoBrandBlock`'s wordmark.

### BEFORE (`MapScreen.kt`, ~line 578-590)

```kotlin
            // Loading scrim — opaque, so the arbitrary default camera position is never seen.
            // Drops away once the camera has been positioned over the user's places (or the timeout
            // fires). (When there are no locations, the empty-state above covers the map instead.)
            if (locations.isNotEmpty() && !cameraPositioned && !revealTimedOut) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
```

### AFTER

```kotlin
            // Loading scrim — opaque, so the arbitrary default camera position is never seen.
            // Drops away once the camera has been positioned over the user's places (or the timeout
            // fires). (When there are no locations, the empty-state above covers the map instead.)
            // Uses the same brand teal as the header (not colorScheme.background) so this reads as
            // a continuation of the branded header rather than a hard cut to blank white — that cut
            // was what made the ~1s load feel like a "flash" on navigation (diagnosed from a screen
            // recording on the A53; see brief intro).
            if (locations.isNotEmpty() && !cameraPositioned && !revealTimedOut) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(macacoBrandBackground()),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SplashGoldBright)
                }
            }
```

`macacoBrandBackground()` and `SplashGoldBright` are both `internal` top-level declarations in
`SplashScreen.kt`, same package (`ui.screens`) as `MapScreen.kt` — no import needed, and this is
the exact same brush already used for the header `Box` a few dozen lines above in this file.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`

---

## Change 2 — Off-screen pin chevrons render before the map is actually positioned

**Problem:** The reactive off-screen-pin tracker (`LaunchedEffect(cameraPositionState.position,
geocodedLocations, mapSizePx)`, ~line 374-398) starts computing `offScreenWestPins` /
`offScreenEastPins` as soon as `mapSizePx` is measured and `geocodedLocations` is non-empty —
neither of which waits for `cameraPositioned`. Since the camera starts at the hardcoded default
(`LatLng(20.0, 0.0)`, zoom 2f) before the "fit all pins" move happens, this LaunchedEffect runs
once against that arbitrary default framing and, depending on where the user's pins actually are,
often finds pins "off-screen" relative to it — so the chevron buttons appear during the blank
loading scrim, before there's any map to navigate on. Visible in the recording: both chevrons are
on screen during the all-white loading panel.

**Fix:** Add `cameraPositioned` to the guard clause so the tracker doesn't populate (and the
chevrons don't render) until the real fitted camera position is in place.

### BEFORE (`MapScreen.kt`, ~line 374-380)

```kotlin
    LaunchedEffect(cameraPositionState.position, geocodedLocations, mapSizePx) {
        if (mapSizePx.width <= 0 || geocodedLocations.isEmpty()) return@LaunchedEffect

        val pos = cameraPositionState.position
```

### AFTER

```kotlin
    LaunchedEffect(cameraPositionState.position, geocodedLocations, mapSizePx, cameraPositioned) {
        // Wait for the real "fit all pins" camera position — otherwise this runs once against the
        // arbitrary default framing (LatLng(20,0), zoom 2) before the fit-all move happens, and the
        // chevrons pop into view during the loading scrim instead of only once there's an actual
        // map to navigate on.
        if (!cameraPositioned || mapSizePx.width <= 0 || geocodedLocations.isEmpty()) return@LaunchedEffect

        val pos = cameraPositionState.position
```

`cameraPositioned` is already declared earlier in the same composable (~line 194) — no new state,
just added to the `LaunchedEffect` key list and the guard.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`

---

## Out of scope

- **Not persisting/caching the map view or camera position across tab switches.** That would
  remove the load entirely on repeat visits, but it directly conflicts with the deliberate design
  decision in `docs/DONE/code-brief-profile-adventures-collapsible-header.md` — re-entering
  Adventures is meant to re-show fresh "X of Y locations mapped" context each visit, not resume
  exactly where you left off. The ~1 second itself (map attach + geocoding + layout) isn't being
  shortened by this brief — Changes 1 & 2 only fix how it *looks* while it happens. If that's ever
  worth revisiting, it's a separate, deliberate brief — not bundled into this bug-fix pass.
- **Not touching the NavHost cross-fade transition itself** (the Journal→Adventures fade at the
  very start of the sequence) — that's Navigation-Compose's default tab-switch behavior, shared
  by all three bottom-nav tabs, and isn't where the "flash" actually comes from (the blank scrim
  color and the premature chevrons are).
- **Not re-tuning `revealTimedOut`'s 8-second safety timeout** — unrelated to this issue, still
  needed as a fallback for offline/geocode-failure cases.
- **Not adding on-device timing diagnostics.** Considered (to find out which stage of the ~1s
  load dominates), but dropped — Changes 1 & 2 already address the actual complaint, and digging
  into raw load time is a separate investigation, not something this bug fix needs to unblock.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Scrim uses `macacoBrandBackground()` + `SplashGoldBright` spinner instead of `colorScheme.background` + `primary` — removes the hard cut to blank white | `MapScreen.kt` |
| 2 | Gate the off-screen-pin chevron tracker on `cameraPositioned` so the arrow buttons don't render before the map is actually positioned | `MapScreen.kt` |
