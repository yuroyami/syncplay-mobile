package com.yuroyami.syncplay.ui.screens.room.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color


@Composable
fun BlackContrastUnderlay(modifier: Modifier = Modifier) {
    val blacky = Color.Black.copy(alpha = 0.7F)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Transparent,Color.Transparent,
                        Color.Transparent,Color.Transparent,Color.Transparent,
                        Color.Transparent, Color.Transparent, Color.Transparent,
                        blacky, blacky)
                )
            )
    )
}