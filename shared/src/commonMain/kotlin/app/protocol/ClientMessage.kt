package app.protocol

import app.protocol.wire.ControllerAuthData
import app.protocol.wire.ErrorData
import app.protocol.wire.FileData
import app.protocol.wire.HelloData
import app.protocol.wire.PlaylistChangeData
import app.protocol.wire.PlaylistIndexData
import app.protocol.wire.ReadyData
import app.protocol.wire.Room
import app.protocol.wire.SetData
import app.protocol.wire.StateData
import app.protocol.wire.TLSData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire messages traveling from a Syncplay client TO the server.
 *
 * One sealed hierarchy used by **both** sides:
 * - The client constructs and serializes these via [syncplayJson] to send.
 * - The server deserializes these via [ClientMessageDeserializer] and dispatches to a
 *   [ClientMessageHandler].
 *
 * Where the wire shape matches the opposite-direction [ServerMessage], the `data` payload
 * is the same `app.protocol.wire.*` data class — the single source of truth lives there.
 */
@Serializable
sealed interface ClientMessage {

    /** Visitor dispatch — calls the matching `on…` method on the handler. */
    suspend fun dispatch(handler: ClientMessageHandler)

    @Serializable
    data class Hello(@SerialName("Hello") val data: HelloData) : ClientMessage {
        override suspend fun dispatch(handler: ClientMessageHandler) = handler.onHello(this)
    }

    @Serializable
    data class State(@SerialName("State") val data: StateData) : ClientMessage {
        override suspend fun dispatch(handler: ClientMessageHandler) = handler.onState(this)
    }

    @Serializable
    data class Set(@SerialName("Set") val data: SetData) : ClientMessage {
        override suspend fun dispatch(handler: ClientMessageHandler) = handler.onSet(this)
    }

    /** Body is meaningless (`null` or `[]`) — its arrival simply asks the server for a `List` reply. */
    @Serializable
    data class List(@SerialName("List") val placeholder: JsonElement? = null) : ClientMessage {
        override suspend fun dispatch(handler: ClientMessageHandler) = handler.onList(this)
    }

    /** Asymmetric: client sends a bare string `{"Chat": "msg"}`, not an object. */
    @Serializable
    data class Chat(@SerialName("Chat") val message: String) : ClientMessage {
        override suspend fun dispatch(handler: ClientMessageHandler) = handler.onChat(this)
    }

    @Serializable
    data class TLS(@SerialName("TLS") val data: TLSData) : ClientMessage {
        override suspend fun dispatch(handler: ClientMessageHandler) = handler.onTLS(this)
    }

    @Serializable
    data class Error(@SerialName("Error") val data: ErrorData) : ClientMessage {
        override suspend fun dispatch(handler: ClientMessageHandler) = handler.onError(this)
    }

    /**
     * Convenience factories for the common `Set` sub-commands the client emits, plus the
     * empty `List` request and TLS handshake. Keeps call sites readable without leaking
     * `SetData(... = ...)` boilerplate everywhere.
     */
    companion object {
        fun roomChange(roomName: String) = Set(SetData(room = Room(roomName)))
        fun file(file: FileData) = Set(SetData(file = file))
        fun readiness(isReady: Boolean, manuallyInitiated: Boolean) = Set(SetData(ready = ReadyData(isReady = isReady, manuallyInitiated = manuallyInitiated)))
        fun playlistChange(files: kotlin.collections.List<String>) = Set(SetData(playlistChange = PlaylistChangeData(files = files)))
        fun playlistIndex(index: Int) = Set(SetData(playlistIndex = PlaylistIndexData(index = index)))
        fun controllerAuth(room: String? = null, password: String) = Set(SetData(controllerAuth = ControllerAuthData(room = room, password = password)))

        /** Empty `List` request — its arrival simply asks the server for a `List` reply. */
        fun listRequest() = List()

        /** STARTTLS request — `{"TLS": {"startTLS": "send"}}`. */
        fun tlsRequest() = TLS(TLSData(startTLS = "send"))
    }
}
