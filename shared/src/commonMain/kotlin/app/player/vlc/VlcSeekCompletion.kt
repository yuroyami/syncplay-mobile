package app.player.vlc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A bounded wait for a picture-in-picture (PiP) seek to reach the native player. A timeout
 * releases the system's completion callback without claiming that the seek succeeded.
 */
internal class VlcSeekCompletion(private val completion: (Result) -> Unit) {
    enum class Result { CONVERGED, SUPERSEDED, UNAVAILABLE, TIMED_OUT, CANCELLED, FAILED }

    private var finished = false

    /** The native adapter makes all calls on the main thread, the completion delivery included. */
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
                            // Use the adapter's 1 s convergence tolerance. Read the native clock
                            // directly, because a displayed seek target proves nothing.
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
            // Also release the system callback when a native read throws. Only the first finish counts.
            finish(Result.FAILED)
        }
    }
}
