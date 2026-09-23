package app.protocol.wire

import kotlinx.serialization.Serializable

/**
 * Round-trip timing data carried in a `State` message.
 *
 * [latencyCalculation] is the server's timestamp and [clientLatencyCalculation] is the
 * client's. Each side sends its own new timestamp and echoes back the other side's last one:
 * - Server to client: a new [latencyCalculation], the echoed [clientLatencyCalculation], and
 *   [serverRtt].
 * - Client to server: the echoed [latencyCalculation], a new [clientLatencyCalculation], and
 *   [clientRtt].
 *
 * All fields are nullable so the same data class fits both shapes.
 */
@Serializable
data class PingData(
    val latencyCalculation: Double? = null,
    val clientLatencyCalculation: Double? = null,
    val clientRtt: Double? = null,
    val serverRtt: Double? = null
)
