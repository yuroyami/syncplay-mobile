package com.yuroyami.syncplay.managers

import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.RoomViewmodel
import kotlinx.coroutines.flow.MutableStateFlow

class UIManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    val hasEnteredPipMode = MutableStateFlow(false)
    val visibleHUD = MutableStateFlow(true)

    var wentForFilePick = false

    override fun invalidate() {
        wentForFilePick = false
        hasEnteredPipMode.value = false
        visibleHUD.value = true
    }
}