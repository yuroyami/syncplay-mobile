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
 * The Application class. It runs the one-time process setup: StrictMode in debug builds, the
 * background Conscrypt install (TLS 1.3), DataStore and its first read, and the global context
 * provider.
 */
class SynkplayApp: Application() {

    override fun onCreate() {
        super.onCreate()

        // Main-thread disk and network reads are a common bug here, and StrictMode logs them.
        if (KiteBuildConfig.IS_DEBUG) enableStrictMode()

        /* Conscrypt adds TLS 1.3. Building it loads a native library, so it installs off the main
         * thread. The TLS upgrade is the only caller that waits for it. */
        SecurityProvider.installInBackground()

        datastore = dataStore(applicationContext, Preferences.SYNKPLAY_PREFS)
        // Read the preferences on a background thread. The activity's splash screen waits for
        // them, so the first frame sees real values and the main thread does no disk reads.
        warmPreferences()

        contextObtainer = ::returnAppContext
    }

    private fun returnAppContext(): Context {
        return applicationContext
    }

    /**
     * Debug only: logs main-thread disk and network access, and leaked resources.
     *
     * It only logs, on purpose. penaltyDeath would turn any third-party leak into a crash on
     * every developer's machine, and penaltyFlashScreen makes the app too hard to look at.
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
