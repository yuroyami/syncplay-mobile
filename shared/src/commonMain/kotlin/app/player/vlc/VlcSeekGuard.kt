package app.player.vlc

/** Keeps a deferred paused seek visible and bounds stale samples once playback resumes. */
internal class VlcSeekGuard {
    private var targetMs: Long? = null
    private var playingSinceMs: Long? = null

    /** Calls and samples must use the same monotonic clock and be serialized by the caller. */
    fun seek(targetMs: Long, nowMs: Long, playing: Boolean = true) {
        this.targetMs = targetMs.coerceAtLeast(0L)
        playingSinceMs = nowMs.takeIf { playing }
    }

    fun reset() {
        targetMs = null
        playingSinceMs = null
    }

    /** Null means the native player has no valid position; zero is a valid position. */
    fun sample(nativeMs: Long, nowMs: Long, playing: Boolean = true): Long? {
        targetMs?.let { target ->
            // Both operands are nonnegative, so the ordered subtraction cannot overflow.
            val settled = nativeMs >= 0L &&
                (if (nativeMs >= target) nativeMs - target else target - nativeMs) <= CONVERGENCE_MS
            // VLC can defer a paused seek until resume. While paused the target is
            // deliberate; once playing, a stale clock may hide behind it for only 1 s.
            val timedOut = if (playing) {
                val startedAt = playingSinceMs ?: nowMs.also { playingSinceMs = it }
                nowMs - startedAt > MAX_HOLD_MS
            } else {
                playingSinceMs = null
                false
            }
            if (settled || timedOut) {
                reset()
            } else {
                return target
            }
        }
        return nativeMs.takeIf { it >= 0L }
    }

    private companion object {
        const val CONVERGENCE_MS = 1_000L
        const val MAX_HOLD_MS = 1_000L
    }
}
