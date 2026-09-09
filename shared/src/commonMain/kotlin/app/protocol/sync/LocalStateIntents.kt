package app.protocol.sync

/** Local seek metadata never goes on the wire. It follows the intent that was actually sent. */
data class LocalSeek(val fromMs: Long, val toMs: Long, val recordUndo: Boolean = true)

data class LocalStateIntent(
    val positionSeconds: Double,
    val playing: Boolean,
    val seek: LocalSeek? = null,
)

/**
 * One latest unsent state, bounded even during a seek storm. The owner holds its sync lock.
 * The existing ignoringOnTheFly gate decides when [takeReady] may send; waiting never creates
 * a packet or increments a counter. A later pause/play preserves a queued explicit seek.
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
     * Match before sending the next intent: client counters can restart at one after an ACK.
     * The server may age a playing seek by its forward delay, bounded by elapsed time here.
     * A duplicate old echo must not consume the next seek's unrelated origin.
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
