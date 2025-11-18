@file:Suppress("SpellCheckingInspection")

package com.yuroyami.syncplay.managers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.managers.preferences.Preferences.CURRENT_THEME
import com.yuroyami.syncplay.managers.preferences.Preferences.CUSTOM_THEMES
import com.yuroyami.syncplay.managers.preferences.flow
import com.yuroyami.syncplay.managers.preferences.set
import com.yuroyami.syncplay.managers.preferences.value
import com.yuroyami.syncplay.ui.screens.theme.SaveableTheme
import com.yuroyami.syncplay.ui.screens.theme.SaveableTheme.Companion.toTheme
import com.yuroyami.syncplay.ui.screens.theme.defaultTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.SharingStarted.Companion.Lazily
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val currentTheme: StateFlow<SaveableTheme> = CURRENT_THEME.flow()
        .flowOn(Dispatchers.IO)
        .map { it.toTheme() }
        .stateIn(scope = viewmodel.viewModelScope, started = Eagerly, defaultTheme)

    val customThemes = CUSTOM_THEMES.flow()
        .flowOn(Dispatchers.IO)
        .map { stringSet ->
            stringSet.map { it.toTheme() }.toList()
        }
        .stateIn(scope = viewmodel.viewModelScope, started = Lazily, emptyList())


    fun changeTheme(theme: SaveableTheme) {
        viewmodel.viewModelScope.launch(Dispatchers.IO) {
            CURRENT_THEME.set(theme.asString())
        }
    }

    /**
     * @return true if theme is saved, false if it already exists
     */
    suspend fun saveNewTheme(theme: SaveableTheme): Boolean {
        return withContext(Dispatchers.IO) {
            val themeJson = theme.asString()

            val customThemes = CUSTOM_THEMES.value().toMutableSet()
            if (customThemes.contains(themeJson)) return@withContext false

            customThemes.add(themeJson)
            CUSTOM_THEMES.set(customThemes)

            changeTheme(theme)
            return@withContext true
        }
    }

    fun deleteTheme(theme: SaveableTheme) {
        viewmodel.viewModelScope.launch(Dispatchers.IO) {
            val themeJson = theme.asString()
            val customThemes = CUSTOM_THEMES.value().toMutableSet()
            customThemes.remove(themeJson)
            CUSTOM_THEMES.set(customThemes)

            if (currentTheme == theme) {
                changeTheme(customThemes.firstOrNull()?.toTheme() ?: defaultTheme)
            }
        }
    }
}