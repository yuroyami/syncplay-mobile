package app.player.vlc

/**
 * Clamps a seek target between 0 and the media length, when the length is known. The bundled C
 * API converts milliseconds to signed microsecond ticks, so the target also stays at or below
 * `Long.MAX_VALUE / 1000`.
 */
internal fun normalizeVlcSeekTarget(targetMs: Long, nativeLengthMs: Long): Long {
    val maxNativeMs = Long.MAX_VALUE / 1_000L
    val upperBound = if (nativeLengthMs > 0L) nativeLengthMs.coerceAtMost(maxNativeMs) else maxNativeMs
    return targetMs.coerceIn(0L, upperBound)
}

internal enum class VlcSeekReadiness { OPENING, READY, UNAVAILABLE }

internal enum class VlcSeekInputState { INACTIVE, OPENING, SEEKABLE, UNSEEKABLE, FAILED }

/**
 * Tracks the opening window of a new input. A media replacement can report the old Playing state
 * before the new input exists.
 */
internal class VlcSeekStartup(private val timeoutMs: Long) {
    private var startedAtMs: Long? = null

    fun begin(nowMs: Long) {
        startedAtMs = nowMs
    }

    fun reset() {
        startedAtMs = null
    }

    fun isOpen(nowMs: Long): Boolean = startedAtMs?.let { nowMs - it < timeoutMs } == true

    fun readiness(
        inputState: VlcSeekInputState,
        nativeLengthMs: Long,
        nativeTimeMs: Long,
        nowMs: Long,
    ): VlcSeekReadiness {
        if (inputState == VlcSeekInputState.SEEKABLE) {
            reset()
            return VlcSeekReadiness.READY
        }
        // A positive native length or time proves an active input that really cannot seek.
        // Parsed wrapper metadata cannot prove that the playback input has opened.
        if (inputState == VlcSeekInputState.FAILED ||
            (inputState == VlcSeekInputState.UNSEEKABLE && (nativeLengthMs > 0L || nativeTimeMs > 0L))
        ) {
            reset()
            return VlcSeekReadiness.UNAVAILABLE
        }
        val started = startedAtMs ?: return VlcSeekReadiness.UNAVAILABLE
        if (nowMs - started >= timeoutMs) {
            reset()
            return VlcSeekReadiness.UNAVAILABLE
        }
        return VlcSeekReadiness.OPENING
    }
}

internal sealed interface VlcSeekDecision {
    data class Submit(val targetMs: Long) : VlcSeekDecision
    data class Wait(val targetMs: Long) : VlcSeekDecision
    data object Rejected : VlcSeekDecision
    data object None : VlcSeekDecision
}

/** Keeps one startup seek until the native input can accept it. It never retries a submitted seek. */
internal class VlcSeekRequests(private val openingTimeoutMs: Long) {
    private data class Request(val targetMs: Long, val requestedAtMs: Long)
    private var pending: Request? = null
    val hasPending: Boolean get() = pending != null

    fun request(targetMs: Long, nowMs: Long) {
        pending = Request(targetMs, nowMs)
    }

    fun reset() {
        pending = null
    }

    fun poll(readiness: VlcSeekReadiness, nativeLengthMs: Long, nowMs: Long): VlcSeekDecision {
        val request = pending ?: return VlcSeekDecision.None
        if (nowMs - request.requestedAtMs >= openingTimeoutMs || readiness == VlcSeekReadiness.UNAVAILABLE) {
            reset()
            return VlcSeekDecision.Rejected
        }
        val target = normalizeVlcSeekTarget(request.targetMs, nativeLengthMs)
        if (readiness == VlcSeekReadiness.OPENING) return VlcSeekDecision.Wait(target)
        reset()
        return VlcSeekDecision.Submit(target)
    }
}
