package app

import app.home.HomeViewmodel
import app.home.JoinConfig
import app.room.RoomViewmodel
import app.utils.loggy
import kotlinx.browser.window

/**
 * The web implementation of [PlatformCallback]: what the page can and cannot do for the app.
 *
 * Most of this interface describes an operating system, and a tab does not have one. Brightness
 * belongs to the device, a launcher shortcut to a launcher, a foreground service to Android.
 * Those calls do nothing, and say so: [supportsBrightness] is false, so the room hides the
 * brightness swipe instead of offering a control that does nothing.
 *
 * The calls with a browser equivalent are the clipboard, sharing and vibration. The browser may
 * refuse them or run them asynchronously, so they are best-effort.
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

    /** The volume belongs to the video element; there is no device stream to change. */
    override fun deviceVolumeSteps(): Int = 0

    /** Not implemented. The Media Session API is the browser's way to reach the OS media keys. */
    override fun mediaSessionInitialize(viewmodel: RoomViewmodel) = Unit
    override fun mediaSessionFinalize() = Unit

    /** A tab cannot listen on a port, so hosting is not offered here. */
    override fun serverServiceStart(port: Int) = Unit
    override fun serverServiceStop() = Unit

    override fun onPlayback(paused: Boolean) = Unit

    /** Document picture-in-picture needs a user gesture, so it belongs on the room's own control. */
    override fun onPictureInPicture(enable: Boolean) = Unit

    override fun performHapticFeedback() {
        // navigator.vibrate exists only in Chrome on Android; elsewhere it is silently missing.
        runCatching { jsVibrate(20) }
    }

    /** The unfiltered picker is an Android workaround for SMB providers; the web needs none. */
    override fun launchSystemFilePicker(onResult: (String?) -> Unit) = onResult(null)

    override fun copyText(text: String) {
        runCatching { jsCopyText(text) }
            .onFailure { loggy("Clipboard write was refused by the browser: $it") }
    }

    /** Web Share where the browser has it, the clipboard where it does not. */
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

/** Nothing calls this. It keeps the kotlinx.browser.window import in use. */
internal fun pageOrigin(): String = runCatching { window.location.origin }.getOrDefault("")
