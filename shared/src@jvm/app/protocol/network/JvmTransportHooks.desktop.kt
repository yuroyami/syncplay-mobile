package app.protocol.network

/** Desktop has no socket tagging policy. */
actual fun tagSocketThread() = Unit

/** The JDK's own TLS provider is always there, so there is nothing to wait for. */
actual suspend fun awaitTlsProviderReady() = Unit
