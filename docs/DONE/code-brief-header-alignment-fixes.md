# Macaco — Two Brand-Header Bugs: Map Landscape Centering & Profile Header Disappearing

Two independent header bugs reported from the current build. Touches `MapScreen.kt` (Change 1)
and `ProfileScreen.kt` (Change 2). No shared code — implement in either order.

---

## Change 1 — MapScreen: collapsed icon isn't centred in landscape

**Problem:** In landscape, once the header collapses (`hasMovedMap = true`), the macaco icon
looks off-centre relative to the map behind it. Root cause: the header `Box` reserves horizontal
clearance for Android's side navigation bar via
`.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))`, then centres
the icon *within that inset-safe area* — which is narrower than the full screen width. But the
`GoogleMap` below it (`app/.../MapScreen.kt` ~line 511-514) is edge-to-edge with no matching
inset. So the icon centres on a different axis than the full-bleed map it sits on top of, and
looks shifted left of the map's true visual centre. This is invisible when expanded because the
trailing wordmark/label content balances it out, but jumps out once collapsed to a bare icon.

The inset was added deliberately (see `docs/DONE/code-brief-map-nav-bar.md`) — the *expanded*
header's trailing text (" · Adventures · 4 of 6 mapped" etc.) genuinely needs that clearance so it
doesn't run under the nav bar. Only the *collapsed* state (icon alone, nothing extending
sideways) has no reason to avoid that space — so the fix scopes the inset to the expanded state
only.

```
BEFORE — collapsed icon centred in the inset-safe area (left of true centre):    AFTER — collapsed icon centred on the true screen width (matches map):
┌────────────────────────────────┬──────┐                                       ┌─────────────────────────────────────┬──────┐
│      [icon]                     │ nav  │                                       │              [icon]                  │ nav  │
│   (centred within padded area,  │ bar  │                                       │   (centred on full width, ignoring   │ bar  │
│    visually left of map centre) │      │                                       │    the inset — nothing extends       │      │
├────────────────────────────────┴──────┤                                       │    sideways to clip)                 │      │
│                                        │                                       ├─────────────────────────────────────┴──────┤
│         edge-to-edge map              │                                       │           edge-to-edge map                  │
└────────────────────────────────────────┘                                     └───────────────────────────────────────────────┘
```

### BEFORE (`MapScreen.kt`, ~line 433-451)

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .background(macacoBrandBackground())
        .statusBarsPadding()
        // Match the page content's horizontal insets (side nav bar / cutout) so the
        // centred brand block stays centred with the rest of the page in landscape.
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
                landscapeTrailing = { /* ...unchanged... */ },
                portraitTrailing = { /* ...unchanged... */ }
            )
        }
```

### AFTER

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .background(macacoBrandBackground())
        .statusBarsPadding()
        // Only the EXPANDED header's trailing wordmark/label content extends sideways far
        // enough to need nav-bar clearance (see docs/DONE/code-brief-map-nav-bar.md). Once
        // collapsed, the header is just a centred icon with nothing to clip — and the map
        // below is edge-to-edge with no matching inset — so skip the inset there and centre
        // on the TRUE screen width instead. Keeping the inset in both states was centring the
        // collapsed icon on a narrower axis than the full-bleed map behind it, making it look
        // shifted left of centre.
        .then(
            if (hasMovedMap) Modifier
            else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        )
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
                landscapeTrailing = { /* ...unchanged... */ },
                portraitTrailing = { /* ...unchanged... */ }
            )
        }
```

No new imports — `WindowInsets`, `WindowInsetsSides`, `windowInsetsPadding` are already imported
in this file.

**Verify on-device:** with a 3-button nav bar in landscape, confirm the collapsed icon (now
centred on full width) doesn't sit close enough to the nav bar to make it feel cramped — it
shouldn't, since it's centred rather than edge-anchored, but worth a look on the actual device
used for the `map-nav-bar` brief (Galaxy A53).

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`

---

## Change 2 — ProfileScreen: collapsed header scrolls away instead of staying pinned

**Problem:** Journal and Help & About render their brand header as a Scaffold `topBar` — pinned
above the scrollable body, so once collapsed it stays visible as a persistent slim icon bar no
matter how far the user scrolls. Profile's header was never wired the same way: the banner `Box`
(`ProfileScreen.kt` ~line 334-351) lives *inside* the same scrollable `Column` as the avatar,
stats, and action buttons (`docs/DONE/code-brief-profile-adventures-collapsible-header.md`
implemented the collapse-on-scroll *shrink*, but never hoisted the banner out of the scroll flow
the way Journal/Help & About do). So on a tall/landscape Profile page — enough content to
scroll — continuing to scroll doesn't just shrink the header to its icon-only state, it scrolls
the (now-small) header off the top of the screen entirely, same as any other content. That's the
"header collapses and disappears" behaviour reported — not the intended persistent icon bar.

**Fix:** Move the banner `Box` out of the scrollable `Column` and into Scaffold's `topBar` slot,
matching `JournalListScreen.kt` / `HelpAboutScreen.kt`. Hoist `profileScrollState`, `isLandscape`,
`collapsed`, and `bannerBottomPadding` above the `Scaffold` call so both the `topBar` lambda and
the body content can read them.

```
BEFORE — banner is regular scrolling content:        AFTER — banner is a pinned Scaffold topBar:
┌───────────────────────────┐                        ┌───────────────────────────┐
│ [icon] macaco   (banner)   │ ─┐ scrolls with        │ [icon]  (topBar — pinned)  │ ← always on screen;
├───────────────────────────┤  │ everything else,      ├───────────────────────────┤   shrinks to icon-only
│ avatar / stats / actions   │  │ eventually scrolls    │ avatar / stats / actions  │   as body scrolls, never
│ (scrollable body)          │ ─┘ fully off-screen      │ (scrollable body)         │   scrolls off entirely
└───────────────────────────┘                        └───────────────────────────┘
```

### BEFORE (`ProfileScreen.kt`, ~line 304-352)

```kotlin
    Scaffold(
        // The branded teal banner runs edge-to-edge under the status bar; opt out of the default
        // top inset and re-apply it inside the banner instead.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
            // Single source of truth for the banner's bottom clearance — reused below for the content
            // pull-up offset so the two can never drift out of sync (that drift was the landscape/collapsed
            // overlap bug: offset was hardcoded to -32dp while this value could be as low as 8dp).
            val bannerBottomPadding = if (collapsed) 8.dp else if (isLandscape) 12.dp else 32.dp
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
                        .padding(top = 4.dp, bottom = bannerBottomPadding)
                )
            }

            // Content pulled up so the avatar overlaps the banner's bottom edge — offset now tracks
            // bannerBottomPadding instead of a hardcoded -32dp, so it can never pull the avatar further up
            // than the banner actually reserved (which was landing it on top of the wordmark in landscape).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = -bannerBottomPadding)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // ...avatar, name, stats card, etc. — unchanged...
```

### AFTER

```kotlin
    val profileScrollState = rememberScrollState()
    val isLandscape = LocalConfiguration.current.screenHeightDp < 480
    // Collapses once the page scrolls — currently only reachable in states where the content
    // (stats + settings buttons) overflows the screen; inert (never true) when everything fits,
    // which today means portrait on a typical phone.
    val collapsed by remember { derivedStateOf { profileScrollState.value > 24 } }
    // Single source of truth for the banner's bottom clearance — reused below for the content
    // pull-up offset so the two can never drift out of sync (that drift was the landscape/collapsed
    // overlap bug: offset was hardcoded to -32dp while this value could be as low as 8dp).
    val bannerBottomPadding = if (collapsed) 8.dp else if (isLandscape) 12.dp else 32.dp

    Scaffold(
        // The branded teal banner runs edge-to-edge under the status bar; opt out of the default
        // top inset and re-apply it inside the banner instead.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Pinned above the scrollable body — same pattern as Journal/Help & About, so the
            // header stays visible (shrunk to icon-only) instead of scrolling away with the
            // rest of the page once the user scrolls past it.
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
                        .padding(top = 4.dp, bottom = bannerBottomPadding)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(profileScrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Content pulled up so the avatar overlaps the banner's bottom edge — offset now tracks
            // bannerBottomPadding instead of a hardcoded -32dp, so it can never pull the avatar further up
            // than the banner actually reserved (which was landing it on top of the wordmark in landscape).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = -bannerBottomPadding)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // ...avatar, name, stats card, etc. — unchanged...
```

Everything after the avatar-overlap `Column` (stats card, divider, action-button grid, delete
account, "member since" footer) stays exactly where it is today, just one level shallower now
that `isLandscape`/`collapsed`/`bannerBottomPadding` are no longer declared inline — they're
already hoisted above, so nothing downstream needs to change. Close out the extra brace from the
removed inline `val` block when you delete the old declarations.

No new imports — `Scaffold`'s `topBar` slot, `rememberScrollState`, `derivedStateOf`, and the
`WindowInsets`/`windowInsetsPadding` family are all already imported in this file.

**Verify on-device — the avatar overlap effect:** the avatar's negative `offset(y =
-bannerBottomPadding)` pull-up trick previously worked because the avatar was the very next thing
in the same scrolling flow right after the banner. Now that the banner is a separate pinned
`topBar` and the avatar is body content padded below it by Scaffold's `innerPadding`, the negative
offset needs to still visually pull the avatar up over the topBar's bottom edge rather than
leaving a gap or clipping oddly. Scaffold normally draws body content on top of the topBar in
their shared region, so this should keep working unmodified — but this is exactly the kind of
thing to eyeball on a device/emulator in both portrait and landscape before calling it done,
especially right at the moment the header transitions between expanded and collapsed
(`bannerBottomPadding` changing from 32dp/12dp down to 8dp).

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/ProfileScreen.kt`

---

## Out of scope

- Not touching `JournalListScreen.kt` or `HelpAboutScreen.kt` — both already pin their header via
  `topBar` and already apply the same horizontal inset to their own scrollable content, so their
  collapsed-icon centring already matches their content (verified by reading both files; no
  edge-to-edge content underneath either, unlike Map).
- Not re-tuning `bannerBottomPadding`'s three magic numbers (32dp/12dp/8dp) — those are unchanged,
  just hoisted to a higher scope.
- Not adding a tap-to-re-expand affordance to either collapsed header — out of scope for a bug fix,
  flag separately if wanted.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Skip the horizontal safe-drawing inset on the collapsed (icon-only) header state so it centres on the true screen width, matching the edge-to-edge map — keep the inset for the expanded state's trailing text | `MapScreen.kt` |
| 2 | Hoist the brand banner out of the scrollable body into Scaffold's `topBar`, so it stays pinned (shrinking to icon-only) instead of scrolling off-screen entirely | `ProfileScreen.kt` |
