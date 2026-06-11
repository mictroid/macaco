package com.houseofmmminq.macaco.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@Composable
fun WanderlogTheme(
    appTheme: AppTheme = AppTheme.WANDERLOG,
    darkTheme: Boolean = false,
    backgroundImageUri: String? = null,
    content: @Composable () -> Unit
) {
    val base = appTheme.colorScheme(darkTheme)

    // When an image is set, make Scaffold backgrounds transparent so the image shows through.
    // Cards/sheets (which use `surface`) stay opaque for readability.
    val colorScheme = if (backgroundImageUri != null) {
        base.copy(background = Color.Transparent)
    } else {
        base
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (backgroundImageUri != null) {
            // Solid base colour so text remains readable even if image is loading
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = base.background)
            }
            AsyncImage(
                model = backgroundImageUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = if (darkTheme) 0.18f else 0.28f,
            )
        }
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
