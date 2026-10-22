package app.protocol.network

import android.net.TrafficStats
import app.utils.SecurityProvider

/** Satisfies Android's StrictMode untagged-socket policy. */
actual fun tagSocketThread() {
    TrafficStats.setThreadStatsTag(0xF00DFAF)
}

/**
 * Waits for the Conscrypt install, which adds TLS 1.3 and runs off the main thread at startup.
 * A TLS handshake always comes long enough after startup to wait here.
 */
actual suspend fun awaitTlsProviderReady() = SecurityProvider.awaitInstalled()
