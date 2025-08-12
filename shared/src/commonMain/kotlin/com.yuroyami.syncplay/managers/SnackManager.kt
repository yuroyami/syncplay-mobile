package com.yuroyami.syncplay.managers

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.logic.AbstractManager
import com.yuroyami.syncplay.logic.SyncplayViewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles showing snackbars in a centralized way for the given [SyncplayViewmodel].
 * This is mostly used for HomeScreen.
 *
 * @property snack Snackbar host state used by the UI to display messages.
 *
 * @see [OSDManager] for the RoomScreen equivalent implementation that doesn't use a Snackbar.
 */
class SnackManager(viewmodel: SyncplayViewmodel) : AbstractManager(viewmodel) {

    /** Holds the current state of the snackbar to be used by the UI. */
    var snack = SnackbarHostState()

    /**
     * Shows a snackbar message from anywhere without blocking, launching on the main thread.
     *
     * @param string The message text to display.
     * @param abruptly If true, dismisses any currently visible snackbar before showing the new one. Otherwise it queues it.
     */
    fun snackItAsync(string: String, abruptly: Boolean = true) {
        viewmodel.viewModelScope.launch(Dispatchers.Main) {
            snackIt(string, abruptly)
        }
    }

    /**
     * Shows a snackbar message. Can be called from coroutines.
     *
     * @param string The message text to display.
     * @param abruptly If true, dismisses any currently visible snackbar before showing the new one. Otherwise it queues it.
     */
    suspend fun snackIt(string: String, abruptly: Boolean = true) {
        if (abruptly) snack.currentSnackbarData?.dismiss()
        snack.showSnackbar(message = string, duration = SnackbarDuration.Short)
    }
}
