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
