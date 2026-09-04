package app

import android.app.Application
import android.content.Context
import SyncplayMobile.shared.KiteBuildConfig
import android.os.StrictMode
import app.preferences.Preferences
import app.preferences.datastore
import app.preferences.warmPreferences
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

        // Written long ago and never called. It is worth having: main-thread disk and network
        // reads are exactly the class of bug this app keeps finding.
        if (KiteBuildConfig.IS_DEBUG) enableStrictMode()

        /* Conscrypt gives us TLS 1.3, and building it loads a native library, so it is installed
         * off the main thread. The TLS upgrade is the only caller that waits for it. */
        SecurityProvider.installInBackground()

        datastore = dataStore(applicationContext, Preferences.SYNKPLAY_PREFS)
        // Read off disk on a background thread. The activity's splash waits on it, so the first
        // frame still sees real values without the main thread doing the reading.
        warmPreferences()

        contextObtainer = ::returnAppContext
    }

    private fun returnAppContext(): Context {
        return applicationContext
    }

    /**
     * Debug only: flags main-thread disk and network reads, and leaked resources.
     *
     * Logging only, deliberately. penaltyDeath would turn any third-party leak into a crash on
     * every developer's machine, and penaltyFlashScreen makes the app unusable to look at.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
    }
}
