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
 * Client-side network layer for the Syncplay protocol. Each platform subclass supplies the
 * socket: TCP on Android, iOS and desktop, a WebSocket on the web.
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

    /** The TLS version that the last upgrade agreed on, such as "TLSv1.3", when the transport can tell. */
    @Volatile
    var tlsVersion: String? = null

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

        /* Decided before the socket opens, not after a TLS answer. A refusal here costs nothing.
         * Later, a connection that never asked for TLS has already sent the password hash in
         * plain text. */
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
         * part was slow. They add a few lines per connection, and the log can be exported from
         * settings, so a report of a flaky join carries them. */
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
            /* Caught before CancellationException on purpose: the dial budget ([DIAL_BUDGET])
             * throws this, and a TimeoutCancellationException rethrown from here would cancel the
             * reconnect campaign that called connect(), ending every further attempt. It is our
             * own deadline firing, not the caller giving up. */
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
            /* Only when the name could not be resolved (see isNameResolutionFailure for why). A
             * host that resolves and then refuses or ignores the connection does the same on its
             * other address, so trying it only doubles the time to the retry that usually works. */
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

    /** How long a connection may stay in the handshake. A test transport shortens it. */
    protected open val handshakeTimeout: Duration get() = HANDSHAKE_TIMEOUT

    private fun armHandshakeDeadline() {
        handshakeDeadlineJob?.cancel()
        handshakeDeadlineJob = viewmodel.viewModelScope.launch(ioDispatcher) {
            delay(handshakeTimeout)
            if (state.value == ConnectionState.CONNECTING) {
                loggy("Handshake timed out after ${handshakeTimeout.inWholeMilliseconds}ms")
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
     * The await is critical: the caller ([RoomCallback.onReceivedTLS]) sends `Hello` as soon as
     * this returns. If the handshake has not completed, the SSL handler either buffers the Hello
     * (Netty and SwiftNIO, which usually works) or, worse, a confused peer reads it as a TLS
     * alert. PC's reference client (`protocols.py`) sends Hello only from its
     * `handshakeCompleted` callback for exactly this reason, and this contract matches it.
     */
    abstract suspend fun upgradeTls()

    /* Volatile: written from the main thread (the room's reconnect action), from transport
     * threads (onDisconnected, onConnectionFailed) and from inside a campaign that aborts itself. */
    @Volatile
    private var reconnectionJob: Job? = null

    /**
     * Schedules automatic reconnection. A single coroutine owns the whole retry loop and keeps
     * retrying until the state reaches CONNECTED (set by `onConnected`), or until [invalidate]
     * or [abortConnection] cancels the job (leaving the room, or a connection refused for good).
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

        /* Started lazily, and only after the field holds it. Launched eagerly, the body can reach
         * abortConnection() before the assignment lands. The abort then cancels whatever the
         * field held before, and this assignment stores a campaign that nothing aborted, which
         * goes on retrying a server that has just refused the connection for good. */
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
                    // run a tight zero-delay reconnect loop that loads the server and the CPU.
                    // Clamping the Duration (not the raw pref number) works whether the pref
                    // reads back as Int or Long.
                    val base = RECONNECTION_INTERVAL.value().seconds.coerceAtLeast(MIN_RECONNECT_INTERVAL)
                    val backoff = (base * (1 shl attempt.coerceAtMost(5))).coerceAtMost(MAX_RECONNECT_INTERVAL)
                    /* Measured from the start of the failed attempt, not from its end. The pause
                     * exists to slow down retries against a server that refuses us, and a
                     * handshake that hung for twenty seconds has already waited longer than any
                     * backoff. Adding the full backoff on top would put a stalled first attempt
                     * and a working second one twenty-two seconds apart. An attempt that fails at
                     * once still waits the whole backoff. */
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
     * This runs as the campaign, not beside it. A separate untracked coroutine cannot be
     * cancelled: it would survive [abortConnection], which exists to end a campaign for good (for
     * example a server that refuses TLS when the user requires it), and start a fresh campaign
     * anyway. Two quick taps would also start two connects at once, because the guard below
     * reads a state that the launched coroutine has not reached yet.
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
     * below. The Syncplay protocol is serial (PC runs one Twisted reactor; the app's own server
     * uses `limitedParallelism(1)`). Handling two `State`s at once would interleave their changes
     * to `protocol.globalPaused`, `globalPositionMs` and the ignoringOnTheFly counters. A channel
     * with one consumer also makes a handler that suspends mid-message (for example the TLS
     * upgrade) finish the whole message before the next line is read.
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
     * Outbound packets, written by one writer STRICTLY in the order they were handed in.
     * Separate fire-and-forget sends would race each other onto the socket, and a room change
     * could arrive after the controller auth that depends on it.
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
     * Enqueues a raw inbound line for ordered processing. Called from raw transport threads
     * (Netty event loop, Ktor reader, SwiftNIO callback), so it must not block.
     */
    fun handlePacket(jsonString: String) {
        if (inboundLines.trySend(jsonString).isSuccess) {
            // Only when the count crosses the limit, so the drop happens once rather than on
            // every line after it, and the counter stays an honest count of what is pending.
            if (inboundBacklog.incrementAndGet() == MAX_INBOUND_BACKLOG + 1) {
                loggy("Inbound backlog passed $MAX_INBOUND_BACKLOG lines; dropping the connection.")
                terminateExistingConnection()
            }
        }
    }

    /**
     * Decodes a raw inbound line and dispatches the typed [WireMessage] to the room's server
     * message handler. The app's own server decodes its inbound lines the same way.
     */
    private suspend fun processPacket(jsonString: String) {
        if (KiteBuildConfig.DEBUG_SYNCPLAY_PROTOCOL) loggy("**SERVER** $jsonString")
        viewmodel.sessionTap?.line(inbound = true, line = jsonString)

        try {
            val message = syncplayJson.decodeFromString(WireMessageDeserializer, jsonString)
            message.dispatch(viewmodel.serverHandler)
        } catch (e: SerializationException) {
            // A single unparseable line must NOT tear down the session. The Syncplay Python
            // protocol is loosely typed and sometimes sends shapes the strict models reject (a
            // user's `features` as `[]`, `size` as a number or a string, a future field of the
            // wrong type; issue #152). Log and skip the offending line; every other message
            // still flows. Only an excerpt is logged: a hostile server must not fill the disk
            // through the log.
            loggy("Skipping unparseable server message: ${jsonString.take(LOGGED_LINE_MAX)}")
            loggy("Reason: ${e.message?.take(LOGGED_LINE_MAX)}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A handler that fails on one message must stop neither this consumer loop (which
            // carries every inbound message) nor the process. Log and move on to the next line.
            loggy("Handler failed on message: ${jsonString.take(LOGGED_LINE_MAX)}")
            loggy(e.stackTraceToString())
        }
    }

    /**
     * Reports a lost socket from the write path, branching the way the transports' own `lost()`
     * callbacks do. A handshake that never connected is a failed connection: reporting it as a
     * disconnection would tell the room it is reconnecting to something it never reached.
     */
    private fun onError() {
        when (state.value) {
            ConnectionState.CONNECTING -> viewmodel.callback.onConnectionFailed()
            else -> viewmodel.callback.onDisconnected()
        }
    }

    /**
     * Encodes a [WireMessage] to JSON and writes it, returning once the write has been made (or
     * given up on). Uses [WireMessage.toJson], so the concrete subclass's serializer is always
     * used, even when [message] is typed as the interface at the call site. That avoids the
     * polymorphic serializer, which would add a `"type"` field that the protocol does not allow.
     *
     * Does nothing in solo mode (watching alone, with no server).
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
     * The reconnect replay uses this. Awaiting each line would run the whole replay on the serial
     * inbound consumer, which is also the only thing that stamps the freshness time the channel
     * watchdog reads. A slow socket with a few lines queued could then hold the consumer past
     * fifteen seconds while `State` packets pile up unread, and the watchdog would declare a
     * healthy connection dead.
     */
    fun sendRawAsync(json: String, queueable: Boolean) {
        if (viewmodel.isSoloMode) return
        outbound.trySend(Outbound(json, queueable, null, generation.value))
    }

    /**
     * Hello and TLS must never be queued: the handshake runs again on reconnect. State must never
     * be queued either. It carries a position or seek that was true the instant the socket died,
     * but the app owns the player, so by reconnect the playhead has moved. Replaying a frozen
     * State (worst case doSeek=true to a stale target) would pull the whole room to a stale
     * position. After a reconnect, the ACK path builds a fresh State from the live player, like
     * PC, which has no outbound queue at all. Chat, playlist and ready messages ARE safe to replay.
     */
    private fun WireMessage.isQueueable(): Boolean =
        this !is WireMessage.Hello && this !is WireMessage.State && this !is WireMessage.TLS &&
            // Nor the keepalive probe: it asks for a roster that will be stale by the time
            // anything replays it, and one probe every fifteen seconds could otherwise push a
            // real chat line off the front of a full queue.
            this !is WireMessage.ListRequest

    /** Write timeouts since the last write that landed. Reset by a success and by a new socket. */
    private val consecutiveWriteTimeouts = atomic(0)

    /**
     * Appends CRLF and writes to the socket with a 10 s timeout, retrying up to three times
     * with a short pause. On final failure, packets flagged [queueable] get queued via
     * [Session.queueOutbound] for replay on reconnect. With no socket at all the write is not
     * retried: the packet is queued (if queueable) and the connection loss is left to the
     * transport's own callback, so a burst of sends cannot start a burst of reconnects.
     *
     * Two of the three outcomes end the attempt rather than repeat it. A retry only makes sense
     * when the transport told us the bytes did not go out. A timeout cannot say that, so the line
     * is not written again: it is queued (if queueable), and only [WRITE_TIMEOUTS_BEFORE_LOSS]
     * timeouts in a row count as a lost socket.
     */
    private suspend fun transmitPacket(json: String, queueable: Boolean) {
        val finalOut = json + "\r\n"
        var attempt = 0
        while (true) {
            try {
                withTimeout(WRITE_TIMEOUT) {
                    if (KiteBuildConfig.DEBUG_SYNCPLAY_PROTOCOL) loggy("Client>>> $finalOut")
                    writeActualString(finalOut)
                }
                viewmodel.sessionTap?.line(inbound = false, line = json)
                consecutiveWriteTimeouts.value = 0
                return
            } catch (_: SocketGoneException) {
                if (queueable) viewmodel.session.queueOutbound(json)
                return
            } catch (e: TimeoutCancellationException) {
                /* Not retried, deliberately. A timeout says the wait was abandoned, not that the
                 * bytes were never sent: the write is already queued in the transport and may well
                 * land. Sending the same line again duplicates a chat message or a playlist edit
                 * on the server. On the Ktor path, a half-written line followed by a whole one
                 * arrives as a single frame, which nothing can parse.
                 *
                 * It is not treated as a dead socket either. One stall is a congested link or a
                 * radio waking up, and the channel watchdog already declares a truly silent
                 * server dead after fifteen seconds. Only a run of timeouts says the socket is
                 * gone. */
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
