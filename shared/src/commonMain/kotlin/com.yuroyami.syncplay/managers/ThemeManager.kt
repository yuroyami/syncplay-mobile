@file:Suppress("SpellCheckingInspection")
package com.yuroyami.syncplay.managers

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.ui.theme.Theming
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Manages application theming and color schemes.
 *
 * Provides a collection of predefined themes using Material Design 3 color schemes
 * with dynamic color generation. Tracks the currently active theme.
 *
 * @param viewmodel The parent ViewModel that owns this manager
 */
class ThemeManager(viewmodel: ViewModel) : AbstractManager(viewmodel) {

    /**
     * The currently active theme.
     */
    val currentTheme = MutableStateFlow<Theme>(ALLEY_LAMPPOST)

    companion object {
        /**
         * Represents a complete theme configuration.
         *
         * @property name The display name of the theme
         * @property scheme The Material Design 3 color scheme
         * @property usesSyncplayGradients Whether this theme uses Syncplay's custom gradient backgrounds
         */
        data class Theme(
            val name: String,
            val scheme: ColorScheme,
            val usesSyncplayGradients: Boolean
        )

        /**
         * "PyncSlay" theme - Syncplay's signature pink theme.
         *
         * Features cute pink primary colors with tonal spot palette style
         * and custom Syncplay gradients.
         */
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

        /**
         * "BlackOLED" theme - Pure black theme optimized for OLED displays.
         *
         * Uses true black backgrounds to take advantage of OLED pixel-off technology
         * for better battery life and contrast. Features monochrome palette style.
         */
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

        /**
         * "Alley Lamppost" theme - Warm amber theme with dark gray backgrounds.
         *
         * Inspired by vintage street lighting with warm yellow-gray primary colors
         * against dark, muted backgrounds. Uses neutral palette style.
         */
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

        /**
         * "Green Goblin" theme (default) - Vibrant green theme.
         *
         * Features bright green primary colors with tonal spot palette style.
         */
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