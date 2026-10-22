package app.player.vlc

/**
 * Reports the seek target while VLC defers a paused seek, and limits how long a stale native
 * sample can hide behind the target once playback resumes.
 */
internal class VlcSeekGuard {
    private var targetMs: Long? = null
    private var playingSinceMs: Long? = null

    /** Calls to this and to [sample] must use the same monotonic clock, serialized by the caller. */
    fun seek(targetMs: Long, nowMs: Long, playing: Boolean = true) {
        this.targetMs = targetMs.coerceAtLeast(0L)
        playingSinceMs = nowMs.takeIf { playing }
    }

    fun reset() {
        targetMs = null
        playingSinceMs = null
    }

    /**
     * The position to report: the seek target while it holds, else the native position. Null
     * means that the native player has no valid position. Zero is a valid position.
     */
    fun sample(nativeMs: Long, nowMs: Long, playing: Boolean = true): Long? {
        targetMs?.let { target ->
            // Both operands are nonnegative, so the ordered subtraction cannot overflow.
            val settled = nativeMs >= 0L &&
                (if (nativeMs >= target) nativeMs - target else target - nativeMs) <= CONVERGENCE_MS
            // VLC can defer a paused seek until playback resumes. While paused, the target is
            // deliberate. Once playing, a stale clock may hide behind the target for only 1 s.
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
