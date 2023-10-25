package com.yuroyami.syncplay.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier

@Composable
expect fun InfiniteLottieAnimation(
    modifier: Modifier,
    json: String,
    speed: Float = 1f,
    isplaying: Boolean = true,
    reverseOnEnd: Boolean = false
)

@Composable
expect fun NightModeToggle(modifier: Modifier, state: State<Boolean>)