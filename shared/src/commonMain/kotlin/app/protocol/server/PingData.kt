package app.protocol.server

import kotlinx.serialization.Serializable

/**
 * Round-trip timing data carried in a [State] message. Used in both directions:
 * - Server→client: echoes client's timestamp via [latencyCalculation], reports [serverRtt].
 * - Client→server: echoes server's timestamp via [latencyCalculation], reports [clientRtt].
 *
 * All fields are nullable to support both shapes with a single data class.
 */
@Serializable
data class PingData(
    val latencyCalculation: Double? = null,
    val clientLatencyCalculation: Double? = null,
    val clientRtt: Double? = null,
    val serverRtt: Double? = null
)
