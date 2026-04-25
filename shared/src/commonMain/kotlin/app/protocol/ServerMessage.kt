package app.protocol

import app.protocol.wire.ChatData
import app.protocol.wire.ErrorData
import app.protocol.wire.HelloData
import app.protocol.wire.ListUserData
import app.protocol.wire.SetData
import app.protocol.wire.StateData
import app.protocol.wire.TLSData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire messages travelling from a Syncplay server TO clients.
 *
 * One sealed hierarchy used by **both** sides:
 * - The server constructs and serializes these via [syncplayJson] to send.
 * - The client deserializes these via [ServerMessageDeserializer] and dispatches to a
 *   [ServerMessageHandler].
 *
 * Where the wire shape matches the opposite-direction [ClientMessage], the `data` payload
 * is the same `app.protocol.wire.*` data class.
 */
@Serializable
sealed interface ServerMessage {

    /** Visitor dispatch — calls the matching `on…` method on the handler. */
    suspend fun dispatch(handler: ServerMessageHandler)

    @Serializable
    data class Hello(@SerialName("Hello") val data: HelloData) : ServerMessage {
        override suspend fun dispatch(handler: ServerMessageHandler) = handler.onHello(this)
    }

    @Serializable
    data class State(@SerialName("State") val data: StateData) : ServerMessage {
        override suspend fun dispatch(handler: ServerMessageHandler) = handler.onState(this)
    }

    @Serializable
    data class Set(@SerialName("Set") val data: SetData) : ServerMessage {
        override suspend fun dispatch(handler: ServerMessageHandler) = handler.onSet(this)
    }

    /** Server's full room/user listing: `{"List": {"<roomName>": {"<userName>": ListUserData}}}`. */
    @Serializable
    data class List(
        @SerialName("List") val rooms: Map<String, Map<String, ListUserData>>
    ) : ServerMessage {
        override suspend fun dispatch(handler: ServerMessageHandler) = handler.onList(this)
    }

    /** Server's chat broadcast: object form `{"Chat": {"username", "message"}}`. */
    @Serializable
    data class Chat(@SerialName("Chat") val data: ChatData) : ServerMessage {
        override suspend fun dispatch(handler: ServerMessageHandler) = handler.onChat(this)
    }

    @Serializable
    data class TLS(@SerialName("TLS") val data: TLSData) : ServerMessage {
        override suspend fun dispatch(handler: ServerMessageHandler) = handler.onTLS(this)
    }

    @Serializable
    data class Error(@SerialName("Error") val data: ErrorData) : ServerMessage {
        override suspend fun dispatch(handler: ServerMessageHandler) = handler.onError(this)
    }
}
