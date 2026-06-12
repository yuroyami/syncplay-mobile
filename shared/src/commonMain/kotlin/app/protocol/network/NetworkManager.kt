package app.protocol.network

import SyncplayMobile.shared.BuildConfig
import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.preferences.Preferences.RECONNECTION_INTERVAL
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * [sendAsync]; encoding goes through [syncplayJson] and onto the wire.
 */
abstract class NetworkManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    open val engine: NetworkEngine = NetworkEngine.SWIFTNIO

    val state = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)

    /** TLS_NO = plain TCP, TLS_YES = encrypted, TLS_ASK = negotiate with server. */
    var tls: TlsState = TlsState.TLS_NO

    enum class NetworkEngine {
        KTOR,    // cross-platform, no TLS
        NETTY,   // Android, TLS
        SWIFTNIO // iOS, TLS
    }

    override fun invalidate() {
        terminateExistingConnection()
        state.value = ConnectionState.DISCONNECTED
        tls = TlsState.TLS_NO
    }

    /**
     * Connects to the server. If [tls] is TLS_ASK, sends a TLS negotiation packet first;
     * otherwise sends Hello directly.
     */
    open suspend fun connect() {
        if (viewmodel.isSoloMode) return

        terminateExistingConnection()
        viewmodel.callback.onConnectionAttempt()
        state.value = ConnectionState.CONNECTING

        try {
            connectSocket()

            if (tls == TlsState.TLS_ASK) {
                send(WireMessage.tlsRequest())
            } else {
                viewmodel.dispatcher.sendHello()
            }
        } catch (e: Exception) {
            loggy(e.stackTraceToString())
            viewmodel.callback.onConnectionFailed()
        }
    }

    abstract suspend fun connectSocket()
    abstract fun supportsTLS(): Boolean
    abstract fun terminateExistingConnection()
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
     * Schedules automatic reconnection.
     *
     * One coroutine owns the whole retry loop. The old design launched a single delayed
     * [connect] and relied on the next [onConnectionFailed] to re-arm a fresh job — but a
     * synchronous connect failure (Netty's `await` timing out, Ktor's connect throwing)
     * re-enters [reconnect] while still nested *inside* this very job. At that moment
     * `reconnectionJob?.isCompleted` is still `false`, so the old guard refused to
     * reschedule and auto-reconnect died after a single attempt. We now loop here instead,
     * and guard on [Job.isActive] so a nested re-entry is a harmless no-op (the running loop
     * keeps retrying on its own).
     *
     * Stops when CONNECTED ([onConnected] sets that), or when the job is cancelled by
     * [invalidate]/[terminateExistingConnection] (manual disconnect / leaving the room).
     */
    fun reconnect() {
        // A loop is already running — let it keep driving retries. Guarding on isActive
        // (not isCompleted) is what fixes the single-retry bug: a synchronous connect
        // failure re-enters here from within the loop, where the job is still active.
        if (reconnectionJob?.isActive == true) return

        reconnectionJob = viewmodel.viewModelScope.launch(Dispatchers.IO) {
            // Drop the stale sync anchor so the first State on the new socket re-anchors the
            // player to the authoritative room position (mirrors PC's _performRetryStateReset).
            // Runs once per reconnect campaign (the isActive guard above prevents re-entry).
            viewmodel.protocol.resetSyncAnchorForReconnect()
            while (isActive && state.value != ConnectionState.CONNECTED) {
                state.value = ConnectionState.SCHEDULING_RECONNECT
                // Clamp the user-configurable interval: it can be 0, which would otherwise
                // spin a tight zero-delay reconnect loop hammering the server and the CPU.
                // Clamping the Duration (not the raw pref number) keeps this agnostic to
                // whether the pref reads back as Int or Long.
                val interval = RECONNECTION_INTERVAL.value().seconds
                    .coerceAtLeast(MIN_RECONNECT_INTERVAL)
                delay(interval)
                if (!isActive || state.value == ConnectionState.CONNECTED) break
                // Re-arm TLS negotiation for the fresh socket. After a successful encrypted
                // session [tls] is left at TLS_YES, but a brand-new socket has no SSL handler
                // in its pipeline — if we reconnect with TLS_YES, connect() skips the startTLS
                // step and sends Hello in plaintext, silently downgrading an encrypted room.
                // Resetting to TLS_ASK makes the reconnect re-do the same negotiation the
                // initial connect did. (TLS_NO is left alone: TLS was disabled / unsupported.)
                if (tls == TlsState.TLS_YES) tls = TlsState.TLS_ASK
                // connect() flips state to CONNECTING; on success the onConnected callback
                // sets CONNECTED and the loop exits next iteration. On failure (sync or
                // async) the state lands back on DISCONNECTED and we retry after the delay.
                connect()
            }
        }
    }

    /**
     * Inbound lines, processed STRICTLY one at a time, in arrival order, by the single
     * consumer below. The old implementation launched one coroutine per line on
     * [Dispatchers.Default] — a multithreaded pool with no ordering guarantee — so two
     * `State`s could be handled out of order or *concurrently*, interleaving their
     * mutations of `protocol.globalPaused`/`globalPositionMs`/ignoringOnTheFly across
     * threads. The python reference protocol is strictly serial (one Twisted reactor);
     * our own server side already enforces the same with `limitedParallelism(1)`. A
     * channel + single consumer goes one step further than a confined dispatcher: a
     * handler that *suspends* mid-message (the Main-thread hops in onState) still
     * finishes the whole message before the next line is even read.
     */
    private val inboundLines = Channel<String>(capacity = Channel.UNLIMITED)

    init {
        viewmodel.viewModelScope.launch(Dispatchers.Default) {
            for (line in inboundLines) processPacket(line)
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
     * Decodes a raw inbound line and dispatches the typed [WireMessage] to
     * [viewmodel.serverHandler]. Same Kotlinx Serialization plumbing as the server's
     * mirror-image pipeline.
     */
    private suspend fun processPacket(jsonString: String) {
        if (BuildConfig.DEBUG_SYNCPLAY_PROTOCOL) loggy("**SERVER** $jsonString")

        try {
            val message = syncplayJson.decodeFromString(WireMessageDeserializer, jsonString)
            message.dispatch(viewmodel.serverHandler)
        } catch (e: SerializationException) {
            // A single unparseable line must NOT tear down the session. The Syncplay
            // python protocol is loosely typed and periodically sends shapes our strict
            // models don't expect (a user's `features` as `[]`, a `size` number-vs-string,
            // a future field of the wrong type, see issue #152). Rethrowing here used to
            // propagate into this dispatch coroutine, drop the connection and spin the
            // reconnect loop — re-fetching the same poisoned List and crashing again every
            // few seconds. Log the offending line and skip it; every other message still
            // flows. Mirrors the server side's ClientConnection.handlePacket, which already
            // swallows-and-continues rather than crashing.
            loggy("Skipping unparseable server message: $jsonString")
            loggy("Reason: ${e.message}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A handler blowing up on one message must kill neither the consumer loop nor
            // the process (this loop is the app's protocol heart — an uncaught exception
            // here used to take the whole app down, e.g. a TLS upgrade failure surfacing
            // through onReceivedTLS). Log loudly and move on to the next line.
            loggy("Handler failed on message: $jsonString")
            loggy(e.stackTraceToString())
        }
    }

    private fun onError() {
        viewmodel.callback.onDisconnected()
    }

    /**
     * Encodes a [WireMessage] to JSON and writes it. Uses [WireMessage.toJson] so the
     * concrete-subclass serializer is always used, even when [message] is typed at the
     * call site as the interface — that protects against the polymorphic-discriminator
     * trap that would otherwise inject a `"type"` field the protocol doesn't allow.
     *
     * No-op in solo mode.
     */
    suspend fun send(message: WireMessage) {
        if (viewmodel.isSoloMode) return
        // Hello must never be queued (the handshake re-runs on reconnect). State must never be
        // queued either: it carries a position/seek that was true the instant the socket died,
        // but the app owns the player so by reconnect the playhead has moved — replaying a frozen
        // State (worst case doSeek=true to a stale target) would yank the whole room. State
        // regenerates fresh from the live player via the ACK path after reconnect, matching PC,
        // which has no outbound queue at all. Chat/playlist/ready ARE legitimate to replay.
        val queueable = message !is WireMessage.Hello && message !is WireMessage.State
        transmitPacket(message.toJson(), queueable = queueable)
    }

    /** Fire-and-forget [send] — launched on [Dispatchers.IO]. */
    fun sendAsync(message: WireMessage) {
        viewmodel.viewModelScope.launch(Dispatchers.IO) { send(message) }
    }

    /**
     * Appends CRLF, writes to socket with a 10s timeout. Retries up to 3 times before giving up.
     * On final failure, packets flagged [queueable] get queued via [Session.queueOutbound] for
     * replay on reconnect. Hello and State are NOT queueable (see [send]).
     */
    suspend fun transmitPacket(json: String, queueable: Boolean = true, retryCounter: Int = 0) {
        withContext(Dispatchers.IO) {
            try {
                withTimeout(10.seconds) {
                    val finalOut = json + "\r\n"
                    if (BuildConfig.DEBUG_SYNCPLAY_PROTOCOL) loggy("Client>>> $finalOut")
                    writeActualString(finalOut)
                }
            } catch (e: Exception) {
                loggy(e.stackTraceToString())
                if (retryCounter >= 3) {
                    loggy("SOCKET INVALID")
                    if (queueable) {
                        viewmodel.session.queueOutbound(json)
                    }
                    onError()
                } else {
                    transmitPacket(json, queueable, retryCounter = retryCounter + 1)
                }
            }
        }
    }

    companion object {
        /**
         * Floor for the reconnect delay. The RECONNECTION_INTERVAL preference allows 0,
         * which would otherwise produce a `delay(0)` tight loop on every retry.
         */
        val MIN_RECONNECT_INTERVAL = 1.seconds
    }
}
