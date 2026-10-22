package app.protocol.wire

import kotlinx.serialization.Serializable

/**
 * STARTTLS-style negotiation payload. The wire value of [startTLS] is always a string, as in
 * the original protocol:
 * - Client to server: `"send"` (request to start TLS).
 * - Server to client: `"true"` or `"false"` (whether the server accepts).
 */
@Serializable
data class TLSData(
    val startTLS: String? = null
)
