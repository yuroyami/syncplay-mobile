package com.yuroyami.syncplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

/**
 * Base class for all "manager" components in the Syncplay architecture.
 *
 * Handles one specific domain (e.g., lifecycle, OSD, snackbars)
 */
abstract class AbstractManager(
    val vm: ViewModel
) {
    open fun invalidate() {}

    inline fun onMainThread(crossinline block: suspend () -> Unit) {
        vm.viewModelScope.launch(Dispatchers.Main.immediate) {
            block()
        }
    }

    inline fun onIOThread(crossinline block: suspend () -> Unit) {
        vm.viewModelScope.launch(Dispatchers.IO) {
            block()
        }
    }
}
