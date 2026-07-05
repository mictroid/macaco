# Macaco — Landscape Layout Follow-ups: Map Header, Entry Detail Brand Block, Onboarding Overlap, Journal List Width

Four A53-landscape fixes that follow up `DONE/code-brief-landscape-layout-consistency.md`: shrink
the Adventures map's oversized landscape header, restack Entry Detail's brand icon above the
wordmark (matching the other screens' compact-header convention), stop onboarding intro copy from
running into the pagination "Next" button, and fix the journal list rendering narrower than Help &
About (and clipping behind the front camera cutout) because it opts out of Scaffold's system
insets and only partially re-applies them. Touches `MapScreen.kt`, `EntryDetailScreen.kt`,
`OnboardingScreen.kt`, `JournalListScreen.kt`.

---

## Change 1 — Adventures map: shrink the landscape header

**Problem:** In landscape, `MapScreen`'s compact header stacks THREE lines (icon → wordmark →
title+count row), unlike every other screen's compact/landscape header (Journal, Entry Detail,
Help & About), which stacks only TWO (icon → single row combining wordmark + count/title). The
extra line, plus 16dp of horizontal padding this variant adds that no other header uses, is what
makes it visibly taller and eats more of the map on the A53's short landscape viewport.

**Fix:** Merge the separate wordmark `Text` and the title `Row` into one row (icon stays on its
own centred line above), and drop the header-specific horizontal padding so it matches the
`vertical = 4.dp`-only padding Journal's landscape header uses.

```
BEFORE (3 lines)                     AFTER (2 lines, matches Journal)
┌──────────────────────────────┐     ┌──────────────────────────────┐
│         🐒 (48dp)            │     │         🐒 (48dp)            │
│         macaco                │     │  macaco · Adventures · 4/4  │
│  Adventures · 4 of 4 mapped   │     └──────────────────────────────┘
└──────────────────────────────┘
```

### BEFORE (`MapScreen.kt`, ~line 418)

```kotlin
          if (isLandscape) {
            // ── Compact landscape header: icon on its own centred row (matching Journal),
            //    then wordmark, then title+count. ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Line 1: icon alone, centred
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                // Line 2: "macaco" wordmark
                Text(
                    text = "macaco",
                    color = SplashGoldBright,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 3.sp
                )
                // Line 3: Adventures title + location count + globe hint
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.map_adventures_title),
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
                    // Globe-spanning hint — compact, dot-separated (reuses the localized
                    // portrait hint string).
                    if (globeSpanning) {
                        Text(
                            text = " · " + stringResource(R.string.map_globe_spanning_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = SplashGold.copy(alpha = 0.75f),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
          } else {
```

### AFTER

```kotlin
          if (isLandscape) {
            // ── Compact landscape header: icon on its own centred row, then a single row
            //    combining wordmark + title + count + globe hint — matches Journal's two-line
            //    compact recipe (this used to be 3 lines and visibly ate more of the map). ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Line 1: icon alone, centred
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                // Line 2: wordmark + title + location count + globe hint, all in one row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "macaco",
                        color = SplashGoldBright,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 3.sp
                    )
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
                    // Globe-spanning hint — compact, dot-separated (reuses the localized
                    // portrait hint string).
                    if (globeSpanning) {
                        Text(
                            text = " · " + stringResource(R.string.map_globe_spanning_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = SplashGold.copy(alpha = 0.75f),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
          } else {
```

Removing the middle line (and the 16dp horizontal padding) trims roughly one text-line's worth of
height (~18–20dp) plus the padding difference off the landscape header — the biggest lever
available without shrinking the 48dp icon itself, which stays for tap-target/branding parity with
the portrait state. **This is a modest reduction, not a dramatic one** — if more height needs to
come off, the next lever is shrinking the icon itself (e.g. 48dp → 36dp) in the `isLandscape`
branch only; not included here since it changes the brand mark's size, which is a call worth
confirming before Code makes it.

**File:** `MapScreen.kt`

---

## Change 5 — Journal header: avatar aligned to the same gutter as content below

**Problem:** The white profile-avatar circle in the journal header is positioned with hardcoded
padding (`end = 4.dp` in the landscape compact header, `horizontal = 4.dp` on the row plus an
extra `end = 12.dp` on the avatar itself in the portrait/expanded header — 16dp total there), while
the tag row and entry list directly below use `macacoContentGutter()` (16dp minimum, growing to
match the shared 840dp content cap on tablets/wide landscape). On a phone the mismatch is small
(4dp vs 16dp in the landscape case); on a tablet, where the gutter can grow to ~150–220dp, the
avatar stays pinned near the true edge while the list content is inset far toward the centre —
visibly disconnected.

**Fix:** Use `macacoContentGutter()` for the avatar's edge padding in both header states instead of
the hardcoded values, so it always lines up with the list/tag-row edge below it.

### 5a — Landscape compact header avatar (`JournalListScreen.kt`, ~line 279)

#### BEFORE

```kotlin
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(40.dp)
                                .padding(end = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
```

#### AFTER

```kotlin
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(40.dp)
                                // Matches the list/tag-row gutter below, instead of a fixed 4dp —
                                // keeps the avatar aligned with content at every screen width.
                                .padding(end = macacoContentGutter()),
                            contentAlignment = Alignment.Center
                        ) {
```

### 5b — Portrait/expanded header avatar (`JournalListScreen.kt`, ~line 321)

#### BEFORE

```kotlin
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.size(40.dp))
                        Spacer(Modifier.weight(1f))
                        if (currentUser != null) {
                            if (profilePhotoUri != null) {
                                AsyncImage(
                                    model = profilePhotoUri,
                                    contentDescription = stringResource(R.string.common_profile),
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { onProfile() },
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .clickable { onProfile() },
                                    contentAlignment = Alignment.Center
                                ) {
```

#### AFTER

```kotlin
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Matches the list/tag-row gutter below, instead of a fixed 4dp.
                            .padding(horizontal = macacoContentGutter()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.size(40.dp))
                        Spacer(Modifier.weight(1f))
                        if (currentUser != null) {
                            if (profilePhotoUri != null) {
                                AsyncImage(
                                    model = profilePhotoUri,
                                    contentDescription = stringResource(R.string.common_profile),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { onProfile() },
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .clickable { onProfile() },
                                    contentAlignment = Alignment.Center
                                ) {
```

The avatar's own `end = 12.dp` padding is dropped in 5b since the row's gutter now supplies the
full edge margin — keeping both would over-inset the avatar past where the list content starts.
`macacoContentGutter` is already imported in this file (used at line 442).

**File:** `JournalListScreen.kt`

---

## Change 2 — Entry Detail: icon stacked above "macaco", not inline

**Problem:** Every other screen's compact/landscape header stacks the monkey icon above the
"macaco" wordmark (see Change 1, Journal, Help & About). Entry Detail's slim header is the odd
one out — icon and wordmark sit side-by-side in the same row as the back button, counter, and
action icons.

**Fix:** Change the centred brand block from a `Row` (icon, spacer, text) to a `Column` (icon on
top, text below), keeping the same two `weight(1f)` spacers so it still centres in the leftover
space between the back/counter cluster and the action cluster. `verticalAlignment =
Alignment.CenterVertically` on the outer `Row` means the back/counter/action icons stay vertically
centred against the taller two-line block automatically — no other changes needed.

```
BEFORE (inline)                                AFTER (stacked)
┌────────────────────────────────────┐         ┌────────────────────────────────────┐
│ ‹ 1/4      🐒 macaco      ⇪ ✎ 🗑   │         │ ‹ 1/4        🐒          ⇪ ✎ 🗑     │
└────────────────────────────────────┘         │            macaco                  │
                                                └────────────────────────────────────┘
```

### BEFORE (`EntryDetailScreen.kt`, ~line 325)

```kotlin
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
```

### AFTER

```kotlin
                    Spacer(Modifier.weight(1f))
                    // Brand block, centred in the leftover space. Icon stacked above the
                    // wordmark (matches Journal/Map/Help & About's compact-header convention —
                    // this used to sit inline, the odd one out). Wordmark drops on narrow
                    // screens so it can't collide with the action cluster.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        if (LocalConfiguration.current.screenWidthDp >= 420) {
                            Text(
                                text = "macaco",
                                color = SplashGoldBright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = 3.sp
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
```

Icon shrinks 32dp → 28dp and the wordmark 16sp → 12sp so the now-two-line block doesn't grow the
~48dp header beyond the touch targets either side (40dp `IconButton`s) — keeps the header slim.
Add import `androidx.compose.foundation.layout.Column` if not already present in this file (the
file already imports `Row`, `Image`, etc.).

**File:** `EntryDetailScreen.kt`

---

## Change 3 — Onboarding: intro copy no longer runs into the "Next" button

**Problem:** `OnboardingScreen`'s per-page content is vertically centred (`Arrangement.Center`)
across the FULL screen height, while the dots + Next/Get Started button float on top via
`Alignment.BottomCenter` without reserving any layout space. On a short landscape viewport (A53
rotated), centring the first page's 140dp icon + wordmark + tagline block across the whole height
pushes its bottom edge down far enough to overlap the button.

**Fix:** Measure the bottom controls' actual rendered height (`onSizeChanged`) and reserve that
much bottom padding on the `HorizontalPager`, so content centres in the space ABOVE the controls,
never behind them. Also shrink the art/text on short screens (same `isLandscape` convention used
in Map/Journal/Help & About) so there's enough room left to breathe once that space is reserved.

```
BEFORE (content centred full-height,        AFTER (content centred ABOVE the
controls float on top, no reservation)      reserved control zone)
┌──────────────────────────────┐            ┌──────────────────────────────┐
│                               │            │        🐒 (smaller)          │
│         🐒 (140dp)           │            │        macaco                │
│         macaco  ← Next covers │            ├──────────────────────────────┤
│  Roam Freely...  the text     │            │   ● ─ ─ ─ ─                  │
│         [ Next ]              │            │        [ Next ]              │
└──────────────────────────────┘            └──────────────────────────────┘
```

### BEFORE (`OnboardingScreen.kt`, full file relevant excerpts)

```kotlin
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == 3

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(macacoBrandBackground())
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (page) {
                    0 -> {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(140.dp)
                        )
                        Spacer(Modifier.height(32.dp))
                        Text(
                            "macaco",
                            color = SplashGoldBright,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = MacacoFontFamily,
                            letterSpacing = 10.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Roam Freely. Forget Nothing.",
                            color = SplashGold.copy(alpha = 0.80f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = MacacoFontFamily,
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    1 -> OnboardingSlide(
                        icon = Icons.Outlined.Cloud,
                        title = "Your journal,\nalways with you",
                        body = "Every memory syncs to the cloud instantly. Write on your phone, relive anywhere."
                    )
                    2 -> OnboardingSlide(
                        icon = Icons.Outlined.Shield,
                        title = "Private.\nYours. Forever.",
                        body = "No ads. No social feed. One purchase — lifetime access, no subscription."
                    )
                    3 -> OnboardingSlide(
                        icon = Icons.Outlined.Explore,
                        title = "Ready to roam?",
                        body = "Sign in to start capturing your adventures."
                    )
                }
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { i -> /* ...dot... */ }
            }
            Spacer(Modifier.height(28.dp))
            Button(/* ...Next / Get Started... */) { /* ... */ }
        }

        if (!isLastPage) {
            TextButton(/* ...Skip... */)
        }
    }
}

@Composable
private fun OnboardingSlide(icon: ImageVector, title: String, body: String) {
    Icon(imageVector = icon, contentDescription = null, tint = SplashGold, modifier = Modifier.size(88.dp))
    Spacer(Modifier.height(32.dp))
    Text(title, color = SplashGoldBright, fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
        fontFamily = MacacoFontFamily, textAlign = TextAlign.Center, lineHeight = 34.sp)
    Spacer(Modifier.height(16.dp))
    Text(body, color = SplashGold.copy(alpha = 0.80f), fontSize = 15.sp, fontWeight = FontWeight.Light,
        fontFamily = MacacoFontFamily, textAlign = TextAlign.Center, lineHeight = 22.sp, letterSpacing = 0.3.sp)
}
```

### AFTER

```kotlin
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == 3
    // Short screens (phone landscape) get smaller art + tighter spacing so intro copy has room
    // once bottomControlsHeightPx below is reserved — otherwise centring across the full height
    // pushes content down into the dots/button.
    val isLandscape = LocalConfiguration.current.screenHeightDp < 480
    val density = LocalDensity.current
    var bottomControlsHeightPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(macacoBrandBackground())
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        HorizontalPager(
            state = pagerState,
            // Reserve space for the bottom dots/button (measured below) so centred content never
            // runs into them — was the direct cause of intro text overlapping "Next" in landscape.
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = with(density) { bottomControlsHeightPx.toDp() })
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (page) {
                    0 -> {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(if (isLandscape) 84.dp else 140.dp)
                        )
                        Spacer(Modifier.height(if (isLandscape) 12.dp else 32.dp))
                        Text(
                            "macaco",
                            color = SplashGoldBright,
                            fontSize = if (isLandscape) 28.sp else 40.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = MacacoFontFamily,
                            letterSpacing = 10.sp
                        )
                        Spacer(Modifier.height(if (isLandscape) 6.dp else 12.dp))
                        Text(
                            "Roam Freely. Forget Nothing.",
                            color = SplashGold.copy(alpha = 0.80f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = MacacoFontFamily,
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    1 -> OnboardingSlide(
                        icon = Icons.Outlined.Cloud,
                        title = "Your journal,\nalways with you",
                        body = "Every memory syncs to the cloud instantly. Write on your phone, relive anywhere.",
                        compact = isLandscape
                    )
                    2 -> OnboardingSlide(
                        icon = Icons.Outlined.Shield,
                        title = "Private.\nYours. Forever.",
                        body = "No ads. No social feed. One purchase — lifetime access, no subscription.",
                        compact = isLandscape
                    )
                    3 -> OnboardingSlide(
                        icon = Icons.Outlined.Explore,
                        title = "Ready to roam?",
                        body = "Sign in to start capturing your adventures.",
                        compact = isLandscape
                    )
                }
            }
        }

        // Bottom controls — onSizeChanged (placed before the bottom padding in the modifier
        // chain, so it captures the padding too) feeds bottomControlsHeightPx above.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomControlsHeightPx = it.height }
                .padding(bottom = if (isLandscape) 16.dp else 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { i -> /* ...dot, unchanged... */ }
            }
            Spacer(Modifier.height(if (isLandscape) 16.dp else 28.dp))
            Button(/* ...Next / Get Started, unchanged... */) { /* ... */ }
        }

        if (!isLastPage) {
            TextButton(/* ...Skip, unchanged... */)
        }
    }
}

@Composable
private fun OnboardingSlide(icon: ImageVector, title: String, body: String, compact: Boolean = false) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = SplashGold,
        modifier = Modifier.size(if (compact) 56.dp else 88.dp)
    )
    Spacer(Modifier.height(if (compact) 16.dp else 32.dp))
    Text(
        title,
        color = SplashGoldBright,
        fontSize = if (compact) 20.sp else 26.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = MacacoFontFamily,
        textAlign = TextAlign.Center,
        lineHeight = if (compact) 26.sp else 34.sp
    )
    Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
    Text(
        body,
        color = SplashGold.copy(alpha = 0.80f),
        fontSize = if (compact) 13.sp else 15.sp,
        fontWeight = FontWeight.Light,
        fontFamily = MacacoFontFamily,
        textAlign = TextAlign.Center,
        lineHeight = if (compact) 18.sp else 22.sp,
        letterSpacing = 0.3.sp
    )
}
```

Add imports: `androidx.compose.runtime.mutableIntStateOf`, `androidx.compose.ui.layout.onSizeChanged`,
`androidx.compose.ui.platform.LocalConfiguration`, `androidx.compose.ui.platform.LocalDensity`.

**File:** `OnboardingScreen.kt`

---

## Change 4 — Journal list: match Help & About's width (fixes clipping behind the camera cutout)

**Problem:** `JournalListScreen`'s `Scaffold` opts entirely out of system insets
(`contentWindowInsets = WindowInsets(0, 0, 0, 0)`) and its content `Column` re-applies only
`WindowInsets.navigationBars.only(WindowInsetsSides.End)` — i.e. it only insets the side with the
3-button nav bar. `HelpAboutScreen`'s `Scaffold` keeps Compose's default `contentWindowInsets`
(`WindowInsets.safeDrawing`, applied on all sides), which additionally covers the front camera
cutout on the opposite edge. Net effect: on the cutout side, Journal has zero inset while Help &
About has a real one — Journal's content column ends up wider on that side and can render entry
cards partly behind the camera, and the two screens' effective content width disagrees even though
both call the same `macacoContentGutter()`.

**Fix:** Swap the partial `navigationBars`-only inset for the same `safeDrawing`-horizontal inset
already used by this screen's own header (line 213) and by every other screen in the app. This
covers both the nav-bar side and the camera-cutout side, and lines Journal's total inset up with
Help & About's Scaffold-default behaviour.

### BEFORE (`JournalListScreen.kt`, ~line 417)

```kotlin
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End))
                    // Faint teal wash from the top so the page isn't a flat slab behind the cards.
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            ) {
```

### AFTER

```kotlin
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // safeDrawing (not just navigationBars) so BOTH the nav-bar side and the
                    // front-camera-cutout side get inset — matches the header (line 213) and
                    // Help & About's default Scaffold insets, so the two screens' content widths
                    // agree and entries never render behind the cutout.
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    // Faint teal wash from the top so the page isn't a flat slab behind the cards.
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            ) {
```

No import changes — `WindowInsets`, `WindowInsetsSides`, `safeDrawing`, `only`, and
`windowInsetsPadding` are already imported in this file (used at line 213 and line 421 itself).

**File:** `JournalListScreen.kt`

---

## Out of scope / intentional

- Change 1 keeps the map's 48dp icon size (parity with the portrait state and other screens'
  expanded headers) — only the redundant third line and its padding are removed.
- Change 2 shrinks the Entry Detail icon/wordmark slightly (32dp→28dp, 16sp→12sp) to keep the
  header from growing much past its current ~48dp now that the brand block is two lines instead
  of one — accepted tradeoff to preserve the "slim" header design intent from the prior brief.
- Change 3's `compact` sizing only kicks in under the existing `isLandscape` threshold
  (`screenHeightDp < 480`), matching the convention already used in Map/Journal/Help & About —
  portrait is untouched apart from the (harmless) bottom-padding reservation.
- No new strings in any change — nothing to localize.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Landscape header: 3 lines → 2 (merge wordmark + title row), drop extra horizontal padding | `MapScreen.kt` |
| 2 | Brand block restacked icon-above-wordmark (was inline), matching other screens | `EntryDetailScreen.kt` |
| 3 | Pager reserves measured bottom-controls height; art/text shrink in landscape | `OnboardingScreen.kt` |
| 4 | Content inset swapped from `navigationBars`-only to `safeDrawing`-horizontal (matches Help & About, fixes camera-cutout clipping) | `JournalListScreen.kt` |
| 5 | Profile avatar edge padding swapped from hardcoded 4/16dp to `macacoContentGutter()`, matching the tag row/list below | `JournalListScreen.kt` |
