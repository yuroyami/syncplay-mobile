package app.protocol.network

import kotlinx.coroutines.TimeoutCancellationException

/** Why a connection failed, in the few classes that a person can act on. */
enum class ConnectionFailure {
    /** The server name did not resolve: a typo, or a network without working name lookup. */
    NameNotFound,

    /** The host answered and refused: the server is not running, or the port is wrong. */
    Refused,

    /** Nothing answered in time: the host is down, or something in between drops the packets. */
    TimedOut,

    /** The connection opened, and the secure connection on top of it failed. */
    Encryption,
}

/**
 * The class of a failed dial, or null when none fits. Like [isNameResolutionFailure], it reads
 * class names and messages along the cause chain, because each platform and transport throws its
 * own type.
 */
internal fun classifyDialFailure(failure: Throwable): ConnectionFailure? {
    if (failure is TimeoutCancellationException) return ConnectionFailure.TimedOut
    if (isNameResolutionFailure(failure)) return ConnectionFailure.NameNotFound
    var cause: Throwable? = failure
    var depth = 0
    while (cause != null && depth < 8) {
        val text = cause::class.simpleName.orEmpty() + " " + cause.message.orEmpty()
        if (REFUSED_MARKERS.any { text.contains(it, ignoreCase = true) }) return ConnectionFailure.Refused
        if (TIMEOUT_MARKERS.any { text.contains(it, ignoreCase = true) }) return ConnectionFailure.TimedOut
        cause = cause.cause
        depth++
    }
    return null
}

/** The JVM's ConnectException says "Connection refused", and so does POSIX (ECONNREFUSED). */
private val REFUSED_MARKERS = listOf("refused", "ECONNREFUSED")

/** The JVM, POSIX, Netty and SwiftNIO words for a connect that ran out of time. */
private val TIMEOUT_MARKERS = listOf("timed out", "timeout", "ETIMEDOUT")
