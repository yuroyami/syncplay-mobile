package app.protocol.network

/**
 * The two things Netty's transport needs that differ between Android and desktop.
 *
 * Everything else about the Netty client is identical on both, which is why the manager itself
 * now lives in `jvmShared` instead of being maintained twice.
 */

/**
 * Tags the socket thread for network accounting. Android's StrictMode requires an untagged
 * socket to be tagged before use; on desktop there is nothing to tag.
 */
expect fun tagSocketThread()

/**
 * Waits for the platform's TLS provider to be ready.
 *
 * Android installs Conscrypt off the main thread at startup, and the handshake is the one caller
 * that needs it in place. Desktop uses the JDK's own provider, which is always there.
 */
expect suspend fun awaitTlsProviderReady()
