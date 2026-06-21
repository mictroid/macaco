# Macaco — MapScreen: Fix map opening on North Atlantic ocean

When the map screen opens, the camera initialises at `LatLng(20.0, 0.0)` zoom 2 — a point in the
Gulf of Guinea off West Africa — while geocoding runs in the background. The user briefly (or, if
geocoding is slow, for several seconds) sees an ocean view before the camera animates to their
actual locations. Fix: show a loading scrim over the map until the first geocoded location is ready,
so the user never sees the default ocean position.

---

## Root cause

**File:** `ui/screens/MapScreen.kt`

```kotlin
// Current — hardcoded ocean default, visible until geocoding completes
val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(LatLng(20.0, 0.0), 2f)
}
```

`geocodeLocations` is called via `LaunchedEffect(locations)` after composition, and the camera
animation fires in a second `LaunchedEffect(mapLoaded, geocodedLocations)` — so there is always a
gap where the ocean default is visible.

---

## Fix

Add a `geocodingReady` flag that becomes true once `geocodedLocations` is non-empty (or when
entries have no locations at all, so the empty state shows immediately). Overlay a loading scrim
on the map while `!geocodingReady`.

**1. Add the ready flag** (just below the existing `var hasAnimated` declaration):

```kotlin
// True once we have at least one geocoded point, or there are no locations to geocode.
val geocodingReady = geocodedLocations.isNotEmpty() || locations.isEmpty()
```

**2. Wrap the `GoogleMap` + empty-state `Column` in a `Box` and add the loading overlay:**

In the existing outer `Box(modifier = Modifier.fillMaxSize())` that contains the `GoogleMap`,
add the scrim as the **last** child so it renders on top:

```kotlin
// Loading scrim — shown until geocoding returns the first result
if (!geocodingReady) {
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

The scrim uses a solid `background` color (not semi-transparent) so the ocean default beneath it
is completely hidden. Once `geocodedLocations` receives its first entry the flag flips, the scrim
disappears, and the `LaunchedEffect` animation zooms the camera to the user's locations.

---

## Required imports (add if missing)

```kotlin
import androidx.compose.material3.CircularProgressIndicator
```

`Box`, `Modifier.background`, and `Alignment` are already used in this file.

---

## Summary

| File | Change |
|------|--------|
| `ui/screens/MapScreen.kt` | Add `geocodingReady` flag; overlay an opaque loading scrim with a `CircularProgressIndicator` until the first geocoded location arrives, hiding the ocean default position |
