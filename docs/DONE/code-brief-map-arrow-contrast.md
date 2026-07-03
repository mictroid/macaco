# Macaco — MapScreen: Chevron Arrow Contrast in Light Mode

In light mode the ◀▶ navigation chevrons are nearly invisible against light blue ocean map
tiles. Fixes both west and east arrows in `MapScreen.kt`.

---

## Chevron background: primaryContainer → primary

**Problem:** Both chevrons use `primaryContainer.copy(alpha = 0.85f)` as their background. In
light mode `primaryContainer` is a very pale, desaturated teal — it blends into the light blue
ocean tiles and is barely visible. In dark mode it's also muted. The icon tint `onPrimaryContainer`
follows the same low-contrast token.

**Fix:** Swap to `primary` (the saturated, opaque brand colour) for the background and `onPrimary`
(white) for the icon tint. Add a 4 dp drop shadow so the circle pops off the map tile regardless
of what colour the tile beneath happens to be. No hex colours, no hardcoded opacities.

```
Before (light mode)                After (light mode)
┌──────────────┐                  ┌──────────────┐
│  pale teal   │ ← low contrast   │  dark teal   │ ← high contrast
│      ‹       │   on ocean       │      ‹       │   on any tile
│  (alpha 85%) │                  │  + shadow    │
└──────────────┘                  └──────────────┘
```

### West chevron (lines ~622–648 of `MapScreen.kt`)

```kotlin
// BEFORE
Box(
    modifier = Modifier
        .align(Alignment.CenterStart)
        .offset { IntOffset(0, yOffsetPx) }
        .padding(start = 8.dp)
        .size(36.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f))
        .clickable { ... },
    contentAlignment = Alignment.Center
) {
    Icon(
        imageVector = Icons.Default.ChevronLeft,
        contentDescription = stringResource(R.string.map_globe_spanning_hint),
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(22.dp)
    )
}

// AFTER — shadow before clip so the shadow renders outside the circle boundary
Box(
    modifier = Modifier
        .align(Alignment.CenterStart)
        .offset { IntOffset(0, yOffsetPx) }
        .padding(start = 8.dp)
        .size(36.dp)
        .shadow(4.dp, CircleShape)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primary)
        .clickable { ... },
    contentAlignment = Alignment.Center
) {
    Icon(
        imageVector = Icons.Default.ChevronLeft,
        contentDescription = stringResource(R.string.map_globe_spanning_hint),
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(22.dp)
    )
}
```

### East chevron (lines ~660–689 of `MapScreen.kt`)

```kotlin
// BEFORE
Box(
    modifier = Modifier
        .align(Alignment.CenterEnd)
        .offset { IntOffset(0, yOffsetPx) }
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End))
        .padding(end = 8.dp)
        .size(36.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f))
        .clickable { ... },
    contentAlignment = Alignment.Center
) {
    Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = stringResource(R.string.map_globe_spanning_hint),
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(22.dp)
    )
}

// AFTER
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
        .clickable { ... },
    contentAlignment = Alignment.Center
) {
    Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = stringResource(R.string.map_globe_spanning_hint),
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(22.dp)
    )
}
```

No import changes needed — `shadow` is in `androidx.compose.ui.draw` which is already present.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | West chevron: `primaryContainer(alpha=0.85f)` → `primary`; `onPrimaryContainer` → `onPrimary`; add `shadow(4.dp, CircleShape)` before `clip` | `MapScreen.kt` |
| 2 | East chevron: same colour + shadow change (nav bar inset padding unchanged) | `MapScreen.kt` |
