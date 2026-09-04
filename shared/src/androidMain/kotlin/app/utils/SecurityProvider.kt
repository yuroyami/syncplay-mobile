package app.utils

import kotlinx.coroutines.CompletableDeferred
import org.conscrypt.Conscrypt
import java.security.Security
import kotlin.concurrent.thread

/**
 * Installs Conscrypt, which is what gives the app TLS 1.3 on every supported Android version.
 *
 * Building the provider loads a native library, which took the main thread for that long before
 * the first frame. It is done on its own thread instead, and the only thing that has to wait for
 * it is the TLS upgrade, which happens much later and off the main thread anyway.
 */
object SecurityProvider {

    private val installed = CompletableDeferred<Unit>()

    /** Called once from Application.onCreate. Safe to call again; later calls do nothing. */
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
