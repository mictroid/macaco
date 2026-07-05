package com.houseofmmminq.macaco.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.ui.theme.MacacoFontFamily
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == 3
    // Short screens (phone landscape) get smaller art + tighter spacing so intro copy has room
    // once bottomControlsHeightPx below is reserved — otherwise centring across the full height
    // pushes content down into the dots/button.
    val isLandscape = LocalConfiguration.current.screenHeightDp < 480
    val density = LocalDensity.current
    var bottomControlsHeightPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(macacoBrandBackground())
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        HorizontalPager(
            state = pagerState,
            // Reserve space for the bottom dots/button (measured below) so centred content never
            // runs into them — was the direct cause of intro text overlapping "Next" in landscape.
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = with(density) { bottomControlsHeightPx.toDp() })
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (page) {
                    0 -> {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(if (isLandscape) 84.dp else 140.dp)
                        )
                        Spacer(Modifier.height(if (isLandscape) 12.dp else 32.dp))
                        Text(
                            "macaco",
                            color = SplashGoldBright,
                            fontSize = if (isLandscape) 28.sp else 40.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = MacacoFontFamily,
                            letterSpacing = 10.sp
                        )
                        Spacer(Modifier.height(if (isLandscape) 6.dp else 12.dp))
                        Text(
                            "Roam Freely. Forget Nothing.",
                            color = SplashGold.copy(alpha = 0.80f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = MacacoFontFamily,
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    1 -> OnboardingSlide(
                        icon = Icons.Outlined.Cloud,
                        title = "Your journal,\nalways with you",
                        body = "Every memory syncs to the cloud instantly. Write on your phone, relive anywhere.",
                        compact = isLandscape
                    )
                    2 -> OnboardingSlide(
                        icon = Icons.Outlined.Shield,
                        title = "Private.\nYours. Forever.",
                        body = "No ads. No social feed. One purchase — lifetime access, no subscription.",
                        compact = isLandscape
                    )
                    3 -> OnboardingSlide(
                        icon = Icons.Outlined.Explore,
                        title = "Ready to roam?",
                        body = "Sign in to start capturing your adventures.",
                        compact = isLandscape
                    )
                }
            }
        }

        // Bottom controls — onSizeChanged (placed before the bottom padding in the modifier
        // chain, so it captures the padding too) feeds bottomControlsHeightPx above.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomControlsHeightPx = it.height }
                .padding(bottom = if (isLandscape) 16.dp else 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { i ->
                    val dotWidth by animateDpAsState(
                        targetValue = if (pagerState.currentPage == i) 24.dp else 8.dp,
                        animationSpec = tween(200),
                        label = "dot_$i"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == i) SplashGoldBright
                                else SplashGold.copy(alpha = 0.35f)
                            )
                    )
                }
            }

            Spacer(Modifier.height(if (isLandscape) 16.dp else 28.dp))

            Button(
                onClick = {
                    if (isLastPage) onComplete()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SplashGoldBright,
                    contentColor = Color(0xFF1A0D00)
                )
            ) {
                Text(
                    if (isLastPage) "Get Started" else "Next",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    fontFamily = MacacoFontFamily
                )
            }
        }

        // Skip — declared last so it sits on top of the full-screen pager in the Box's z-order
        // and actually receives taps. When declared before the pager it was drawn behind it, so
        // the pager intercepted the touches and Skip appeared dead.
        if (!isLastPage) {
            TextButton(
                onClick = onComplete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(
                    "Skip",
                    color = SplashGold.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontFamily = MacacoFontFamily
                )
            }
        }
    }
}

@Composable
private fun OnboardingSlide(icon: ImageVector, title: String, body: String, compact: Boolean = false) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = SplashGold,
        modifier = Modifier.size(if (compact) 56.dp else 88.dp)
    )
    Spacer(Modifier.height(if (compact) 16.dp else 32.dp))
    Text(
        title,
        color = SplashGoldBright,
        fontSize = if (compact) 20.sp else 26.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = MacacoFontFamily,
        textAlign = TextAlign.Center,
        lineHeight = if (compact) 26.sp else 34.sp
    )
    Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
    Text(
        body,
        color = SplashGold.copy(alpha = 0.80f),
        fontSize = if (compact) 13.sp else 15.sp,
        fontWeight = FontWeight.Light,
        fontFamily = MacacoFontFamily,
        textAlign = TextAlign.Center,
        lineHeight = if (compact) 18.sp else 22.sp,
        letterSpacing = 0.3.sp
    )
}
