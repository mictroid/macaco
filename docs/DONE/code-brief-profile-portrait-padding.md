# Macaco — Profile: Padding Tightening, Single Column Everywhere, Actions In Flow (v3)

v3 supersedes v2 after a design revision approved on device screenshots (2026-07-04, second
round): **the action grid is no longer pinned to the bottom.** It flows directly after the
stats card behind a gold divider, and "Member since" moves to the very bottom as footer
metadata under Delete Account — the mid-screen whitespace pool moves below the content where
it reads as breathing room. Changes 1–7 (padding tightening, verified against vc57 source)
are unchanged. Change 8 (single column everywhere) is SIMPLER than v2: with the grid always
in flow, the pinned-vs-scrolling conditional disappears entirely.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/ProfileScreen.kt`
(+ `strings.xml` ×11 for Change 9)

**Source verification (2026-07-04):** all BEFORE snippets and line anchors (~122, ~315,
~707, ~731, ~852, ~901, ~933, ~1035) were re-verified against the file at commit `74d622d`
("feat(profile): single-column layout on all tablets" — the vc57 state) after the working
tree was restored from the disk-corruption incident. If your read of any anchor disagrees,
trust your local file and re-locate by the snippet text, not the line number — and if a
whole snippet is missing, STOP and flag it (that would mean the tree drifted past vc57).

```
Portrait & landscape & tablet — ONE structure (all scrolls as a single column):

┌─────────────────────────────┐
│  banner  (top 4 + bot 32)   │
│         [avatar overlaps]   │
│  name / email / badge       │
│  stats card  pad v=10       │
│  ── gold divider ──         │   ← secondary token (gold on default theme)
│  [ Settings ] [ Help    ]   │
│  [ Share    ] [ Rate    ]   │
│  [ Subscr.  ] [ SignOut ]   │
│  🗑 Delete Account          │
│  Member since July 2026     │   ← footer metadata
│  (whitespace pools below)   │
└─────────────────────────────┘
```

---

## Change 1 — Reduce banner bottom padding + matching content offset (~line 707 + ~line 731)

The avatar overlaps the banner by `offset(y = (-44).dp)`; reduce both together so the
overlap shrinks in sync and no gap appears.

### BEFORE
```kotlin
Column(
    modifier = Modifier
        .align(Alignment.Center)
        .fillMaxWidth()
        .padding(top = 6.dp, bottom = 44.dp),
    ...
```
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .offset(y = (-44).dp)
        .padding(horizontal = 24.dp),
    ...
```

### AFTER
```kotlin
Column(
    modifier = Modifier
        .align(Alignment.Center)
        .fillMaxWidth()
        .padding(top = 4.dp, bottom = 32.dp),
    ...
```
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .offset(y = (-32).dp)
        .padding(horizontal = 24.dp),
    ...
```

---

## Change 2 — Tighten spacers in the scrollable user-info section (~lines 801–833)

### BEFORE
```kotlin
Spacer(Modifier.height(8.dp))   // after avatar

Text(user.displayName, ...)
Spacer(Modifier.height(2.dp))
Text(user.email, ...)

Spacer(Modifier.height(8.dp))   // before badge

Surface(...) { /* badge */ }

Spacer(Modifier.height(16.dp))  // before stats card
```

### AFTER
```kotlin
Spacer(Modifier.height(6.dp))   // after avatar

Text(user.displayName, ...)
Spacer(Modifier.height(2.dp))
Text(user.email, ...)

Spacer(Modifier.height(6.dp))   // before badge

Surface(...) { /* badge */ }

Spacer(Modifier.height(10.dp))  // before stats card
```

---

## Change 3 — Reduce stats card vertical padding (~line 852)

### BEFORE
```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 14.dp, horizontal = 16.dp),
    ...
```

### AFTER
```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 10.dp, horizontal = 16.dp),
    ...
```

---

## Change 4 — Move memberSince to the footer, under Delete Account (revised in v3)

**Was (v1/v2):** padding reduction only. **Now:** the whole `memberSince` block (the
`val memberSince = user.createdAt?.let { … }` computation plus its `if (memberSince != null)
Text(…)` , ~lines 892–903) moves OUT of the identity section and renders as the last element
of the action section, directly below the Delete Account button:

```kotlin
// AFTER — appended at the end of the signed-in action section (below the Delete TextButton):
                val memberSince = user.createdAt?.let { millis ->
                    java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
                        .format(java.util.Date(millis))
                }
                if (memberSince != null) {
                    Text(
                        text = stringResource(R.string.profile_member_since, memberSince),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 4.dp)
                    )
                }
```

Note: the action section needs `user` in scope — it already checks `currentUser != null`;
use `currentUser!!.createdAt` or bind `val user = currentUser` at the top of the branch,
matching the identity section's existing pattern.

---

## Change 5 — Tighten action grid spacing and top padding (~lines 933–987)

### BEFORE
```kotlin
val gridSpacing = 8.dp
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(top = 12.dp),
    verticalArrangement = Arrangement.spacedBy(gridSpacing)
```

### AFTER
```kotlin
val gridSpacing = 6.dp
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(top = 8.dp),
    verticalArrangement = Arrangement.spacedBy(gridSpacing)
```

---

## Change 6 — Reduce ProfileActionTile min height and inner padding (~lines 1032–1046)

### BEFORE
```kotlin
Card(
    modifier = Modifier
        .weight(1f)
        .heightIn(min = 64.dp)
        .clickable(onClick = onClick),
    ...
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
```

### AFTER
```kotlin
Card(
    modifier = Modifier
        .weight(1f)
        .heightIn(min = 56.dp)
        .clickable(onClick = onClick),
    ...
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
```

Note: `heightIn(min = 56.dp)` keeps the tile itself ≥ the 48dp touch-target minimum — do not
go below 48.

---

## Change 7 — Reduce Delete Account button padding and bottom spacer (~lines 990–1018)

### BEFORE
```kotlin
TextButton(
    ...
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(top = 4.dp, bottom = 12.dp),
    ...
)
...
Spacer(Modifier.height(12.dp))
```

### AFTER
```kotlin
TextButton(
    ...
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(top = 2.dp, bottom = 6.dp),
    ...
)
...
Spacer(Modifier.height(4.dp))
```

---

## Change 8 — One single-column layout everywhere; action section flows after the stats

**Problem:** Phone landscape still renders the legacy two-pane layout (compact header + left
identity pane + right stats pane) — on device it clips the account chip under the header and
strands Delete Account in the right pane. Tablets were already switched to single-column
(commit `74d622d`). And in portrait, the bottom-pinned grid leaves a whitespace pool in the
middle of the screen (device screenshot, second round).

**Fix (structure):** one layout for every size/orientation; the action section is part of the
scroll content, separated from the stats by a gold divider.

1. Delete the entire `if (isLandscape) { … }` branch (~lines 315–669: from
   `// ── LANDSCAPE: full-width header + two-pane Row` through `} // end landscape Column`)
   so the current `else` (portrait single-column) becomes the only layout. Remove the
   `val isLandscape = configuration.screenHeightDp < 480` declaration (~line 122) and the
   now-orphaned `if/else` wrapper + `// end else (portrait)` brace.

2. Move the ENTIRE pinned section — the `if (currentUser != null) { grid + delete } else
   { Sign In button }` block that sits after the `weight(1f)` Box (~lines 932–1017) — INTO the
   scrollable column, immediately after the stats card, prefixed by the divider:

```kotlin
// Root structure becomes (weight(1f) Box + pinned section GONE):
    Column(Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            /* banner + identity + stats — unchanged (memberSince NO longer here, see Change 4) */

            // Seam between the identity zone and the actions zone. Chrome policy: content
            // chrome follows the theme — `secondary` renders macaco gold on the default theme.
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
            )

            /* action section: 2×3 grid + Delete + memberSince footer (or Sign In button
               when signed out) — bodies unchanged, placement now in-flow */

            Spacer(Modifier.height(4.dp)) // (value per Change 7)
        }
    }
```

3. Dead-code cleanup: `ProfileUtilityCard` and `ProfileUtilityRow` (bottom of file) were only
   referenced by the deleted landscape pane — remove both. Prune imports that die with the
   branch (`windowInsetsPadding`, `WindowInsetsSides`, `navigationBars`, `fillMaxHeight` if
   unused elsewhere in this file).

4. Wide-screen sanity: cap the grid width so tiles don't stretch absurdly in landscape —
   add `.widthIn(max = 560.dp)` to the grid Column; the scroll column's
   `horizontalAlignment = CenterHorizontally` centres it.

---

## Change 9 (recommended) — Stats: count videos in the media stat

**Problem:** The last stat is `entries.sumOf { it.photoUris.size }` labelled "Photos" —
video clips (vc54 feature) are invisible in the user's own stats.

### BEFORE (~line 885)
```kotlin
                        StatItem(
                            value = entries.sumOf { it.photoUris.size }.toString(),
                            label = stringResource(R.string.profile_photos)
                        )
```

### AFTER
```kotlin
                        StatItem(
                            value = entries.sumOf { it.photoUris.size + it.videoUris.size }.toString(),
                            label = stringResource(R.string.profile_media)
                        )
```

| Key | EN value |
|-----|----------|
| `profile_media` | Media |

(Keep `profile_photos` in strings.xml — other surfaces may reference it; just stop using it
here. ×11 languages for the new key.)

---

## Scope notes

- v3 design decision (device screenshots, 2026-07-04, second round): actions flow up behind a
  gold divider; "Member since" is footer metadata under Delete; remaining whitespace pools at
  the BOTTOM of the screen. This supersedes v2's "pinned grid is the approved reference" note.
- The divider uses `MaterialTheme.colorScheme.secondary` per the shipped chrome policy
  (`docs/DONE/code-brief-theme-chrome-policy.md`) — gold on the default theme, theme-correct
  on the other six. Do NOT hardcode `SplashGold` here.
- Dialogs, photo-source sheet, and the back arrow are untouched.
- Test matrix: phone portrait (grid directly under divider, memberSince under Delete,
  whitespace at bottom), phone landscape (single column scrolls, nothing clipped), tablet
  portrait + landscape, signed-out state (Sign In button under the divider).

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Banner bottom 44→32dp + content offset −44→−32dp | `ProfileScreen.kt` |
| 2 | Post-avatar spacer 8→6dp; pre-badge 8→6dp; pre-stats 16→10dp | `ProfileScreen.kt` |
| 3 | Stats card vertical padding 14→10dp | `ProfileScreen.kt` |
| 4 | memberSince moves to footer under Delete Account (bodySmall, centred) | `ProfileScreen.kt` |
| 5 | Action grid top padding 12→8dp, gap 8→6dp | `ProfileScreen.kt` |
| 6 | Tile min height 64→56dp, inner padding 12→8dp, icon-label gap 6→4dp | `ProfileScreen.kt` |
| 7 | Delete padding top 4→2 / bottom 12→6dp; bottom spacer 12→4dp | `ProfileScreen.kt` |
| 8 | Two-pane landscape deleted; ONE single-column layout; action section in-flow after a `secondary`-token divider; `ProfileUtilityCard`/`Row` removed | `ProfileScreen.kt` |
| 9 | Photos stat → Media (photos + videos), 1 new key ×11 | `ProfileScreen.kt`, `strings.xml` |
