package app

import android.app.Application
import android.content.Context
import android.os.StrictMode
import app.preferences.Preferences
import app.preferences.datastore
import app.utils.contextObtainer
import app.utils.dataStore
import org.conscrypt.Conscrypt
import java.security.Security

/**
 * Application entry point. Runs one-time process init: installs the Conscrypt security
 * provider (TLS 1.3 on API 24+), initializes DataStore, and registers the global context
 * provider. Lives for the whole app process.
 */
class SynkplayApp: Application() {

    override fun onCreate() {
        super.onCreate()

        /* Install Conscrypt as the top-priority security provider for TLS 1.3 support. */
        runCatching {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }

        datastore = dataStore(applicationContext, Preferences.SYNKPLAY_PREFS)

        contextObtainer = ::returnAppContext
    }

    private fun returnAppContext(): Context {
        return applicationContext
    }

    /**
     * Debug-only: StrictMode to flag main-thread disk/network I/O and VM resource leaks.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .penaltyFlashScreen()
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .penaltyDeath()
                .build()
        )
    }
}