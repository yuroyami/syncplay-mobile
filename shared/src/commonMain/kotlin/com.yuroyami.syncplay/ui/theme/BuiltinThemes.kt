package com.yuroyami.syncplay.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle

val BLANK_THEME = SaveableTheme(
    name = "Untitled theme",
    primaryColor = Color.Blue.toArgb(),
    isDark = false,
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
    primaryColor = Theming.SP_CUTE_PINK.toArgb(),
    isDark = true,
    style = PaletteStyle.TonalSpot
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
    style = PaletteStyle.Neutral
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
    style = PaletteStyle.Neutral
)

/**
 * "Green Goblin" theme (default) - Vibrant green theme.
 *
 * Features bright green primary colors with tonal spot palette style.
 */
val GREEN_GOBLIN = SaveableTheme(
    name = "Green Goblin",
    primaryColor = Color.Green.toArgb(),
    isDark = true,
    style = PaletteStyle.TonalSpot,
)