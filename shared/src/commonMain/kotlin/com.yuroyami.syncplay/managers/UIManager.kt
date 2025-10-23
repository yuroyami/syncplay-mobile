package com.yuroyami.syncplay.managers

import androidx.compose.runtime.mutableStateListOf
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.SyncplayViewmodel
import com.yuroyami.syncplay.ui.screens.adam.Screen
import kotlinx.coroutines.flow.MutableStateFlow

class UIManager(viewmodel: SyncplayViewmodel): AbstractManager(viewmodel) {

    val backStack = mutableStateListOf<Screen>(Screen.Home)

    var hasEnteredRoomOnce = false

    val hasEnteredPipMode = MutableStateFlow(false)
    val visibleHUD = MutableStateFlow(true)

    var wentForFilePick = false

    fun navigateTo(screen: Screen) {
        backStack.clear()
        backStack.add(screen)
    }

    fun popBackstack() {
        backStack.removeLastOrNull()
        if (backStack.isEmpty()) backStack.add(Screen.Home) //safeguard against backstack complete clearance which is an invalid state
    }

    override fun invalidate() {
        backStack.clear()
        backStack.add(Screen.Home)
        wentForFilePick = false
        hasEnteredPipMode.value = false
        visibleHUD.value = true
    }
}