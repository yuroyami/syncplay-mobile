package app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle


val defaultTheme: SaveableTheme
    get() = TRINITY

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

/** The flagship theme: the Trinity brand colors (ultraviolet/orchid/coral) seeded into a dark scheme. */
val TRINITY = SaveableTheme(
    name = "Trinity",
    primaryColor = Theming.NeoSP1.toArgb(),
    secondaryColor = Theming.NeoSP2.toArgb(),
    tertiaryColor = Theming.NeoSP3.toArgb(),
    contrast = 0.0,
    isDark = true,
    isAMOLED = false,
    style = PaletteStyle.TonalSpot,
    syncplayGradients = true
)

/** Light counterpart of Trinity: same brand seeds on a light scheme. */
val DAYLIGHT = SaveableTheme(
    name = "Daylight",
    primaryColor = Theming.NeoSP1.toArgb(),
    secondaryColor = Theming.NeoSP2.toArgb(),
    tertiaryColor = Theming.NeoSP3.toArgb(),
    contrast = 0.0,
    isDark = false,
    isAMOLED = false,
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

/** Pure AMOLED theme: true-black backgrounds with a silver accent so controls stay visible. */
val GrayOLED = SaveableTheme(
    name = "GrayOLED",
    primaryColor = Color(0xFFCFCFCF).toArgb(),
    secondaryColor = Color(0xFF9E9E9E).toArgb(),
    tertiaryColor = Color(0xFF8A8A8A).toArgb(),
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
