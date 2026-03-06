package app.home

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the home screen where users configure and initiate room connections.
 *
 * @property backStack The navigation stack managing screen transitions
 */
class HomeViewmodel(val backStack: SnapshotStateList<Screen>) : ViewModel() {

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
        }
    }

    var snack = SnackbarHostState()

    fun snackItAsync(string: String, abruptly: Boolean = true) {
        viewModelScope.launch(Dispatchers.Main) {
            snackIt(string, abruptly)
        }
    }

    suspend fun snackIt(string: String, abruptly: Boolean = true) {
        if (abruptly) snack.currentSnackbarData?.dismiss()
        snack.showSnackbar(message = string, duration = SnackbarDuration.Short)
    }
}