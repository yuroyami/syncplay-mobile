package com.yuroyami.syncplay.managers

import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Manages UI state and visibility for the Syncplay room screen.
 *
 * Tracks various UI components including Picture-in-Picture mode, HUD visibility,
 * popup dialogs, and file picker state.
 *
 * @property viewmodel The parent RoomViewModel that owns this manager
 */
class UIManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {
    
    companion object {
        enum class RoomOrientation { LANDSCAPE, PORTRAIT }
    }
    val roomOrientation = MutableStateFlow<RoomOrientation>(RoomOrientation.LANDSCAPE)

    /**
     * Whether the app has entered Picture-in-Picture mode.
     * Used to adjust UI behavior when in PiP.
     */
    val hasEnteredPipMode = MutableStateFlow(false)

    /**
     * Whether the HUD (Heads-Up Display) overlay is currently visible.
     * Controls visibility of playback controls and room information.
     */
    val visibleHUD = MutableStateFlow(true)

    /**
     * Whether the chat history popup dialog is currently displayed.
     */
    val popupChatHistory = MutableStateFlow(false)

    /**
     * Whether the "Create Managed Room" popup dialog is currently displayed.
     */
    val popupCreateManagedRoom = MutableStateFlow(false)

    /**
     * Whether the "Identify as Room Operator" popup dialog is currently displayed.
     */
    val popupIdentifyAsRoomOperator = MutableStateFlow(false)


    
    val popupSeekToPosition = MutableStateFlow(false)


    /**
     * Tracks whether the user has navigated away for file picking.
     * Used to handle lifecycle events during file selection.
     */
    var wentForFilePick = false

    /**
     * Resets all UI state to default values.
     * Called when leaving the room or cleaning up the manager.
     */
    override fun invalidate() {
        wentForFilePick = false
        hasEnteredPipMode.value = false
        visibleHUD.value = true
    }
}