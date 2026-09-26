package app.server

/** The web build has no server. */
actual val serverTlsSupported: Boolean = false

actual fun hostTlsFingerprint(): String? = null
