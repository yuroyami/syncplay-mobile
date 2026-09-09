package app.protocol.network

import SyncplayMobile.shared.KiteBuildConfig
import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.preferences.Preferences.RECONNECTION_INTERVAL
import app.preferences.Preferences.TLS_ENABLE
import app.preferences.Preferences.TLS_REQUIRED
import app.preferences.value
import app.protocol.WireMessage
import app.protocol.WireMessageDeserializer
import app.protocol.WireMessageHandler
import app.protocol.models.ConnectionState
import app.protocol.models.TlsState
import app.protocol.syncplayJson
import app.room.RoomViewmodel
import app.utils.ioDispatcher
import app.utils.loggy
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlin.time.Duration.Companion.seconds

/**
 * Client-side TCP network layer.
 *
 * Inbound: raw lines → [syncplayJson] decode via [WireMessageDeserializer] → typed
 * [WireMessage] → [WireMessage.dispatch] into the room's [WireMessageHandler].
 *
 * Outbound: callers construct typed [WireMessage] instances and pass them to [send] /
 * [sendAsync]; encoding goes through [syncplayJson] and onto the wire, in the order the calls
 * were made, through one writer.
 */
abstract class NetworkManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    open val engine: NetworkEngine = NetworkEngine.SWIFTNIO

    val state = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)

    /** TLS_NO = plain TCP, TLS_YES = encrypted, TLS_ASK = negotiate with server. */
    var tls: TlsState = TlsState.TLS_NO

    /**
     * Whether this socket is actually encrypted, for the room to show. Distinct from [tls],
     * which flips to TLS_YES the moment we commit to the upgrade so a second TLS message is
     * ignored. This only becomes true once the handshake has really completed.
     */
    val encrypted = MutableStateFlow(false)

    enum class NetworkEngine {
        KTOR,     // cross-platform, no TLS
        NETTY,    // Android and desktop, TLS
        SWIFTNIO, // iOS, TLS
        WEBSOCKET // web, encrypted only when the page is served over https
    }

    /** Thrown by [writeActualString] when there is no socket at all: not a retry case. */
    class SocketGoneException : Exception("No socket to write to")

    override fun invalidate() {
        handshakeDeadlineJob?.cancel()
        handshakeDeadlineJob = null
        reconnectionJob?.cancel()
        reconnectionJob = null
        terminateExistingConnection()
        state.value = ConnectionState.DISCONNECTED
        tls = TlsState.TLS_NO
        encrypted.value = false
    }

    /**
     * Connects to the server. If [tls] is TLS_ASK, sends a TLS negotiation packet first;
     * otherwise sends Hello directly. The handshake (socket, optional TLS, Hello and its reply)
     * has a deadline: a server that accepts and then says nothing must not leave the room in
     * CONNECTING forever, where no watchdog runs.
     */
    open suspend fun connect(announceTlsCheck: Boolean = true) {
        if (viewmodel.isSoloMode) return

        terminateExistingConnection()
        generation.incrementAndGet()
        consecutiveWriteTimeouts.value = 0
        encrypted.value = false

        /* Before the socket, not after an answer. A refusal here has cost nothing; the same
         * refusal one step later has already put the password hash on the wire in plain text. */
        when (armTlsFromSettings()) {
            TlsDecision.REFUSE -> {
                viewmodel.callback.onTlsRequiredButUnavailable()
                abortConnection()
                return
            }
            TlsDecision.ASK -> if (announceTlsCheck) viewmodel.callback.onTLSCheck()
            TlsDecision.PLAIN -> Unit
        }

        viewmodel.callback.onConnectionAttempt()
        state.value = ConnectionState.CONNECTING
        armHandshakeDeadline()

        /* Phase timings, always on. A handshake against the official server is three round
         * trips and takes seconds even when it works, so "it did not connect" needs to say which
         * part was slow. These are a handful of lines per connection and the log is exportable
         * from settings, which is what a report of a flaky join actually needs to carry. */
        handshakeStartedAt = TimeSource.Monotonic.markNow()
        try {
            connectSocketOrFallback()
            loggy("Handshake: socket open after ${sinceHandshakeStart()}")

            if (tls == TlsState.TLS_ASK) {
                send(WireMessage.tlsRequest())
                loggy("Handshake: TLS request sent after ${sinceHandshakeStart()}")
            } else {
                viewmodel.dispatcher.sendHello()
                loggy("Handshake: Hello sent after ${sinceHandshakeStart()}")
            }
        } catch (e: TimeoutCancellationException) {
            /* Caught before CancellationException on purpose: the dial budget above throws this,
             * and a TimeoutCancellationException rethrown from here would cancel the reconnect
             * campaign that called connect(), ending every further attempt. It is our own
             * deadline firing, not the caller giving up. */
            loggy("Handshake: dial gave up after ${sinceHandshakeStart()}")
            terminateExistingConnection()
            viewmodel.callback.onConnectionFailed()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loggy("Handshake: failed after ${sinceHandshakeStart()}")
            loggy(e.stackTraceToString())
            viewmodel.callback.onConnectionFailed()
        }
    }

    /** When the current handshake began, for the phase timings in the log. */
    @Volatile
    private var handshakeStartedAt: TimeSource.Monotonic.ValueTimeMark? = null

    /** How long the current handshake has been running, as a printable string. */
    fun sinceHandshakeStart(): String {
        val started = handshakeStartedAt ?: return "?"
        return "${started.elapsedNow().inWholeMilliseconds}ms"
    }

    /**
     * Dials, and on failure dials the session's fallback address once.
     *
     * Only the official server has a fallback, and only because it is dialled by name: a network
     * whose DNS is broken or blocked can still reach the address the app was built with. A
     * fallback that works is kept for the rest of the session, so later reconnects go straight
     * to it. TLS is unaffected either way, because the certificate is checked against the name
     * the user typed, never against whatever the socket dialled.
     */
    private suspend fun connectSocketOrFallback() {
        try {
            dialWithBudget()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketGoneException) {
            // This attempt was superseded by a newer one, not refused by the host. Dialling the
            // fallback here would swap the session onto a pinned address for no reason.
            throw e
        } catch (e: Exception) {
            val fallback = viewmodel.session.fallbackHost
            if (fallback == null || fallback == viewmodel.session.serverHost) throw e
            /* Only when the name could not be resolved. The fallback exists for a broken or
             * blocked resolver, and nothing else: a host that resolves fine and then refuses or
             * ignores the connection will do exactly the same on its other address, so trying it
             * only spends a second dial timeout before reporting the failure the caller already
             * had. That doubled the time to the retry that usually works. */
            if (!isNameResolutionFailure(e)) throw e
            loggy("Could not resolve ${viewmodel.session.serverHost} (${e.message}); trying $fallback")
            // Whatever the failed attempt left behind goes before the next one starts.
            terminateExistingConnection()
            viewmodel.session.serverHost = fallback
            dialWithBudget()
        }
    }

    /**
     * One dial, with a ceiling above the transport's own.
     *
     * A dial is the one phase that can stall with nothing to notice it: the socket is not open,
     * so no read ever arrives, and a transport whose own deadline does not fire leaves the entire
     * handshake budget to a connect that is going nowhere. Per dial rather than around the pair,
     * so a slow name failure cannot eat the fallback's turn. Measured against the official server
     * a working dial is about a second.
     */
    private suspend fun dialWithBudget() = withTimeout(DIAL_BUDGET) { connectSocket() }


    private var handshakeDeadlineJob: Job? = null

    private fun armHandshakeDeadline() {
        handshakeDeadlineJob?.cancel()
        handshakeDeadlineJob = viewmodel.viewModelScope.launch(ioDispatcher) {
            delay(HANDSHAKE_TIMEOUT)
            if (state.value == ConnectionState.CONNECTING) {
                loggy("Handshake timed out after ${HANDSHAKE_TIMEOUT.inWholeSeconds}s")
                terminateExistingConnection()
                viewmodel.callback.onConnectionFailed()
            }
        }
    }

    /** Drops the connection for good: no reconnect loop. For a refused plain-text downgrade. */
    fun abortConnection() {
        reconnectionJob?.cancel()
        reconnectionJob = null
        handshakeDeadlineJob?.cancel()
        handshakeDeadlineJob = null
        terminateExistingConnection()
        state.value = ConnectionState.DISCONNECTED
    }

    /**
     * Decides the TLS mode for a fresh socket from the settings and this transport, and applies
     * it. Called by [connect] on every attempt, including reconnects: a new socket has no TLS
     * handler in its pipeline, and a server that answered "false" once must be asked again
     * rather than pinned to plain text.
     */
    fun armTlsFromSettings(): TlsDecision {
        val decision = decideTls(TLS_ENABLE.value(), TLS_REQUIRED.value(), supportsTLS())
        tls = if (decision == TlsDecision.ASK) TlsState.TLS_ASK else TlsState.TLS_NO
        return decision
    }

    abstract suspend fun connectSocket()
    abstract fun supportsTLS(): Boolean
    abstract fun terminateExistingConnection()

    /**
     * Writes [s] and returns once the transport has accepted it, throwing when it has not, so the
     * retry and queue logic in [transmitPacket] sees real outcomes. Throws [SocketGoneException]
     * when there is no socket.
     */
    abstract suspend fun writeActualString(s: String)

    /**
     * Inserts the TLS handler into the channel pipeline AND awaits handshake completion
     * before returning.
     *
     * The await is critical: callers (specifically [RoomCallback.onReceivedTLS]) send
     * `Hello` immediately after this returns. If the handshake hasn't completed, the
     * Hello is either buffered by the SSL handler (Netty/SwiftNIO — usually works) or,
     * worse, gets framed as a TLS alert by a confused peer. PC's reference client
     * (`protocols.py`) gates `sendHello` on the `handshakeCompleted` callback for
     * exactly this reason — we mirror that contract.
     */
    abstract suspend fun upgradeTls()

    /* Volatile: written from the main thread (the room's reconnect action), from transport
     * threads (onDisconnected, onConnectionFailed) and from inside a campaign that aborts itself. */
    @Volatile
    private var reconnectionJob: Job? = null

    /**
     * Schedules automatic reconnection. A single coroutine owns the whole retry loop and keeps
     * retrying until the state reaches CONNECTED ([onConnected]) or the job is cancelled by
     * [invalidate]/[abortConnection] (manual disconnect / leaving the room).
     *
     * The guard is on [Job.isActive], not isCompleted: a synchronous connect failure re-enters
     * [reconnect] from within the running loop, where the job is still active, so the re-entry
     * is a harmless no-op and the existing loop keeps driving retries.
     *
     * Each attempt waits for the previous handshake to settle (the deadline guarantees it does),
     * and the pause between attempts doubles up to [MAX_RECONNECT_INTERVAL], so a server that is
     * down does not keep the radio busy every two seconds for hours.
     */
    fun reconnect(skipFirstBackoff: Boolean = false) {
        if (reconnectionJob?.isActive == true) return

        /* Started lazily and only after the field holds it. Launched eagerly, the body could
         * reach abortConnection() before the assignment landed: the abort then cancelled and
         * cleared whatever was there before, and this assignment stored a campaign nothing had
         * aborted, which went on retrying a server that had just refused the connection for good. */
        val campaign = viewmodel.viewModelScope.launch(ioDispatcher, start = CoroutineStart.LAZY) {
            // Drop the stale sync anchor so the first State on the new socket re-anchors the
            // player to the authoritative room position (mirrors PC's _performRetryStateReset).
            // Runs once per reconnect campaign (the isActive guard above prevents re-entry).
            viewmodel.protocol.resetSyncAnchorForReconnect()
            var attempt = 0
            var skipBackoff = skipFirstBackoff
            // How long the attempt that just failed took. The pause before the next one counts
            // it, so an attempt that already spent twenty seconds does not then wait again.
            var lastAttemptTook: Duration = Duration.ZERO
            while (isActive && state.value != ConnectionState.CONNECTED) {
                state.value = ConnectionState.SCHEDULING_RECONNECT
                if (skipBackoff) {
                    skipBackoff = false
                } else {
                    // Clamp the user-configurable interval: it can be 0, which would otherwise
                    // spin a tight zero-delay reconnect loop hammering the server and the CPU.
                    // Clamping the Duration (not the raw pref number) keeps this agnostic to
                    // whether the pref reads back as Int or Long.
                    val base = RECONNECTION_INTERVAL.value().seconds.coerceAtLeast(MIN_RECONNECT_INTERVAL)
                    val backoff = (base * (1 shl attempt.coerceAtMost(5))).coerceAtMost(MAX_RECONNECT_INTERVAL)
                    /* Measured from the start of the failed attempt, not from its end. The point
                     * of the pause is to stop hammering a server that is refusing us, and a
                     * handshake that hung for twenty seconds has paid that many times over: the
                     * old form added the full backoff on top, so a first attempt that stalled and
                     * a second that would have worked were twenty-two seconds apart. An attempt
                     * that fails instantly still waits the whole thing. */
                    val remaining = backoff - lastAttemptTook
                    if (remaining > Duration.ZERO) delay(remaining)
                }
                if (!isActive || state.value == ConnectionState.CONNECTED) break
                // connect() flips state to CONNECTING; on success the onConnected callback
                // sets CONNECTED. On failure (sync, async, or the handshake deadline) the state
                // lands back on DISCONNECTED. Either way, wait for it before trying again, or a
                // slow handshake gets torn down by its own retry.
                val startedAt = TimeSource.Monotonic.markNow()
                connect(announceTlsCheck = false)
                state.first { it != ConnectionState.CONNECTING }
                lastAttemptTook = startedAt.elapsedNow()
                attempt++
            }
        }
        reconnectionJob = campaign
        campaign.start()
    }

    /**
     * Retries at once instead of waiting out the backoff. The running campaign is dropped first,
     * so the next attempt starts now rather than after the delay it was already sleeping through.
     *
     * This runs as the campaign, not beside it. It used to launch its own untracked coroutine,
     * which nothing could cancel: [abortConnection] exists to end a campaign for good (a server
     * that refuses TLS when the user demands it), and that orphan survived the abort and started
     * a fresh campaign anyway. Two quick taps also produced two concurrent connects, because the
     * guard below reads a state the launched coroutine had not reached yet.
     */
    fun reconnectNow() {
        if (viewmodel.isSoloMode) return
        if (state.value == ConnectionState.CONNECTED || state.value == ConnectionState.CONNECTING) return
        reconnectionJob?.cancel()
        reconnectionJob = null
        reconnect(skipFirstBackoff = true)
    }

    /**
     * Inbound lines, processed STRICTLY one at a time in arrival order by the single consumer
     * below. The Syncplay protocol is serial (PC runs one Twisted reactor; the server side here
     * uses `limitedParallelism(1)`); handling two `State`s concurrently would interleave their
     * mutations of `protocol.globalPaused`/`globalPositionMs`/ignoringOnTheFly. A channel plus
     * single consumer also guarantees a handler that suspends mid-message (Main-thread hops in
     * onState) finishes the whole message before the next line is read.
     */
    private val inboundLines = Channel<String>(capacity = Channel.UNLIMITED)

    /**
     * One outbound packet: its JSON, whether a failed write may be replayed, who is waiting on
     * it, and which socket it was written for.
     */
    private class Outbound(
        val json: String,
        val queueable: Boolean,
        val done: CompletableDeferred<Unit>?,
        val generation: Int,
    )

    /**
     * Which socket we are on. Increases on every connect, so work that was queued for the
     * previous one can be told apart from work meant for this one: a State computed against a
     * room we have since left is not something the new socket should say.
     */
    private val generation = atomic(0)

    /**
     * Outbound packets, written STRICTLY in the order they were handed in by one writer. Two
     * fire-and-forget sends used to race each other onto the socket, so a room change could
     * arrive after the controller auth that depended on it.
     */
    private val outbound = Channel<Outbound>(capacity = Channel.UNLIMITED)

    /**
     * Lines handed in but not yet processed.
     *
     * [inboundLines] has to be unbounded: dropping a protocol line would leave the room acting on
     * a state that never arrived, so backpressure is not an option here. That leaves the depth as
     * the thing to watch. A server sending faster than this client can parse, forever, is either
     * broken or hostile, and the queue is the only place that shows up before the process runs
     * out of memory.
     */
    private val inboundBacklog = atomic(0)

    init {
        viewmodel.viewModelScope.launch(Dispatchers.Default) {
            for (line in inboundLines) {
                inboundBacklog.decrementAndGet()
                processPacket(line)
            }
        }
        viewmodel.viewModelScope.launch(ioDispatcher) {
            for (item in outbound) {
                try {
                    if (item.generation != generation.value) {
                        // Written for a socket that is gone. Anything replayable waits for the
                        // new handshake instead of going out stale.
                        if (item.queueable) viewmodel.session.queueOutbound(item.json)
                        continue
                    }
                    transmitPacket(item.json, item.queueable)
                } finally {
                    item.done?.complete(Unit)
                }
            }
        }
    }

    /**
     * Enqueues a raw inbound line for ordered processing. Called from raw transport
     * threads (Netty event loop / Ktor reader / SwiftNIO callback) — must not block.
     */
    fun handlePacket(jsonString: String) {
        if (inboundLines.trySend(jsonString).isSuccess) {
            // On the crossing only, so the counter stays an honest count of what is pending and
            // the drop happens once rather than on every line after it.
            if (inboundBacklog.incrementAndGet() == MAX_INBOUND_BACKLOG + 1) {
                loggy("Inbound backlog passed $MAX_INBOUND_BACKLOG lines; dropping the connection.")
                terminateExistingConnection()
            }
        }
    }

    /**
     * Decodes a raw inbound line and dispatches the typed [WireMessage] to the room's
     * server handler. Same serialization plumbing as the server's mirror-image pipeline.
     */
    private suspend fun processPacket(jsonString: String) {
        if (KiteBuildConfig.DEBUG_SYNCPLAY_PROTOCOL) loggy("**SERVER** $jsonString")

        try {
            val message = syncplayJson.decodeFromString(WireMessageDeserializer, jsonString)
            message.dispatch(viewmodel.serverHandler)
        } catch (e: SerializationException) {
            // A single unparseable line must NOT tear down the session. The Syncplay python
            // protocol is loosely typed and periodically sends shapes the strict models reject
            // (a user's `features` as `[]`, `size` number-vs-string, a future field of the
            // wrong type; issue #152). Log and skip the offending line; every other message
            // still flows. Mirrors the server side's ClientConnection.handlePacket. Only an
            // excerpt is logged: a hostile server must not fill the disk through the log.
            loggy("Skipping unparseable server message: ${jsonString.take(LOGGED_LINE_MAX)}")
            loggy("Reason: ${e.message?.take(LOGGED_LINE_MAX)}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A handler blowing up on one message must kill neither this consumer loop (the
            // app's protocol heart) nor the process. Log and move on to the next line.
            loggy("Handler failed on message: ${jsonString.take(LOGGED_LINE_MAX)}")
            loggy(e.stackTraceToString())
        }
    }

    /**
     * The write path's version of a lost socket, branching the way the transports' own `lost()`
     * callbacks do. Reporting a disconnection for a handshake that never connected told the room
     * it was reconnecting to something it had never reached.
     */
    private fun onError() {
        when (state.value) {
            ConnectionState.CONNECTING -> viewmodel.callback.onConnectionFailed()
            else -> viewmodel.callback.onDisconnected()
        }
    }

    /**
     * Encodes a [WireMessage] to JSON and writes it, returning once the write has been made (or
     * given up on). Uses [WireMessage.toJson] so the concrete-subclass serializer is always
     * used, even when [message] is typed at the call site as the interface — that protects
     * against the polymorphic-discriminator trap that would otherwise inject a `"type"` field
     * the protocol doesn't allow.
     *
     * No-op in solo mode.
     */
    suspend fun send(message: WireMessage) {
        if (viewmodel.isSoloMode) return
        val done = CompletableDeferred<Unit>()
        outbound.send(Outbound(message.toJson(), message.isQueueable(), done, generation.value))
        done.await()
    }

    /** Fire-and-forget [send]: same writer, same order, nobody waits. */
    fun sendAsync(message: WireMessage) {
        if (viewmodel.isSoloMode) return
        outbound.trySend(Outbound(message.toJson(), message.isQueueable(), null, generation.value))
    }

    /** A pre-encoded line (a replayed queue entry) through the same ordered writer. */
    suspend fun sendRaw(json: String, queueable: Boolean) {
        if (viewmodel.isSoloMode) return
        val done = CompletableDeferred<Unit>()
        outbound.send(Outbound(json, queueable, done, generation.value))
        done.await()
    }

    /**
     * Fire-and-forget [sendRaw]: same writer, same order, nobody waits.
     *
     * The reconnect replay uses this. Awaiting each line meant the whole replay ran on the serial
     * inbound consumer, which is also the only thing that stamps the freshness clock the channel
     * watchdog reads. A slow socket with a few lines queued could therefore hold the consumer
     * past fifteen seconds while State packets piled up unread, and the watchdog would call a
     * perfectly healthy connection dead.
     */
    fun sendRawAsync(json: String, queueable: Boolean) {
        if (viewmodel.isSoloMode) return
        outbound.trySend(Outbound(json, queueable, null, generation.value))
    }

    /**
     * Hello must never be queued (the handshake re-runs on reconnect). State must never be
     * queued either: it carries a position/seek that was true the instant the socket died,
     * but the app owns the player so by reconnect the playhead has moved — replaying a frozen
     * State (worst case doSeek=true to a stale target) would yank the whole room. State
     * regenerates fresh from the live player via the ACK path after reconnect, matching PC,
     * which has no outbound queue at all. Chat/playlist/ready ARE legitimate to replay.
     */
    private fun WireMessage.isQueueable(): Boolean =
        this !is WireMessage.Hello && this !is WireMessage.State && this !is WireMessage.TLS &&
            // Nor the keepalive probe: it asks for a roster that will be stale by the time
            // anything replays it, and one probe every fifteen seconds could otherwise push a
            // real chat line off the front of a full queue.
            this !is WireMessage.ListRequest

    /**
     * Appends CRLF and writes to the socket with a 10 s timeout, retrying up to three times
     * with a short pause. On final failure, packets flagged [queueable] get queued via
     * [Session.queueOutbound] for replay on reconnect. With no socket at all the write is not
     * retried: the packet is queued (if queueable) and the connection loss is left to the
     * transport's own callback, so a burst of sends cannot start a burst of reconnects.
     *
     * Two of the three outcomes end the attempt rather than repeat it. A retry only makes sense
     * when the transport told us the bytes did not go out; a timeout cannot say that, so it is
     * treated as a lost socket instead of being written again.
     */
    /** Write timeouts since the last write that landed. Reset by a success and by a new socket. */
    private val consecutiveWriteTimeouts = atomic(0)

    private suspend fun transmitPacket(json: String, queueable: Boolean) {
        val finalOut = json + "\r\n"
        var attempt = 0
        while (true) {
            try {
                withTimeout(WRITE_TIMEOUT) {
                    if (KiteBuildConfig.DEBUG_SYNCPLAY_PROTOCOL) loggy("Client>>> $finalOut")
                    writeActualString(finalOut)
                }
                consecutiveWriteTimeouts.value = 0
                return
            } catch (_: SocketGoneException) {
                if (queueable) viewmodel.session.queueOutbound(json)
                return
            } catch (e: TimeoutCancellationException) {
                /* Not retried, deliberately. A timeout says the wait was abandoned, not that the
                 * bytes stayed home: the write is already queued in the transport and may well
                 * land. Sending the same line again duplicated a chat message or a playlist edit
                 * on the server, and on the Ktor path a half-written line followed by a whole one
                 * framed as a single frame, which nothing can parse.
                 *
                 * It is not treated as a dead socket either. One stall is a congested link or a
                 * radio waking up, and the channel watchdog already declares a genuinely silent
                 * server dead after fifteen seconds. Only a run of them says the socket is gone. */
                loggy("Write timed out after ${WRITE_TIMEOUT.inWholeSeconds}s: ${e.message}")
                if (queueable) viewmodel.session.queueOutbound(json)
                if (consecutiveWriteTimeouts.incrementAndGet() >= WRITE_TIMEOUTS_BEFORE_LOSS) {
                    loggy("$WRITE_TIMEOUTS_BEFORE_LOSS writes in a row timed out; treating the socket as gone.")
                    consecutiveWriteTimeouts.value = 0
                    onError()
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loggy(e.stackTraceToString())
            }
            attempt++
            if (attempt > WRITE_RETRIES) {
                loggy("SOCKET INVALID")
                if (queueable) viewmodel.session.queueOutbound(json)
                onError()
                return
            }
            delay(WRITE_RETRY_PAUSE_MS * attempt)
        }
    }

    companion object {
        /**
         * Floor for the reconnect delay. The RECONNECTION_INTERVAL preference allows 0,
         * which would otherwise produce a `delay(0)` tight loop on every retry.
         */
        val MIN_RECONNECT_INTERVAL = 1.seconds

        /** Ceiling for the doubled reconnect delay. */
        val MAX_RECONNECT_INTERVAL = 60.seconds

        /** Socket open, optional TLS, Hello and its reply must all land within this. */
        val HANDSHAKE_TIMEOUT = 20.seconds

        /**
         * Ceiling on the dial alone, above each transport's own connect timeout so it only fires
         * when that one did not. Against the official server a working dial is about a second.
         */
        val DIAL_BUDGET = 12.seconds

        /**
         * How many unparsed inbound lines may wait before the peer is treated as hostile.
         * Generous: a busy room's join burst is a few dozen lines, not thousands.
         */
        const val MAX_INBOUND_BACKLOG = 5_000

        val WRITE_TIMEOUT = 10.seconds

        /** Consecutive write timeouts that together mean the socket, not the moment, is the problem. */
        const val WRITE_TIMEOUTS_BEFORE_LOSS = 3
        const val WRITE_RETRIES = 3
        const val WRITE_RETRY_PAUSE_MS = 250L
        const val LOGGED_LINE_MAX = 300
    }
}
