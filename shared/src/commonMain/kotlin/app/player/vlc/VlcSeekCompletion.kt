package app.player.vlc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** A bounded PiP wait. A timeout releases the system completion without claiming seek success. */
internal class VlcSeekCompletion(private val completion: (Result) -> Unit) {
    enum class Result { CONVERGED, SUPERSEDED, UNAVAILABLE, TIMED_OUT, CANCELLED, FAILED }

    private var finished = false

    /** All calls, including completion delivery, are confined to Main by the native adapter. */
    fun finish(result: Result) {
        if (finished) return
        finished = true
        completion(result)
    }

    suspend fun await(
        isCurrent: () -> Boolean,
        targetMs: () -> Long?,
        nativePositionMs: () -> Long?,
    ) {
        try {
            val result = withTimeoutOrNull(2_000L) {
                var outcome: Result? = null
                while (outcome == null) {
                    if (!isCurrent()) {
                        outcome = Result.SUPERSEDED
                    } else {
                        val target = targetMs()
                        val native = nativePositionMs()
                        outcome = when {
                            target == null -> Result.UNAVAILABLE
                            native == null || native < 0L -> null
                            // Match the adapter's existing 1 s convergence tolerance. Read the
                            // native clock directly: a displayed seek target is not evidence.
                            (if (native >= target) native - target else target - native) <= 1_000L ->
                                Result.CONVERGED
                            else -> null
                        }
                    }
                    if (outcome == null) delay(50)
                }
                outcome
            }
            finish(result ?: Result.TIMED_OUT)
        } catch (cancelled: CancellationException) {
            finish(Result.CANCELLED)
            throw cancelled
        } finally {
            // Also release the system callback if a native read fails. finish is idempotent.
            finish(Result.FAILED)
        }
    }
}
