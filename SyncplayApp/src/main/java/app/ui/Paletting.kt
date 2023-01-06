package app.ui

import androidx.compose.ui.graphics.Color

object Paletting {

    /** Main Syncplay Colors, found in logo */
    val SP_YELLOW = Color(255, 198, 4)
    val SP_ORANGE = Color(255, 113, 54)
    val SP_PINK = Color(255, 40, 97)
    val SP_GRADIENT = listOf(SP_YELLOW, SP_ORANGE, SP_PINK)

    /** More Colors */
    val SP_PALE = Color(255, 233, 166)
    val SP_CUTE_PINK = Color(255, 92, 135)
    val SP_INTENSE_PINK = Color(255, 21, 48)

    val SP_TEXT_GRAY = Color(122, 122, 122)
    val SP_TEXT_DARKGRAY = Color(90, 90, 90)

    val BG_DARK_1 = Color(35, 35, 35)
    val BG_DARK_2 = Color(50, 50, 50)
    val BG_Gradient_DARK = listOf(BG_DARK_1, BG_DARK_2, BG_DARK_1)

    val BG_LIGHT_1 = Color(150, 150, 150)
    val BG_LIGHT_2 = Color(247, 247, 247)
    val BG_Gradient_LIGHT = listOf(BG_LIGHT_1, BG_LIGHT_2, BG_LIGHT_1)

    val SHADER_GRADIENT = listOf(
        Color.Transparent,
        Color(0, 0, 0, 100),
        Color(0, 0, 0, 100),
        Color(0, 0, 0, 100),
        Color(0, 0, 0, 100),
        Color(0, 0, 0, 100),
        //Color(0,0,0, 255),
        Color(0, 0, 0, 100),
        Color.Transparent
    )

    /** dimensions */
    const val ROOM_ICON_SIZE = 38

}