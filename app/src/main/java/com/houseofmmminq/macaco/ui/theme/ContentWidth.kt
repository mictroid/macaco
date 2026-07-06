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
