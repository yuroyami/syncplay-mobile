package com.yuroyami.syncplay

import com.yuroyami.syncplay.models.JoinConfig
import com.yuroyami.syncplay.viewmodels.HomeViewmodel
import platform.AVKit.AVPictureInPictureController
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIApplicationShortcutIcon.Companion.iconWithType
import platform.UIKit.UIApplicationShortcutIconType
import platform.UIKit.UIApplicationShortcutItem
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
    /**
     * Called when playback state changes.
     *
     * Currently used to update Picture-in-Picture controls (pause/play buttons).
     * TODO: Implement PiP control updates.
     *
     * @param paused True if playback is paused, false if playing
     */
    override fun onPlayback(paused: Boolean) {
        //TODO: Here we only need to update PIP pause/play buttons or other params.
    }

    /**
     * Starts or stops Picture-in-Picture mode for video playback.
     *
     * Checks if PiP is supported on the device, then attempts to start PiP if:
     * - PiP is possible for the current player
     * - User is in a room
     * - Media is loaded
     *
     * TODO: Complete implementation with proper player layer extraction and state checks.
     *
     * @param enable True to enter PiP mode (currently only true is handled)
     */
    override fun onPictureInPicture(enable: Boolean) {
        if (AVPictureInPictureController.isPictureInPictureSupported()) {
            /* TODO val layer = when (viewmodel?.player) {
                is AvPlayer -> (viewmodel?.player as? AvPlayer)?.avPlayerLayer
                is VlcPlayer -> (viewmodel?.player as? VlcPlayer)?.pipLayer
                else -> null
            }
            layer?.let {
                pipcontroller = AVPictureInPictureController(layer)
            } */
        }

        if (pipcontroller?.pictureInPicturePossible == true
        //TODO && isRoom.value
        //TODO && viewmodel?.media != null
        ) {
            pipcontroller?.startPictureInPicture()
        }
    }

    /**
     * Maximum brightness value for iOS devices (normalized to 1.0).
     */
    private const val MAX_VOLUME = 100

    /**
     * Maximum brightness value for iOS devices (normalized to 1.0).
     */
    private const val MAX_BRIGHTNESS = 1.0f

    /**
     * Gets the maximum brightness level.
     *
     * @return 1.0 (iOS uses normalized 0.0-1.0 range)
     */
    override fun getMaxBrightness() = MAX_BRIGHTNESS

    /**
     * Gets the current screen brightness level.
     *
     * @return Current brightness as a float between 0.0 and 1.0
     */
    override fun getCurrentBrightness(): Float = UIScreen.mainScreen.brightness.toFloat()

    /**
     * Sets the screen brightness level.
     *
     * Automatically coerces the value to the valid range (0.0 to 1.0).
     *
     * @param v The new brightness level (0.0 = minimum, 1.0 = maximum)
     */
    override fun changeCurrentBrightness(v: Float) {
        UIScreen.mainScreen.brightness = v.coerceIn(0.0f, MAX_BRIGHTNESS).toDouble()
    }

    /**
     * Handles language change requests by opening iOS Settings.
     *
     * Since iOS requires language changes to be made in system Settings, this
     * opens the Settings app to let the user change the app's language preference.
     *
     * @param newLang The requested language code (not used, as iOS handles the change)
     */
    override fun onLanguageChanged(newLang: String) {
        NSURL(string = UIApplicationOpenSettingsURLString).let { url ->
            UIApplication.sharedApplication.openURL(url, mapOf<Any?, Any>(), null)
        }
    }

    /**
     * Creates a Home Screen Quick Action (3D Touch shortcut) for quickly joining a room.
     *
     * Encodes the room configuration into the shortcut type string, allowing the app
     * to parse it later when the shortcut is tapped. Uses a favorite icon to represent
     * saved room configurations.
     *
     * TODO: Localize the success message
     *
     * @receiver HomeViewmodel for accessing the snack manager
     * @param joinInfo The room configuration to save as a shortcut
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

        // Add the shortcut item to the application
        UIApplication.sharedApplication.shortcutItems = UIApplication.sharedApplication.shortcutItems?.plus(shortcutItem)

        //TODO: Localize
        snackManager.snackItAsync("Shortcut added: ${joinInfo.room}")
    }

    /**
     * Removes all saved room configuration shortcuts from the Home Screen.
     *
     * Clears the entire shortcut items array, removing all Quick Actions.
     */
    override fun onEraseConfigShortcuts() {
        UIApplication.sharedApplication.shortcutItems = emptyList<UIApplicationShortcutItem>()
    }
}