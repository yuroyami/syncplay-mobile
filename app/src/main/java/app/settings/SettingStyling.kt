package app.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import app.R

class SettingStyling(
    val titleSize: Float = 15f,
    val titleFont: Font = Font(R.font.directive4bold),
    val titleFilling: List<Color>? = listOf(Color.Black),
    val titleStroke: List<Color>? = null,
    val titleShadow: List<Color>? = null,

    val summarySize: Float = 11f,
    val summaryFont: Font = Font(R.font.inter),
    val summaryColor: Color = Color(160, 160, 160),

    val iconSize: Float = 28f,
    val iconTints: List<Color> = listOf(Color.Gray),
    val iconShadows: List<Color> = listOf(Color.DarkGray),

    val paddingUsed: Float = 12f,
)