package app

import app.home.HomeViewmodel
import app.home.JoinConfig

/**
 * The system operations that each platform (Android, iOS, desktop, web) implements in its own
 * way, such as brightness, shortcuts, the media session and the file picker.
 */
interface PlatformCallback {


    /**
     * Saves the join details of a room as a shortcut. A room is the group of people who watch
     * together. Android adds a launcher shortcut and asks to pin it; iOS adds a Quick Action.
     *
     * @receiver The home screen's view model
     * @param joinInfo The join details to save as a shortcut
     */
    fun HomeViewmodel.onSaveConfigShortcut(joinInfo: JoinConfig)

    /**
     * Removes all saved room shortcuts.
     */
    fun onEraseConfigShortcuts()

    /**
     * Gets the current screen brightness.
     *
     * @return The brightness, from 0 to [getMaxBrightness]
     */
    fun getCurrentBrightness(): Float

    /**
     * Gets the highest brightness value that [changeCurrentBrightness] accepts.
     *
     * @return The highest brightness value. Every platform returns 1.
     */
    fun getMaxBrightness(): Float

    /**
     * Sets the screen brightness.
     *
     * @param v The new brightness, from 0 to [getMaxBrightness]
     */
    fun changeCurrentBrightness(v: Float)

    /**
     * The device's own music volume. [deviceVolumeSteps] is its number of steps, or 0 where the
     * platform gives no way to set it (iOS, desktop, web). The other two functions are called only
     * when it is above 0.
     */
    fun deviceVolumeSteps(): Int = 0
    fun getDeviceVolume(): Int = 0
    fun setDeviceVolume(step: Int) {}


    /**
     * Puts the room on the lock screen. It takes the room because the player behind the media
     * session is whichever engine (video player) the room built.
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
     * Called when playback pauses or resumes. Android uses it to update the Picture-in-Picture
     * controls.
     *
     * @param paused True if playback is paused, false if playing
     */
    fun onPlayback(paused: Boolean)

    /**
     * Enters or leaves Picture-in-Picture mode.
     *
     * @param enable True to enter Picture-in-Picture mode, false to leave it
     */
    fun onPictureInPicture(enable: Boolean)

    /** Delivers a touch-feedback pulse on the platform UI thread, respecting device settings. */
    fun performHapticFeedback()

    /**
     * Opens the system file chooser with no extension or MIME filter. Some third-party document
     * providers (for example SMB shares on Android) report MIME types that FileKit's filtered
     * picker hides, and this chooser still shows their files.
     *
     * Only Android implements it. The other platforms pass null to [onResult] at once.
     *
     * @param onResult invoked on the main thread with the picked URI as a string, or null if
     *                 the user cancelled. On Android it is a `content://` URI, and its read
     *                 grant may last only for this session.
     */
    fun launchSystemFilePicker(onResult: (String?) -> Unit)

    /** Puts [text] on the system clipboard. */
    fun copyText(text: String)

    /** Opens the system share sheet with [text]; desktop copies instead. */
    fun shareText(text: String)
}