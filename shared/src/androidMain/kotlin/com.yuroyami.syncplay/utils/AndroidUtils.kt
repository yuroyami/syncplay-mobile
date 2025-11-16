package com.yuroyami.syncplay.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.yuroyami.syncplay.managers.datastore.createDataStore
import java.util.Locale

/**
 * Global function reference for obtaining the application Context.
 *
 * Initialized in [com.yuroyami.syncplay.SyncplayApp.onCreate] to provide
 * access to the application context from anywhere in the codebase without
 * passing Context through multiple layers.
 *
 * **Usage:**
 * ```kotlin
 * val context = contextObtainer()
 * ```
 */
lateinit var contextObtainer: () -> Context

/**
 * Creates a DataStore instance for persistent key-value storage on Android.
 *
 * Wraps the common-code createDataStore function with Android-specific file path
 * resolution. The DataStore file is created in the app's internal files directory.
 *
 * DataStore is used throughout the app for storing user preferences, settings,
 * and configuration values that persist across app sessions.
 *
 * @param context Android context for accessing the app's file directory
 * @param fileName Name of the DataStore file (e.g., "syncplay_prefs")
 * @return DataStore<Preferences> instance for reading/writing key-value data
 */
fun dataStore(context: Context, fileName: String): DataStore<Preferences> =
    createDataStore(
        producePath = { context.filesDir.resolve(fileName).absolutePath }
    )

/**
 * Changes the app's language/locale at runtime.
 *
 * Creates a new configuration context with the specified locale. This affects
 * how resources (strings, layouts) are resolved for this context.
 *
 * Note: This uses the deprecated Configuration constructor and setLocale, but is
 * still the recommended approach for runtime locale changes until a better API
 * is available.
 *
 * @param lang ISO 639-1 language code (e.g., "en", "fr", "ar")
 * @return A new Context with the specified locale applied
 */
@Suppress("DEPRECATION")
fun Context.changeLanguage(lang: String): Context {
    val locale = Locale(lang)
    Locale.setDefault(locale)
    val config = Configuration()
    config.setLocale(locale)
    return createConfigurationContext(config)
}

/**
 * Binds a lifecycle watchdog observer to the Activity.
 *
 * Currently disabled (TODO) - was used to track Activity lifecycle events
 * and notify the LifecycleManager when the Activity transitions between states.
 *
 * When implemented, this would:
 * - Observe lifecycle events (CREATE, START, RESUME, PAUSE, STOP)
 * - Forward events to the lifecycle watchdog for handling background state
 * - Enable features like pausing playback when app goes to background
 *
 * TODO: Re-implement lifecycle observation for background state management
 */
fun ComponentActivity.bindWatchdog() {
    /* TODO
    val watchdog = viewmodel!!.lifecycleWatchdog
    val lifecycleObserver = object: LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_CREATE -> watchdog.onCreate()
                Lifecycle.Event.ON_START -> watchdog.onStart()
                Lifecycle.Event.ON_RESUME -> watchdog.onResume()
                Lifecycle.Event.ON_PAUSE -> watchdog.onPause()
                Lifecycle.Event.ON_STOP -> watchdog.onStop()
                else -> {}
            }
        }
    }

    lifecycle.addObserver(lifecycleObserver)

     */
}

/**
 * Controls display cutout mode for devices with notches or camera cutouts.
 *
 * On Android P (API 28) and above, allows content to extend into the cutout area
 * (useful for fullscreen video) or restricts content to avoid the cutout.
 *
 * @param enable true to extend content into cutout (short edges), false to avoid cutout
 */
fun Activity.cutoutMode(enable: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (enable) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
        }
    }
}

/**
 * Hides system UI bars (status bar and navigation bar) for immersive fullscreen mode.
 *
 * Provides two implementation strategies:
 * - **Modern** (default): Uses WindowInsetsControllerCompat for Android 11+ compatibility
 * - **Deprecated**: Uses legacy systemUiVisibility flags for older devices
 *
 * Modern implementation allows system bars to be revealed temporarily by swiping,
 * then auto-hides them again (transient behavior).
 *
 * @param useDeprecated If true, uses deprecated systemUiVisibility API (for compatibility)
 */
@Suppress("DEPRECATION")
fun Activity.hideSystemUI(useDeprecated: Boolean = false) {
    runOnUiThread {
        if (!useDeprecated) {
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            val decorView: View = window.decorView
            val uiOptions = decorView.systemUiVisibility
            var newUiOptions = uiOptions
            newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_LOW_PROFILE
            newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_FULLSCREEN
            newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_IMMERSIVE
            newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            decorView.systemUiVisibility = newUiOptions
            View.OnSystemUiVisibilityChangeListener { newmode ->
                if (newmode != newUiOptions) {
                    hideSystemUI(false)
                }
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

        }
    }
}

/**
 * Shows system UI bars (status bar and navigation bar) after being hidden.
 *
 * Restores normal system bar visibility with default behavior. Provides two
 * implementation strategies matching [hideSystemUI].
 *
 * @param useDeprecated If true, uses deprecated systemUiVisibility API (for compatibility)
 */
@Suppress("DEPRECATION")
fun Activity.showSystemUI(useDeprecated: Boolean = false) {
    runOnUiThread {
        if (!useDeprecated) {
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        } else {
            val decorView: View = window.decorView
            decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_VISIBLE

        }
    }
}