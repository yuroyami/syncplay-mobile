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
 * Wire messages exchanged over the Syncplay TCP protocol — one sealed hierarchy used by
 * both directions.
 *
 * Five variants ([Hello], [State], [Set], [TLS], [Error]) are wire-symmetric: identical
 * JSON shape regardless of who built it. The remaining two top-level keys are split into
 * directional variants because their payload differs by direction:
 *
 *  - `Chat` — client sends a bare string ([ChatRequest]); server broadcasts an object
 *    ([ChatBroadcast]).
 *  - `List` — client sends an empty/null body ([ListRequest]); server replies with the
 *    populated room/user map ([ListResponse]).
 *
 * Decoding goes through [WireMessageDeserializer], which inspects both the top-level key
 * and the payload shape to pick the right variant — same code path on both sides.
 * Encoding is just `syncplayJson.encodeToString(message)` on a typed instance.
 *
 * [dispatch] hands the parsed message to a [WireMessageHandler]. The client's
 * [app.room.RoomServerMessageHandler] and the server's [app.server.ClientConnection] are
 * the two implementations — each overrides only the variants its side actually receives.
 */
@Serializable
sealed interface WireMessage {

    /** Visitor dispatch — invokes the matching `on…` method on [handler]. */
    suspend fun dispatch(handler: WireMessageHandler)

    /**
     * Encodes this message to its wire JSON form.
     *
     * Implemented per subclass on purpose: `syncplayJson.encodeToString(this)` from inside
     * a subclass binds the reified type parameter to the concrete subclass, which uses
     * the subclass's own serializer. Encoding via the interface type would otherwise go
     * through Kotlinx Serialization's polymorphic serializer and inject a `"type"` class
     * discriminator that the Syncplay protocol does not allow. Routing through this
     * method makes the trap structurally impossible — callers can hold a `WireMessage`
     * reference and still get the right wire format.
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
     * Client→server `{"List": null}` request — body is meaningless, server only cares
     * about the key. The default must be [JsonNull] (not Kotlin `null`) so the field is
     * actually emitted under `explicitNulls = false`; otherwise the message would
     * collapse to `{}` and the server wouldn't recognize it as a list request.
     */
    @Serializable
    data class ListRequest(@SerialName("List") val placeholder: JsonElement = JsonNull) : WireMessage {
        override suspend fun dispatch(handler: WireMessageHandler) = handler.onListRequest(this)
        override fun toJson(): String = syncplayJson.encodeToString(this)
    }

    /** Server→client full room/user listing: `{"List": {"<room>": {"<user>": ListUserData}}}`. */
    @Serializable
    data class ListResponse(
        @SerialName("List") val rooms: Map<String, Map<String, ListUserData>>
    ) : WireMessage {
        override suspend fun dispatch(handler: WireMessageHandler) = handler.onListResponse(this)
        override fun toJson(): String = syncplayJson.encodeToString(this)
    }

    /** Client→server bare-string chat: `{"Chat": "msg"}`. */
    @Serializable
    data class ChatRequest(@SerialName("Chat") val message: String) : WireMessage {
        override suspend fun dispatch(handler: WireMessageHandler) = handler.onChatRequest(this)
        override fun toJson(): String = syncplayJson.encodeToString(this)
    }

    /** Server→client chat broadcast object: `{"Chat": {"username", "message"}}`. */
    @Serializable
    data class ChatBroadcast(@SerialName("Chat") val data: ChatData) : WireMessage {
        override suspend fun dispatch(handler: WireMessageHandler) = handler.onChatBroadcast(this)
        override fun toJson(): String = syncplayJson.encodeToString(this)
    }

    /**
     * Convenience builders for the most common shapes. Both sides use these — the
     * direction-specific helpers are noted.
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

        // -- Client→server asymmetric --
        fun listRequest() = ListRequest()
        fun chatRequest(message: String) = ChatRequest(message)

        /** STARTTLS request — `{"TLS": {"startTLS": "send"}}`. */
        fun tlsRequest() = TLS(TLSData(startTLS = "send"))

        // -- Server→client asymmetric --
        fun chatBroadcast(username: String, message: String) =
            ChatBroadcast(ChatData(username = username, message = message))

        /** STARTTLS reply — `{"TLS": {"startTLS": "true"|"false"}}`. */
        fun tlsResponse(supported: Boolean) = TLS(TLSData(startTLS = supported.toString()))
    }
}
