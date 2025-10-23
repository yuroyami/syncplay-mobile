package com.yuroyami.syncplay.managers

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.SyncplayViewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OSDManager(viewmodel: SyncplayViewmodel): AbstractManager(viewmodel) {

    val osdMsg = mutableStateOf("")

    var osdJob: Job? = null

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

    override fun invalidate() {
        osdMsg.value == ""
        runCatching {
            osdJob?.cancel(null)
        }
    }
}