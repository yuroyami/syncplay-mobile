package com.yuroyami.syncplay.managers.player

import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import org.jetbrains.compose.resources.DrawableResource

/** Media player engine usable in Syncplay; platform engines listed via availablePlatformVideoEngines. */
interface VideoEngine {
    /** Engine display name (e.g., ExoPlayer, MPV, VLC, AVPlayer, VLCKit). */
    val name: String

    /** True if this is the default engine for the current platform. */
    val isDefault: Boolean

    /** True if the engine is available and instantiable on this platform. */
    val isAvailable: Boolean

    /** Icon resource for this engine in the settings UI. */
    val img: DrawableResource

    /** Create a player instance bound to the given RoomViewModel. */
    fun createImpl(viewmodel: RoomViewmodel): PlayerImpl
}