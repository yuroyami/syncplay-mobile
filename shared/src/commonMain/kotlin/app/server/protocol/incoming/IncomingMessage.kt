package app.server.protocol.incoming

import app.server.ClientConnection
import kotlinx.serialization.Serializable

/**
 * Sealed hierarchy of wire messages received on the server side (client→server).
 *
 * Mirrors the client-side [app.protocol.server.ServerMessage] pipeline: each variant is a
 * `@Serializable` data class, routed by [IncomingMessageDeserializer] based on the top-level
 * JSON key, then [handle]d with server-side logic.
 *
 * Shared sub-data classes ([app.protocol.server.Hello.HelloData], [app.protocol.server.State.StateData],
 * [app.protocol.server.Set.SetData], [app.protocol.server.FileData], etc.) are reused — only the
 * wire envelope differs because direction-specific shapes diverge for Chat/TLS/List.
 */
@Serializable
sealed interface IncomingMessage {

    suspend fun handle(connection: ClientConnection)
}
