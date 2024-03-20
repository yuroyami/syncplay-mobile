package com.yuroyami.syncplay.models

import androidx.compose.ui.graphics.Color

data class MessagePalette(
    val timestampColor: Color,
    val selftagColor: Color,
    val friendtagColor: Color,
    val systemmsgColor: Color,
    val usermsgColor: Color,
    val errormsgColor: Color,
    val includeTimestamp: Boolean = true
)