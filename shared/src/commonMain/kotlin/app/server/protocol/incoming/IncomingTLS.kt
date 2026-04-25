package app.server.protocol.incoming

import app.protocol.server.TLS
import app.server.ClientConnection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Client-originated TLS upgrade request. The client sends `{"TLS": {"startTLS": "send"}}`;
 * the server replies with `{"TLS": {"startTLS": "true"|"false"}}`.
 *
 * Reuses [TLS.TLSData] since the wire shape matches — only the `startTLS` value differs
 * by direction (request vs. answer), and that is just a string in both.
 */
@Serializable
data class IncomingTLS(
    @SerialName("TLS") val tls: TLS.TLSData
) : IncomingMessage {

    override suspend fun handle(connection: ClientConnection) {
        connection.handleTlsRequest(tls)
    }
}
