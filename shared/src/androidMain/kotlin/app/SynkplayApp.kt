package app

import android.app.Application
import android.content.Context
import android.os.StrictMode
import app.preferences.Preferences
import app.preferences.datastore
import app.utils.SecurityProvider
import app.utils.contextObtainer
import app.utils.dataStore

/**
 * Application entry point. Runs one-time process init: starts the Conscrypt install (TLS 1.3),
 * initializes DataStore, and registers the global context provider. Lives for the whole app
 * process.
 */
class SynkplayApp: Application() {

    override fun onCreate() {
        super.onCreate()

        /* Conscrypt gives us TLS 1.3, and building it loads a native library, so it is installed
         * off the main thread. The TLS upgrade is the only caller that waits for it. */
        SecurityProvider.installInBackground()

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
