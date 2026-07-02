# Macaco — MapScreen: East Chevron Navigation Bar Clearance

Prevents the right (east) off-screen pin arrow from being hidden under the Android navigation
bar in landscape. Touches one file: `ui/screens/MapScreen.kt`.

---

## Fix: Respect navigation bar inset on east chevron

**Problem:** In landscape on the A53, Android's navigation bar (software button row or gesture
bar) appears on the right edge of the screen. The east chevron Box uses `.padding(end = 8.dp)`,
which puts it only 8dp from the screen edge — directly under the nav bar. The button is
technically rendered but the nav bar intercepts the touch, so it cannot be pressed.

**Fix:** Add `windowInsetsPadding` for the navigation bar's end (right) side before the 8dp
padding so the button stays inside the tappable area. Use
`WindowInsets.navigationBars.only(WindowInsetsSides.End)` — this avoids adding unnecessary
padding on the sides that don't have the nav bar.

```kotlin
// BEFORE (MapScreen.kt ~line 635)
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

// AFTER — add windowInsetsPadding BEFORE the end padding
Box(
    modifier = Modifier
        .align(Alignment.CenterEnd)
        .offset { IntOffset(0, yOffsetPx) }
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End))
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
```

**Imports to add if missing:**
```kotlin
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
```

**Why not `navigationBarsPadding()` on the parent Box?** The parent is the full-screen overlay
Box. Adding nav bar padding there would shift ALL overlays (the scrim, empty state, west arrow)
away from their edges — undesirable. Scoping it only to the east chevron keeps everything else
in place.

**West chevron unchanged.** The west (left) chevron uses `.padding(start = 8.dp)` and is on the
left edge where the nav bar never appears, so it doesn't need this fix.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End))` to east chevron | `MapScreen.kt` |
