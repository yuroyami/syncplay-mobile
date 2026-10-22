package app.server

import app.server.tls.ServerTls

actual val serverTlsSupported: Boolean = true

actual fun hostTlsFingerprint(): String? = ServerTls.identity.fingerprint
