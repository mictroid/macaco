# Macaco — Profile & Adventures: Add Collapsible Header

**Depends on `docs/code-brief-macaco-brand-header-consistency.md` — implement that one first.**
This brief's BEFORE snippets assume `MacacoBrandBlock` already exists
(`ui/components/MacacoBrandBlock.kt`) and that `ProfileScreen.kt` / `MapScreen.kt` already call it
per that brief's Change 3 and Change 4. If that brief hasn't landed yet, land it first — otherwise
these diffs won't line up with the file.

Journal and Help & About already collapse their header to icon-only once you scroll away from the
top. Profile and Adventures don't have that at all today. Two additions, each with its own
trigger:

1. **Profile** — collapse on scroll, same mechanism as Journal/Help & About (`scrollState.value >
   24`). In portrait there's currently nothing to scroll, so this is inert there today — it only
   starts doing anything once the page has enough content to scroll, in whichever orientation
   that happens to be true. No orientation gating needed; it just naturally activates where it's
   needed.
2. **Adventures** — collapse the instant the user starts panning/zooming the map, in both
   portrait and landscape. Trigger is `cameraPositionState.isMoving` (already in scope at
   `MapScreen.kt:183`), but **latched, not live-bound**: the first time `isMoving` goes true, flip
   a `hasMovedMap` flag to `true` and never back — the header then stays collapsed for the rest of
   that visit to the screen. (Binding directly to the live `isMoving` value would make the header
   pop back open every time a drag gesture settles between pans, which looks jittery. If a live
   toggle turns out to feel better on-device — closer to how some map apps transiently hide chrome
   only *during* a gesture — that's a one-line change: pass `cameraPositionState.isMoving` straight
   into `collapsed` instead of the latch. Worth an on-device comparison before committing either
   way.)

---

## Change 1 — ProfileScreen: collapse on scroll

**Problem:** No collapse at all today. Once the header-consistency brief lands, Profile's content
`Column` already scrolls (`verticalScroll(rememberScrollState())` — the scroll state is currently
created inline and thrown away, so nothing can read its position).

**Fix:** Hoist the scroll state into a named `val`, derive `collapsed` from it exactly like
Journal/Help & About do, and pass it through to `MacacoBrandBlock`.

```
Expanded (not scrolled):        Collapsed (scrolled past ~24px, either orientation):
┌────────────────────────┐      ┌────────────────────────┐
│      [48dp icon]        │      │      [48dp icon]       │
│       macaco             │      └────────────────────────┘
└────────────────────────┘      (avatar/stats/etc. below, unchanged)
```

### BEFORE (post-header-consistency-brief state, ~lines 306–341)

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    val isLandscape = LocalConfiguration.current.screenHeightDp < 480
    // Branded banner: splash teal radial with the gold "macaco" wordmark.
    // The avatar below overlaps its bottom edge.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(macacoBrandBackground())
            .statusBarsPadding()
    ) {
        // Profile is a bottom-nav tab (reached via the nav bar), so a back arrow here
        // is redundant — the brand block stands alone, centred. No page-label subtitle,
        // to match Journal's header exactly.
        MacacoBrandBlock(
            isLandscape = isLandscape,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 4.dp, bottom = if (isLandscape) 12.dp else 32.dp)
        )
    }
```

### AFTER

```kotlin
val profileScrollState = rememberScrollState()
Column(
    modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .verticalScroll(profileScrollState),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    val isLandscape = LocalConfiguration.current.screenHeightDp < 480
    // Collapses once the page scrolls — currently only reachable in states where the content
    // (stats + settings buttons) overflows the screen; inert (never true) when everything fits,
    // which today means portrait on a typical phone.
    val collapsed by remember { derivedStateOf { profileScrollState.value > 24 } }
    // Branded banner: splash teal radial with the gold "macaco" wordmark.
    // The avatar below overlaps its bottom edge.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(macacoBrandBackground())
            .statusBarsPadding()
            .animateContentSize()
    ) {
        // Profile is a bottom-nav tab (reached via the nav bar), so a back arrow here
        // is redundant — the brand block stands alone, centred. No page-label subtitle,
        // to match Journal's header exactly.
        MacacoBrandBlock(
            isLandscape = isLandscape,
            collapsed = collapsed,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(
                    top = 4.dp,
                    bottom = if (collapsed) 8.dp else if (isLandscape) 12.dp else 32.dp
                )
        )
    }
```

**Heads up for whoever implements this:** the avatar a few lines below is pulled up with a fixed
`.offset(y = (-32).dp)` to overlap the banner's bottom edge (tuned for the *expanded* banner
height). Once the banner can also render at its much-shorter collapsed height, check that overlap
still looks right when collapsed — it may want to become `if (collapsed) (-8).dp else (-32).dp`
or similar. This is a visual call best made on-device, not guessed from source.

Add imports: `androidx.compose.runtime.derivedStateOf`, `androidx.compose.animation.animateContentSize`
(if not already present from the header-consistency brief).

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/ProfileScreen.kt`

---

## Change 2 — MapScreen (Adventures): collapse on map movement

**Problem:** No collapse today, in either orientation. The header always shows its full
landscape-compact-row or portrait-stacked form, permanently eating vertical space even once the
user is actively panning/zooming the map — the one screen where map area is the actual point.

**Fix:** Add a one-way `hasMovedMap` latch driven by `cameraPositionState.isMoving`, and collapse
`MacacoBrandBlock` once it flips. Since the composable already branches on `collapsed` /
`isLandscape` internally, this is also a chance to simplify: the header-consistency brief's Change
3 kept MapScreen's own `if (isLandscape) { ... } else { ... }` wrapper with two separate
`MacacoBrandBlock` calls inside; collapse that into a single call now that a third state
(collapsed) needs handling too — one call site is easier to reason about than three.

### BEFORE (post-header-consistency-brief state, ~lines 405–510+)

```kotlin
Column(modifier = Modifier.fillMaxSize()) {
    // In landscape on phones (short screen) the tall centered brand block eats ~120dp of map;
    // collapse it to a single slim row. Tablets stay tall (~750dp+) and keep the full header.
    val isLandscape = LocalConfiguration.current.screenHeightDp < 480
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(macacoBrandBackground())
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    ) {
      if (isLandscape) {
        MacacoBrandBlock(
            isLandscape = true,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = " · " + stringResource(R.string.map_adventures_title),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f)
            )
            if (locations.isNotEmpty()) {
                val mappedCount = locations.count { it in geocodedLocations }
                Text(
                    text = " · " + stringResource(R.string.map_locations_mapped, mappedCount, locations.size),
                    color = SplashGold.copy(alpha = 0.70f),
                    fontSize = 12.sp,
                    fontFamily = MacacoFontFamily
                )
            }
            if (globeSpanning) {
                Text(
                    text = " · " + stringResource(R.string.map_globe_spanning_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = SplashGold.copy(alpha = 0.75f),
                    letterSpacing = 0.5.sp
                )
            }
        }
      } else {
        MacacoBrandBlock(
            isLandscape = false,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 2.dp, bottom = 10.dp)
        ) {
            Text(
                stringResource(R.string.map_adventures_title),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
            if (locations.isNotEmpty()) {
                val mappedCount = locations.count { it in geocodedLocations }
                Text(
                    stringResource(R.string.map_locations_mapped, mappedCount, locations.size),
                    color = SplashGold.copy(alpha = 0.70f)
                )
            }
            // ...rest of portrait trailing content (globe-spanning hint etc.) unchanged
        }
      }
    }
    ...
    GoogleMap(
        modifier = ...,
        cameraPositionState = cameraPositionState,
        ...
    )
```

### AFTER

```kotlin
Column(modifier = Modifier.fillMaxSize()) {
    // In landscape on phones (short screen) the tall centered brand block eats ~120dp of map;
    // collapse it to a single slim row. Tablets stay tall (~750dp+) and keep the full header.
    val isLandscape = LocalConfiguration.current.screenHeightDp < 480

    // Once the user starts panning/zooming, collapse the header down to the icon and keep it
    // that way for the rest of this visit to the screen — maximizes map space during active
    // exploration, in either orientation. Latched (not live-bound to isMoving) so the header
    // doesn't pop back open every time a drag settles between pans.
    var hasMovedMap by remember { mutableStateOf(false) }
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving) hasMovedMap = true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(macacoBrandBackground())
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .animateContentSize()
    ) {
        MacacoBrandBlock(
            isLandscape = isLandscape,
            collapsed = hasMovedMap,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(
                    top = if (isLandscape) 4.dp else 2.dp,
                    bottom = if (hasMovedMap) 8.dp else if (isLandscape) 4.dp else 10.dp
                ),
            landscapeTrailing = {
                Text(
                    text = " · " + stringResource(R.string.map_adventures_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
                if (locations.isNotEmpty()) {
                    val mappedCount = locations.count { it in geocodedLocations }
                    Text(
                        text = " · " + stringResource(R.string.map_locations_mapped, mappedCount, locations.size),
                        color = SplashGold.copy(alpha = 0.70f),
                        fontSize = 12.sp,
                        fontFamily = MacacoFontFamily
                    )
                }
                if (globeSpanning) {
                    Text(
                        text = " · " + stringResource(R.string.map_globe_spanning_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = SplashGold.copy(alpha = 0.75f),
                        letterSpacing = 0.5.sp
                    )
                }
            },
            portraitTrailing = {
                Text(
                    stringResource(R.string.map_adventures_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
                if (locations.isNotEmpty()) {
                    val mappedCount = locations.count { it in geocodedLocations }
                    Text(
                        stringResource(R.string.map_locations_mapped, mappedCount, locations.size),
                        color = SplashGold.copy(alpha = 0.70f)
                    )
                }
                // ...rest of portrait trailing content (globe-spanning hint etc.) unchanged,
                // still inside this lambda / ColumnScope
            }
        )
    }
```

`cameraPositionState` is declared earlier in the same composable (line ~183), so it's already in
scope at this point — no need to move or duplicate its declaration.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`

---

## Out of scope

- Not re-implementing the latch as a toggle the user can tap to re-expand — if that turns out to
  be wanted after trying the latch on-device, that's a small follow-up (tap the collapsed icon →
  reset `hasMovedMap` to `false`).
- Not touching Settings — it has no scrollable content and no map, so neither trigger applies.
- Not persisting `hasMovedMap` across navigation (e.g. via `rememberSaveable`) — it resets to
  expanded every time the user re-enters the Adventures tab, which seems like the right default
  (you get the full "X of Y locations mapped" context again each visit) but flag if that's wrong.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Collapse on scroll (hoisted scroll state + `derivedStateOf`, threshold matches Journal/Help & About) | `ProfileScreen.kt` |
| 2 | Collapse on first map movement (latched `isMoving`), unify the two `MacacoBrandBlock` call sites into one | `MapScreen.kt` |
