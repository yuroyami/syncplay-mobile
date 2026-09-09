package app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.utils.ioDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Base class for all "manager" components. Provides coroutine dispatch helpers tied to the
 * parent ViewModel's scope.
 *
 * @property vm The parent ViewModel whose scope is used for coroutine execution.
 */
abstract class AbstractManager(val vm: ViewModel) {

    open fun invalidate() {}

    inline fun onMainThread(crossinline block: suspend () -> Unit) {
        vm.viewModelScope.launch(Dispatchers.Main.immediate) { block() }
    }

    /** Returns the job so a caller that has to stop its own work later can hold it. */
    inline fun onIOThread(crossinline block: suspend () -> Unit): Job =
        vm.viewModelScope.launch(ioDispatcher) { block() }
}