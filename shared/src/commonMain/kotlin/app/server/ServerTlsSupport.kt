package app.server

/**
 * True where the built-in server can encrypt its connections: Android and the desktop. There the
 * server engine can switch a plain socket to TLS in the middle of a connection, as Syncplay does.
 */
expect val serverTlsSupported: Boolean

/** The fingerprint of the host's certificate, made on first use. Null where the server cannot encrypt. */
expect fun hostTlsFingerprint(): String?
