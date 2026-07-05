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
