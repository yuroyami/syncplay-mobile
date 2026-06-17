package app

import app.home.HomeViewmodel
import app.home.JoinConfig
import app.player.avplayer.AVPlayerEngine
import app.player.vlc.VlcKitImpl
import platform.AVKit.AVPictureInPictureController
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIApplicationShortcutIcon.Companion.iconWithType
import platform.UIKit.UIApplicationShortcutIconType
import platform.UIKit.UIApplicationShortcutItem
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UIScreen
import platform.UIKit.shortcutItems

/**
 * iOS-specific implementation of platform callbacks for system-level operations.
 *
 * Handles iOS-specific features including:
 * - Picture-in-Picture (PiP) video playback
 * - Screen brightness control
 * - Device orientation changes
 * - Home screen shortcut management
 * - System settings navigation
 *
 * This object bridges the gap between Syncplay's platform-agnostic code and iOS APIs.
 */
object ApplePlatformCallback : PlatformCallback {
    /** TODO: update PiP pause/play buttons when playback state changes. */
    override fun onPlayback(paused: Boolean) {
    }

    /**
     * Starts or stops Picture-in-Picture mode for video playback.
     *
     * Dispatches to whichever engine is currently active:
     * - **AVPlayer** uses [AVPictureInPictureController] built around [AVPlayerLayer]
     *   (the iOS-native path, gated by [AVPictureInPictureController.isPictureInPictureSupported]).
     * - **VLCKit** (≥ 4.0) runs PiP via its own `VLCPictureInPictureWindowControlling`
     *   protocol (see [VlcKitImpl.enterPictureInPicture]).
     * - MPV has no native iOS PiP and is excluded by `supportsPictureInPicture = false`.
     *
     * @param enable True to enter PiP mode, false to exit it.
     */
    override fun onPictureInPicture(enable: Boolean) {
        if (!AVPictureInPictureController.isPictureInPictureSupported()) return

        when (val player = roomViewmodel?.player) {
            is AVPlayerEngine.AVPlayerImpl -> {
                if (enable) {
                    player.avPlayerLayer?.let { layer ->
                        // AVPictureInPictureController must be (re)constructed against the
                        // current AVPlayerLayer; cache it so the system can resume the
                        // same PiP session if the user dismisses and re-enters.
                        pipcontroller = AVPictureInPictureController(layer)
                        if (pipcontroller?.pictureInPicturePossible == true && roomViewmodel?.media != null) {
                            pipcontroller?.startPictureInPicture()
                        }
                    }
                } else {
                    pipcontroller?.stopPictureInPicture()
                }
            }
            is VlcKitImpl -> {
                if (enable) player.enterPictureInPicture() else player.exitPictureInPicture()
            }
            else -> {
                // Engines without PiP support reach here only if the UI gates failed.
            }
        }
    }

    private const val MAX_VOLUME = 100

    /** iOS brightness is a normalized 0.0–1.0 value. */
    private const val MAX_BRIGHTNESS = 1.0f

    override fun getMaxBrightness() = MAX_BRIGHTNESS

    override fun getCurrentBrightness(): Float = UIScreen.mainScreen.brightness.toFloat()

    /** Coerces into the valid 0.0–1.0 brightness range before applying. */
    override fun changeCurrentBrightness(v: Float) {
        UIScreen.mainScreen.brightness = v.coerceIn(0.0f, MAX_BRIGHTNESS).toDouble()
    }


    override fun mediaSessionInitialize() {

    }

    override fun mediaSessionFinalize() {

    }

    override fun serverServiceStart(port: Int) {
        // No foreground service on iOS — server runs only while app is in foreground
    }

    override fun serverServiceStop() {
        // No-op on iOS
    }

    /** Opens iOS Settings — iOS only allows per-app language changes there, not in-app. */
    override fun onLanguageChanged(newLang: String) {
        NSURL(string = UIApplicationOpenSettingsURLString).let { url ->
            UIApplication.sharedApplication.openURL(url, mapOf<Any?, Any>(), null)
        }
    }

    /**
     * Adds a Home Screen Quick Action for joining a room. The [JoinConfig] is encoded into the
     * shortcut's `type` string (joined by `','#'`) so it can be parsed back when tapped.
     */
    override fun HomeViewmodel.onSaveConfigShortcut(joinInfo: JoinConfig) {
        val type = with(joinInfo) { listOf(user, room, ip, port.toString(), pw) }.joinToString("','#'")

        val shortcutItem = UIApplicationShortcutItem(
            type = type,
            localizedTitle = joinInfo.room,
            localizedSubtitle = null,
            icon = iconWithType(UIApplicationShortcutIconType.UIApplicationShortcutIconTypeFavorite),
            userInfo = null
        )

        UIApplication.sharedApplication.shortcutItems = UIApplication.sharedApplication.shortcutItems?.plus(shortcutItem)

        //TODO: Localize
        snackItAsync("Shortcut added: ${joinInfo.room}")
    }

    /** Removes all room-join Quick Actions from the Home Screen. */
    override fun onEraseConfigShortcuts() {
        UIApplication.sharedApplication.shortcutItems = emptyList<UIApplicationShortcutItem>()
    }

    override fun performHapticFeedback() {
        val generator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
        generator.impactOccurred()
    }

    /**
     * iOS doesn't suffer from the Android-SAF-SMB MIME-filtering problem the equivalent
     * Android override addresses, so this is a no-op. The regular FileKit picker is already
     * the system picker (UIDocumentPickerViewController) on iOS. Returning null tells the
     * caller to fall back to the normal flow.
     */
    override fun launchSystemFilePicker(onResult: (String?) -> Unit) {
        onResult(null)
    }
}