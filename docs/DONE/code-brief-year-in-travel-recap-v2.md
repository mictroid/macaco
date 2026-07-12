# Macaco — Year in Travel: brand the in-app screen to match the share export

Read `docs/DONE/code-brief-year-in-travel-recap.md` first for v1 context. That brief shipped
`YearInTravelScreen.kt` (year picker + stats + Share button) and `YearRecapRenderer.kt` (the
branded 1080×1920 PNG rendered when the user taps Share). The problem this brief fixes: the PNG
export looks great — dark-teal background, gold "2026", gold highlight lines, monkey logo + QR
band, matching the Splash/Login/Purchase visual language — but the *in-app screen itself* is a
plain `TopAppBar` + default Material text on a white background. It looks like a debug preview of
the thing you're about to export, not a branded surface in its own right. This brief ports the
existing brand language already used by `SettingsScreen.kt` / `ProfileScreen.kt` /
`JournalListScreen.kt` into `YearInTravelScreen.kt`, reusing existing components and colors —
no new assets, no new strings.

Two files touched: `ui/screens/YearInTravelScreen.kt`, `ui/screens/ProfileScreen.kt` (one
composable gets two new optional parameters; existing call sites are unaffected).

---

## Change 1 — Branded header band (replaces the plain `TopAppBar`)

**Problem:** `YearInTravelScreen`'s `TopAppBar` (lines 57–70 today) is default Material 3 —
plain text title, no icon, no wordmark. Every other top-level screen reached from Profile
(`SettingsScreen`, and `ProfileScreen` itself) uses a fixed teal-radial-gradient band
(`macacoBrandBackground()`, defined in `SplashScreen.kt`, same package so no import needed) with
a white back button and a centered `MacacoBrandBlock` (icon + gold "macaco" wordmark + a white
trailing label). `YearInTravelScreen` is the odd one out.

**Fix:** replace the `Scaffold`'s `topBar` entirely. Follow `SettingsScreen.kt`'s header block
(~lines 340–420) exactly: drop the `TopAppBar`, give the `Scaffold` `contentWindowInsets =
WindowInsets(0, 0, 0, 0)` and `containerColor = MaterialTheme.colorScheme.background`, and put a
fixed-height `Box` with `.background(macacoBrandBackground()).statusBarsPadding()` as the first
child of the content `Column`, containing the back button + `MacacoBrandBlock`. Landscape
handling is intentionally skipped — this screen doesn't branch on orientation today, so `AFTER`
below only wires the portrait (`isLandscape = false`) path, matching the current scope.

```
┌─────────────────────────────────┐
│  ←        🐒                    │  ← teal radial band (macacoBrandBackground())
│         macaco · Year in Travel │     white back icon, gold wordmark, white label
├─────────────────────────────────┤
│                                  │  ← MaterialTheme.colorScheme.background from here down
│            2026                 │  ← Change 2
│  [2026] [2025] [2024]           │
│  ┌────────────────────────────┐ │
│  │   7        1                │ │  ← Change 3 (card)
│  │  Memories  Trips             │ │
│  │   7       40                │ │
│  │ Locations  Media             │ │
│  └────────────────────────────┘ │
│  Most common mood: 😍            │  ← Change 3 (gold)
│  Most used tag: architecture     │
│  Busiest month: February         │
│                                  │
│         🐒 macaco                │  ← Change 4
│  [      Share my year       ]   │
└─────────────────────────────────┘
```

```kotlin
// ui/screens/YearInTravelScreen.kt — BEFORE (lines 57–70)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.year_recap_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
```

```kotlin
// ui/screens/YearInTravelScreen.kt — AFTER

    Scaffold(
        // Branded header runs edge-to-edge under the status bar, same pattern as SettingsScreen —
        // opt out of the default top inset and re-apply it inside the band via statusBarsPadding().
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(macacoBrandBackground())
                    .statusBarsPadding()
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart).padding(4.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = Color.White
                    )
                }
                MacacoBrandBlock(
                    isLandscape = false,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 2.dp, bottom = 10.dp)
                ) {
                    Text(
                        stringResource(R.string.year_recap_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
```

Note the rest of the existing body (the `Column(Modifier.padding(padding).fillMaxSize().padding
(24.dp)) { ... }` with the year chips, stats, and Share button) now nests *inside* this outer
`Column`, right after the closing brace of the brand-band `Box` — apply `padding` from the
`Scaffold` lambda to that inner `Column` as before (it'll just be near-zero now since there's no
`TopAppBar`, but keeping it is harmless and future-proofs against system bar changes).

New imports needed: `androidx.compose.foundation.background`, `androidx.compose.foundation.layout.statusBarsPadding`,
`androidx.compose.foundation.layout.WindowInsets`, `androidx.compose.ui.graphics.Color`,
`com.houseofmmminq.macaco.ui.components.MacacoBrandBlock`. (`macacoBrandBackground()` needs no
import — same `ui.screens` package.)

**File:** `ui/screens/YearInTravelScreen.kt`.

---

## Change 2 — Big gold year numeral (mirrors the export's "2026")

**Problem:** the selected year only appears as a small label inside a `FilterChip` — there's no
visual echo of the export PNG's huge gold "2026" that anchors the whole card.

**Fix:** right after the brand band (before the year-chip `LazyRow`), add the selected year as a
large bold heading in `SplashGold` (the muted-for-light-backgrounds gold token already used this
way in `JournalListScreen`'s accent text — not `SplashGoldBright`, which is reserved for the
dark-teal screens).

```kotlin
// ui/screens/YearInTravelScreen.kt — ADD, inside the inner Column, before the year-chip LazyRow

            Text(
                selectedYear.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = SplashGold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
```

New imports: `androidx.compose.ui.text.font.FontWeight`, `androidx.compose.ui.text.style.TextAlign`.
(`SplashGold` is `internal` in `ui.screens.SplashScreen.kt` — same package, no import needed.)

**File:** `ui/screens/YearInTravelScreen.kt`.

---

## Change 3 — Stat grid in a card + gold highlight lines

**Problem:** the four stats (`StatItem` calls, lines 102–111) float directly on the background
with no container, and the mood/tag/month highlight lines (114–134) render in plain
`MaterialTheme.typography.bodyLarge` default-color text — both blend into the page instead of
reading as a "recap," unlike `ProfileScreen`'s own stats card which is at least wrapped in an
elevated `Card`.

**Fix:** wrap the two stat `Row`s in an elevated `Card` (same shape/elevation as
`ProfileScreen`'s stats card, ~line 476), and recolor the highlight lines gold + bold. `StatItem`
is shared with `ProfileScreen` (`internal fun StatItem` in `ProfileScreen.kt`, line 729) — don't
change its default styling, since that would also recolor Profile's all-time stats card. Instead
give it two optional color parameters that default to today's colors, and pass gold/bold-white
overrides only from `YearInTravelScreen`.

```kotlin
// ui/screens/ProfileScreen.kt — BEFORE (lines 727–734)

// Not private: reused by YearInTravelScreen's stat grid (same package).
@Composable
internal fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

```kotlin
// ui/screens/ProfileScreen.kt — AFTER

// Not private: reused by YearInTravelScreen's stat grid (same package). valueColor/labelColor
// default to today's colors so every existing call site (ProfileScreen's own stats card) is
// visually unchanged; YearInTravelScreen passes gold/onSurface overrides for its recap card.
@Composable
internal fun StatItem(
    value: String,
    label: String,
    valueColor: Color = Color.Unspecified,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, style = MaterialTheme.typography.bodySmall, color = labelColor)
    }
}
```

(`Color.Unspecified` for `valueColor`'s default makes `Text` fall back to
`LocalContentColor`/`MaterialTheme.colorScheme.onSurface` — identical to today's unset-color
behavior, so this is a no-op for existing calls.)

```kotlin
// ui/screens/YearInTravelScreen.kt — BEFORE (lines 96–134, inside the `else` branch)

                // Stat grid mirroring ProfileScreen's card (reuses its StatItem composable).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(value = recap.entryCount.toString(), label = stringResource(R.string.profile_memories))
                    StatItem(value = recap.tripCount.toString(), label = stringResource(R.string.profile_trips))
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(value = recap.locationCount.toString(), label = stringResource(R.string.profile_locations))
                    StatItem(value = recap.mediaCount.toString(), label = stringResource(R.string.profile_media))
                }
                Spacer(Modifier.height(24.dp))
                recap.topMood?.let {
                    Text(
                        stringResource(R.string.year_recap_top_mood, it),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                recap.topTag?.let {
                    Text(
                        stringResource(R.string.year_recap_top_tag, it),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                recap.busiestMonth?.let {
                    Text(
                        stringResource(R.string.year_recap_busiest_month, it),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
```

```kotlin
// ui/screens/YearInTravelScreen.kt — AFTER

                // Stat grid in an elevated card (matches ProfileScreen's stats card), values in
                // gold to echo the exported PNG's stat treatment.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatItem(value = recap.entryCount.toString(), label = stringResource(R.string.profile_memories), valueColor = SplashGold)
                            StatItem(value = recap.tripCount.toString(), label = stringResource(R.string.profile_trips), valueColor = SplashGold)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatItem(value = recap.locationCount.toString(), label = stringResource(R.string.profile_locations), valueColor = SplashGold)
                            StatItem(value = recap.mediaCount.toString(), label = stringResource(R.string.profile_media), valueColor = SplashGold)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                recap.topMood?.let {
                    Text(
                        stringResource(R.string.year_recap_top_mood, it),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = SplashGold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                recap.topTag?.let {
                    Text(
                        stringResource(R.string.year_recap_top_tag, it),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = SplashGold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                recap.busiestMonth?.let {
                    Text(
                        stringResource(R.string.year_recap_busiest_month, it),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = SplashGold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
```

New imports in `YearInTravelScreen.kt`: `androidx.compose.material3.Card`,
`androidx.compose.material3.CardDefaults`, `androidx.compose.foundation.shape.RoundedCornerShape`.

**Files:** `ui/screens/YearInTravelScreen.kt`, `ui/screens/ProfileScreen.kt`.

---

## Change 4 — Brand mark above the Share button

**Problem:** the Share button sits alone at the bottom with no branding, even though the PNG it
produces has a logo + wordmark band right above the QR code. The in-app screen should tease that.

**Fix:** add a small, centered icon + "macaco" wordmark directly above the `Button`, reusing
`MacacoBrandBlock`'s `collapsed = true` icon-only mode plus a standalone gold label underneath —
`MacacoBrandBlock` on its own only draws the icon when collapsed, so add the wordmark as a
sibling `Text`, matching the export's icon-then-wordmark stacking.

```kotlin
// ui/screens/YearInTravelScreen.kt — BEFORE (lines 137–158)

            Spacer(Modifier.weight(1f))
            Button(
```

```kotlin
// ui/screens/YearInTravelScreen.kt — AFTER

            Spacer(Modifier.weight(1f))
            MacacoBrandBlock(isLandscape = false, collapsed = true)
            Text(
                "macaco",
                color = SplashGold,
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                textAlign = TextAlign.Center
            )
            Button(
```

New import: `androidx.compose.ui.unit.sp` (likely already present via other `.dp` usage — check
before adding a duplicate import).

**File:** `ui/screens/YearInTravelScreen.kt`.

---

## Scope

- **In:** branded header band (icon + gold wordmark + "Year in Travel" label on the teal
  radial gradient), big gold year numeral, stat card with gold values, gold highlight lines,
  brand mark above Share — all reusing existing colors/components (`macacoBrandBackground()`,
  `MacacoBrandBlock`, `SplashGold`), no new assets.
- **Out:** landscape-specific layout for this screen (it doesn't branch on orientation today;
  don't add a new `isLandscape` computed value unless you're also handling it end-to-end — pass
  `isLandscape = false` as shown above).
- **Out:** recoloring the `FilterChip` year selector — left on Material 3 defaults; flag if it
  looks visually disconnected once the big gold year numeral is in place above it, but don't
  restyle preemptively.
- **Out (intentional deviation from the "MD3 tokens only" convention):** this screen uses the
  fixed brand colors `SplashGold` / `macacoBrandBackground()` rather than
  `MaterialTheme.colorScheme` tokens. That's consistent with how `SettingsScreen`, `ProfileScreen`,
  `JournalListScreen`, `LoginScreen`, and `PurchaseScreen` already treat their brand-identity
  chrome — those elements intentionally look the same across all 7 themes rather than adapting,
  because they're reinforcing the "macaco" mark itself, not themed content.

---

## Verification

1. Open Year in Travel — confirm the teal brand band renders under the status bar (no double
   status-bar padding, no content clipped behind it) and the back button still navigates to
   Profile.
2. Confirm `ProfileScreen`'s own stats card is pixel-identical to before this change (StatItem's
   new params must be additive-only).
3. Switch between years with the `FilterChip` row — confirm the big gold year numeral updates
   and the stat card/highlight lines re-render correctly, including the zero-entries empty state
   (`recap.entryCount == 0`), which should still show cleanly below the brand band.
4. Check both light and dark app themes — `SplashGold` and `macacoBrandBackground()` are
   theme-independent by design (see Scope note above); confirm they still read clearly against
   `MaterialTheme.colorScheme.background` in both.
5. Tap Share — confirm the exported PNG is unaffected (this brief only touches the in-app
   screen, not `YearRecapRenderer`).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Branded teal header band replaces plain `TopAppBar` | `YearInTravelScreen.kt` |
| 2 | Big gold year numeral below the header | `YearInTravelScreen.kt` |
| 3 | Stat grid in an elevated card + gold highlight lines | `YearInTravelScreen.kt`, `ProfileScreen.kt` |
| 4 | Brand mark (icon + wordmark) above Share button | `YearInTravelScreen.kt` |
