package com.yuroyami.syncplay.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Paletting {

    /** Main Syncplay Colors, found in logo */
    val SP_YELLOW = Color(255, 198, 4)
    val SP_ORANGE = Color(255, 113, 54)
    val SP_PINK = Color(255, 40, 97)
    val SP_GRADIENT = listOf(SP_YELLOW, SP_ORANGE, SP_PINK)

    /** Default in-room colors for messages */
    val MSG_SELF_TAG = Color(204, 36, 36, 255)
    val MSG_FRIEND_TAG = Color(96, 130, 182)
    val MSG_SYSTEM = Color(230, 230, 230)
    val MSG_ERROR = Color(150, 20, 20, 255)
    val MSG_CHAT = Color.White
    val MSG_TIMESTAMP = Color(255, 95, 135)

    /** Some room widget colors */
    val ROOM_USER_READY_ICON = Color(130, 203, 120, 255)
    val ROOM_USER_UNREADY_ICON = Color(247, 92, 94, 255)
    val ROOM_USER_USER_TEXT_SELF = Color(236, 191, 57)


    val OLD_SP_YELLOW = Color(255, 214, 111)
    val OLD_SP_PINK = Color(239, 100, 147)

    /** More Colors */
    val SP_PALE = Color(255, 233, 166)
    val SP_CUTE_PINK = Color(255, 92, 135)
    val SP_INTENSE_PINK = Color(255, 21, 48)

    val SP_TEXT_GRAY = Color(122, 122, 122)
    val SP_TEXT_DARKGRAY = Color(90, 90, 90)

    val BG_DARK_1 = Color(35, 35, 35)
    val BG_DARK_2 = Color(50, 50, 50)
    val BG_Gradient_DARK = listOf(BG_DARK_1, BG_DARK_2, BG_DARK_1)

    val BG_LIGHT_1 = Color(141, 141, 141, 255)
    val BG_LIGHT_2 = Color(223, 220, 220, 255)
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
    const val USER_INFO_IC_SIZE = 16
    const val USER_INFO_TXT_SIZE = 10


    @Composable
    fun backgroundGradient(): List<Color> {
        return listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background
        )
    }

    @Composable
    fun isInDarkMode(): Boolean {
        return MaterialTheme.colorScheme.primary == md_theme_dark_primary
    }
}