package com.yuroyami.syncplay

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StrictMode
import androidx.media3.session.MediaSession
import com.yuroyami.syncplay.managers.preferences.Preferences
import com.yuroyami.syncplay.managers.preferences.datastore
import com.yuroyami.syncplay.utils.contextObtainer
import com.yuroyami.syncplay.utils.dataStore
import org.conscrypt.Conscrypt
import java.security.Security

/**
 * Main Application class for the Syncplay Android app.
 *
 * Handles application-level initialization including:
 * - TLS/SSL security provider setup (Conscrypt for modern TLS support)
 * - DataStore initialization for persistent settings storage
 * - Context provider registration for accessing app context globally
 * - Optional StrictMode configuration for debugging
 *
 * This class is instantiated once per app process and lives for the entire app lifecycle.
 */
class SyncplayApp: Application() {

    //This allows us to share the mediasession instance we get from common module's BasePlayer
    //with SyncplayMediaSessionService using reflection
    lateinit var mediaSession: MediaSession

    /***
     * Performs the one-time critical initialization:
     * 1. Installs Conscrypt security provider for TLS 1.3 support on Android 7.0+
     * 2. Initializes DataStore for settings persistence
     * 3. Registers context provider for global access
     */
    override fun onCreate() {
        super.onCreate()

        /* TLS (mainly TLSv1.3) support, via Conscrypt */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // API 24 (Nougat)
            runCatching {
                // Insert Conscrypt as the highest priority security provider
                // This enables TLS 1.3 and modern cryptographic protocols
                Security.insertProviderAt(Conscrypt.newProvider(), 1)
            }
        }

        //Initializing datastore
        datastore = dataStore(applicationContext, Preferences.SYNCPLAY_PREFS)

        // Register application context provider for global access
        contextObtainer = ::returnAppContext

        //if (BuildConfig.DEBUG) enableStrictMode()
    }

    /**
     * Provides the application context for global access.
     *
     * Used by utility functions that need context but aren't tied to a specific Activity.
     *
     * @return The application context
     */
    private fun returnAppContext(): Context {
        return applicationContext
    }

    /**
     * Enables Android StrictMode for detecting performance and correctness issues.
     *
     * **Thread Policy**: Detects blocking operations on main thread (disk I/O, network)
     * **VM Policy**: Detects memory leaks and resource issues
     *
     * Useful during development to catch common mistakes.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll() // Detect everything
                .penaltyLog() // Log violations to Logcat
                .penaltyFlashScreen() // Flash screen on violation (visual indicator)
                //.penaltyDeath() // Crash on violation (use carefully!)
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll() // Detect all VM violations
                .penaltyLog() // Log violations
                .penaltyDeath() // Crash on violation (use carefully!)
                .build()
        )
    }
}