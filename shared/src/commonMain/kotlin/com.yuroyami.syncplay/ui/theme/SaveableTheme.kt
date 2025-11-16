package com.yuroyami.syncplay.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.Contrast
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
    val contrast: Contrast = Contrast.Default,
    val isDark: Boolean = false,
    val isAMOLED: Boolean = false,
    val style: PaletteStyle = PaletteStyle.TonalSpot,
    val syncplayGradients: Boolean = true
) {
    fun asString() = Json.encodeToString(this)

    val dynamicScheme: ColorScheme by lazy {
        dynamicColorScheme(
            primary = Color(primaryColor),
            secondary = secondaryColor?.let { Color(it) },
            tertiary = tertiaryColor?.let { Color(it) },
            contrastLevel = contrast.value,
            specVersion = ColorSpec.SpecVersion.SPEC_2021,
            error = Color.Red,
            neutral = neutralColor?.let { Color(it) },
            neutralVariant = neutralVariantColor?.let { Color(it) },
            isDark = isDark,
            isAmoled = isAMOLED,
            style = style
        )
    }

    companion object {
        fun String.toTheme(): SaveableTheme = Json.decodeFromString(this)
    }
}