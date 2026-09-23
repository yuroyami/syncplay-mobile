package app.home.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs the update check for the home view model. It keeps a successful answer, and only a failed
 * check can run again.
 */
class UpdateCheckController(
    private val scope: CoroutineScope,
    private val fetchLatest: suspend () -> UpdateCheck.Result = UpdateCheck::latest,
) {
    var result: UpdateCheck.Result? by mutableStateOf(null)
        private set
    var isChecking by mutableStateOf(false)
        private set

    val canCheck: Boolean
        get() = !isChecking && (result == null || result == UpdateCheck.Result.Unreachable)

    fun check() {
        if (!canCheck) return
        // Set before launching, so rapid taps cannot queue multiple requests.
        isChecking = true
        scope.launch {
            try {
                result = fetchLatest()
            } finally {
                isChecking = false
            }
        }
    }
}
