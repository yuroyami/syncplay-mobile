package app.protocol.network

import SyncplayMobile.shared.BuildConfig
import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.preferences.Preferences.RECONNECTION_INTERVAL
import app.preferences.value
import app.protocol.ProtocolManager.Companion.createPacketInstance
import app.protocol.event.ClientMessage
import app.protocol.syncplayJson
import app.protocol.models.ConnectionState
import app.protocol.models.TlsState
import app.room.RoomViewmodel
import app.utils.ProtocolApi
import app.utils.loggy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

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
                send<ClientMessage.TLS>()
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

    fun handlePacket(jsonString: String) {
        viewmodel.viewModelScope.launch(Dispatchers.Default) {
            if (BuildConfig.DEBUG_SYNCPLAY_PROTOCOL) loggy("**SERVER** $jsonString")

            try {
                syncplayJson.decodeFromString(
                    deserializer = ServerMessageDeserializer,
                    string = jsonString
                ).handle(
                    protocol = viewmodel.protocol,
                    viewmodel = viewmodel,
                    dispatcher = viewmodel.networkManager,
                    callback = viewmodel.callback
                )
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

    typealias SendablePacket = String

    private val sendChannel = Channel<SendablePacket>(capacity = Channel.BUFFERED)

    @ProtocolApi
    inline fun <reified T : ClientMessage> sendAsync(noinline init: suspend T.() -> Unit = {}) {
        viewmodel.viewModelScope.launch(Dispatchers.IO) { send(init) }
    }

    @ProtocolApi
    suspend inline fun <reified T : ClientMessage> send(noinline init: suspend T.() -> Unit = {}) {
        if (viewmodel.isSoloMode) return

        val packetInstance = createPacketInstance<T>(protocolManager = viewmodel.protocol)
        init(packetInstance)
        val jsonPacket = Json.encodeToString(packetInstance.build())
        transmitPacket(jsonPacket, packetClass = T::class)
    }

    /** Appends CRLF, writes to socket with 10s timeout. Retries up to 3 times before giving up. */
    suspend fun transmitPacket(json: SendablePacket, packetClass: KClass<out ClientMessage>? = null, retryCounter: Int = 0) {
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
                    if (packetClass != ClientMessage.Hello::class && packetClass != null) {
                        viewmodel.session.outboundQueue.add(json)
                    }
                    onError()
                } else {
                    transmitPacket(json, packetClass, retryCounter = retryCounter + 1)
                }
            }
        }
    }
}