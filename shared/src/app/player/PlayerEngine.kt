package app.player

import app.room.RoomViewmodel
import org.jetbrains.compose.resources.DrawableResource

/** An engine: one of the video players that the app can drive. `availablePlatformPlayerEngines`
 *  lists the engines of each platform. */
interface PlayerEngine {
    /** The engine's display name (for example ExoPlayer, mpv, VLCKit, AVPlayer, KitePlayer). */
    val name: String

    /** True if this is the default engine for the current platform. */
    val isDefault: Boolean

    /** True if this engine is the operating system's own player, not a bundled one
     *  (Media3/ExoPlayer on Android, AVFoundation on iOS, the browser's player on the web). The
     *  engine picker can show a "System" badge for it: the engine needs no extra native libraries.
     *  Desktop has no such engine. */
    val isSystem: Boolean get() = false

    /** True if the engine is available and can be created on this platform. */
    val isAvailable: Boolean

    /** True if the engine is still experimental (not fully stable). The engine picker shows a
     *  warning badge for it, so the user knows what they choose. */
    val isExperimental: Boolean get() = false

    /** The icon of this engine in the engine picker. */
    val img: DrawableResource

    /** Creates a player instance for the given [viewmodel]. */
    fun createImpl(viewmodel: RoomViewmodel): PlayerImpl
}