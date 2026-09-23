package app.protocol.network

/** What a fresh socket should do about encryption. */
enum class TlsDecision {
    /** Ask the server to upgrade before anything else is sent. */
    ASK,

    /** Talk in plain text; the user has not asked for more. */
    PLAIN,

    /** Encryption was required and this connection cannot give it. Do not open it. */
    REFUSE,
}

/**
 * Whether to ask for encryption, accept plain text, or refuse the connection outright.
 *
 * Required wins over enabled. "Require encrypted connection" means encrypted or nothing, whatever
 * the enable switch happens to say, and a transport with no TLS at all cannot satisfy it: the Ktor
 * engine has no opportunistic upgrade, so requiring encryption on it means not connecting.
 *
 * This is decided before the socket opens. If the requirement were read only when a TLS answer
 * arrives, a connection that never asks (TLS disabled, or a transport without TLS) would already
 * have sent the Hello carrying the password hash in plain text.
 */
fun decideTls(enabled: Boolean, required: Boolean, transportSupportsTls: Boolean): TlsDecision = when {
    required && !transportSupportsTls -> TlsDecision.REFUSE
    required || (enabled && transportSupportsTls) -> TlsDecision.ASK
    else -> TlsDecision.PLAIN
}
