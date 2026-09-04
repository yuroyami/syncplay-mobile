package app.protocol

/**
 * Where to dial, and whose name the certificate must carry.
 *
 * These are two different things for the official server and only for it: `syncplay.pl` resolves
 * to an address the server itself answers on, but its certificate is issued to the name, not the
 * address. So the socket dials [dialHost] while TLS verifies [certificateHost] and sends it as
 * SNI. For every other server the two are the same string.
 */
data class ServerEndpoint(val dialHost: String, val certificateHost: String)

/** The official server's name, and the address it actually answers on. */
const val OFFICIAL_SERVER_NAME = "syncplay.pl"
const val OFFICIAL_SERVER_ADDRESS = "151.80.32.178"

/**
 * Turns what the user typed into an endpoint.
 *
 * Blank means the official server: the host field being empty used to leave the dial host as the
 * empty string, because "" is not "syncplay.pl", so the connection went nowhere while the
 * certificate name looked right.
 */
fun resolveServerEndpoint(typedHost: String): ServerEndpoint {
    val typed = typedHost.trim()
    if (typed.isEmpty() || typed == OFFICIAL_SERVER_NAME) {
        return ServerEndpoint(dialHost = OFFICIAL_SERVER_ADDRESS, certificateHost = OFFICIAL_SERVER_NAME)
    }
    return ServerEndpoint(dialHost = typed, certificateHost = typed)
}
