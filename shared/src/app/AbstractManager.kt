package app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.utils.ioDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The base class for the managers: helper classes that each own one part of a view model's work.
 * It gives them coroutine helpers that run in the view model's scope.
 *
 * @property vm The view model whose scope runs the coroutines.
 */
abstract class AbstractManager(val vm: ViewModel) {

    open fun invalidate() {}

    inline fun onMainThread(crossinline block: suspend () -> Unit) {
        vm.viewModelScope.launch(Dispatchers.Main.immediate) { block() }
    }

    /** Runs [block] on the IO dispatcher and returns the job, so a caller can cancel it later. */
    inline fun onIOThread(crossinline block: suspend () -> Unit): Job =
        vm.viewModelScope.launch(ioDispatcher) { block() }
}