package app.server.protocol.incoming

import app.server.ClientConnection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Client-originated chat — payload is a bare string (`{"Chat": "message"}`), unlike the
 * server's outgoing chat broadcast which is an object (`{"Chat": {"username", "message"}}`).
 * That asymmetry is why this is a distinct type from [app.protocol.server.Chat].
 */
@Serializable
data class IncomingChat(
    @SerialName("Chat") val message: String
) : IncomingMessage {

    override suspend fun handle(connection: ClientConnection) {
        connection.handleChatString(message)
    }
}
