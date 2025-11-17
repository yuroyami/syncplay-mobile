@file:Suppress("SpellCheckingInspection")

package com.yuroyami.syncplay.managers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.managers.preferences.Preferences.ALL_THEMES
import com.yuroyami.syncplay.managers.preferences.Preferences.CURRENT_THEME
import com.yuroyami.syncplay.managers.preferences.flow
import com.yuroyami.syncplay.managers.preferences.get
import com.yuroyami.syncplay.managers.preferences.set
import com.yuroyami.syncplay.ui.theme.SaveableTheme
import com.yuroyami.syncplay.ui.theme.SaveableTheme.Companion.toTheme
import com.yuroyami.syncplay.ui.theme.defaultTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
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
        .map { it.toTheme() }
        .stateIn(scope = viewmodel.viewModelScope, started = Eagerly, defaultTheme)

    val flow = ALL_THEMES.flow()

    val customThemes: StateFlow<List<SaveableTheme>> = ALL_THEMES.flow()
        .map { themeSet ->
            themeSet.map map2@ { themeJson ->
                themeJson.toTheme()
            }.toList()
        }
        .stateIn(scope = viewmodel.viewModelScope, started = Eagerly, emptyList())


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

            val customThemes = ALL_THEMES.get().toMutableSet()
            if (customThemes.contains(themeJson)) return@withContext false

            customThemes.add(themeJson)
            ALL_THEMES.set(customThemes)

            changeTheme(theme)
            return@withContext true
        }
    }

    fun deleteTheme(theme: SaveableTheme) {
        viewmodel.viewModelScope.launch(Dispatchers.IO) {
            val themeJson = theme.asString()
            val customThemes = ALL_THEMES.get().toMutableSet()
            customThemes.remove(themeJson)
            ALL_THEMES.set(customThemes)

            if (currentTheme == theme) {
                changeTheme(customThemes.firstOrNull()?.toTheme() ?: defaultTheme)
            }
        }
    }
}