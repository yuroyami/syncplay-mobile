package app.utils

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.SyncplayActivity
import app.preferences.createDataStore
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.toAndroidUri
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap

/**
 * Global accessor for the application Context, initialized at app startup. Safe because the
 * app runs as a single task with one visible context (the SyncplayActivity), so there is no
 * multi-process ambiguity.
 */
lateinit var contextObtainer: () -> Context

/**
 * Creates a DataStore in the app's internal files directory, resolving the path before
 * delegating to the common-code factory.
 */
fun dataStore(context: Context, fileName: String): DataStore<Preferences> =
    createDataStore(
        producePath = { context.filesDir.resolve(fileName).absolutePath }
    )

/**
 * Returns a new Context whose resources resolve against the given locale.
 *
 * @param lang ISO 639-1 language code (e.g., "en", "fr", "ar")
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
 * Forwards Activity lifecycle events (CREATE/START/RESUME/PAUSE/STOP) to the room's UI state
 * manager. Registered once in onCreate, before any room exists, so the room is looked up at
 * event time: resolving it at registration time bound nothing and left every background gate dead.
 */
fun SyncplayActivity.bindWatchdog() {
    lifecycle.addObserver(
        observer = LifecycleEventObserver { _, event ->
            val watchdog = roomViewmodel?.uiState ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_CREATE -> watchdog.onLifecycleCreate()
                Lifecycle.Event.ON_START -> watchdog.onLifecycleStart()
                Lifecycle.Event.ON_RESUME -> watchdog.onLifecycleResume()
                Lifecycle.Event.ON_PAUSE -> watchdog.onLifecyclePause()
                Lifecycle.Event.ON_STOP -> watchdog.onLifecycleStop()
                else -> {}
            }
        }
    )
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
fun ComponentActivity.hideSystemUI(useDeprecated: Boolean = false) {
    runOnUiThread {
        windowsWithHiddenBars += window
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
fun ComponentActivity.showSystemUI(useDeprecated: Boolean = false) {
    runOnUiThread {
        // Before the show call: its animation is dispatched at once and must pass the mask.
        windowsWithHiddenBars -= window
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

@Suppress("DEPRECATION")
fun ComponentActivity.applyActivityUiProperties() {
    window.attributes = window.attributes.apply {
        flags = flags and WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS.inv()
    }
    window.statusBarColor = Color.Transparent.toArgb()
    window.navigationBarColor = Color.Transparent.toArgb()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    /** Telling Android that it should keep the screen on */
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    WindowCompat.setDecorFitsSystemWindows(window, false)
}

/** The windows whose bars [hideSystemUI] has hidden. Weak keys: an activity window must not outlive its activity. */
private val windowsWithHiddenBars: MutableSet<Window> = Collections.newSetFromMap(WeakHashMap())

/**
 * Reports no system bars to the view tree while [hideSystemUI] is in effect in a fullscreen window.
 *
 * Android tells the window about hidden bars anyway. When the notification shade opens it takes
 * the bars over and the window is told they are visible; when it closes, the window gets them back
 * and runs the hide animation itself. Below Android 16 the fade-out of the transient bars after a
 * top-edge swipe arrives the same way. Everything padded on the status bar followed each of these,
 * so the room chrome dropped and slid back up. Here the bar insets are zeroed in the plain dispatch
 * and in every animation frame, so Compose sees no bar until [showSystemUI] asks for the bars back.
 * Split screen and the other multi-window modes pass through: there the system keeps the bars
 * visible and the padding has to be real.
 */
fun ComponentActivity.maskHiddenSystemBars() {
    if (Build.VERSION.SDK_INT < 30) return
    val decor = window.decorView
    val statusBars = WindowInsetsCompat.Type.statusBars()
    val navigationBars = WindowInsetsCompat.Type.navigationBars()
    fun masking() = window in windowsWithHiddenBars && !isInMultiWindowMode
    fun WindowInsetsCompat.withoutBars(): WindowInsetsCompat = WindowInsetsCompat.Builder(this)
        .setInsets(statusBars, Insets.NONE)
        .setInsets(navigationBars, Insets.NONE)
        .setVisible(statusBars or navigationBars, false)
        .build()

    // The decor keeps its own handling of the insets; only what it hands down changes.
    ViewCompat.setOnApplyWindowInsetsListener(decor) { view, insets ->
        ViewCompat.onApplyWindowInsets(view, if (masking()) insets.withoutBars() else insets)
    }
    ViewCompat.setWindowInsetsAnimationCallback(decor, object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
        override fun onProgress(insets: WindowInsetsCompat, runningAnimations: MutableList<WindowInsetsAnimationCompat>): WindowInsetsCompat =
            if (masking()) insets.withoutBars() else insets
    })
}

val PlatformFile.uri: Uri
    get() = toAndroidUri(contextObtainer().packageName+".provider")

/**
 * A directly-openable Android [Uri] for handing this file to a player engine.
 *
 * Picker results wrap a `content://` Uri (SAF) and pass through untouched. Files we wrote
 * ourselves (e.g. a subtitle downloaded into our own storage) wrap a [java.io.File] and become
 * a `file://` Uri. Unlike [uri], this never routes through FileProvider, so it also works for
 * app-internal paths (filesDir/logs) that aren't declared in provider_paths.xml — which is where
 * downloaded subtitles land. A bare filesystem path (no scheme) makes ExoPlayer's content
 * resolver and mpv's resolveUri both reject the file silently.
 */
val PlatformFile.playableUri: Uri
    get() = when (val af = androidFile) {
        is AndroidFile.UriWrapper -> af.uri
        is AndroidFile.FileWrapper -> Uri.fromFile(af.file)
    }