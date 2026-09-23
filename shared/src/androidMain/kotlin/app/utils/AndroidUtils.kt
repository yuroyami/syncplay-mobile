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
 * A global getter for the application Context. SynkplayApp.onCreate sets it at startup. The app
 * runs in a single process, so one application Context serves all code.
 */
lateinit var contextObtainer: () -> Context

/** Creates a DataStore file in the app's internal files directory, through the common factory. */
fun dataStore(context: Context, fileName: String): DataStore<Preferences> =
    createDataStore(
        producePath = { context.filesDir.resolve(fileName).absolutePath }
    )

/**
 * Returns a new Context whose resources use the given locale. It also sets the default locale.
 *
 * @param lang An ISO 639-1 language code, such as "en", "fr" or "ar".
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
 * Forwards Activity lifecycle events (CREATE, START, RESUME, PAUSE, STOP) to the room's UI state
 * manager. It is registered once in onCreate, before any room exists, so it looks the room up at
 * event time. A room looked up at registration time would be null, and the room's background
 * handling would never run.
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
 * Hides the status bar and the navigation bar for immersive fullscreen, and marks the window for
 * [maskHiddenSystemBars].
 *
 * By default it uses WindowInsetsControllerCompat: a swipe shows the bars for a moment, and then
 * they hide again.
 *
 * @param useDeprecated If true, uses the legacy systemUiVisibility flags instead.
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
 * Shows the status bar and the navigation bar again, with the default bar behavior, and ends
 * [maskHiddenSystemBars] for the window.
 *
 * @param useDeprecated If true, uses the legacy systemUiVisibility flags. That path only adds
 *   SYSTEM_UI_FLAG_VISIBLE, which is 0, so it leaves the bars as they are.
 */
@Suppress("DEPRECATION")
fun ComponentActivity.showSystemUI(useDeprecated: Boolean = false) {
    runOnUiThread {
        // Unmark the window first: the show animation starts at once and must not be masked.
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

    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    WindowCompat.setDecorFitsSystemWindows(window, false)
}

/**
 * The windows whose bars [hideSystemUI] has hidden. The keys are weak, because an activity window
 * must not outlive its activity.
 */
private val windowsWithHiddenBars: MutableSet<Window> = Collections.newSetFromMap(WeakHashMap())

/**
 * Reports no system bars to the view tree while [hideSystemUI] is in effect in a fullscreen
 * window.
 *
 * Android tells the window about hidden bars anyway. When the notification shade opens, it takes
 * over the bars and the window is told they are visible. When the shade closes, the window gets
 * the bars back and runs the hide animation itself. Below Android 16, the fade-out of the
 * transient bars after a top-edge swipe arrives the same way. Without this mask, everything
 * padded by the status bar follows each of these, so the room controls drop and slide back up.
 * This zeroes the bar insets in the plain dispatch and in every animation frame, so Compose sees
 * no bar until [showSystemUI] asks for the bars back. Split screen and the other multi-window
 * modes pass through: there the system keeps the bars visible, and the padding must be real.
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
 * An Android [Uri] that a player engine can open directly.
 *
 * Picker results wrap a `content://` Uri (SAF) and pass through unchanged. Files that the app
 * wrote itself (such as a downloaded subtitle) wrap a [java.io.File] and become a `file://` Uri.
 * Unlike [uri], this never goes through FileProvider, so it works for any app-internal path,
 * whether or not provider_paths.xml declares it. A bare filesystem path (no scheme) makes
 * ExoPlayer's content resolver and mpv's resolveUri both reject the file silently.
 */
val PlatformFile.playableUri: Uri
    get() = when (val af = androidFile) {
        is AndroidFile.UriWrapper -> af.uri
        is AndroidFile.FileWrapper -> Uri.fromFile(af.file)
    }