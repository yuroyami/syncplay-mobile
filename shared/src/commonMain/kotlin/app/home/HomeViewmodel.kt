package app.home

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.Screen
import app.home.components.UpdateCheckController
import app.uicomponents.frames.NoticeQueue
import app.uicomponents.frames.NoticeSeverity
import app.utils.ioDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The home screen's view model: joining, and the notices the screen shows at its bottom edge. */
class HomeViewmodel(val backStack: SnapshotStateList<Screen>) : ViewModel() {

    val updateCheck = UpdateCheckController(viewModelScope)

    /**
     * Saves [joinConfig] when the remember setting is on, then opens the room. A null [joinConfig]
     * opens solo mode (offline playback).
     */
    suspend fun joinRoom(joinConfig: JoinConfig?) {
        withContext(ioDispatcher) { joinConfig?.save() }
        withContext(Dispatchers.Main) { backStack.add(Screen.Room(joinConfig)) }
    }

    val notices = NoticeQueue()

    fun snackItAsync(string: String, abruptly: Boolean = true) {
        viewModelScope.launch(Dispatchers.Main) { snackIt(string, abruptly) }
    }

    suspend fun snackIt(string: String, abruptly: Boolean = true) {
        withContext(Dispatchers.Main) {
            if (abruptly) notices.clear()
            notices.post(string, NoticeSeverity.Info, holdMs = 3000L)
        }
    }
}
