package app.home

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.Screen
import app.uicomponents.frames.NoticeQueue
import app.uicomponents.frames.NoticeSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The home screen's viewmodel: joining, and the notices the screen shows at its bottom edge. */
class HomeViewmodel(val backStack: SnapshotStateList<Screen>) : ViewModel() {

    /** Saves the configuration when remembering is on, then opens the room; null joins alone. */
    suspend fun joinRoom(joinConfig: JoinConfig?) {
        withContext(Dispatchers.IO) { joinConfig?.save() }
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
