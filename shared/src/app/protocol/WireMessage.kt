package app.protocol

import app.protocol.wire.ChatData
import app.protocol.wire.ControllerAuthData
import app.protocol.wire.ErrorData
import app.protocol.wire.FileData
import app.protocol.wire.HelloData
import app.protocol.wire.ListUserData
import app.protocol.wire.NewControlledRoom
import app.protocol.wire.PlaylistChangeData
import app.protocol.wire.PlaylistIndexData
import app.protocol.wire.ReadyData
import app.protocol.wire.Room
import app.protocol.wire.SetData
import app.protocol.wire.StateData
import app.protocol.wire.TLSData
import app.protocol.wire.UserSetData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * Wire messages exchanged over the Syncplay protocol: one sealed hierarchy used by both
 * directions.
 *
 * Five variants ([Hello], [State], [Set], [TLS], [Error]) are wire-symmetric: the JSON shape
 * is the same whichever side built it. The other two top-level keys are split into
 * directional variants, because their payload differs by direction:
 *
 *  - `Chat`: the client sends a bare string ([ChatRequest]); the server broadcasts an object
 *    ([ChatBroadcast]).
 *  - `List`: the client sends an empty or null body ([ListRequest]); the server replies with
 *    the populated room and user map ([ListResponse]).
 *
 * Decoding goes through [WireMessageDeserializer], which reads both the top-level key and the
 * payload shape to pick the right variant. Both sides use the same code path. Encoding goes
 * through [toJson], which always uses the concrete subclass's serializer.
 *
 * [dispatch] hands the parsed message to a [WireMessageHandler]. The client's
 * [app.room.RoomServerMessageHandler] and the server's [app.server.ClientConnection] are the
 * two implementations, and each overrides only the variants that its side receives.
 */
@Serializable
sealed interface WireMessage {

    /** Visitor dispatch: calls the matching `on…` method on [handler]. */
    suspend fun dispatch(handler: WireMessageHandler)

    /**
     * Encodes this message to its wire JSON form.
     *
     * Implemented per subclass on purpose: `syncplayJson.encodeToString(this)` inside a
     * subclass binds the reified type parameter to the concrete subclass, so the subclass's
     * own serializer is used. Encoding through the interface type would go through Kotlinx
     * Serialization's polymorphic serializer and add a `"type"` class discriminator, which the
     * Syncplay protocol does not allow. Routing through this method rules that out: callers can
     * hold a `WireMessage` reference and still get the right wire format.
     */
    fun toJson(): String

    @Serializable
    data class Hello(@SerialName("Hello") val data: HelloData) : WireMessage {
        override suspend fun dispatch(handler: WireMessageHandler) = handler.onHello(this)
        override fun toJson(): String = syncplayJson.encodeToString(this)
    }

    @Serializable
    data class State(@SerialName("State") val data: StateData) : WireMessage {
        override suspend fun dispatch(handler: WireMessageHandler) = handler.onState(this)
        override fun toJson(): String = syncplayJson.encodeToString(this)
    }

    @Serializable
    data class Set(@SerialName("Set") val data: SetData) : WireMessage {
        override suspend fun dispatch(handler: WireMessageHandler) = handler.onSet(this)
        override fun toJson(): String = syncplayJson.encodeToString(this)
    }

    @Serializable
    data class TLS(@SerialName("TLS") val data: TLSData) : WireMessage {
        override suspend fun dispatch(handler: WireMessageHandler) = handler.onTLS(this)
        override fun toJson(): String = syncplayJson.encodeToString(this)
    }

    @Serializable
    data class Error(@SerialName("Error") val data: ErrorData) : WireMessage {
        override suspend fun dispatch(handler: WireMessageHandler) = handler.onError(this)
        override fun toJson(): String = syncplayJson.encodeToString(this)
    }

    /**
     * Client-to-server `{"List": null}` request. The body means nothing; the server only reads
     * the key. The default must be [JsonNull] (not Kotlin `null`), so the field is still
     * written under `explicitNulls = false`. Otherwise the message would shrink to `{}` and the
     * server would not recognize it as a list request.
     */
    @Serializable
    data class ListRequest(@SerialName("List") val placeholder: JsonElement = JsonNull) : WireMessage {
        override suspend fun dispatch(handler: WireMessageHandler) = handler.onListRequest(this)
        override fun toJson(): String = syncplayJson.encodeToString(this)
    }

    /** Server-to-client room and user listing: `{"List": {"<room>": {"<user>": ListUserData}}}`. */
    @Serializable
    data class ListResponse(
        @SerialName("List") val rooms: Map<String, Map<String, ListUserData>>
    ) : WireMessage {
        override suspend fun dispatch(handler: WireMessageHandler) = handler.onListResponse(this)
        override fun toJson(): String = syncplayJson.encodeToString(this)
    }

    /** Client-to-server bare-string chat: `{"Chat": "msg"}`. */
    @Serializable
    data class ChatRequest(@SerialName("Chat") val message: String) : WireMessage {
        override suspend fun dispatch(handler: WireMessageHandler) = handler.onChatRequest(this)
        override fun toJson(): String = syncplayJson.encodeToString(this)
    }

    /** Server-to-client chat broadcast object: `{"Chat": {"username", "message"}}`. */
    @Serializable
    data class ChatBroadcast(@SerialName("Chat") val data: ChatData) : WireMessage {
        override suspend fun dispatch(handler: WireMessageHandler) = handler.onChatBroadcast(this)
        override fun toJson(): String = syncplayJson.encodeToString(this)
    }

    /**
     * Convenience builders for the most common shapes. Both sides use them; the
     * direction-specific helpers are marked.
     */
    companion object {
        // -- Symmetric Set sub-command shortcuts --
        fun roomChange(roomName: String) = Set(SetData(room = Room(roomName)))
        fun file(file: FileData) = Set(SetData(file = file))
        fun readiness(
            isReady: Boolean,
            manuallyInitiated: Boolean,
            username: String? = null,
            setBy: String? = null
        ) = Set(
            SetData(
                ready = ReadyData(
                    username = username,
                    isReady = isReady,
                    manuallyInitiated = manuallyInitiated,
                    setBy = setBy
                )
            )
        )

        fun playlistChange(files: kotlin.collections.List<String>, user: String? = null) =
            Set(SetData(playlistChange = PlaylistChangeData(user = user, files = files)))

        fun playlistIndex(index: Int, user: String? = null) =
            Set(SetData(playlistIndex = PlaylistIndexData(user = user, index = index)))

        fun controllerAuth(
            room: String? = null,
            password: String? = null,
            user: String? = null,
            success: Boolean = false
        ) = Set(
            SetData(
                controllerAuth = ControllerAuthData(
                    user = user,
                    room = room,
                    password = password,
                    success = success
                )
            )
        )

        fun newControlledRoom(roomName: String, password: String) =
            Set(SetData(newControlledRoom = NewControlledRoom(password = password, roomName = roomName)))

        fun userBroadcast(map: Map<String, UserSetData>) = Set(SetData(user = map))
        fun error(message: String?) = Error(ErrorData(message = message))

        // -- Client-to-server asymmetric --
        fun listRequest() = ListRequest()
        fun chatRequest(message: String) = ChatRequest(message)

        /** STARTTLS request: `{"TLS": {"startTLS": "send"}}`. */
        fun tlsRequest() = TLS(TLSData(startTLS = "send"))

        // -- Server-to-client asymmetric --
        fun chatBroadcast(username: String, message: String) =
            ChatBroadcast(ChatData(username = username, message = message))

        /** STARTTLS reply: `{"TLS": {"startTLS": "true"|"false"}}`. */
        fun tlsResponse(supported: Boolean) = TLS(TLSData(startTLS = supported.toString()))
    }
}
