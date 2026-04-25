package app.server.protocol.incoming

import app.server.ClientConnection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Client-originated `List` request — body is meaningless (`null` or `[]`); the act of receiving
 * one tells the server to respond with the full room/user listing.
 *
 * The wire shape from clients differs from the server's response shape (which is a populated
 * map), so this is a separate type from [app.protocol.server.ListResponse].
 */
@Serializable
data class IncomingList(
    @SerialName("List") val list: JsonElement? = null
) : IncomingMessage {

    override suspend fun handle(connection: ClientConnection) {
        connection.handleListRequest()
    }
}
