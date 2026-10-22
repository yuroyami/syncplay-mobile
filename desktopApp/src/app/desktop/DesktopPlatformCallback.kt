package app.desktop

import app.PlatformCallback
import app.home.HomeViewmodel
import app.home.JoinConfig
import app.room.RoomViewmodel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Desktop implementation of [PlatformCallback], the calls that the shared code makes into the
 * platform. Most of them are mobile features (PiP, haptics, foreground services, screen
 * brightness) and are safe no-ops here:
 *  - Brightness: a desktop OS gives apps no screen-brightness control, so [supportsBrightness]
 *    is false and the room screen ignores the brightness swipe.
 *  - Media session and server foreground services: a desktop process simply keeps running. The
 *    hosted server lives in ServerHostSession for the life of the process and needs no service.
 *  - Shortcuts: there are no launcher shortcuts. A room (the group of people watching together)
 *    is joined from the home screen or from the command line (see Main.kt).
 */
object DesktopPlatformCallback : PlatformCallback {

    override fun HomeViewmodel.onSaveConfigShortcut(joinInfo: JoinConfig) {}

    override fun onEraseConfigShortcuts() {}

    override fun getCurrentBrightness(): Float = 0.5f

    override fun getMaxBrightness(): Float = 1f

    override fun changeCurrentBrightness(v: Float) {}

    /** No desktop OS lets an app set screen brightness, so the room shows no fake readout. */
    override val supportsBrightness: Boolean get() = false

    override fun mediaSessionInitialize(viewmodel: RoomViewmodel) {}

    override fun mediaSessionFinalize() {}

    override fun serverServiceStart(port: Int) {}

    override fun serverServiceStop() {}

    override fun onPlayback(paused: Boolean) {}

    override fun onPictureInPicture(enable: Boolean) {}

    override fun performHapticFeedback() {}

    override fun launchSystemFilePicker(onResult: (String?) -> Unit) {
        // Desktop uses FileKit's own picker; the unfiltered fallback picker is Android-only.
        onResult(null)
    }

    override fun copyText(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }

    override fun shareText(text: String) = copyText(text)
}
