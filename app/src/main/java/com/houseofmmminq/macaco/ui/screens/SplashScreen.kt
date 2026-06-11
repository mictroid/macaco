package com.houseofmmminq.macaco.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.houseofmmminq.macaco.R
import kotlinx.coroutines.delay

// Brand colours sampled from macaco_splash.svg. Shared with AppLockScreen.
internal val SplashTealCenter = Color(0xFF1C6C7E)
internal val SplashTealMid = Color(0xFF0A4A58)
internal val SplashTealEdge = Color(0xFF042830)
internal val SplashGoldBright = Color(0xFFF0C840)
internal val SplashGold = Color(0xFFE8B020)

/** The deep-ocean teal radial gradient that backs the splash and lock screens. */
internal fun macacoBrandBackground(): Brush = Brush.radialGradient(
    0f to SplashTealCenter,
    0.48f to SplashTealMid,
    1f to SplashTealEdge
)

private const val LOGO_FADE_IN_MS = 850
private const val TEXT_FADE_IN_MS = 750
private const val TEXT_FADE_IN_DELAY_MS = 350L   // wordmark/tagline trail the logo
private const val HOLD_MS = 2300L                // fully-revealed dwell time (~5s total splash)
private const val FADE_OUT_MS = 750              // dissolve into the app

/**
 * Branded launch screen: the Macaco monkey on a deep-ocean teal gradient with the wordmark and
 * tagline, matching macaco_splash.svg.
 *
 * Sequence: the logo fades in while scaling up subtly, the wordmark + tagline trail in just after,
 * a short hold, then everything dissolves out before [onFinished] hands control to the app.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val currentOnFinished by rememberUpdatedState(onFinished)
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.86f) }
    val textAlpha = remember { Animatable(0f) }
    val rootAlpha = remember { Animatable(1f) }   // drives the closing fade-out

    LaunchedEffect(Unit) {
        logoScale.animateTo(1f, animationSpec = tween(LOGO_FADE_IN_MS, easing = EaseOutCubic))
    }
    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, animationSpec = tween(LOGO_FADE_IN_MS, easing = LinearEasing))
        delay(TEXT_FADE_IN_DELAY_MS)
        textAlpha.animateTo(1f, animationSpec = tween(TEXT_FADE_IN_MS, easing = LinearEasing))
        delay(HOLD_MS)
        rootAlpha.animateTo(0f, animationSpec = tween(FADE_OUT_MS, easing = LinearEasing))
        currentOnFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(macacoBrandBackground()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            // Closing fade dissolves the branding while the teal stays put, so the hand-off to the
            // app never flashes the bare window background.
            modifier = Modifier.alpha(rootAlpha.value)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(220.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "macaco",
                color = SplashGoldBright,
                fontSize = 44.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 8.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha.value)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Roam Freely. Forget Nothing.",
                color = SplashGold.copy(alpha = 0.82f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha.value)
            )
        }
    }
}
