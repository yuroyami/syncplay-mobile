package app.protocol.network

/**
 * Whether a failed dial was the resolver's fault rather than the host's.
 *
 * This is the one question the official server's address fallback turns on. The fallback exists
 * for a broken or blocked resolver and nothing else: a host that resolves fine and then refuses
 * or ignores the connection behaves the same on its other address, so dialling that address only
 * spends a second connect timeout before reporting the failure the caller already had. That
 * doubled the time to the retry that usually works.
 *
 * The exception type differs by platform and by transport, so this reads class names and messages
 * along the cause chain rather than catching one class. A false negative only means the fallback
 * is not tried, which is the safe direction to be wrong in.
 */
internal fun isNameResolutionFailure(failure: Throwable): Boolean {
    var cause: Throwable? = failure
    var depth = 0
    while (cause != null && depth < MAX_CAUSE_DEPTH) {
        val name = cause::class.simpleName.orEmpty()
        val text = cause.message.orEmpty()
        if (RESOLUTION_MARKERS.any { name.contains(it, ignoreCase = true) || text.contains(it, ignoreCase = true) }) {
            return true
        }
        cause = cause.cause
        depth++
    }
    return false
}

/** A cause chain deeper than this is a loop or a wrapper storm; either way, stop walking. */
private const val MAX_CAUSE_DEPTH = 8

/**
 * What the platforms call a name that would not resolve. The first two are class names (the JVM's
 * `UnknownHostException`, NIO's `UnresolvedAddressException`); the rest are the message texts that
 * macOS, Linux, Android and Netty's own resolver produce.
 */
private val RESOLUTION_MARKERS = listOf(
    "UnknownHost",
    "UnresolvedAddress",
    "nodename nor servname",
    "Name or service not known",
    "Unable to resolve host",
    "failed to resolve",
)
