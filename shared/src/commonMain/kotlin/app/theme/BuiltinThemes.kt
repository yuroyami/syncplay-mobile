package app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle


val defaultTheme: SaveableTheme
    get() = SILVER_LAKE

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

/** Signature pink AMOLED theme with the Rainbow palette style. */
val PYNCSLAY = SaveableTheme(
    name = "PyncSlay",
    primaryColor = -44800,
    secondaryColor = -13794424,
    tertiaryColor = -2654317,
    neutralColor = -29276,
    neutralVariantColor = -16777216,
    contrast = 0.0,
    isAMOLED = true,
    style = PaletteStyle.Rainbow,
    syncplayGradients = false
)

/** Pure AMOLED theme: true-black backgrounds across all color slots. */
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

/** Warm amber primary over dark-gray backgrounds, Neutral palette style. */
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
    primaryColor = Color(179, 179, 179, 255).toArgb(),
    secondaryColor = Color(104, 136, 190).toArgb(),
    tertiaryColor = Color(61, 80, 113, 255).toArgb(),
    neutralColor = Color(204, 219, 238).toArgb(),
    isDark = true,
    style = PaletteStyle.Neutral,
    syncplayGradients = true
)