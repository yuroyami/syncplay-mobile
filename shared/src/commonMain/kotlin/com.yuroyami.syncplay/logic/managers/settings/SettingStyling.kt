package com.yuroyami.syncplay.logic.managers.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font

data class SettingStyling(
    val titleSize: Float = 16f,
    val titleFont: Font? = null, //{ composeRes.fonts.Directive4.bold.asFont()!! },
    val titleFilling: List<Color>? = listOf(Color.Black),
    val titleStroke: List<Color>? = null,
    val titleShadow: List<Color>? = null,

    val summarySize: Float = 14f,
    val summaryFont: Font? = null, //= { composeRes.fonts.Inter.regular.asFont()!! },
    val summaryColor: Color = Color(160, 160, 160),

    val iconSize: Float = 28f,
    val iconTints: List<Color> = listOf(Color.Gray),
    val iconShadows: List<Color> = listOf(Color.DarkGray),

    val paddingUsed: Float = 12f,
)