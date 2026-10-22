package app.server

/** The iOS server engine cannot switch a plain socket to TLS, so it always answers no. */
actual val serverTlsSupported: Boolean = false

actual fun hostTlsFingerprint(): String? = null
