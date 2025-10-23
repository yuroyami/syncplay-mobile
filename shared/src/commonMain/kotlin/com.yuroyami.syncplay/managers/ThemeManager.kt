@file:Suppress("SpellCheckingInspection")
package com.yuroyami.syncplay.managers

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.SyncplayViewmodel
import com.yuroyami.syncplay.ui.theme.Theming
import kotlinx.coroutines.flow.MutableStateFlow

class ThemeManager(viewmodel: SyncplayViewmodel) : AbstractManager(viewmodel) {

    val currentTheme = MutableStateFlow<Theme>(GREEN_GOBLIN)

    companion object {
        data class Theme(
            val name: String,
            val scheme: ColorScheme,
            val usesSyncplayGradients: Boolean
        )

        val PYNCSLAY = Theme(
            name = "PyncSlay",
            scheme = dynamicColorScheme(
                primary = Theming.SP_CUTE_PINK,
                isDark = true,
                isAmoled = false,
                //tertiary = Theming.SP_YELLOW,
                style = PaletteStyle.TonalSpot,
                specVersion = ColorSpec.SpecVersion.SPEC_2021,
            ),
            usesSyncplayGradients = true
        )

        val AMOLED = Theme(
            name = "BlackOLED",
            usesSyncplayGradients = true,
            scheme = dynamicColorScheme(
                primary = Color.Magenta,
                isDark = true,
                isAmoled = true,
                secondary = null,
                tertiary = null,
                neutral = null,
                neutralVariant = null,
                error = Color.Red,
                style = PaletteStyle.Monochrome,
                contrastLevel = Contrast.Medium.value,
                specVersion = ColorSpec.SpecVersion.SPEC_2021,
            )
        )

        val ALLEY_LAMPPOST = Theme(
            name = "Alley Lamppost",
            usesSyncplayGradients = true,
            scheme = dynamicColorScheme(
                primary = Color(255, 214, 111),
                isDark = true,
                isAmoled = false,
                secondary = Color(35, 35, 35),
                tertiary = Color(35, 35, 35),
                neutral = Color(35, 35, 35),
                neutralVariant = Color.Gray,
                error = Color.Red,
                style = PaletteStyle.Neutral,
                contrastLevel = Contrast.Default.value,
                specVersion = ColorSpec.SpecVersion.SPEC_2021,
            )
        )

        val GREEN_GOBLIN =
            Theme(
                name = "Green Goblin", usesSyncplayGradients = true, scheme = dynamicColorScheme(
                    primary = Color.Green,
                    isDark = true,
                    isAmoled = false,
                    secondary = null,
                    tertiary = null,
                    neutral = null,
                    neutralVariant = null,
                    error = Color.Red,
                    style = PaletteStyle.TonalSpot,
                    contrastLevel = Contrast.Default.value,
                    specVersion = ColorSpec.SpecVersion.SPEC_2021,
                )
            )
    }
}