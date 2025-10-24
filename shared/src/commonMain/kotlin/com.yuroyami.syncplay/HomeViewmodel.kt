package com.yuroyami.syncplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.yuroyami.syncplay.managers.SnackManager
import com.yuroyami.syncplay.managers.ThemeManager
import com.yuroyami.syncplay.models.JoinConfig
import com.yuroyami.syncplay.ui.screens.adam.Screen
import com.yuroyami.syncplay.utils.platformCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class HomeViewmodel(val backStack: NavBackStack<NavKey>) : ViewModel() {

    /** Displays snack messages in home screen */
    val snackManager: SnackManager by lazy { SnackManager(this) }

    /** Manages themes */
    val themeManager: ThemeManager by lazy { ThemeManager(this) }

    fun joinRoom(joinConfig: JoinConfig?) {
        viewModelScope.launch(Dispatchers.IO) {
            joinConfig?.save() //Remembering info
        }
        platformCallback.onRoomEnterOrLeave(PlatformCallback.RoomEvent.ENTER)
        backStack.clear()
        backStack.add(Screen.Room(joinConfig))
    }

    /** End of viewmodel */
    override fun onCleared() {
        super.onCleared()
        snackManager.invalidate()
        themeManager.invalidate()
    }
}