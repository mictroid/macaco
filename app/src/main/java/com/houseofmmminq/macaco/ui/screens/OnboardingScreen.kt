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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(macacoBrandBackground())
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
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

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
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
                            modifier = Modifier.size(140.dp)
                        )
                        Spacer(Modifier.height(32.dp))
                        Text(
                            "macaco",
                            color = SplashGoldBright,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = MacacoFontFamily,
                            letterSpacing = 10.sp
                        )
                        Spacer(Modifier.height(12.dp))
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
                        body = "Every memory syncs to the cloud instantly. Write on your phone, relive anywhere."
                    )
                    2 -> OnboardingSlide(
                        icon = Icons.Outlined.Shield,
                        title = "Private.\nYours. Forever.",
                        body = "No ads. No social feed. One purchase — lifetime access, no subscription."
                    )
                    3 -> OnboardingSlide(
                        icon = Icons.Outlined.Explore,
                        title = "Ready to roam?",
                        body = "Sign in to start capturing your adventures."
                    )
                }
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
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

            Spacer(Modifier.height(28.dp))

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
    }
}

@Composable
private fun OnboardingSlide(icon: ImageVector, title: String, body: String) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = SplashGold,
        modifier = Modifier.size(88.dp)
    )
    Spacer(Modifier.height(32.dp))
    Text(
        title,
        color = SplashGoldBright,
        fontSize = 26.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = MacacoFontFamily,
        textAlign = TextAlign.Center,
        lineHeight = 34.sp
    )
    Spacer(Modifier.height(16.dp))
    Text(
        body,
        color = SplashGold.copy(alpha = 0.80f),
        fontSize = 15.sp,
        fontWeight = FontWeight.Light,
        fontFamily = MacacoFontFamily,
        textAlign = TextAlign.Center,
        lineHeight = 22.sp,
        letterSpacing = 0.3.sp
    )
}
