# Macaco — Header Bugs: Profile Landscape Overlap, Journal Portrait Count, Map Premature Collapse

Three independent bug fixes surfaced during review. Touches `ProfileScreen.kt`,
`JournalListScreen.kt`, and `MapScreen.kt` — one change each, no shared code.

---

## Change 1 — Profile: "macaco" wordmark hidden behind avatar in landscape

**Problem:** The identity `Column` (avatar, name, stats) is pulled up to overlap the banner's
bottom edge via a hardcoded `.offset(y = (-32).dp)` (`ProfileScreen.kt`, ~line 356). The banner's
actual bottom clearance varies by state — `bottom = if (collapsed) 8.dp else if (isLandscape)
12.dp else 32.dp` (~line 347) — but the offset never adjusts to match. In portrait the two values
happen to be equal (32dp / 32dp), so it looks fine. In landscape the banner only reserves 12dp of
clearance but the content is still pulled up a full 32dp — 20dp too far — landing the avatar
directly on top of the "macaco" wordmark. Same problem, worse, once collapsed (8dp reserved, 32dp
pulled).

This was actually flagged as a risk in `docs/DONE/code-brief-profile-adventures-collapsible-header.md`
("Heads up for whoever implements this...") when the collapsed state was added, but the offset was
never revisited — this brief closes that loop.

**Fix:** Compute the banner's bottom padding once as a named value, and reuse the *same* value for
both the banner's padding and the content's pull-up offset, so they can never drift apart again.

```
Landscape — BEFORE (offset overshoots clearance)   Landscape — AFTER (offset matches clearance)
┌─────────────────────────┐                        ┌─────────────────────────┐
│      [icon] macaco       │  ← 12dp clearance      │      [icon] macaco       │  ← 12dp clearance
│ ╭──╮ ←avatar overlaps    │                        └─────────────────────────┘
│ │  │   text (bug)        │                        │ ╭──╮  ← avatar sits      │
└─┴──┴─────────────────────┘                        │ │  │    flush below      │
                                                     └─┴──┴─────────────────────┘
```

### BEFORE (`ProfileScreen.kt`, ~lines 323–357)

```kotlin
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

// Content pulled up so the avatar overlaps the banner's bottom edge.
Column(
    modifier = Modifier
        .fillMaxWidth()
        .offset(y = (-32).dp)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
```

### AFTER

```kotlin
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
```

No new imports — `Dp` supports unary minus natively (`-bannerBottomPadding`).

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/ProfileScreen.kt`

---

## Change 2 — Journal List: "X memories" count missing in portrait

**Problem:** `MacacoBrandBlock` (`ui/components/MacacoBrandBlock.kt:40-46`) declares two trailing
content slots in this order: `portraitTrailing` then `landscapeTrailing`. In Kotlin, a trailing
lambda `{ ... }` written outside the parentheses always binds to the function's *last* parameter —
regardless of what it's named — so it binds to `landscapeTrailing` here, always, no matter which
call site it is.

`JournalListScreen.kt`'s portrait call (~line 339, `isLandscape = false`) passes its memories-count
`Text` as exactly that kind of unnamed trailing lambda. It compiles fine (the lambda body doesn't
reference anything specific to either `ColumnScope` or `RowScope`), but it silently lands in
`landscapeTrailing`, which is never read in the portrait branch — `MacacoBrandBlock`'s `else`
branch (portrait) calls `portraitTrailing()`, which is still the default empty `{}`. Net effect:
the count never renders in portrait.

The landscape call site (~line 237) has the identical unnamed-trailing-lambda pattern, but since
`isLandscape = true` there, it happens to bind to the parameter that branch actually reads — which
is why the count only ever shows up in landscape.

**Fix:** Pass the portrait content as an explicit named argument, `portraitTrailing = { ... }`,
instead of a bare trailing lambda.

### BEFORE (`JournalListScreen.kt`, ~lines 339–354)

```kotlin
MacacoBrandBlock(
    isLandscape = false,
    modifier = Modifier.align(Alignment.TopCenter)
) {
    // Slogan removed from this daily-open surface; it stays on the
    // splash/login/purchase screens (the persuasion moments).
    if (entries.isNotEmpty()) {
        val count = visibleEntries.size
        val memoriesText = pluralStringResource(R.plurals.journal_list_memories, count, count)
        Text(
            memoriesText + if (selectedTags.isNotEmpty()) " · ${stringResource(R.string.journal_list_filtered)}" else "",
            style = MaterialTheme.typography.labelMedium,
            color = SplashGold.copy(alpha = 0.8f)
        )
    }
}
```

### AFTER

```kotlin
MacacoBrandBlock(
    isLandscape = false,
    modifier = Modifier.align(Alignment.TopCenter),
    portraitTrailing = {
        // Slogan removed from this daily-open surface; it stays on the
        // splash/login/purchase screens (the persuasion moments).
        if (entries.isNotEmpty()) {
            val count = visibleEntries.size
            val memoriesText = pluralStringResource(R.plurals.journal_list_memories, count, count)
            Text(
                memoriesText + if (selectedTags.isNotEmpty()) " · ${stringResource(R.string.journal_list_filtered)}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = SplashGold.copy(alpha = 0.8f)
            )
        }
    }
)
```

Optional hardening (not required to fix the bug, but prevents a repeat if the parameter order in
`MacacoBrandBlock` ever changes): the landscape call at ~line 237 currently relies on the same
unnamed-trailing-lambda binding working out by coincidence. Consider naming it
`landscapeTrailing = { ... }` explicitly too, for the same reason.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`

---

## Change 3 — Adventures (Map): header collapses immediately on open

**Problem:** The header collapse latch (`MapScreen.kt`, ~lines 416–419):

```kotlin
var hasMovedMap by remember { mutableStateOf(false) }
LaunchedEffect(cameraPositionState.isMoving) {
    if (cameraPositionState.isMoving) hasMovedMap = true
}
```

is meant to collapse the header only once the *user* starts panning/zooming. But every time this
screen opens, an earlier `LaunchedEffect` (~lines 223–362) auto-frames the camera over all of the
user's geocoded locations via `cameraPositionState.move(update)` — and for globe-spanning pin sets,
a second corrective `move()` right after (~line 350). Both of those programmatic moves also toggle
`cameraPositionState.isMoving`, which this latch can't distinguish from a real user gesture. Result:
the header collapses to icon-only the instant the auto-fit camera move happens (essentially as soon
as the screen opens and geocoding is warm), rather than waiting for the user to actually touch the
map.

**Fix:** Arm the user-gesture detector only after the initial auto-fit sequence has had time to
fully settle (including the optional globe-spanning re-center move), using a short delay gated on
`cameraPositioned` (already tracked, set `true` once the initial fit succeeds — see ~line 321).

```
BEFORE: opens → auto-fit move() fires → isMoving pulses → hasMovedMap=true → header
        collapses immediately, before the user has touched anything.

AFTER:  opens → auto-fit move() fires (ignored, detector not armed yet) → ~400ms grace
        period → detector arms → header only collapses on the user's first real pan/zoom.
```

### BEFORE (`MapScreen.kt`, ~lines 407–420)

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
```

### AFTER

```kotlin
Column(modifier = Modifier.fillMaxSize()) {
    // In landscape on phones (short screen) the tall centered brand block eats ~120dp of map;
    // collapse it to a single slim row. Tablets stay tall (~750dp+) and keep the full header.
    val isLandscape = LocalConfiguration.current.screenHeightDp < 480

    // The initial "fit all pins" camera move (and, for globe-spanning sets, the follow-up
    // re-center move) also toggles cameraPositionState.isMoving — without this guard the header
    // below latches collapsed the instant that auto-fit happens, before the user ever touches the
    // map. Arm user-gesture detection only after the auto-fit has had time to fully settle.
    var readyToDetectUserPan by remember { mutableStateOf(false) }
    LaunchedEffect(cameraPositioned) {
        if (cameraPositioned) {
            delay(400) // lets any auto-fit isMoving pulse (incl. the globe-spanning re-center) finish
            readyToDetectUserPan = true
        }
    }

    // Once the user starts panning/zooming, collapse the header down to the icon and keep it
    // that way for the rest of this visit to the screen — maximizes map space during active
    // exploration, in either orientation. Latched (not live-bound to isMoving) so the header
    // doesn't pop back open every time a drag settles between pans.
    var hasMovedMap by remember { mutableStateOf(false) }
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && readyToDetectUserPan) hasMovedMap = true
    }
```

`cameraPositioned` and `delay` are already declared/imported in this file (`cameraPositioned` at
~line 194, `import kotlinx.coroutines.delay` at ~line 89) — no new imports needed.

**Out of scope:** not changing the latch-vs-live-toggle behavior itself (already an intentional,
previously-flagged design choice) — only fixing what counts as the first "move."

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Tie avatar pull-up offset to the same value as the banner's bottom padding, fixing wordmark/avatar overlap in landscape (and collapsed) | `ProfileScreen.kt` |
| 2 | Pass portrait trailing content as a named `portraitTrailing =` argument instead of a bare trailing lambda, fixing the missing memories count in portrait | `JournalListScreen.kt` |
| 3 | Gate the "user moved the map" collapse latch behind a short settle delay after the initial auto-fit, so the header no longer collapses the instant the screen opens | `MapScreen.kt` |
