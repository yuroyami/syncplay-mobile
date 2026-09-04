package app

import app.home.HomeViewmodel
import app.home.JoinConfig

/**
 * Platform-specific callback interface for handling system-level operations.
 *
 * Provides abstractions for Android/iOS/Desktop platform functionality that needs
 * to be implemented differently per platform, such as brightness control, shortcuts,
 * and system UI states.
 */
interface PlatformCallback {

    /**
     * Called when the user changes the application language.
     *
     * @param newLang The new language code (e.g., "en", "fr", "es")
     */
    fun onLanguageChanged(newLang: String)

    /**
     * Saves a room configuration as a platform-specific shortcut.
     *
     * On Android, this creates a launcher shortcut or dynamic link.
     *
     * @receiver The HomeViewmodel instance managing the configuration
     * @param joinInfo The room join configuration to save as a shortcut
     */
    fun HomeViewmodel.onSaveConfigShortcut(joinInfo: JoinConfig)

    /**
     * Removes all saved configuration shortcuts from the platform.
     */
    fun onEraseConfigShortcuts()

    /**
     * Gets the current system brightness level.
     *
     * @return The current brightness value (typically 0.0 to 1.0 or 0 to max)
     */
    fun getCurrentBrightness(): Float

    /**
     * Gets the maximum possible brightness level for the device.
     *
     * @return The maximum brightness value supported by the device
     */
    fun getMaxBrightness(): Float

    /**
     * Changes the system brightness to the specified value.
     *
     * @param v The new brightness value (range depends on platform implementation)
     */
    fun changeCurrentBrightness(v: Float)

    /**
     * The device's own music volume. [deviceVolumeSteps] is its step count, 0 where the platform
     * gives no way to set it (iOS, desktop); the other two are only called when it is above 0.
     */
    fun deviceVolumeSteps(): Int = 0
    fun getDeviceVolume(): Int = 0
    fun setDeviceVolume(step: Int) {}


    /**
     * Puts the room on the lock screen. Takes the room because the player behind the session is
     * whichever engine the room built, and there are five of them.
     */
    fun mediaSessionInitialize(viewmodel: app.room.RoomViewmodel)
    fun mediaSessionFinalize()

    fun serverServiceStart(port: Int)
    fun serverServiceStop()

    /** The hosted server's client count changed; a platform notification may show it. */
    fun serverClientsChanged(port: Int, clients: Int) {}

    /** Whether the platform lets the app change screen brightness; the swipe is hidden where it cannot. */
    val supportsBrightness: Boolean get() = true

    /**
     * Called when playback state changes.
     *
     * Used to update system UI elements, particularly during PiP mode.
     *
     * @param paused True if playback is paused, false if playing
     */
    fun onPlayback(paused: Boolean)

    /**
     * Called when Picture-in-Picture mode should be enabled or disabled.
     *
     * @param enable True to enter PiP mode, false to exit
     */
    fun onPictureInPicture(enable: Boolean)

    /**
     * Triggers a haptic feedback vibration using the platform's vibration API directly.
     *
     * This bypasses Compose's [LocalHapticFeedback] which relies on View.performHapticFeedback()
     * and can silently fail when the system "Touch feedback" setting is disabled —
     * a setting independent of the ringer mode.
     */
    fun performHapticFeedback()

    /**
     * Launches the OS-native file picker WITHOUT any extension/MIME filtering, so that
     * third-party DocumentsProviders (notably SMB share providers on Android) whose files
     * report unrecognized MIME types still appear and can be selected.
     *
     * Used as a fallback for mpv playback of SMB-backed files which the default FileKit
     * picker hides when an extension filter is applied.
     *
     * @param onResult invoked on the main thread with the picked URI as a string,
     *                 or null if the user cancelled. Android returns a `content://` URI
     *                 with persistable read permission; iOS returns a `file://` path.
     */
    fun launchSystemFilePicker(onResult: (String?) -> Unit)

    /** Puts [text] on the system clipboard. */
    fun copyText(text: String)

    /** Opens the system share sheet with [text]; desktop copies instead. */
    fun shareText(text: String)
}