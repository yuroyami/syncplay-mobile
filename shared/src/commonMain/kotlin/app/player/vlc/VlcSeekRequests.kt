package app.player.vlc

/** The bundled C API converts milliseconds to signed microsecond ticks. */
internal fun normalizeVlcSeekTarget(targetMs: Long, nativeLengthMs: Long): Long {
    val maxNativeMs = Long.MAX_VALUE / 1_000L
    val upperBound = if (nativeLengthMs > 0L) nativeLengthMs.coerceAtMost(maxNativeMs) else maxNativeMs
    return targetMs.coerceIn(0L, upperBound)
}

internal enum class VlcSeekReadiness { OPENING, READY, UNAVAILABLE }

internal enum class VlcSeekInputState { INACTIVE, OPENING, SEEKABLE, UNSEEKABLE, FAILED }

/** Media replacement can report the old Playing state before the new input exists. */
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
        // Positive native length/time proves an active, genuinely unseekable input.
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

/** Keeps one startup command until the native input can accept it; never retries a submitted seek. */
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
