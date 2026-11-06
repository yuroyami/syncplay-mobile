package app.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SaveableTheme(
    val name: String,
    val primaryColor: Int,
    val secondaryColor: Int? = null,
    val tertiaryColor: Int? = null,
    val neutralColor: Int? = null,
    val neutralVariantColor: Int? = null,
    val contrast: Double = 0.0,
    val isDark: Boolean = true,
    val isAMOLED: Boolean = false,
    val style: PaletteStyle = PaletteStyle.TonalSpot,
    /**
     * Nothing reads this. Every brand ramp takes the theme's seeds that sit close in hue
     * ([buildBrandRamp]). The field stays so that saved themes still decode.
     */
    val syncplayGradients: Boolean = true
) {
    fun asString() = Json.encodeToString(this)

    val dynamicScheme: ColorScheme by lazy {
        dynamicColorScheme(
            primary = Color(primaryColor),
            secondary = secondaryColor?.let { Color(it) },
            tertiary = tertiaryColor?.let { Color(it) },
            contrastLevel = contrast,
            specVersion = ColorSpec.SpecVersion.SPEC_2021,
            neutral = neutralColor?.let { Color(it) },
            neutralVariant = neutralVariantColor?.let { Color(it) },
            isDark = isDark,
            isAmoled = isAMOLED,
            style = style
        )
    }

    /** The three seeds of the brand ramp. A missing seed falls back to the brand colour. */
    private val rampSeeds: List<Color> by lazy {
        listOf(
            Color(primaryColor),
            secondaryColor?.let { Color(it) } ?: Theming.NeoSP2,
            tertiaryColor?.let { Color(it) } ?: Theming.NeoSP3,
        )
    }

    /** The brand ramp of this theme on its own ground ([buildBrandRamp]), built once. */
    val brandRamp: List<Color> by lazy { buildBrandRamp(rampSeeds, lightInk = !isDark) }

    /** The brand ramp for dark ink. The room over the video is always dark, so it uses this ramp. */
    val videoRamp: List<Color> by lazy { if (isDark) brandRamp else buildBrandRamp(rampSeeds, lightInk = false) }

    companion object {
        fun String.toTheme(): SaveableTheme = Json.decodeFromString(this)
    }
}