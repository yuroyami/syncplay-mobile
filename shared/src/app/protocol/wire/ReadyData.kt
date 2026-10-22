package app.protocol.wire

import kotlinx.serialization.Serializable

/**
 * Readiness payload, used in both directions:
 * - Server to client: includes [username] (and [setBy] when a controller set it).
 * - Client to server: usually just [isReady] and [manuallyInitiated]; a controller may also
 *   send a target [username].
 */
@Serializable
data class ReadyData(
    val username: String? = null,
    val isReady: Boolean? = null,
    val manuallyInitiated: Boolean? = null,
    val setBy: String? = null
)
