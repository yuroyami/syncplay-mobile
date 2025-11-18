package com.yuroyami.syncplay.ui.screens.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle


val defaultTheme: SaveableTheme
    get() = PYNCSLAY

val BLANK_THEME = SaveableTheme(
    name = "Untitled theme",
    primaryColor = Color.Blue.toArgb(),
    isDark = true,
    isAMOLED = false,
    secondaryColor = null,
    tertiaryColor = Color.Cyan.toArgb(),
    style = PaletteStyle.TonalSpot,
    syncplayGradients = true
)

/**
 * "PyncSlay" theme - Syncplay's signature pink theme.
 *
 * Features cute pink primary colors with tonal spot palette style
 * and custom Syncplay gradients.
 */
val PYNCSLAY = SaveableTheme(
    name = "PyncSlay",
    primaryColor = -44800,
    secondaryColor = -13794424,
    tertiaryColor = -2654317,
    neutralColor = -29276,
    neutralVariantColor = -16777216,
    contrast = 0.0,
    isAMOLED = true,
    style = PaletteStyle.Rainbow
)

/**
 * "GrayOLED" theme - Pure AMOLED theme optimized for OLED displays.
 *
 * Uses true black backgrounds to take advantage of OLED pixel-off technology
 * for better battery life and contrast.
 */
val GrayOLED = SaveableTheme(
    name = "GrayOLED",
    primaryColor = Color.Black.toArgb(),
    secondaryColor = Color.Black.toArgb(),
    tertiaryColor = Color.Black.toArgb(),
    neutralColor = Color.Black.toArgb(),
    neutralVariantColor = Color.Black.toArgb(),
    contrast = 0.0,
    isDark = true,
    isAMOLED = true,
    style = PaletteStyle.Neutral,
    syncplayGradients = false
)

/**
 * "Alley Lamppost" theme - Warm amber theme with dark gray backgrounds.
 *
 * Inspired by vintage street lighting with warm yellow-gray primary colors
 * against dark, muted backgrounds. Uses neutral palette style.
 */
val ALLEY_LAMP = SaveableTheme(
    name = "Alley Lamp",
    primaryColor = Color(255, 214, 111).toArgb(),
    secondaryColor = Color(35, 35, 35).toArgb(),
    tertiaryColor = Color(35, 35, 35).toArgb(),
    neutralColor = Color(35, 35, 35).toArgb(),
    neutralVariantColor = Color.Gray.toArgb(),
    isDark = true,
    style = PaletteStyle.Neutral,
    syncplayGradients = false
)

val SILVER_LAKE = SaveableTheme(
    name = "Silver Lake",
    primaryColor = Color(68, 112, 173).toArgb(),
    secondaryColor = Color(104, 136, 190).toArgb(),
    tertiaryColor = Color(35, 60, 103).toArgb(),
    neutralColor = Color(204, 219, 238).toArgb(),
    isDark = true,
    style = PaletteStyle.TonalSpot,
    syncplayGradients = false
)