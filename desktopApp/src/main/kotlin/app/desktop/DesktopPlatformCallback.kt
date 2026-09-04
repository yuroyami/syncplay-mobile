package app.desktop

import app.PlatformCallback
import app.home.HomeViewmodel
import app.home.JoinConfig
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.Locale

/**
 * Desktop implementation of the platform callback surface. Most operations are inherently
 * mobile (PiP, haptics, foreground services, screen brightness) and are safe no-ops here:
 *  - Brightness: desktop OSes give apps no screen-brightness control; the in-room brightness
 *    gesture is mobile-only anyway (it only attaches to touch swipes).
 *  - Media session / server foreground services: a desktop process just keeps running; the
 *    built-in server lives in the ServerViewmodel scope and needs no service to survive.
 *  - Shortcuts: no launcher-shortcut concept; rooms are joined from the home screen.
 */
object DesktopPlatformCallback : PlatformCallback {

    override fun onLanguageChanged(newLang: String) {
        // Compose Desktop reads its strings against the JVM's default locale, so setting that is
        // what makes the choice mean anything here. Screens already composed keep the strings they
        // resolved, so the change is complete at the next start; there is no Activity to recreate.
        applyDisplayLanguage(newLang)
    }

    override fun HomeViewmodel.onSaveConfigShortcut(joinInfo: JoinConfig) {}

    override fun onEraseConfigShortcuts() {}

    override fun getCurrentBrightness(): Float = 0.5f

    override fun getMaxBrightness(): Float = 1f

    override fun changeCurrentBrightness(v: Float) {}

    /** No desktop OS lets an app set screen brightness; the room hides the swipe rather than fake a readout. */
    override val supportsBrightness: Boolean get() = false

    override fun mediaSessionInitialize() {}

    override fun mediaSessionFinalize() {}

    override fun serverServiceStart(port: Int) {}

    override fun serverServiceStop() {}

    override fun onPlayback(paused: Boolean) {}

    override fun onPictureInPicture(enable: Boolean) {}

    override fun performHapticFeedback() {}

    override fun launchSystemFilePicker(onResult: (String?) -> Unit) {
        // FileKit's own picker is the desktop path; the no-filter fallback is Android-only.
        onResult(null)
    }

    override fun copyText(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }

    override fun shareText(text: String) = copyText(text)
}

/**
 * Points the JVM's default locale at [lang], which is where Compose Desktop reads its strings
 * from. A blank choice means "follow the system", so nothing is forced.
 */
internal fun applyDisplayLanguage(lang: String) {
    if (lang.isBlank()) return
    runCatching { Locale.setDefault(Locale.forLanguageTag(lang)) }
}
