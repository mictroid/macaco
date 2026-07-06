package com.houseofmmminq.macaco.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.ui.screens.SplashGoldBright

/** Single fixed icon size for the brand block in every state — matches Journal's header. */
private val MacacoBrandIconSize = 48.dp

/**
 * Shared "macaco" brand block (icon + wordmark), used by every top-level screen's header so the
 * icon size and wordmark style can never drift out of sync again. Sizes are fixed to Journal's
 * current header (the canonical reference); only the trailing page-label/count content is
 * per-screen, passed in via [portraitTrailing] / [landscapeTrailing].
 *
 * - [collapsed]: icon alone, centred — no wordmark. Used for scroll-collapsed or very short
 *   headers.
 * - [isLandscape] (ignored if [collapsed]): icon above a single Row of "macaco" + [landscapeTrailing].
 * - Otherwise (portrait, expanded): icon above a Column of "macaco" + [portraitTrailing], pulled
 *   up 10dp so it sits snug under the icon (matches Journal).
 */
@Composable
fun MacacoBrandBlock(
    isLandscape: Boolean,
    collapsed: Boolean = false,
    modifier: Modifier = Modifier,
    portraitTrailing: @Composable ColumnScope.() -> Unit = {},
    landscapeTrailing: @Composable RowScope.() -> Unit = {},
) {
    when {
        collapsed -> {
            Box(
                modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(MacacoBrandIconSize)
                )
            }
        }
        isLandscape -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(MacacoBrandIconSize)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "macaco",
                        color = SplashGoldBright,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 4.sp
                    )
                    landscapeTrailing()
                }
            }
        }
        else -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(MacacoBrandIconSize)
                )
                Column(
                    modifier = Modifier.offset(y = (-10).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "macaco",
                        color = SplashGoldBright,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 3.sp
                    )
                    portraitTrailing()
                }
            }
        }
    }
}
