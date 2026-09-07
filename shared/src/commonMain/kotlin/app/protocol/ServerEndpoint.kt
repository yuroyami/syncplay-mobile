package app.protocol

/**
 * Where to dial, whose name the certificate must carry, and what to try if the dial fails.
 *
 * The first two are different things for the official server and only for it: its certificate is
 * issued to `syncplay.pl`, not to the address behind it. So TLS verifies [certificateHost] and
 * sends it as SNI whatever [dialHost] turns out to be. For every other server the two are the
 * same string and there is nothing to fall back to.
 */
data class ServerEndpoint(
    val dialHost: String,
    val certificateHost: String,
    val fallbackDialHost: String? = null,
)

/** The official server's name, and the address it answered on when this was last checked. */
const val OFFICIAL_SERVER_NAME = "syncplay.pl"
const val OFFICIAL_SERVER_ADDRESS = "151.80.32.178"

/**
 * Turns what the user typed into an endpoint.
 *
 * Blank means the official server: the host field being empty used to leave the dial host as the
 * empty string, because "" is not "syncplay.pl", so the connection went nowhere while the
 * certificate name looked right.
 *
 * The official server is dialled by name, with the pinned address kept only as a fallback. It
 * used to be the other way round, and an address literal is not reachable everywhere: an
 * IPv6-only mobile network reaches IPv4 hosts by synthesising an address from the DNS answer, so
 * a client that never asks DNS has nothing to connect to. iOS carriers do exactly that, which
 * made the official server unreachable there with no way to tell why. Asking by name also means a
 * server that moves keeps working without an app release.
 */
fun resolveServerEndpoint(typedHost: String): ServerEndpoint {
    val typed = typedHost.trim()
    if (typed.isEmpty() || typed == OFFICIAL_SERVER_NAME) {
        return ServerEndpoint(
            dialHost = OFFICIAL_SERVER_NAME,
            certificateHost = OFFICIAL_SERVER_NAME,
            fallbackDialHost = OFFICIAL_SERVER_ADDRESS,
        )
    }
    return ServerEndpoint(dialHost = typed, certificateHost = typed)
}
