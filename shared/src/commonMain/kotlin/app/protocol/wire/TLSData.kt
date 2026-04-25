package app.protocol.wire

import kotlinx.serialization.Serializable

/**
 * STARTTLS-style negotiation payload. The wire value of [startTLS] is always a string per
 * the original protocol:
 * - Client→server: `"send"` (request to start TLS).
 * - Server→client: `"true"` or `"false"` (whether the server accepts).
 */
@Serializable
data class TLSData(
    val startTLS: String? = null
)
