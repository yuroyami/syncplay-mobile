@file:Suppress("SpellCheckingInspection")

package com.yuroyami.syncplay.managers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.MISC_ALL_THEMES
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.MISC_CURRENT_THEME
import com.yuroyami.syncplay.managers.datastore.valueFlow
import com.yuroyami.syncplay.managers.datastore.valueSuspendingly
import com.yuroyami.syncplay.managers.datastore.writeValue
import com.yuroyami.syncplay.ui.theme.ALLEY_LAMP
import com.yuroyami.syncplay.ui.theme.SaveableTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /**
     * @return true if theme is saved, false if it already exists
     */
    suspend fun saveNewTheme(theme: SaveableTheme): Boolean {
        return withContext(Dispatchers.IO) {
            val customThemeListJson = valueSuspendingly(MISC_ALL_THEMES, "[]")
            val list = Json.decodeFromString<List<SaveableTheme>>(customThemeListJson).toMutableList()

            if (list.contains(theme)) return@withContext false

            list.add(theme)
            val listEncodedAgain = Json.encodeToString(list)
            writeValue(MISC_ALL_THEMES, listEncodedAgain)

            changeTheme(theme)

            return@withContext true
        }
    }

    fun deleteTheme(theme: SaveableTheme, thisThemeWasSelected: Boolean) {
        viewmodel.viewModelScope.launch(Dispatchers.IO) {
            val customThemeListJson = valueSuspendingly(MISC_ALL_THEMES, "[]")
            val list = Json.decodeFromString<List<SaveableTheme>>(customThemeListJson).toMutableList()

            list.remove(theme)
            val listEncodedAgain = Json.encodeToString(list)
            writeValue(MISC_ALL_THEMES, listEncodedAgain)

            if (thisThemeWasSelected) {
                changeTheme(list.firstOrNull() ?: ALLEY_LAMP)
            }
        }
    }
}