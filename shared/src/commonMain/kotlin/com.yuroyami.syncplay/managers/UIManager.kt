package com.yuroyami.syncplay.managers

import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.RoomViewmodel
import com.yuroyami.syncplay.ui.screens.adam.Screen
import com.yuroyami.syncplay.ui.screens.adam.backStack
import kotlinx.coroutines.flow.MutableStateFlow

class UIManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    val hasEnteredPipMode = MutableStateFlow(false)
    val visibleHUD = MutableStateFlow(true)

    var wentForFilePick = false

    override fun invalidate() {
        backStack.clear()
        backStack.add(Screen.Home)
        wentForFilePick = false
        hasEnteredPipMode.value = false
        visibleHUD.value = true
    }
}