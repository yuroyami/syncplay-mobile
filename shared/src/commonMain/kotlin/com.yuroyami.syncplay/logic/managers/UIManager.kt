package com.yuroyami.syncplay.logic.managers

import androidx.navigation.NavController
import com.yuroyami.syncplay.logic.AbstractManager
import com.yuroyami.syncplay.logic.SyncplayViewmodel
import kotlinx.coroutines.flow.MutableStateFlow

class UIManager(viewmodel: SyncplayViewmodel): AbstractManager(viewmodel) {

    lateinit var nav: NavController

    var hasDoneStartupSlideAnimation = false

    val hasEnteredPipMode = MutableStateFlow(false)
    val visibleHUD = MutableStateFlow(true)

    var wentForFilePick = false
}