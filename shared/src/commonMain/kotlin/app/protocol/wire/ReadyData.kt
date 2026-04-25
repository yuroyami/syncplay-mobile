package app.protocol.wire

import kotlinx.serialization.Serializable

/**
 * Readiness payload, used in both directions:
 * - Server→client: includes [username] (and optional [setBy] when a controller forced it).
 * - Client→server: typically just [isReady] + [manuallyInitiated]; controllers may also
 *   send a target [username].
 */
@Serializable
data class ReadyData(
    val username: String? = null,
    val isReady: Boolean? = null,
    val manuallyInitiated: Boolean? = null,
    val setBy: String? = null
)
