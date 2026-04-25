package app.server.protocol.incoming

import app.protocol.server.Error
import app.server.ClientConnection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Client-originated `Error` (rare — typically clients don't send this, but the protocol
 * permits it). Same shape as [app.protocol.server.Error] — reuses [Error.ErrorData].
 */
@Serializable
data class IncomingError(
    @SerialName("Error") val error: Error.ErrorData
) : IncomingMessage {

    override suspend fun handle(connection: ClientConnection) {
        connection.handleClientError(error.message)
    }
}
