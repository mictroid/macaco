# Macaco — Landscape/Tablet Layout Consistency: Shared Content Width, Insets, Collapsing Headers

Five approved changes that make the journal list, Help & About, Profile, and Entry Detail behave
consistently in landscape and on tablets: a shared max-content-width token (new
`ContentWidth.kt`), horizontal safe-drawing insets on Profile (A53 3-button nav bug), a
scroll-away compact journal header in landscape, a collapsing branded Help & About header, and a
slim branded Entry Detail top bar. Touches `JournalListScreen.kt`, `HelpAboutScreen.kt`,
`ProfileScreen.kt`, `EntryDetailScreen.kt`, plus one new file.

**Context from DONE briefs:**
- `DONE/code-brief-profile-landscape-nav-inset.md` fixed this same nav-bar overlap on the OLD
  two-pane Profile layout (right pane Box, ~line 513). The later single-column rework
  (`DONE/code-brief-profile-single-column.md`) replaced that layout and lost the inset — this
  brief re-applies it to the current structure.
- `DONE/code-brief-theme-chrome-policy.md` establishes: brand-fixed colours are allowed only on
  splash, app lock, and `macacoBrandBackground()` screen headers. Change 5 converts Entry
  Detail's themed `TopAppBar` into a `macacoBrandBackground()` header — that is a *brand
  moment* under the policy, so the fixed splash teal/gold is deliberate and allowed there.

---

## Change 1 — New shared content-width token (`ContentWidth.kt`)

**Problem:** Every list-style screen invents its own width rule. The journal list uses 80 dp
gutters on sw600dp+ (`JournalListScreen.kt:426`); Help & About uses plain full-width 16 dp
padding. On the tablet in landscape the two screens visibly disagree.

**Fix:** One token: content is capped at **840 dp** (Material 3 medium-breakpoint pane width)
and centred; below that width the gutter floors at 16 dp. Expressed as a gutter (`Dp`) so it
works both as `LazyColumn.contentPadding` (scroll region stays full-bleed) and as plain
`padding` on scrolling `Column`s.

```
Phone portrait (360dp)      Tablet landscape (1280dp)
┌─┬───────────────┬─┐       ┌────────┬─────────────┬────────┐
│ │    content    │ │       │ 220dp  │  840dp max  │ 220dp  │
│ │  16dp gutters │ │       │ gutter │   centred   │ gutter │
└─┴───────────────┴─┘       └────────┴─────────────┴────────┘
```

### NEW FILE — `app/src/main/java/com/houseofmmminq/macaco/ui/theme/ContentWidth.kt`

```kotlin
package com.houseofmmminq.macaco.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Max width for single-column list content (Material 3 medium-breakpoint pane width).
 * Shared by JournalListScreen and HelpAboutScreen so list screens line up on tablets
 * and in phone landscape.
 */
val MacacoContentMaxWidth: Dp = 840.dp

/**
 * Horizontal gutter that centres content at [MacacoContentMaxWidth]: never less than [min],
 * growing symmetrically once the screen is wider than the cap.
 */
@Composable
fun macacoContentGutter(min: Dp = 16.dp): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val fromCap = (screenWidth - MacacoContentMaxWidth) / 2
    return if (fromCap > min) fromCap else min
}
```

**File:** `ui/theme/ContentWidth.kt` (new)

---

## Change 2 — Journal list adopts the shared gutter

**Problem:** Hardcoded `80.dp` tablet gutter disagrees with Help & About.

**Fix:** Replace the ad-hoc computation with `macacoContentGutter()`. Behaviour change on
tablet: gutters grow from 80 dp to whatever centres the 840 dp column (e.g. ~220 dp at
1280 dp wide) — approved. Phone portrait and phone landscape below 872 dp keep 16 dp.

### BEFORE (`JournalListScreen.kt`, ~line 424)

```kotlin
                // Wider side gutters on tablets (sw600dp+) so the single-column list doesn't stretch.
                val listHorizontalPadding =
                    if (LocalConfiguration.current.screenWidthDp >= 600) 80.dp else 16.dp
```

### AFTER

```kotlin
                // Shared width rule (ContentWidth.kt): content capped at MacacoContentMaxWidth
                // and centred, so the journal list and Help & About line up on wide screens.
                val listHorizontalPadding = macacoContentGutter()
```

Add import: `import com.houseofmmminq.macaco.ui.theme.macacoContentGutter`

The horizontally-scrolling `TagFilterRow` above the list intentionally stays full-bleed — do
not apply the gutter there.

**File:** `JournalListScreen.kt`

---

## Change 3 — Help & About adopts the shared gutter

**Problem:** Plain `padding(16.dp)` lets FAQ cards stretch the full tablet width.

**Fix:** Same token on the scrolling content column. (The `rememberScrollState()` hoisting
shown here is required by Change 6 — same file, do them together.)

### BEFORE (`HelpAboutScreen.kt`, ~line 144)

```kotlin
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
```

### AFTER

```kotlin
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)   // hoisted — see Change 6
                .padding(horizontal = macacoContentGutter(), vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
```

Add import: `import com.houseofmmminq.macaco.ui.theme.macacoContentGutter`

**File:** `HelpAboutScreen.kt`

---

## Change 4 — Profile: horizontal safe-drawing insets (A53 3-button nav)

**Problem:** In landscape on devices with a side system nav bar (A53, 3-button mode), the
Profile action tiles (Settings / Help / Share / Rate / Subscription / Sign Out) and the Delete
Account button render partly under the bar. Root cause: the Scaffold opts out of insets
(`contentWindowInsets = WindowInsets(0, 0, 0, 0)`, line ~301) for the edge-to-edge banner and
only re-applies the *top* inset inside the banner — horizontal insets are never re-applied.
(Help & About is unaffected because it keeps default Scaffold insets; Settings is unaffected
because its scroll column already has `.navigationBarsPadding()`.)

**Fix:** Add `.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))`
to each content sibling *below* the banner (the banner itself stays edge-to-edge on purpose).
`safeDrawing` covers both the side nav bar and display cutouts, on whichever side they land.
The inset modifier must come BEFORE the visual `padding` so the margin is added on top of the
inset, not consumed by it.

Add imports (`WindowInsets` is already imported):

```kotlin
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
```

### 4a — Identity content column

#### BEFORE (~line 361)

```kotlin
            // Content pulled up so the avatar overlaps the banner's bottom edge.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-32).dp)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
```

#### AFTER

```kotlin
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

### 4b — Divider

#### BEFORE (~line 550)

```kotlin
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
            )
```

#### AFTER

```kotlin
            HorizontalDivider(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
            )
```

### 4c — Action-tile grid column

#### BEFORE (~line 560)

```kotlin
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(gridSpacing)
                ) {
```

#### AFTER

```kotlin
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(gridSpacing)
                ) {
```

### 4d — Delete Account button

#### BEFORE (~line 617)

```kotlin
                TextButton(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 2.dp, bottom = 6.dp),
```

#### AFTER

```kotlin
                TextButton(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 24.dp)
                        .padding(top = 2.dp, bottom = 6.dp),
```

If any further siblings render below Delete Account (e.g. the "Member since" footer), apply the
same one-line inset to them following the same pattern.

**File:** `ProfileScreen.kt`

---

## Change 5 — Journal header: scroll-away in landscape

**Problem:** In landscape the header always renders the compact single row
(`isLandscape || collapsed` at ~line 214) and never reacts to scroll — on the A53's short
landscape viewport it permanently costs ~48 dp. In portrait the header collapses on scroll;
landscape should go one step further and hide the row entirely, reappearing at top.

**Fix:** Keep the brand-coloured status-bar strip always visible (so content never slides
under the status bar), and wrap the header row in `AnimatedVisibility` that hides it when
`isLandscape && collapsed`. The existing `collapsed` derivedState (list scrolled >24 px) is
reused untouched. Also add horizontal safe-drawing insets so the avatar clears a side nav bar.

```
Landscape, at top:                   Landscape, scrolled:
┌──────────────────────────────┐     ┌──────────────────────────────┐
│ ▒ status bar (brand strip) ▒ │     │ ▒ status bar (brand strip) ▒ │
│  ‹40›  🐒 macaco · 8 mem  (M)│     ├──────────────────────────────┤
├──────────────────────────────┤     │  #tags ─────────────────────│
│  #tags ──────────────────────│     │  ┌────────── entry ───────┐ │
```

### BEFORE (`JournalListScreen.kt`, ~line 205)

```kotlin
                val isLandscape = LocalConfiguration.current.screenHeightDp < 480
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(macacoBrandBackground())
                        .statusBarsPadding()
                        .padding(bottom = if (isLandscape || collapsed) 0.dp else 4.dp)
                        .animateContentSize()
                ) {
                  if (isLandscape || collapsed) {
```

### AFTER

```kotlin
                val isLandscape = LocalConfiguration.current.screenHeightDp < 480
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(macacoBrandBackground())
                        .statusBarsPadding()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                ) {
                  // Landscape: the compact row scrolls away entirely (the brand strip behind the
                  // status bar stays). Portrait keeps the tall→compact collapse.
                  AnimatedVisibility(
                      visible = !(isLandscape && collapsed),
                      enter = expandVertically(),
                      exit = shrinkVertically()
                  ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (isLandscape || collapsed) 0.dp else 4.dp)
                            .animateContentSize()
                    ) {
                      if (isLandscape || collapsed) {
```

The entire existing `if/else` content (compact row + tall brand block) moves inside the new
inner `Box` unchanged — only indentation shifts, plus one extra closing brace for the
`AnimatedVisibility` block at the end of the header.

Add imports (`safeDrawing` companions per Change 4 if not present; `WindowInsets`,
`WindowInsetsSides`, `windowInsetsPadding` are already imported in this file):

```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.safeDrawing
```

**File:** `JournalListScreen.kt`

---

## Change 6 — Help & About: collapsing branded header

**Problem:** Help & About has a plain `TopAppBar` ("Help & About" + back) *and* an in-content
brand band — double header cost, and the brand band scrolls away as dumb content. The journal
list already has the right pattern: tall brand block at rest, slim centred row when scrolled
or in landscape.

**Fix:** Replace the `TopAppBar` with a journal-style collapsing brand header in the `topBar`
slot, and delete the in-content brand band. The icon stays centred in both states. Landscape
starts compact and scroll-hides like Change 5 (parity). The version label lives in the
expanded state; in landscape it's reachable by rotating to portrait — accepted tradeoff.

```
Portrait, at top:                    Portrait, scrolled / landscape:
┌──────────────────────────────┐     ┌──────────────────────────────┐
│ ‹                            │     │ ‹    🐒 macaco · Help    ‹40›│
│           🐒 (64dp)          │     └──────────────────────────────┘
│          m a c a c o         │
│   Roam Freely. Forget Nothing│
│        Macaco 1.6 (58)       │
└──────────────────────────────┘
```

### 6a — Hoist scroll state and derive `collapsed` (top of `HelpAboutScreen`, after `versionLabel`)

```kotlin
    val scrollState = rememberScrollState()
    val collapsed by remember {
        derivedStateOf { scrollState.value > 24 }
    }
    val isLandscape = LocalConfiguration.current.screenHeightDp < 480
```

### 6b — Replace the TopAppBar

#### BEFORE (~line 129)

```kotlin
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
```

#### AFTER

```kotlin
    Scaffold(
        topBar = {
            // Collapsing brand header, matching the journal list: tall brand block at rest,
            // slim centred row when scrolled or in landscape; scroll-hides in landscape.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(macacoBrandBackground())
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            ) {
                AnimatedVisibility(
                    visible = !(isLandscape && collapsed),
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Box(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                        if (isLandscape || collapsed) {
                            // ── Compact: back start, brand centred, 40dp trailing anchor ──
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.common_back),
                                        tint = Color.White
                                    )
                                }
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Image(
                                            painter = painterResource(R.drawable.ic_launcher_foreground),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .offset(y = (-2).dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "macaco",
                                            color = SplashGoldBright,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Light,
                                            letterSpacing = 4.sp
                                        )
                                        Text(
                                            text = " · " + stringResource(R.string.help_title),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SplashGold.copy(alpha = 0.7f),
                                            maxLines = 1
                                        )
                                    }
                                }
                                Spacer(Modifier.size(40.dp))   // symmetric trailing anchor
                            }
                        } else {
                            // ── Expanded: back rides the top edge, brand block centred ──
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.common_back),
                                    tint = Color.White
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp)
                                )
                                Column(
                                    modifier = Modifier.offset(y = (-10).dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "macaco",
                                        color = SplashGoldBright,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Light,
                                        letterSpacing = 6.sp
                                    )
                                    Text(
                                        text = "Roam Freely. Forget Nothing.",
                                        color = SplashGold.copy(alpha = 0.82f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Light,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(Modifier.size(4.dp))
                                    Text(
                                        stringResource(R.string.settings_version_value, versionLabel),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
```

### 6c — Delete the in-content brand band

Remove the entire "Branded header band" `Column` (current lines ~152–192: the block starting
`// ── Branded header band: the Macaco splash identity …` through its closing brace). The
feedback CTA `Row` becomes the first child of the content column.

Imports to add: `androidx.compose.animation.AnimatedVisibility`, `expandVertically`,
`shrinkVertically`, `androidx.compose.foundation.layout.Box`,
`androidx.compose.foundation.layout.statusBarsPadding`, `WindowInsets` companions
(`WindowInsets`, `WindowInsetsSides`, `only`, `safeDrawing`, `windowInsetsPadding`),
`animateContentSize`, `derivedStateOf`/`getValue`, `LocalConfiguration`, `contentAlignment`
helpers as flagged by the IDE. `TopAppBar`/`TopAppBarDefaults` imports can be removed.
`macacoBrandBackground`, `SplashGold`, `SplashGoldBright` are in the same package — no import.

**File:** `HelpAboutScreen.kt`

---

## Change 7 — Entry Detail: slim branded top bar

**Problem:** The detail header is a full-height (64 dp) `TopAppBar` that is mostly empty — a
back arrow, a small "2/3" counter, three actions — and carries no Macaco branding, unlike
every other screen header. On the A53 in landscape it eats a fifth of the viewport.

**Fix:** Replace the `TopAppBar` with the journal list's compact header recipe: a ~48 dp row on
`macacoBrandBackground()` (a brand moment per the chrome policy — see context above). Back +
counter lead, brand block centred between the clusters, share/edit/delete trail. On narrow
screens (<420 dp wide) the wordmark drops and only the monkey icon shows, so nothing collides
with the three action buttons in portrait.

```
┌────────────────────────────────────────────────────────┐
│ ‹  2/3        🐒 macaco            ⇪    ✎    🗑        │  ~48dp
└────────────────────────────────────────────────────────┘
  narrow portrait (<420dp):  ‹  2/3      🐒      ⇪ ✎ 🗑
```

Note: the brand block sits centred in the *leftover* space between the asymmetric clusters
(flow layout with weighted spacers), not at the absolute screen centre — this guarantees no
overlap at any width. Accepted optical compromise.

### BEFORE (`EntryDetailScreen.kt`, ~line 278)

```kotlin
        topBar = {
            TopAppBar(
                title = {
                    // Orientation among entries — only worth showing once there's more than one.
                    if (entries.size > 1) {
                        AnimatedContent(
                            targetState = entriesPagerState.currentPage + 1,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                            },
                            label = "entryCounter"
                        ) { pageNumber ->
                            Text(
                                text = "$pageNumber / ${entries.size}",
                                style = MaterialTheme.typography.labelLarge,
                                // Match the header's back/share/edit/delete icons (onPrimary) so the
                                // counter is legible on the teal header across all themes.
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { shareEntry(context, currentEntry, cachedDrivePhotos) }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.entry_detail_share_cd))
                    }
                    IconButton(onClick = { onEdit(currentEntry.id) }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.entry_detail_edit_cd))
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.entry_detail_delete_cd)
                        )
                    }
                },
                // Branded header: the active theme's primary colour with on-primary icons, so it
                // reads as a header band across all themes (not hardcoded teal).
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
```

### AFTER

```kotlin
        topBar = {
            // Slim brand header (~48dp vs TopAppBar's 64dp). macacoBrandBackground() headers
            // are brand moments per the chrome policy, so the fixed splash palette is correct
            // here — this replaces the previous theme-primary TopAppBar.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(macacoBrandBackground())
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Color.White
                        )
                    }
                    // Orientation among entries — only worth showing once there's more than one.
                    if (entries.size > 1) {
                        AnimatedContent(
                            targetState = entriesPagerState.currentPage + 1,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                            },
                            label = "entryCounter"
                        ) { pageNumber ->
                            Text(
                                text = "$pageNumber / ${entries.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = SplashGold,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // Brand block, centred in the leftover space. Wordmark drops on narrow
                    // screens so it can't collide with the action cluster.
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .offset(y = (-2).dp)
                    )
                    if (LocalConfiguration.current.screenWidthDp >= 420) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "macaco",
                            color = SplashGoldBright,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 4.sp
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { shareEntry(context, currentEntry, cachedDrivePhotos) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.entry_detail_share_cd), tint = Color.White)
                    }
                    IconButton(onClick = { onEdit(currentEntry.id) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.entry_detail_edit_cd), tint = Color.White)
                    }
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.entry_detail_delete_cd),
                            tint = Color.White
                        )
                    }
                }
            }
        },
```

Imports to add: `androidx.compose.foundation.Image`, `androidx.compose.ui.res.painterResource`,
`androidx.compose.foundation.background`, `statusBarsPadding`, `offset`, `width`, and the
`WindowInsets` companions (`WindowInsets`, `WindowInsetsSides`, `only`, `safeDrawing`,
`windowInsetsPadding`) — verify against existing imports, several are already present.
`TopAppBar`/`TopAppBarDefaults` imports become removable. `macacoBrandBackground`, `SplashGold`,
`SplashGoldBright` are same-package.

The 40 dp `IconButton` sizes keep the 48 dp effective touch target via the default 4 dp
`minimumInteractiveComponentSize` inset behaviour — matches the journal header's compact row.
If the a11y touch-target lint flags them, wrap with `Modifier.minimumInteractiveComponentSize()`
instead of growing the row.

**File:** `EntryDetailScreen.kt`

---

## Out of scope / intentional

- **SettingsScreen** — audited, already safe: its scroll column has `.navigationBarsPadding()`
  (line ~451). No change.
- **Tag filter row** on the journal list stays full-bleed (horizontal scroller).
- **Version label in landscape Help & About** — only visible in the expanded (portrait) header;
  accepted tradeoff.
- **No new strings** — reuses `help_title`, `common_back`, `settings_version_value`, and the
  entry-detail content descriptions. Nothing to localize.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | New shared 840 dp max-content-width token + `macacoContentGutter()` | `ui/theme/ContentWidth.kt` (new) |
| 2 | Journal list gutter uses shared token (replaces 80 dp tablet hardcode) | `JournalListScreen.kt` |
| 3 | Help & About content uses shared token | `HelpAboutScreen.kt` |
| 4 | Horizontal safe-drawing insets on 4 content blocks below the banner (A53 side-nav fix) | `ProfileScreen.kt` |
| 5 | Landscape: compact header scroll-hides (brand status-bar strip stays) + side insets | `JournalListScreen.kt` |
| 6 | TopAppBar + in-content brand band → collapsing branded header (icon stays centred) | `HelpAboutScreen.kt` |
| 7 | TopAppBar → slim ~48 dp branded header row with centred brand block | `EntryDetailScreen.kt` |
