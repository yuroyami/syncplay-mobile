package app.server.protocol.incoming

import app.protocol.server.Hello
import app.server.ClientConnection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Client-originated `Hello` (handshake). Reuses [Hello.HelloData] from the server-message
 * hierarchy — same wire shape, different direction.
 *
 * Validates required fields, checks the server password, and lets [ClientConnection]
 * register the new watcher.
 */
@Serializable
data class IncomingHello(
    @SerialName("Hello") val hello: Hello.HelloData
) : IncomingMessage {

    override suspend fun handle(connection: ClientConnection) {
        val username = hello.username?.trim()
        val roomName = hello.room?.name?.trim()
        val version = hello.realversion ?: hello.version

        if (username.isNullOrEmpty() || roomName.isNullOrEmpty() || version == null) {
            connection.dropWithError("Hello command does not have enough parameters")
            return
        }

        connection.acceptHello(
            username = username,
            roomName = roomName,
            clientVersion = version,
            clientPassword = hello.password,
            features = hello.features
        )
    }
}
