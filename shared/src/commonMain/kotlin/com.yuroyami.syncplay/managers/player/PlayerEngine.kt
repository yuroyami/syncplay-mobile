package com.yuroyami.syncplay.managers.player

import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import org.jetbrains.compose.resources.DrawableResource

/**
 * Interface defining a media player engine that can be used in Syncplay.
 *
 * Each platform provides a list of available engines via [com.yuroyami.syncplay.utils.availablePlatformPlayerEngines].
 */
interface PlayerEngine {
    /**
     * name of the player engine.
     * "ExoPlayer", "MPV", "VLC", "AVPlayer", "VLCKit"
     */
    val name: String

    /**
     * Whether this engine is the default choice for the current platform.
     * Only one engine per platform should have this set to true.
     */
    val isDefault: Boolean

    /**
     * Whether this engine is currently available and can be instantiated.
     * May be false if required libraries are missing or the platform doesn't support it.
     */
    val isAvailable: Boolean

    /**
     * Icon resource representing this player engine.
     * Used in settings UI for player selection.
     */
    val img: DrawableResource

    /**
     * Creates a new instance of the player for the given room.
     *
     * @param viewmodel The RoomViewModel that will own and manage this player instance
     * @return A concrete BasePlayer implementation for this engine
     */
    fun instantiate(viewmodel: RoomViewmodel): BasePlayer
}