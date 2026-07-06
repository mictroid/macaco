# Macaco — Extend Full-Width Landscape Content to Phone (v2)

Follow-up to `docs/DONE/code-brief-content-width-landscape.md` (shipped vc61), which made
`macacoContentGutter()` skip the 840dp content cap in landscape, but only on genuinely
tablet-sized devices (`smallestScreenWidthDp >= 600`) — phone landscape (including the Galaxy
A53) was deliberately left on the old cap logic. Now extending the same full-width treatment to
phone landscape too: remove the tablet-only gate so *any* landscape orientation skips the cap,
phone or tablet. This is purely a change to the extra visual gutter — it does not touch, and must
not regress, the separate safe-area insets that already protect Journal/Help & About content and
the Journal FAB from the A53's system nav bar (which sits on the side edge in landscape) and
camera cutout (both handled by `WindowInsets.safeDrawing`, applied independently of the gutter —
see `docs/DONE/code-brief-content-width-landscape.md` Change 2 and
`docs/DONE/code-brief-landscape-layout-consistency-v2.md` Change 4). Touches
`ui/theme/ContentWidth.kt` only.

---

## Change: drop the tablet-only gate — any landscape screen skips the 840dp cap

**Problem:** `macacoContentGutter()` currently only skips the cap when
`config.screenWidthDp > config.screenHeightDp && config.smallestScreenWidthDp >= 600` — i.e. only
on tablets. Phone landscape falls through to `(screenWidth - 840dp) / 2`, which on the A53
(landscape `screenWidthDp` ≈ 873dp) computes a gutter only marginally above the 16dp minimum, but
the intent now is to make this unconditional: full-width content in landscape regardless of
device class, matching what tablet landscape already gets.

**Fix:** Remove the `smallestScreenWidthDp >= 600` condition — check orientation only
(`screenWidthDp > screenHeightDp`) and return `min` whenever true, on any device.

**Not in scope / already handled separately:** this function only controls the extra *visual*
centering gutter. It does not add or remove any system-bar/cutout protection — that's the
`WindowInsets.safeDrawing` handling already in `JournalListScreen`'s content `Column` and
`floatingActionButton`, and in `HelpAboutScreen`'s (default) `Scaffold` padding, all independent of
this function and untouched by this change. So on the A53 in landscape, content and the FAB stay
clear of the side nav bar and camera cutout exactly as they do today — this change only removes
the extra ~16dp-ish centering margin on top of that existing protection.

```
BEFORE (A53 landscape — old cap logic,        AFTER (A53 landscape — same treatment
gutter ~16-17dp, computed from the cap)       as tablet: unconditional min gutter)
┌──────────────────────────────────────┐      ┌──────────────────────────────────────┐
│ inset │ content (≈840dp cap) │ inset │      │ inset │  content fills full width │ inset │
└──────────────────────────────────────┘      └──────────────────────────────────────┘
  ↑ safeDrawing inset (nav bar / camera)          ↑ same safeDrawing inset — unchanged
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

### AFTER

```kotlin
package com.houseofmmminq.macaco.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Max width for single-column list content (Material 3 medium-breakpoint pane width).
 * Shared by JournalListScreen and HelpAboutScreen so list screens line up in portrait — but the
 * cap is skipped in landscape orientation entirely (phone or tablet), where it only ever left
 * empty gutters with no content benefit, since there's no second pane to balance against. This
 * does NOT affect the separate `WindowInsets.safeDrawing` handling that keeps content and the
 * Journal FAB clear of the system nav bar / camera cutout on devices like the Galaxy A53 — that
 * protection lives in JournalListScreen/HelpAboutScreen independently of this gutter.
 */
val MacacoContentMaxWidth: Dp = 840.dp

/**
 * Horizontal gutter that centres content at [MacacoContentMaxWidth]: never less than [min],
 * growing symmetrically once the screen is wider than the cap. Skipped entirely (always
 * returns [min]) in landscape orientation, on any device — content fills the screen width there
 * instead, same as portrait already does (portrait width rarely exceeds the cap, so it was
 * already effectively full-width there).
 */
@Composable
fun macacoContentGutter(min: Dp = 16.dp): Dp {
    val config = LocalConfiguration.current
    val isLandscape = config.screenWidthDp > config.screenHeightDp
    if (isLandscape) return min
    val screenWidth = config.screenWidthDp.dp
    val fromCap = (screenWidth - MacacoContentMaxWidth) / 2
    return if (fromCap > min) fromCap else min
}
```

No import changes — `LocalConfiguration` is already imported.

**File:** `ui/theme/ContentWidth.kt`

---

## Out of scope / intentional

- Portrait is unchanged — the cap still exists there for the rare case of a very wide portrait
  screen.
- Does not touch `JournalListScreen.kt`'s FAB inset fix or its content `Column`'s
  `WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)` handling, nor
  `HelpAboutScreen.kt`'s default `Scaffold` insets — all of that safe-area protection is
  orthogonal to this gutter change and stays exactly as shipped in vc61.
- No new strings; no localization impact.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `macacoContentGutter()` drops the `smallestScreenWidthDp >= 600` gate, returning `min` in landscape unconditionally (phone or tablet), so Journal and Help & About fill the full width in phone landscape too — safe-area insets for the A53's nav bar/camera cutout are unaffected (handled separately) | `ui/theme/ContentWidth.kt` |
