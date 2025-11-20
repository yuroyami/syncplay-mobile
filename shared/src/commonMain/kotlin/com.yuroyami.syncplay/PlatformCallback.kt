package com.yuroyami.syncplay

import com.yuroyami.syncplay.managers.player.BasePlayer
import com.yuroyami.syncplay.models.JoinConfig
import com.yuroyami.syncplay.viewmodels.HomeViewmodel

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


    fun initializeMediaSession(player: BasePlayer)

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
}