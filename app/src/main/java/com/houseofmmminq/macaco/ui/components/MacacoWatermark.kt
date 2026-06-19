package com.houseofmmminq.macaco.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Draws a single line-art macaco icon (goggled monkey face) centred at [cx],[cy] with radius [r].
 * Fine stroke weight scaled to the radius; used to tile a subtle empty-state watermark.
 */
private fun DrawScope.drawMacacoIcon(cx: Float, cy: Float, r: Float, color: Color) {
    val lw = (r * 0.055f).coerceAtLeast(1f)
    val stroke = Stroke(width = lw)

    // Head circle
    drawCircle(color = color, radius = r, center = Offset(cx, cy), style = stroke)

    // Ears — 3 concentric rings per side
    val earCy = cy - r * 0.05f
    for (ex in listOf(cx - r * 0.90f, cx + r * 0.90f)) {
        for (er in listOf(r * 0.34f, r * 0.21f, r * 0.09f)) {
            if (er > lw) {
                drawCircle(color = color, radius = er, center = Offset(ex, earCy), style = stroke)
            }
        }
    }

    // Goggle outer frame ring + filled lens
    val lr = r * 0.32f
    val lox = r * 0.33f
    val loy = cy - r * 0.05f
    for (gx in listOf(cx - lox, cx + lox)) {
        drawCircle(color = color, radius = lr + lw * 2f, center = Offset(gx, loy), style = stroke)
        drawCircle(color = color, radius = lr, center = Offset(gx, loy), style = Fill)
    }

    // Bridge between lenses
    drawRect(
        color = color,
        topLeft = Offset(cx - lox + lr, loy - lw),
        size = Size((lox - lr) * 2f, lw * 2f)
    )

    // Muzzle oval
    val mw = r * 0.30f
    val mh = r * 0.20f
    val my = cy + r * 0.42f
    drawOval(
        color = color,
        topLeft = Offset(cx - mw, my - mh),
        size = Size(mw * 2f, mh * 2f),
        style = stroke
    )

    // Nostrils — two filled dots
    val nd = (r * 0.065f).coerceAtLeast(lw)
    val ndx = mw * 0.42f
    val ndy = mh * 0.15f
    drawCircle(color = color, radius = nd, center = Offset(cx - ndx, my + ndy), style = Fill)
    drawCircle(color = color, radius = nd, center = Offset(cx + ndx, my + ndy), style = Fill)
}

/**
 * A [Box] that tiles faint macaco icons behind [content] as an empty-state watermark. Icons sit
 * on a staggered grid — odd rows offset by half the horizontal spacing — so the pattern reads as
 * a diamond lattice rather than a random scatter. Every icon is the same size at the same alpha,
 * so the layout needs no randomness to stay stable across recompositions.
 * Icons use the theme's primary colour, so the pattern adapts to all themes.
 */
@Composable
fun MacacoWatermarkBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val spacingX = 78.dp.toPx() // 130dp x 0.6
            val spacingY = 54.dp.toPx() // 90dp x 0.6
            val iconR = 8.dp.toPx() // radius = 8dp (halved from 16dp)
            val alpha = 0.16f // 16% opacity — clearly visible, uniform

            var row = 0
            var y = -spacingY * 0.5f
            while (y < size.height + spacingY) {
                val xOffset = if (row % 2 == 1) spacingX * 0.5f else 0f
                var x = -spacingX + xOffset
                while (x < size.width + spacingX) {
                    drawMacacoIcon(x, y, iconR, primaryColor.copy(alpha = alpha))
                    x += spacingX
                }
                y += spacingY
                row++
            }
        }

        content()
    }
}
