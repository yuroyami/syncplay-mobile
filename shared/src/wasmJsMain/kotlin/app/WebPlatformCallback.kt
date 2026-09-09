package app

import app.home.HomeViewmodel
import app.home.JoinConfig
import app.room.RoomViewmodel
import app.utils.loggy
import kotlinx.browser.window

/**
 * What the page can and cannot do on the app's behalf.
 *
 * Most of this interface describes an operating system, and a tab does not have one. Brightness
 * belongs to the device, a launcher shortcut belongs to a launcher, a foreground service belongs
 * to Android. Those answer honestly rather than pretending: [supportsBrightness] is false, so the
 * room hides the brightness swipe instead of offering a control that does nothing.
 *
 * The three that do have a browser equivalent are clipboard, sharing and the invite link, and all
 * three are permission-gated and asynchronous, so they are best-effort.
 */
object WebPlatformCallback : PlatformCallback {

    /** A bookmark is the browser's shortcut, and only the person at the keyboard can make one. */
    override fun HomeViewmodel.onSaveConfigShortcut(joinInfo: JoinConfig) {
        loggy("Saving a shortcut is the browser's own bookmark on this platform.")
    }

    override fun onEraseConfigShortcuts() = Unit

    /** A page cannot read or set screen brightness, so the room's swipe is hidden entirely. */
    override val supportsBrightness: Boolean = false
    override fun getCurrentBrightness(): Float = 1f
    override fun getMaxBrightness(): Float = 1f
    override fun changeCurrentBrightness(v: Float) = Unit

    /** Volume is the element's own; there is no device stream to move. */
    override fun deviceVolumeSteps(): Int = 0

    /** The Media Session API can put this on the OS media keys; not wired yet. */
    override fun mediaSessionInitialize(viewmodel: RoomViewmodel) = Unit
    override fun mediaSessionFinalize() = Unit

    /** A tab cannot listen on a port, so hosting is not offered here. */
    override fun serverServiceStart(port: Int) = Unit
    override fun serverServiceStop() = Unit

    override fun onPlayback(paused: Boolean) = Unit

    /** Document picture-in-picture needs a user gesture, so it belongs on the room's own control. */
    override fun onPictureInPicture(enable: Boolean) = Unit

    override fun performHapticFeedback() {
        // navigator.vibrate is Android Chrome only and silently absent elsewhere.
        runCatching { jsVibrate(20) }
    }

    /** The unfiltered picker is an Android workaround for SMB providers; nothing to do here. */
    override fun launchSystemFilePicker(onResult: (String?) -> Unit) = onResult(null)

    override fun copyText(text: String) {
        runCatching { jsCopyText(text) }
            .onFailure { loggy("Clipboard write was refused by the browser: $it") }
    }

    /** Web Share where the browser has it, clipboard where it does not. */
    override fun shareText(text: String) {
        val shared = runCatching { jsShareText(text) }.getOrDefault(false)
        if (!shared) copyText(text)
    }
}

private fun jsVibrate(ms: Int): Boolean =
    js("(typeof navigator.vibrate === 'function' ? navigator.vibrate(ms) : false)")

private fun jsCopyText(text: String): Boolean =
    js("(navigator.clipboard ? (navigator.clipboard.writeText(text), true) : false)")

private fun jsShareText(text: String): Boolean =
    js("(navigator.share ? (navigator.share({text: text}), true) : false)")

/** Kept so the import above is used even while the rest of this file is browser-API free. */
internal fun pageOrigin(): String = runCatching { window.location.origin }.getOrDefault("")
