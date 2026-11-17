package app.player.vlc

import kotlin.math.roundToLong

/**
 * Finds a native clock that stops after a seek while playback goes on (#233).
 *
 * VLCKit 4 can leave its clock frozen after a seek, often at the target plus the pause time,
 * while the video plays on from the target. One more seek while playing repairs the clock. This
 * class tells the caller when to send that seek, and where: the last target plus the play time
 * since then. It asks for at most one repair per seek.
 */
internal class VlcClockWatch {
    private var targetMs: Long? = null
    private var playedMs = 0.0
    private var stalledMs = 0L
    private var movedMs = 0L
    private var lastNowMs: Long? = null
    private var lastNativeMs: Long? = null

    /** Watches the clock after a seek to [targetMs]. A newer seek replaces the older one. */
    fun seek(targetMs: Long) {
        reset()
        this.targetMs = targetMs.coerceAtLeast(0L)
    }

    fun reset() {
        targetMs = null
        playedMs = 0.0
        stalledMs = 0L
        movedMs = 0L
        lastNowMs = null
        lastNativeMs = null
    }

    /**
     * Takes one native sample. Returns the position to seek to when the clock has stood still for
     * [STALL_MS] of play time, else null. [playing] must be false while the player pauses or
     * refills its buffer, so that time counts neither as play time nor as a stall. Calls must use
     * one monotonic clock for [nowMs].
     */
    fun sample(nativeMs: Long, nowMs: Long, playing: Boolean, rate: Float): Long? {
        val target = targetMs ?: return null
        val previousNow = lastNowMs
        val previousNative = lastNativeMs
        lastNowMs = nowMs
        lastNativeMs = nativeMs
        if (!playing || rate <= 0f || nativeMs < 0L || previousNow == null || previousNative == null || previousNative < 0L) {
            stalledMs = 0L
            return null
        }
        val elapsedMs = (nowMs - previousNow).coerceAtLeast(0L)
        playedMs += elapsedMs * rate.toDouble()
        val advancedMs = nativeMs - previousNative
        when {
            advancedMs == 0L -> stalledMs += elapsedMs
            // A clock that runs at about the play rate is healthy. A larger step or a step back
            // is the seek landing, which proves nothing about the clock.
            advancedMs > 0L && advancedMs <= elapsedMs * maxOf(rate, 1f) * 2 + JITTER_MS -> {
                stalledMs = 0L
                movedMs += advancedMs
            }
            else -> stalledMs = 0L
        }
        if (movedMs >= HEALTHY_MS) {
            reset()
            return null
        }
        if (stalledMs >= STALL_MS) {
            val repairMs = target + playedMs.roundToLong()
            reset()
            return repairMs
        }
        return null
    }

    private companion object {
        /** Play time without a clock change that counts as a frozen clock. */
        const val STALL_MS = 1_500L

        /** Clock progress that proves the clock runs, after which the watch stops. */
        const val HEALTHY_MS = 2_000L

        /** Allowed timing noise between two samples. */
        const val JITTER_MS = 250L
    }
}
