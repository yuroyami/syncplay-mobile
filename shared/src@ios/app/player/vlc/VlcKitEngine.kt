package app.player.vlc

import app.player.PlayerEngine
import app.player.PlayerImpl
import app.room.RoomViewmodel
import org.jetbrains.compose.resources.DrawableResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.vlc

/**
 * The VLCKit engine: VLC's iOS framework, with wide codec support. An engine is one of the video
 * players the app can drive. This is the default iOS engine.
 *
 * **Characteristics:**
 * - Widest format support (MKV, AVI, FLV and many others)
 * - Most subtitle formats (SRT, SSA, ASS and others)
 * - Uses more battery than AVPlayer
 * - Makes the app larger, because it bundles its codecs
 *
 * **Best for:** users who need to play many file formats and subtitle types.
 */
object VlcKitEngine : PlayerEngine {
    override val isAvailable: Boolean = true
    override val isDefault: Boolean = true
    override val name: String = "VLCKit"
    override val img: DrawableResource = Res.drawable.vlc

    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl = VlcKitImpl(viewmodel)
}