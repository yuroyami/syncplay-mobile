package com.yuroyami.syncplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

/**
 * Base class for all "manager" components in the Syncplay architecture.
 *
 * Manages a specific domain (e.g., lifecycle, OSD, snackbars) and provides
 * coroutine dispatch utilities tied to the parent ViewModel's lifecycle.
 *
 * @property vm The parent ViewModel whose scope is used for coroutine execution
 */
abstract class AbstractManager(
    val vm: ViewModel
) {
    /**
     * Called to refresh or recompute the manager's state.
     * Override to implement domain-specific invalidation logic.
     */
    open fun invalidate() {}

    /**
     * Executes the given [block] on the Main dispatcher with immediate dispatch.
     * The coroutine is scoped to the parent ViewModel's lifecycle.
     *
     * @param block The suspend function to execute on the main thread
     */
    inline fun onMainThread(crossinline block: suspend () -> Unit) {
        vm.viewModelScope.launch(Dispatchers.Main.immediate) {
            block()
        }
    }

    /**
     * Executes the given [block] on the IO dispatcher.
     * The coroutine is scoped to the parent ViewModel's lifecycle.
     *
     * @param block The suspend function to execute on the IO thread
     */
    inline fun onIOThread(crossinline block: suspend () -> Unit) {
        vm.viewModelScope.launch(Dispatchers.IO) {
            block()
        }
    }
}