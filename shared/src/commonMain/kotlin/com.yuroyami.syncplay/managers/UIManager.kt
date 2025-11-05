package com.yuroyami.syncplay.managers

import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import kotlinx.coroutines.flow.MutableStateFlow

class UIManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    val hasEnteredPipMode = MutableStateFlow(false)
    val visibleHUD = MutableStateFlow(true)

    val popupChatHistory = MutableStateFlow(false)
    val popupCreateManagedRoom = MutableStateFlow(false)
    val popupIdentifyAsRoomOperator = MutableStateFlow(false)

    var wentForFilePick = false

    override fun invalidate() {
        wentForFilePick = false
        hasEnteredPipMode.value = false
        visibleHUD.value = true
    }
}