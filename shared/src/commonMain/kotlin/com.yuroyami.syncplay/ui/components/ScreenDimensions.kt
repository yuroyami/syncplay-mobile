package com.yuroyami.syncplay.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo

val screenHeightPx: Int
    @Composable get() = LocalWindowInfo.current.containerSize.height

val screenWidthPx: Int
    @Composable get() = LocalWindowInfo.current.containerSize.width