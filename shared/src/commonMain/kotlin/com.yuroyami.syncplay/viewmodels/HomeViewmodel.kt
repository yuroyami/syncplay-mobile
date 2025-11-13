package com.yuroyami.syncplay.viewmodels

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import com.yuroyami.syncplay.PlatformCallback
import com.yuroyami.syncplay.managers.SnackManager
import com.yuroyami.syncplay.models.JoinConfig
import com.yuroyami.syncplay.ui.screens.adam.Screen
import com.yuroyami.syncplay.utils.platformCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * ViewModel for the home screen where users configure and initiate room connections.
 *
 * @property backStack The navigation stack managing screen transitions
 */
class HomeViewmodel(val backStack: SnapshotStateList<Screen>) : ViewModel() {

    /**
     * Manages snackbar messages displayed on the home screen.
     * Lazily initialized when first accessed.
     */
    val snackManager: SnackManager by lazy { SnackManager(this) }

    /**
     * Joins a Syncplay room with the provided configuration.
     *
     * Saves the configuration for future use, navigates to the room screen,
     * and notifies the platform of the room entry event.
     *
     * @param joinConfig The room connection configuration, or null for solo mode (offline video-player-only mode)
     */
    suspend fun joinRoom(joinConfig: JoinConfig?) {
        withContext(Dispatchers.IO) {
            joinConfig?.save() //Remembering info
        }

        withContext(Dispatchers.Main) {
            backStack.add(Screen.Room(joinConfig))
            platformCallback.onRoomEnterOrLeave(PlatformCallback.RoomEvent.ENTER)
        }
    }

    /**
     * Invalidates relevant managers (only snackbarManager in this case)
     */
    override fun onCleared() {
        super.onCleared()
        snackManager.invalidate()
    }
}