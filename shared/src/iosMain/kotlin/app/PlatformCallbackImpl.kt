package app

import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import app.home.HomeViewmodel
import app.home.JoinConfig
import app.player.avplayer.AVPlayerEngine
import app.player.vlc.VlcKitImpl
import app.player.kite.KiteImpl
import platform.AVKit.AVPictureInPictureController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationShortcutIcon.Companion.iconWithType
import platform.UIKit.UIApplicationShortcutIconType
import platform.UIKit.UIApplicationShortcutItem
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UIScreen
import platform.UIKit.shortcutItems
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIPasteboard
import kotlinx.serialization.json.Json

/**
 * The iOS implementation of [PlatformCallback], for system features that shared code cannot
 * reach on its own:
 * - Picture-in-Picture (PiP) video playback
 * - Screen brightness
 * - Home Screen Quick Actions for joining a room (a group of people watching together)
 * - Haptic feedback
 * - Copying and sharing text
 */
object ApplePlatformCallback : PlatformCallback {
    /**
     * Nothing to do on iOS. AVKit follows AVPlayer's rate by itself, and the VLC engine refreshes
     * its picture-in-picture play state on every state change.
     */
    override fun onPlayback(paused: Boolean) {
    }

    /**
     * Starts or stops Picture-in-Picture mode. Does nothing when the device does not support PiP
     * ([AVPictureInPictureController.isPictureInPictureSupported]).
     *
     * The call goes to the active engine (the video player in use):
     * - **AVPlayer** uses an [AVPictureInPictureController] built on its `AVPlayerLayer`.
     * - **VLCKit** (4.0 and later) runs PiP through its own `VLCPictureInPictureWindowControlling`
     *   protocol (see [VlcKitImpl.enterPictureInPicture]).
     *
     * @param enable True to enter PiP mode, false to exit it.
     */
    override val supportsPictureInPicture: Boolean
        get() = AVPictureInPictureController.isPictureInPictureSupported()

    override fun onPictureInPicture(enable: Boolean) {
        if (!AVPictureInPictureController.isPictureInPictureSupported()) return

        when (val player = roomViewmodel?.player) {
            is AVPlayerEngine.AVPlayerImpl -> {
                if (enable) player.enterPictureInPicture() else player.exitPictureInPicture()
            }
            is VlcKitImpl -> {
                if (enable) player.enterPictureInPicture() else player.exitPictureInPicture()
            }
            is KiteImpl -> {
                if (enable) player.enterPictureInPicture() else player.exitPictureInPicture()
            }
            else -> {
                // Engines without PiP support get here only if the UI failed to block the request.
            }
        }
    }

    /** iOS brightness runs from 0.0 to 1.0. */
    private const val MAX_BRIGHTNESS = 1.0f

    override fun getMaxBrightness() = MAX_BRIGHTNESS

    override fun getCurrentBrightness(): Float = UIScreen.mainScreen.brightness.toFloat()

    /** Clamps [v] to the 0.0 to 1.0 range before applying it. */
    override fun changeCurrentBrightness(v: Float) {
        UIScreen.mainScreen.brightness = v.coerceIn(0.0f, MAX_BRIGHTNESS).toDouble()
    }


    override fun mediaSessionInitialize(viewmodel: app.room.RoomViewmodel) {

    }

    override fun mediaSessionFinalize() {

    }

    override fun serverServiceStart(port: Int) {
        // iOS has no foreground service, so the server runs only while the app is in the foreground.
    }

    override fun serverServiceStop() {
        // No-op on iOS
    }

    /**
     * Adds a Home Screen Quick Action for joining a room. The shortcut's `type` string holds the
     * [JoinConfig] as JSON, and [handleShortcut] decodes it.
     */
    override fun HomeViewmodel.onSaveConfigShortcut(joinInfo: JoinConfig) {
        val type = Json.encodeToString(joinInfo)

        val shortcutItem = UIApplicationShortcutItem(
            type = type,
            localizedTitle = joinInfo.room,
            localizedSubtitle = null,
            icon = iconWithType(UIApplicationShortcutIconType.UIApplicationShortcutIconTypeFavorite),
            userInfo = null
        )

        UIApplication.sharedApplication.shortcutItems = UIApplication.sharedApplication.shortcutItems?.plus(shortcutItem)
    }

    /** Removes all room-join Quick Actions from the Home Screen. */
    override fun onEraseConfigShortcuts() {
        UIApplication.sharedApplication.shortcutItems = emptyList<UIApplicationShortcutItem>()
    }

    private var hapticGenerator: UIImpactFeedbackGenerator? = null

    override fun performHapticFeedback() {
        dispatch_async(dispatch_get_main_queue()) {
            val generator = hapticGenerator ?: UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
                .also { hapticGenerator = it }
            generator.prepare()
            generator.impactOccurred()
        }
    }

    /**
     * Calls [onResult] with null, the same result as a cancelled pick, so the caller keeps the
     * normal flow. The Android version works around FileKit's extension filter, which hides
     * files from SMB share providers. iOS has no such problem: its FileKit picker is already
     * the system picker (UIDocumentPickerViewController).
     */
    override fun launchSystemFilePicker(onResult: (String?) -> Unit) {
        onResult(null)
    }

    override fun copyText(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }

    override fun shareText(text: String) {
        val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(sheet, animated = true, completion = null)
    }
}