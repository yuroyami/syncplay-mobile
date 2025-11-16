@file:Suppress("SpellCheckingInspection")

package com.yuroyami.syncplay.managers

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.MISC_ALL_THEMES
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.MISC_CURRENT_THEME
import com.yuroyami.syncplay.managers.datastore.valueFlow
import com.yuroyami.syncplay.managers.datastore.valueSuspendingly
import com.yuroyami.syncplay.managers.datastore.writeValue
import com.yuroyami.syncplay.ui.theme.SaveableTheme
import com.yuroyami.syncplay.ui.theme.Theming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Manages application theming and color schemes.
 *
 * Provides a collection of predefined themes using Material Design 3 color schemes
 * with dynamic color generation. Tracks the currently active theme.
 *
 * @param viewmodel The parent ViewModel that owns this manager
 */
class ThemeManager(val viewmodel: ViewModel) : AbstractManager(viewmodel) {

    /**
     * The currently active theme.
     */
    val currentTheme = valueFlow(MISC_CURRENT_THEME, ALLEY_LAMP.asString())
        .stateIn(scope = viewmodel.viewModelScope, started = Eagerly, ALLEY_LAMP.asString())

    val customThemes: StateFlow<List<SaveableTheme>> = valueFlow(MISC_ALL_THEMES, "[]")
        .map { Json.decodeFromString<List<SaveableTheme>>(it) }
        .stateIn(scope = viewmodel.viewModelScope, started = Eagerly, listOf())

    fun changeTheme(theme: SaveableTheme) {
        viewmodel.viewModelScope.launch(Dispatchers.IO) {
            writeValue(MISC_CURRENT_THEME, theme.asString())
        }
    }

    fun saveNewTheme(theme: SaveableTheme) {
        viewmodel.viewModelScope.launch(Dispatchers.IO) {
            val customThemeListJson = valueSuspendingly(MISC_ALL_THEMES, "[]")
            val list = Json.decodeFromString<List<SaveableTheme>>(customThemeListJson).toMutableList()
            list.add(theme)
            val listEncodedAgain = Json.encodeToString(list)
            writeValue(MISC_ALL_THEMES, listEncodedAgain)

            changeTheme(theme)
        }
    }

    companion object {
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
         * "BlackOLED" theme - Pure black theme optimized for OLED displays.
         *
         * Uses true black backgrounds to take advantage of OLED pixel-off technology
         * for better battery life and contrast. Features monochrome palette style.
         */
        val BLACKOLED = SaveableTheme(
            name = "BlackOLED",
            primaryColor = Color.Magenta.toArgb(),
            contrast = Contrast.Medium,
            isDark = true,
            isAMOLED = true,
            style = PaletteStyle.Monochrome
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
    }
}