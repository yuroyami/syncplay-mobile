package app

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.home.HomeViewmodel
import app.preferences.Preferences.CURRENT_THEME
import app.preferences.Preferences.CUSTOM_THEMES
import app.preferences.Preferences.USER_ID
import app.preferences.flow
import app.preferences.set
import app.preferences.value
import app.room.RoomViewmodel
import app.theme.SaveableTheme
import app.theme.SaveableTheme.Companion.toTheme
import app.theme.defaultTheme
import app.utils.WeakRef
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
import kotlin.uuid.Uuid

/**
 * Application-level ViewModel for managing global Syncplay state.
 *
 * Maintains app-wide settings, theme configuration, and tracks high-level
 * user interaction state that persists across screen navigation.
 */
class SyncplayViewmodel : ViewModel() {

    /** The navigation backstack */
    val backstack = mutableStateListOf<Screen>(Screen.Home)

    /**
     * Whether the shared playlist feature is enabled.
     *
     * When disabled, all shared playlist functionality should be hidden/disabled
     * throughout the UI, and this state should be communicated to the server.
     *
     * TODO: Advertise this to server, disable shared-playlist-related functionality everywhere when this is off
     */
    val isSharedPlaylistEnabled = mutableStateOf(true)

    /**
     * Tracks whether the user has entered a room at least once during this app session.
     *
     * Used for UI/UX decisions like adjusting first-time behavior.
     */
    var hasEnteredRoomOnce = false

    /** Weak References for our child viewmodels so we can access them from SyncplayActivity
     * but also not prevent them from getting garbage-collected when they're finalized (invalidated and cleared)
     */
    var homeWeakRef: WeakRef<HomeViewmodel>? = null
    var roomWeakRef: WeakRef<RoomViewmodel>? = null

    /**
     * The currently active theme.
     */
    val currentTheme: StateFlow<SaveableTheme> = CURRENT_THEME.flow()
        .flowOn(Dispatchers.IO)
        .map { it.toTheme() }
        .stateIn(scope = viewModelScope, started = Eagerly, defaultTheme)

    val customThemes = CUSTOM_THEMES.flow()
        .flowOn(Dispatchers.IO)
        .map { stringSet ->
            stringSet.map { it.toTheme() }.toList()
        }
        .stateIn(scope = viewModelScope, started = Lazily, emptyList())


    fun changeTheme(theme: SaveableTheme) {
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
            val themeJson = theme.asString()
            val customThemes = CUSTOM_THEMES.value().toMutableSet()
            customThemes.remove(themeJson)
            CUSTOM_THEMES.set(customThemes)

            if (currentTheme == theme) {
                changeTheme(customThemes.firstOrNull()?.toTheme() ?: defaultTheme)
            }
        }
    }

    init {
        //Generate a unique ID for the user and persist, to use it for Klipy API only.
        viewModelScope.launch(Dispatchers.IO) {
            val userId = USER_ID.value()
            if (userId == null) USER_ID.set(Uuid.generateV7().toHexString())
        }
    }
}