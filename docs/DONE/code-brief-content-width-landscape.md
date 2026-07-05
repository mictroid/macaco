# Macaco — Tablet Landscape: Fill Screen Width + Fix FAB Hidden Behind Nav Bar

Two related landscape fixes. (1) `macacoContentGutter()` caps Journal + Help & About content at
`MacacoContentMaxWidth` (840dp), centering it with growing gutters once the screen exceeds that
width; on tablet landscape this leaves large dead white space either side of the content. Fix:
skip the cap on genuinely tablet-sized screens only (using `smallestScreenWidthDp`, which is
orientation-independent — a plain `screenWidthDp` check would wrongly treat a large phone's long
edge in landscape, e.g. the Galaxy A53's ~873dp landscape width, as "tablet"). (2) Separately, on
the Galaxy A53 in landscape, the Journal screen's "New Entry" FAB renders partly behind the
system navigation bar, because `JournalListScreen`'s `Scaffold` opts out of `contentWindowInsets`
entirely and the FAB was never given its own inset padding (the content list already gets this via
manual `safeDrawing` padding — the FAB was missed). Touches `ui/theme/ContentWidth.kt` and
`ui/screens/JournalListScreen.kt`.

---

## Change 1 — skip the 840dp cap on tablet-sized screens in landscape orientation

**Problem:** `macacoContentGutter()` computes the gutter from `(screenWidth - 840dp) / 2` whenever
that's larger than the 16dp minimum, with no orientation or size check. On a tablet in landscape
(screenWidthDp typically 900–1200dp+), this produces a large symmetric gutter, so `JournalListScreen`
and `HelpAboutScreen` (both of which use this gutter for their content padding) end up with a narrow
column of content floating in the middle of a much wider screen. In portrait, screen width rarely
exceeds 840dp, so the cap never engages there and content already fills the width — that's the
behavior we want carried over to tablet landscape specifically (phone landscape should keep its
current behavior — it isn't the reported problem, and its width rarely made the cap engage
meaningfully in the first place).

**Fix:** Detect "genuinely a tablet" using `LocalConfiguration.current.smallestScreenWidthDp >= 600`
— this is the device's short-edge width, fixed regardless of rotation, matching Android's own
`sw600dp` resource-qualifier convention. **Deliberately not** `screenWidthDp >= 600` (the pattern
used elsewhere in this codebase, e.g. `JournalListScreen`'s `EntryPhotoArea.isTablet`): in
landscape, `screenWidthDp` is the device's *long* edge, and large phones can exceed 600dp there
too — the Galaxy A53, for example, is ~873dp wide in landscape. A plain `screenWidthDp` check
would misclassify it as a tablet and remove its gutter as well, which is not the intent here.
Combine with the existing `screenWidthDp > screenHeightDp` landscape test, and short-circuit to
the `min` gutter when both are true, skipping the cap/centering math entirely. Phone landscape
(smallest width < 600dp) falls through to the existing cap logic unchanged.

```
BEFORE (tablet landscape — capped + centered)         AFTER (tablet landscape — fills width)
┌───────────────────────────────────────────┐         ┌───────────────────────────────────────────┐
│        │                           │      │         │16dp                                  16dp│
│ ~220dp │   content (840dp cap)     │~220dp│         │  content fills full screen width          │
│        │                           │      │         │                                            │
└───────────────────────────────────────────┘         └───────────────────────────────────────────┘
```

### BEFORE (`ui/theme/ContentWidth.kt`, full file)

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

### AFTER

```kotlin
package com.houseofmmminq.macaco.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Max width for single-column list content (Material 3 medium-breakpoint pane width).
 * Shared by JournalListScreen and HelpAboutScreen so list screens line up on tablets
 * and in phone landscape — but the cap is skipped on genuinely tablet-sized devices
 * (smallestScreenWidthDp >= 600) in landscape orientation, where it left large empty gutters
 * with no content benefit, since there's no second pane to balance against. Phone landscape
 * (including large phones like the Galaxy A53, whose long edge exceeds 600dp when rotated)
 * keeps the cap unchanged.
 */
val MacacoContentMaxWidth: Dp = 840.dp

/**
 * Horizontal gutter that centres content at [MacacoContentMaxWidth]: never less than [min],
 * growing symmetrically once the screen is wider than the cap. Skipped entirely (always
 * returns [min]) on tablet-sized devices in landscape orientation — content fills the screen
 * width there instead, same as portrait already does (portrait width rarely exceeds the cap,
 * so it was already effectively full-width there).
 */
@Composable
fun macacoContentGutter(min: Dp = 16.dp): Dp {
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp
    // smallestScreenWidthDp (not screenWidthDp) so this reflects device class, not rotation —
    // screenWidthDp in landscape is the long edge, which exceeds 600dp on large phones too
    // (e.g. Galaxy A53 ~873dp), and those should NOT lose their gutter.
    val isTabletLandscape = config.screenWidthDp > config.screenHeightDp &&
        config.smallestScreenWidthDp >= 600
    if (isTabletLandscape) return min
    val fromCap = (screenWidth - MacacoContentMaxWidth) / 2
    return if (fromCap > min) fromCap else min
}
```

No import changes — `LocalConfiguration` is already imported.

**File:** `ui/theme/ContentWidth.kt`

---

## Change 2 — Journal FAB: give it the same inset padding as the content below it

**Problem:** On the Galaxy A53 in landscape, the "New Entry" `ExtendedFloatingActionButton` renders
partly behind the system navigation bar (which occupies the side edge in landscape, not the bottom).
`JournalListScreen`'s `Scaffold` sets `contentWindowInsets = WindowInsets(0, 0, 0, 0)` deliberately,
so the screen can hand-manage insets itself — the header applies its own `.statusBarsPadding()` and
the content `Column` applies its own `WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)`
(see the "Journal list: match Help & About's width" fix already in `docs/DONE/`). The FAB, in the
`floatingActionButton` slot, was never given equivalent inset padding, so with `contentWindowInsets`
zeroed there's nothing protecting it from the nav bar.

**Fix:** Add `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` directly to the FAB, matching
the inset handling already used elsewhere on this screen.

```
BEFORE (A53 landscape — FAB clipped by nav bar)      AFTER (FAB clears the nav bar)
┌────────────────────────────────────────┬──┐        ┌────────────────────────────────────┬────┐
│                                  [New Ent│▌]│        │                            [New Entry]│    │
└────────────────────────────────────────┴──┘        └────────────────────────────────────┴────┘
                                            ↑ nav bar                                        ↑ nav bar
                                        (FAB text clipped)                          (FAB fully clear)
```

### BEFORE (`JournalListScreen.kt`, ~line 405)

```kotlin
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onNewEntry,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.common_new_entry)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            },
```

### AFTER

```kotlin
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onNewEntry,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.common_new_entry)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    // Scaffold's contentWindowInsets is zeroed on this screen (hand-managed
                    // insets elsewhere), so the FAB needs its own — otherwise it renders behind
                    // the system nav bar in landscape (nav bar sits on the side edge there, not
                    // the bottom), as seen on the Galaxy A53.
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                )
            },
```

No import changes — `Modifier`, `WindowInsets`, `WindowInsets.safeDrawing`, and
`windowInsetsPadding` are already imported in this file (used at line 213/426 etc.).

**File:** `ui/screens/JournalListScreen.kt`

---

## Out of scope / intentional

- Portrait behavior is unchanged (the cap still exists there for the rare case of a very wide
  portrait screen); only genuinely tablet-sized landscape drops the cap.
- Phone landscape is unchanged — including large phones whose landscape long edge exceeds 600dp
  (Galaxy A53 and similar), which is exactly why `smallestScreenWidthDp` is used instead of
  `screenWidthDp` for the tablet check in Change 1.
- Change 1 only touches the shared gutter helper. `JournalListScreen`'s per-card layout (e.g. the
  photo-grid proportions in `EntryPhotoArea`) and `HelpAboutScreen`'s card layouts already use
  `fillMaxWidth()` internally, so they'll stretch to fill the wider available width automatically
  once the gutter shrinks — no per-card changes needed.
- Change 2 is scoped to the Journal FAB specifically, since it's the only `floatingActionButton` in
  the app (confirmed by search — no other screen has one).
- No new strings; no localization impact.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `macacoContentGutter()` returns `min` (skips the 840dp cap) on genuinely tablet-sized devices (`smallestScreenWidthDp >= 600`) in landscape orientation, so Journal and Help & About fill the full screen width on tablet landscape instead of leaving large centered gutters; phone landscape (incl. large phones like the A53) unchanged | `ui/theme/ContentWidth.kt` |
| 2 | "New Entry" FAB gets `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` so it no longer renders behind the system nav bar in landscape (observed on the Galaxy A53) | `ui/screens/JournalListScreen.kt` |
