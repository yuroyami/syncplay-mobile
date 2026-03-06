package app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

/**
 * Base class for all "manager" components in the Syncplay architecture.
 *
 * provides coroutine dispatch utilities tied to the parent ViewModel's lifecycle
 * and invalidation trigger
 *
 * @property vm The parent ViewModel whose scope is used for coroutine execution
 */
abstract class AbstractManager(val vm: ViewModel) {

    open fun invalidate() {}

    inline fun onMainThread(crossinline block: suspend () -> Unit) {
        vm.viewModelScope.launch(Dispatchers.Main.immediate) { block() }
    }

    inline fun onIOThread(crossinline block: suspend () -> Unit) {
        vm.viewModelScope.launch(Dispatchers.IO) { block() }
    }
}