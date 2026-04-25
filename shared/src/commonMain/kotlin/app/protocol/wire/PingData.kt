package app.protocol.wire

import kotlinx.serialization.Serializable

/**
 * Round-trip timing data carried in a `State` message.
 *
 * Both directions populate [latencyCalculation] (echoes the peer's last timestamp), but
 * the secondary fields differ:
 * - Server→client: also sets [serverRtt] and [clientLatencyCalculation].
 * - Client→server: also sets [clientLatencyCalculation] and [clientRtt].
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
