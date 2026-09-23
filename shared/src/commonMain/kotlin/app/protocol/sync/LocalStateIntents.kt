package app.protocol.sync

/**
 * A local seek's origin and target. It never goes on the wire; it follows the intent that was
 * actually sent, so the self echo can be matched to it.
 */
data class LocalSeek(val fromMs: Long, val toMs: Long, val recordUndo: Boolean = true)

data class LocalStateIntent(
    val positionSeconds: Double,
    val playing: Boolean,
    val seek: LocalSeek? = null,
)

/**
 * Holds the latest unsent local state, and only the latest, so it stays bounded even during
 * many rapid seeks. The owner holds its sync lock around every call. The `ignoringOnTheFly`
 * gate decides when [takeReady] may send; waiting never creates a packet or increments a
 * counter. A later pause or play keeps a queued explicit seek.
 */
class LocalStateIntents {
    private var pending: LocalStateIntent? = null
    private var sentSeek: SentSeek? = null
    var revision: Long = 0L
        private set

    fun isCurrent(revision: Long): Boolean = this.revision == revision

    private data class SentSeek(val intent: LocalStateIntent, val counter: Int, val sentAtMs: Long)

    fun offer(intent: LocalStateIntent) {
        revision++
        val previous = pending
        pending = if (intent.seek == null && previous?.seek != null) {
            previous.copy(playing = intent.playing)
        } else intent
    }

    fun takeReady(canSend: Boolean): LocalStateIntent? {
        if (!canSend) return null
        return pending.also { pending = null }
    }

    fun sent(intent: LocalStateIntent, counter: Int, nowMs: Long) {
        sentSeek = intent.takeIf { it.seek != null }?.let { SentSeek(it, counter, nowMs) }
    }

    /**
     * Matches an inbound self-seek echo to the last sent seek, and returns that seek's origin.
     * Call it before sending the next intent: client counters can restart at 1 after an ACK.
     * The server may age a playing seek by its forward delay, so the echo may run ahead of the
     * sent position by up to the elapsed time. An old duplicate echo must not consume the next
     * seek's unrelated origin.
     */
    fun consumeSeekEcho(positionSeconds: Double, clientCounter: Int?, nowMs: Long): LocalSeek? {
        val sent = sentSeek ?: return null
        if (clientCounter != null && clientCounter != sent.counter) return null
        val age = if (sent.intent.playing) (nowMs - sent.sentAtMs).coerceAtLeast(0L) / 1000.0 else 0.0
        val delta = positionSeconds - sent.intent.positionSeconds
        if (delta < -0.001 || delta > age + 0.001) return null
        sentSeek = null
        return sent.intent.seek
    }

    /** A server override may acknowledge our command without echoing a self-seek. */
    fun forgetAcknowledgedSeek() { sentSeek = null }

    fun clear() {
        revision++
        pending = null
        sentSeek = null
    }
}
