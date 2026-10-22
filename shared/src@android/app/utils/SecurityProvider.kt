package app.utils

import kotlinx.coroutines.CompletableDeferred
import org.conscrypt.Conscrypt
import java.security.Security
import kotlin.concurrent.thread

/**
 * Installs Conscrypt, which gives the app TLS 1.3 on every supported Android version.
 *
 * Building the provider loads a native library, which would delay the first frame on the main
 * thread. So the install runs on its own thread. Only the TLS upgrade waits for it, and that
 * upgrade happens much later and off the main thread.
 */
object SecurityProvider {

    private val installed = CompletableDeferred<Unit>()

    /** Called once from Application.onCreate. A call after the install finished does nothing. */
    fun installInBackground() {
        if (installed.isCompleted) return
        thread(name = "conscrypt-install", isDaemon = true) {
            runCatching { Security.insertProviderAt(Conscrypt.newProvider(), 1) }
                .onFailure { loggy("Conscrypt install failed, falling back to the platform provider: ${it.message}") }
            installed.complete(Unit)
        }
    }

    /** Suspends until the provider is in place (or its install has failed and been logged). */
    suspend fun awaitInstalled() = installed.await()
}
