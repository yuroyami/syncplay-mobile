package com.yuroyami.syncplay.managers

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages on-screen display (OSD) messages in the Syncplay room interface.
 *
 * Handles displaying temporary messages with automatic dismissal after a delay.
 * Only one OSD message can be active at a time - new messages cancel previous ones.
 *
 * @property viewmodel The parent RoomViewModel that owns this manager
 */
class OSDManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    /**
     * The current OSD message to display. Empty string indicates no message.
     */
    val osdMsg = mutableStateOf("")

    /**
     * The active coroutine job displaying the current OSD message, if any.
     * Used to cancel previous messages when new ones arrive.
     */
    var osdJob: Job? = null

    /**
     * Displays a new OSD message by invoking the provided [getter] function.
     *
     * Cancels any existing OSD message before displaying the new one.
     * The message automatically dismisses after 2 seconds.
     *
     * @param getter A suspend function that returns the message string to display
     */
    fun dispatchOSD(getter: suspend () -> String) {
        runCatching {
            osdJob?.cancel(null)
        }
        osdJob = viewmodel.viewModelScope.launch(Dispatchers.IO) {
            osdMsg.value = getter()
            delay(2000) //TODO Don't hardcore delay, make it a setting
            osdMsg.value = ""
        }
    }

    /**
     * Clears the current OSD message and cancels any active display job.
     * Called when the manager needs to reset its state.
     */
    override fun invalidate() {
        osdMsg.value = ""
        runCatching {
            osdJob?.cancel(null)
        }
    }
}