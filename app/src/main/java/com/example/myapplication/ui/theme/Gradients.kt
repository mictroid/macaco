package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Colors for decorative "hero" gradients (emoji headers / banners).
 *
 * In dark mode the M3 container colors are already deep and saturated, so they read as rich. In
 * light mode those same containers are pale pastels, which look washed out — so we anchor the
 * gradient on the vibrant [primary] accent (fading into its soft container tint) to give the light
 * theme the same punch as dark. Use only behind non-text content (emoji/imagery): the light variant
 * is too saturated for `onPrimaryContainer` body text.
 */
@Composable
@ReadOnlyComposable
fun heroGradientColors(): List<Color> {
    val cs = MaterialTheme.colorScheme
    return if (cs.surface.luminance() > 0.5f) {
        listOf(cs.primary, cs.primaryContainer)
    } else {
        listOf(cs.primaryContainer, cs.secondaryContainer)
    }
}

/** True when the active scheme is a light one (used to decide banner styling). */
@Composable
@ReadOnlyComposable
fun isLightTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() > 0.5f
