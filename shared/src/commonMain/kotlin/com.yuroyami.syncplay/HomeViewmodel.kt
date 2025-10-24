package com.yuroyami.syncplay

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.managers.SnackManager
import com.yuroyami.syncplay.managers.ThemeManager
import com.yuroyami.syncplay.models.JoinConfig
import com.yuroyami.syncplay.ui.screens.adam.Screen
import com.yuroyami.syncplay.utils.platformCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class HomeViewmodel(val backStack: SnapshotStateList<Screen>) : ViewModel() {

    /** Displays snack messages in home screen */
    val snackManager: SnackManager by lazy { SnackManager(this) }

    /** Manages themes */
    val themeManager: ThemeManager by lazy { ThemeManager(this) }

    fun joinRoom(joinConfig: JoinConfig?) {
        viewModelScope.launch(Dispatchers.IO) {
            joinConfig?.save() //Remembering info
        }
        backStack.add(Screen.Room(joinConfig))
        //backStack.removeFirst()
        platformCallback.onRoomEnterOrLeave(PlatformCallback.RoomEvent.ENTER)

    }

    /** End of viewmodel */
    override fun onCleared() {
        super.onCleared()
        snackManager.invalidate()
        themeManager.invalidate()
    }
}