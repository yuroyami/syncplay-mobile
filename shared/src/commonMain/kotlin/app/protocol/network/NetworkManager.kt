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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    abstract fun upgradeTls()

    private var reconnectionJob: Job? = null
    fun reconnect() {
        if (state.value == ConnectionState.DISCONNECTED) {
            if (reconnectionJob == null || reconnectionJob?.isCompleted == true) {
                reconnectionJob = viewmodel.viewModelScope.launch(Dispatchers.IO) {
                    state.value = ConnectionState.SCHEDULING_RECONNECT
                    delay(RECONNECTION_INTERVAL.value().seconds)
                    connect()
                }
            }
        }
    }

    /**
     * Decodes a raw inbound line and dispatches the typed [WireMessage] to
     * [viewmodel.serverHandler]. Same Kotlinx Serialization plumbing as the server's
     * mirror-image pipeline.
     */
    fun handlePacket(jsonString: String) {
        viewmodel.viewModelScope.launch(Dispatchers.Default) {
            if (BuildConfig.DEBUG_SYNCPLAY_PROTOCOL) loggy("**SERVER** $jsonString")

            try {
                val message = syncplayJson.decodeFromString(WireMessageDeserializer, jsonString)
                message.dispatch(viewmodel.serverHandler)
            } catch (e: SerializationException) {
                loggy("Problematic Json: $jsonString")
                loggy("Serialization error: ${e.message}")
                throw e
            }
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
        transmitPacket(message.toJson(), isHello = message is WireMessage.Hello)
    }

    /** Fire-and-forget [send] — launched on [Dispatchers.IO]. */
    fun sendAsync(message: WireMessage) {
        viewmodel.viewModelScope.launch(Dispatchers.IO) { send(message) }
    }

    /**
     * Appends CRLF, writes to socket with a 10s timeout. Retries up to 3 times before giving up.
     * On final failure non-Hello packets get queued in [Session.outboundQueue].
     */
    suspend fun transmitPacket(json: String, isHello: Boolean = false, retryCounter: Int = 0) {
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
                    if (!isHello) {
                        viewmodel.session.outboundQueue.add(json)
                    }
                    onError()
                } else {
                    transmitPacket(json, isHello, retryCounter = retryCounter + 1)
                }
            }
        }
    }
}
