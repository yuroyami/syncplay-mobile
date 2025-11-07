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

class HomeViewmodel(val backStack: SnapshotStateList<Screen>) : ViewModel() {

    /** Displays snack messages in home screen */
    val snackManager: SnackManager by lazy { SnackManager(this) }

    suspend fun joinRoom(joinConfig: JoinConfig?) {
        withContext(Dispatchers.IO) {
            joinConfig?.save() //Remembering info
        }

        withContext(Dispatchers.Main) {
            backStack.add(Screen.Room(joinConfig))
            platformCallback.onRoomEnterOrLeave(PlatformCallback.RoomEvent.ENTER)
        }

    }

    /** End of viewmodel */
    override fun onCleared() {
        super.onCleared()
        snackManager.invalidate()
    }
}