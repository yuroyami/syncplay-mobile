package app.protocol.network

import SyncplayMobile.shared.KiteBuildConfig
import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.preferences.Preferences.RECONNECTION_INTERVAL
import app.preferences.Preferences.TLS_ENABLE
import app.preferences.value
import app.protocol.WireMessage
import app.protocol.WireMessageDeserializer
import app.protocol.WireMessageHandler
import app.protocol.models.ConnectionState
import app.protocol.models.TlsState
import app.protocol.syncplayJson
import app.room.RoomViewmodel
import app.utils.loggy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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
        KTOR,    // cross-platform, no TLS
        NETTY,   // Android, TLS
        SWIFTNIO // iOS, TLS
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
    open suspend fun connect() {
        if (viewmodel.isSoloMode) return

        terminateExistingConnection()
        encrypted.value = false
        viewmodel.callback.onConnectionAttempt()
        state.value = ConnectionState.CONNECTING
        armHandshakeDeadline()

        try {
            connectSocket()

            if (tls == TlsState.TLS_ASK) {
                send(WireMessage.tlsRequest())
            } else {
                viewmodel.dispatcher.sendHello()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loggy(e.stackTraceToString())
            viewmodel.callback.onConnectionFailed()
        }
    }

    private var handshakeDeadlineJob: Job? = null

    private fun armHandshakeDeadline() {
        handshakeDeadlineJob?.cancel()
        handshakeDeadlineJob = viewmodel.viewModelScope.launch(Dispatchers.IO) {
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
    fun reconnect() {
        if (reconnectionJob?.isActive == true) return

        reconnectionJob = viewmodel.viewModelScope.launch(Dispatchers.IO) {
            // Drop the stale sync anchor so the first State on the new socket re-anchors the
            // player to the authoritative room position (mirrors PC's _performRetryStateReset).
            // Runs once per reconnect campaign (the isActive guard above prevents re-entry).
            viewmodel.protocol.resetSyncAnchorForReconnect()
            var attempt = 0
            while (isActive && state.value != ConnectionState.CONNECTED) {
                state.value = ConnectionState.SCHEDULING_RECONNECT
                // Clamp the user-configurable interval: it can be 0, which would otherwise
                // spin a tight zero-delay reconnect loop hammering the server and the CPU.
                // Clamping the Duration (not the raw pref number) keeps this agnostic to
                // whether the pref reads back as Int or Long.
                val base = RECONNECTION_INTERVAL.value().seconds.coerceAtLeast(MIN_RECONNECT_INTERVAL)
                val backoff = (base * (1 shl attempt.coerceAtMost(5))).coerceAtMost(MAX_RECONNECT_INTERVAL)
                delay(backoff)
                if (!isActive || state.value == ConnectionState.CONNECTED) break
                // Re-arm TLS negotiation for the fresh socket from the setting, not from the last
                // answer: a new socket has no TLS handler in its pipeline, and a server that
                // answered "false" once must be asked again rather than pinned to plain text.
                tls = if (TLS_ENABLE.value() && supportsTLS()) TlsState.TLS_ASK else TlsState.TLS_NO
                // connect() flips state to CONNECTING; on success the onConnected callback
                // sets CONNECTED. On failure (sync, async, or the handshake deadline) the state
                // lands back on DISCONNECTED. Either way, wait for it before trying again, or a
                // slow handshake gets torn down by its own retry.
                connect()
                state.first { it != ConnectionState.CONNECTING }
                attempt++
            }
        }
    }

    /**
     * Retries at once instead of waiting out the backoff. The running campaign is dropped first,
     * so the next attempt starts now rather than after the delay it was already sleeping through.
     */
    fun reconnectNow() {
        if (viewmodel.isSoloMode) return
        if (state.value == ConnectionState.CONNECTED || state.value == ConnectionState.CONNECTING) return
        reconnectionJob?.cancel()
        reconnectionJob = null
        viewmodel.viewModelScope.launch(Dispatchers.IO) {
            tls = if (TLS_ENABLE.value() && supportsTLS()) TlsState.TLS_ASK else TlsState.TLS_NO
            connect()
            // Whatever the outcome, the ordinary loop takes over from here.
            state.first { it != ConnectionState.CONNECTING }
            if (state.value != ConnectionState.CONNECTED) reconnect()
        }
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

    /** One outbound packet: its JSON, whether a failed write may be replayed, and who is waiting on it. */
    private class Outbound(val json: String, val queueable: Boolean, val done: CompletableDeferred<Unit>?)

    /**
     * Outbound packets, written STRICTLY in the order they were handed in by one writer. Two
     * fire-and-forget sends used to race each other onto the socket, so a room change could
     * arrive after the controller auth that depended on it.
     */
    private val outbound = Channel<Outbound>(capacity = Channel.UNLIMITED)

    init {
        viewmodel.viewModelScope.launch(Dispatchers.Default) {
            for (line in inboundLines) processPacket(line)
        }
        viewmodel.viewModelScope.launch(Dispatchers.IO) {
            for (item in outbound) {
                try {
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
        inboundLines.trySend(jsonString)
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
            loggy("Reason: ${e.message}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A handler blowing up on one message must kill neither this consumer loop (the
            // app's protocol heart) nor the process. Log and move on to the next line.
            loggy("Handler failed on message: ${jsonString.take(LOGGED_LINE_MAX)}")
            loggy(e.stackTraceToString())
        }
    }

    private fun onError() {
        viewmodel.callback.onDisconnected()
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
        outbound.send(Outbound(message.toJson(), message.isQueueable(), done))
        done.await()
    }

    /** Fire-and-forget [send]: same writer, same order, nobody waits. */
    fun sendAsync(message: WireMessage) {
        if (viewmodel.isSoloMode) return
        outbound.trySend(Outbound(message.toJson(), message.isQueueable(), null))
    }

    /** A pre-encoded line (a replayed queue entry) through the same ordered writer. */
    suspend fun sendRaw(json: String, queueable: Boolean) {
        if (viewmodel.isSoloMode) return
        val done = CompletableDeferred<Unit>()
        outbound.send(Outbound(json, queueable, done))
        done.await()
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
        this !is WireMessage.Hello && this !is WireMessage.State && this !is WireMessage.TLS

    /**
     * Appends CRLF and writes to the socket with a 10 s timeout, retrying up to three times
     * with a short pause. On final failure, packets flagged [queueable] get queued via
     * [Session.queueOutbound] for replay on reconnect. With no socket at all the write is not
     * retried: the packet is queued (if queueable) and the connection loss is left to the
     * transport's own callback, so a burst of sends cannot start a burst of reconnects.
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
                return
            } catch (_: SocketGoneException) {
                if (queueable) viewmodel.session.queueOutbound(json)
                return
            } catch (e: TimeoutCancellationException) {
                loggy("Write timed out: ${e.message}")
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

        val WRITE_TIMEOUT = 10.seconds
        const val WRITE_RETRIES = 3
        const val WRITE_RETRY_PAUSE_MS = 250L
        const val LOGGED_LINE_MAX = 300
    }
}
