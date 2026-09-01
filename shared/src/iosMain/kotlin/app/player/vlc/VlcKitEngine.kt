package app.player.vlc

import app.player.PlayerEngine
import app.player.PlayerImpl
import app.room.RoomViewmodel
import org.jetbrains.compose.resources.DrawableResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.vlc

/**
 * VLC (VLCKit) - VLC's iOS framework with extensive codec support.
 *
 * **Characteristics:**
 * - Widest format support (MKV, AVI, FLV, and many others)
 * - Supports most subtitle formats (SRT, SSA, ASS, etc.)
 * - More battery consumption than AVPlayer
 * - Larger app size due to bundled codecs
 *
 * **Best for:** Users who need to play various file formats and subtitle types.
 */
object VlcKitEngine : PlayerEngine {
    override val isAvailable: Boolean = true
    override val isDefault: Boolean = true
    override val name: String = "VLCKit"
    override val img: DrawableResource = Res.drawable.vlc

    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl = VlcKitImpl(viewmodel)
}