package com.yuroyami.syncplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

/**
 * Base class for all "manager" components in the Syncplay architecture.
 *
 * The goal is to avoid dumping every responsibility into [SyncplayViewmodel],
 * which would turn it into a giant "god object." Instead, each manager:
 * - Handles one specific domain (e.g., lifecycle, OSD, snackbars)
 * - Has full access to [SyncplayViewmodel] for coordination
 * - Remains lightweight, modular, and testable
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
