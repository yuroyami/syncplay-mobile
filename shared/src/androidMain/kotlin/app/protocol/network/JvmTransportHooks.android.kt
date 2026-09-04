package app.protocol.network

import android.net.TrafficStats
import app.utils.SecurityProvider

/** Satisfies Android's StrictMode untagged-socket policy. */
actual fun tagSocketThread() {
    TrafficStats.setThreadStatsTag(0xF00DFAF)
}

/**
 * Conscrypt gives us TLS 1.3 and is installed off the main thread at startup. The handshake is
 * always far enough after startup to simply wait here.
 */
actual suspend fun awaitTlsProviderReady() = SecurityProvider.awaitInstalled()
