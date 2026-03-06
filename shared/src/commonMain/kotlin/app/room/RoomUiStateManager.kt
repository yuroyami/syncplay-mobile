package app.room

import app.AbstractManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.concurrent.Volatile

class RoomUiStateManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    val msg = MutableStateFlow<String>("")

    companion object {
        enum class RoomOrientation { LANDSCAPE, PORTRAIT }
    }
    val roomOrientation = MutableStateFlow<RoomOrientation>(RoomOrientation.LANDSCAPE)

    val hasEnteredPipMode = MutableStateFlow(false)
    val visibleHUD = MutableStateFlow(true)
    val popupChatHistory = MutableStateFlow(false)
    val popupCreateManagedRoom = MutableStateFlow(false)
    val popupIdentifyAsRoomOperator = MutableStateFlow(false)
    val popupSeekToPosition = MutableStateFlow(false)

    val tabCardUserInfo = MutableStateFlow(false)
    val tabCardSharedPlaylist = MutableStateFlow(false)
    val tabCardRoomPreferences = MutableStateFlow(false)
    val tabLock = MutableStateFlow(false)

    val controlPanel = MutableStateFlow(false)

    /** True while the user has navigated away for file picking. */
    var wentForFilePick = false

    fun toggleUserInfo(forcedState: Boolean? = null) {
        tabCardUserInfo.value = forcedState ?: !tabCardUserInfo.value
        if (tabCardUserInfo.value) {
            tabCardSharedPlaylist.value = false
            tabCardRoomPreferences.value = false
        }
    }

    fun toggleSharedPlaylist(forcedState: Boolean? = null) {
        tabCardSharedPlaylist.value = forcedState ?: !tabCardSharedPlaylist.value
        if (tabCardSharedPlaylist.value) {
            tabCardUserInfo.value = false
            tabCardRoomPreferences.value = false
        }
    }

    fun toggleRoomPreferences(forcedState: Boolean? = null) {
        tabCardRoomPreferences.value = forcedState ?: !tabCardRoomPreferences.value
        if (tabCardRoomPreferences.value) {
            tabCardUserInfo.value = false
            tabCardSharedPlaylist.value = false
        }
    }

    /** Room Lifecycle mapping according to platform (iOS/Android)
    * - [onLifecycleCreate] → `viewDidLoad` / `onCreate`
    * - [onLifecycleStart] → `viewWillAppear` / `onStart`
    * - [onLifecycleResume] → `viewDidAppear` / `onResume`
    * - [onLifecyclePause] → `viewWillDisappear` / `onPause`
    * - [onLifecycleStop] → `viewDidDisappear` / `onStop`
    */

    /** True after [onLifecycleStop] unless in PiP mode. */
    @Volatile
    var background = false

    fun onLifecycleCreate() {
        // Nothing to do
    }

    fun onLifecycleStart() { background = false
    }

    fun onLifecycleResume() {
        background = false
    }

    fun onLifecyclePause() {
        // Nothing to do
    }

    /** Pauses playback unless in Picture-in-Picture mode. */
    fun onLifecycleStop() {
        if (!hasEnteredPipMode.value) {
            background = true
            onMainThread { viewmodel.player.pause() }
        }
    }

    val isInBackground: Boolean
        get() = background


    /** Resets all UI state to defaults. */
    override fun invalidate() {
        wentForFilePick = false
        hasEnteredPipMode.value = false
        visibleHUD.value = true
    }
}