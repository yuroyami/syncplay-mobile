package app

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.preferences.Preferences.CURRENT_THEME
import app.theme.migrated
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
import app.utils.ioDispatcher
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
 * The app-wide view model. It owns the navigation back stack and the themes, and it lives across
 * all screens.
 */
class SyncplayViewmodel : ViewModel() {

    init {
        app.utils.cleanupOldLogs()
    }

    val backstack = mutableStateListOf<Screen>(Screen.Home)

    /**
     * Whether the shared playlist is on. The shared playlist is the file list that everyone in a
     * room follows. Nothing reads this flag yet.
     *
     * TODO: Advertise this to server, disable shared-playlist-related functionality everywhere when this is off
     */
    val isSharedPlaylistEnabled = mutableStateOf(true)

    /**
     * Whether the user has entered a room at least once during this app session. The home screen
     * shows its tips only before that, and the first room opens the user list once.
     */
    var hasEnteredRoomOnce = false

    /** A weak reference to the room's view model. The platform hosts (SyncplayActivity, the iOS
     * and desktop apps) reach it through this, and a cleared view model can still be collected.
     * AdamScreen sets it. A join from outside the form goes through [app.home.PendingJoin].
     */
    var roomWeakRef: WeakRef<RoomViewmodel>? = null

    /**
     * The active theme.
     */
    val currentTheme: StateFlow<SaveableTheme> = CURRENT_THEME.flow()
        .flowOn(ioDispatcher)
        .map { it.toTheme().migrated() }
        .stateIn(scope = viewModelScope, started = Eagerly, defaultTheme)

    val customThemes = CUSTOM_THEMES.flow()
        .flowOn(ioDispatcher)
        .map { stringSet ->
            stringSet.map { it.toTheme() }.toList()
        }
        .stateIn(scope = viewModelScope, started = Lazily, emptyList())


    fun changeTheme(theme: SaveableTheme) {
        viewModelScope.launch(ioDispatcher) {
            CURRENT_THEME.set(theme.asString())
        }
    }

    /**
     * Saves [theme] as a custom theme and makes it the active theme.
     *
     * @return true if the theme is saved, false if it already exists
     */
    suspend fun saveNewTheme(theme: SaveableTheme): Boolean {
        return withContext(ioDispatcher) {
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
        viewModelScope.launch(ioDispatcher) {
            val themeJson = theme.asString()
            val customThemes = CUSTOM_THEMES.value().toMutableSet()
            customThemes.remove(themeJson)
            CUSTOM_THEMES.set(customThemes)

            // Deleting the active theme falls back to the first remaining custom one, else the default.
            if (currentTheme.value == theme) {
                changeTheme(customThemes.firstOrNull()?.toTheme() ?: defaultTheme)
            }
        }
    }

    /**
     * Replaces [old] with [new] in one datastore write, so a fast save cannot lose the theme
     * between a delete and an add, and makes [new] the active theme. Returns false and changes
     * nothing when [new] already exists as another theme.
     */
    suspend fun replaceTheme(old: SaveableTheme, new: SaveableTheme): Boolean = withContext(ioDispatcher) {
        val oldJson = old.asString()
        val newJson = new.asString()
        val customThemes = CUSTOM_THEMES.value().toMutableSet()
        if (newJson != oldJson && newJson in customThemes) return@withContext false
        customThemes.remove(oldJson)
        customThemes.add(newJson)
        CUSTOM_THEMES.set(customThemes)
        changeTheme(new)
        true
    }

    init {
        // Creates and saves a unique user ID once. Only the Klipy API (GIF search) uses it.
        viewModelScope.launch(ioDispatcher) {
            val userId = USER_ID.value()
            if (userId == null) USER_ID.set(Uuid.generateV7().toHexString())
        }
    }
}