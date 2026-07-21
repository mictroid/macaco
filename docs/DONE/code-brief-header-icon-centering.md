# Macaco — Branded Header: Icon/Splash Centering Fix (Journal, Adventures, Help & About, Entry Detail)

Fixes the reported bug: the header `macaco` icon doesn't stay centered with the splash screen's
icon position — it visibly shifts between the expanded and scroll-collapsed header states, and
looks fine on some screens (Profile) but off on others (Adventures), in both portrait and
landscape. Files touched: `JournalListScreen.kt`, `MapScreen.kt`, `HelpAboutScreen.kt`,
`EntryDetailScreen.kt`.

---

## Root cause

`SplashScreen.kt` centers its icon with a plain `Box(contentAlignment = Alignment.Center)` over
`fillMaxSize()` — no window-insets padding at all, so it centers on the **true physical screen
width**.

Every branded header is supposed to continue that same centered position (`MacacoBrandBlock.kt`
centers its icon the same way — `Box`/`Column` + `fillMaxWidth()` + `Center`/`CenterHorizontally`).
But six screens each hand-roll their own header `Box` around `MacacoBrandBlock`, and they don't
agree on whether that outer `Box` also carries
`windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))` — the padding
that keeps content clear of the landscape on-screen navigation bar. Since `MacacoBrandBlock`
centers itself *within whatever width its parent gives it*, this single line changes which width
the icon centers against:

| Screen | Horizontal safe-drawing inset on the header `Box`? | Result |
|---|---|---|
| **ProfileScreen** | Never applied | ✅ Icon always centers on true screen width — matches splash. This is the screen the report calls "good." |
| **SettingsScreen** / **YearInTravelScreen** | Never applied | ✅ Same as Profile (no scroll-collapse on these, so no state to compare, but consistent with the fix below) |
| **JournalListScreen** | Always applied (collapsed and expanded alike) | ⚠️ Consistent *within* the screen, but centers on a narrower width than Profile/Splash — the icon sits differently than the splash it followed |
| **HelpAboutScreen** | Always applied (collapsed and expanded alike) | ⚠️ Same as Journal |
| **EntryDetailScreen** | Always applied (fixed header, no collapse) | ⚠️ Same as Journal |
| **MapScreen (Adventures)** | **Conditionally applied — only when NOT collapsed** (`MapScreen.kt:440-443`, `hasMovedMap`) | 🔴 The width the icon centers against *changes* the moment the header collapses (user pans/zooms the map) — the icon visibly jumps sideways by the width of the nav-bar inset. This is the "Adventure is misaligned... when scrolling you can see the icon is not centered" bug. Worst in landscape, where the on-screen nav bar column is widest. |

The Map conditional was added deliberately (see the comment at `MapScreen.kt:433-439`) to stop the
collapsed icon from centering on a narrower axis than the edge-to-edge map behind it — a real
problem, but the fix introduced a new one: now the *expanded* and *collapsed* states use two
different centering axes on the same screen.

## Fix

Stop letting the horizontal safe-drawing inset decide where the brand icon centers, on every
screen. The icon should always center on the **true screen width** — matching the splash screen
and matching Profile/Settings/YearInTravel, in every state. Where a screen has leading/trailing
interactive content that genuinely needs nav-bar clearance (back button, search, avatar, share/
edit/delete icons, Map's landscape trailing label), apply the inset to *that content specifically*
— not to the container the icon centers against.

```
BEFORE (Map, collapsing):                 AFTER (Map, both states):
┌───────────────┬────────┐                ┌────────────────────┬──┐
│   [icon]      │ navbar │  expanded       │       [icon]       │▓▓│  expanded
├───────────────┴────────┤                ├────────────────────┴──┤
│     [icon]         ▓▓▓ │  collapsed      │       [icon]        ▓▓│  collapsed
└─────────────────────────┘                └────────────────────────┘
  icon shifts left on collapse               icon never moves
```

---

## 1. MapScreen.kt — remove the collapse-conditional inset

**Problem:** `windowInsetsPadding(Horizontal)` on the outer header `Box` is applied only when
`!hasMovedMap`, so the icon's centering width changes the instant the header collapses.

**Fix:** Drop the inset from the outer `Box` entirely (icon/background always center on true
width, matching the already-correct collapsed behavior). Give the landscape `landscapeTrailing`
content its own end-inset so long labels ("· Adventures · 9 of 9 locations mapped · ...") still
clear the nav bar in landscape, without moving the icon.

```kotlin
// BEFORE — MapScreen.kt:428-454
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
        landscapeTrailing = { /* ... Text(...) x3 ... */ },
        portraitTrailing = { /* ... Text(...) x3 ... */ }
    )
}

// AFTER
Box(
    modifier = Modifier
        .fillMaxWidth()
        .background(macacoBrandBackground())
        .statusBarsPadding()
        // Icon always centers on the TRUE screen width in every state — matches the splash
        // screen and Profile/Settings/YearInTravel, and stops the icon jumping sideways when
        // the header collapses on pan/zoom. Nav-bar clearance for the expanded landscape
        // trailing label is handled inside MacacoBrandBlock's landscapeTrailing content below,
        // not here, so it never affects where the icon itself centers.
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
            Row(
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End)),
                verticalAlignment = Alignment.CenterVertically
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
        },
        portraitTrailing = { /* unchanged — portrait has no nav-bar column to clear */ }
    )
}
```

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`

---

## 2. JournalListScreen.kt — move the inset off the icon's centering container

**Problem:** `windowInsetsPadding(Horizontal)` sits on the outer header `Box` (`JournalListScreen.kt:287-293`),
so the icon centers on an inset-narrowed width in every state — consistent with itself, but
different from Profile/Splash.

**Fix:** Remove it from the outer `Box`; apply it locally to the leading search button / trailing
avatar clusters instead, since those are the elements that actually risk sitting under the nav bar.

```kotlin
// BEFORE — JournalListScreen.kt:287-293
Box(
    modifier = Modifier
        .fillMaxWidth()
        .background(macacoBrandBackground())
        .statusBarsPadding()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        .animateContentSize()
) {

// AFTER
Box(
    modifier = Modifier
        .fillMaxWidth()
        .background(macacoBrandBackground())
        .statusBarsPadding()
        // No horizontal inset here — the brand icon centers on the TRUE screen width in every
        // state (matches the splash and Profile). Nav-bar clearance moves to the leading/
        // trailing action clusters below instead.
        .animateContentSize()
) {
```

Then wrap the two side clusters that currently have no nav-bar clearance of their own:

```kotlin
// Landscape leading search button — JournalListScreen.kt:335-341
Box(
    modifier = Modifier
        .align(Alignment.TopStart)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
        .size(40.dp)
        .padding(start = macacoContentGutter()),
    contentAlignment = Alignment.Center
) { /* IconButton(onSearch) unchanged */ }

// Landscape trailing avatar — JournalListScreen.kt:350-357
Box(
    modifier = Modifier
        .align(Alignment.TopEnd)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
        .size(40.dp)
        .padding(end = macacoContentGutter()),
    contentAlignment = Alignment.Center
) { /* avatar unchanged */ }
```

The portrait leading/trailing Row (`JournalListScreen.kt:394-440`, search button + `Spacer(weight
1f)` + avatar) doesn't need a change — portrait has no side nav-bar column, and the
`MacacoBrandBlock` at line 442 already centers independently via `Alignment.TopCenter` on the
(now uninset) outer `Box`.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`

---

## 3. HelpAboutScreen.kt — same move

**Problem:** identical pattern at `HelpAboutScreen.kt:205-211` — outer header `Box` always carries
the horizontal inset.

**Fix:**

```kotlin
// BEFORE — HelpAboutScreen.kt:205-211
Box(
    modifier = Modifier
        .fillMaxWidth()
        .background(macacoBrandBackground())
        .statusBarsPadding()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        .animateContentSize()
) {

// AFTER
Box(
    modifier = Modifier
        .fillMaxWidth()
        .background(macacoBrandBackground())
        .statusBarsPadding()
        // Icon centers on the TRUE screen width — matches splash/Profile. Back button gets its
        // own leading inset below instead.
        .animateContentSize()
) {
```

Both the landscape back button (`HelpAboutScreen.kt:224-233`) and the portrait back button
(`HelpAboutScreen.kt:261-272`) need `windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))`
added to their `IconButton` modifier so they still clear a left-side nav-bar column (some devices
place the 3-button nav on the left in landscape depending on rotation).

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/HelpAboutScreen.kt`

---

## 4. EntryDetailScreen.kt — same move (no scroll-collapse, but still inconsistent with Profile)

**Problem:** `EntryDetailScreen.kt:289-294` carries the same unconditional horizontal inset. This
screen already pins its brand block to `Alignment.Center` of the *inner* padded `Box` (see the
comment at `EntryDetailScreen.kt:331-335` — this was already fixed once for a different drift
bug), but that inner `Box` still inherits the outer `Box`'s inset-narrowed width.

**Fix:**

```kotlin
// BEFORE — EntryDetailScreen.kt:289-295
Box(
    modifier = Modifier
        .fillMaxWidth()
        .background(macacoBrandBackground())
        .statusBarsPadding()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
) {

// AFTER
Box(
    modifier = Modifier
        .fillMaxWidth()
        .background(macacoBrandBackground())
        .statusBarsPadding()
        // Icon centers on the TRUE screen width — matches splash/Profile.
) {
```

The leading cluster (back button + page counter, `EntryDetailScreen.kt:302-330`) and the trailing
action icons (share/edit/delete, just after) each need
`windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))` /
`...End` added to their own `Row`/container modifiers so they keep clearing the nav bar without
affecting where the centered icon sits.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/EntryDetailScreen.kt`

---

## Out of scope

- `ProfileScreen.kt`, `SettingsScreen.kt`, `YearInTravelScreen.kt` — already correct (no horizontal
  inset on the header container); no changes needed, used as the reference behavior above.
- `MacacoBrandBlock.kt` itself is unchanged — the bug is entirely in how each screen sizes the
  container `MacacoBrandBlock` centers within, not in the shared component.
- This does not touch icon *size* (48dp / 36dp collapsed) — only horizontal centering.

## Verification

Since this is a layout/inset fix, it needs on-device (or emulator) visual confirmation across:
portrait and landscape, on a device with 3-button nav *and* one with gesture nav, for Journal
(scrolled + at rest), Adventures/Map (panned + at rest), Help & About (scrolled + at rest), and
Entry Detail — confirming the icon sits at the same horizontal position as the splash screen in
every case, and that no back/search/avatar/action icon is clipped by the nav bar in landscape.
Static review can't fully confirm inset behavior — flag this back to Michael for a real-device
check after building.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Remove collapse-conditional horizontal inset; move nav-bar clearance to landscape trailing label only | `MapScreen.kt` |
| 2 | Remove horizontal inset from header container; add it to leading search button + trailing avatar (landscape only) | `JournalListScreen.kt` |
| 3 | Remove horizontal inset from header container; add it to back button (landscape + portrait) | `HelpAboutScreen.kt` |
| 4 | Remove horizontal inset from header container; add it to leading (back+counter) and trailing (action icons) clusters | `EntryDetailScreen.kt` |
