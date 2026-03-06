package app.player.vlc

import SyncplayMobile.shared.BuildConfig
import app.player.PlayerImpl
import app.player.PlayerEngine
import app.room.RoomViewmodel
import org.jetbrains.compose.resources.DrawableResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.vlc

/**
 * VLC - Popular cross-platform media player with maximum format support.
 *
 * **Characteristics:**
 * - Widest format support including QuickTime, Xvid, and obscure codecs
 * - Very powerful but least stable (potential crashes/bugs)
 * - Large library size
 * - Requires native libraries
 *
 * **Best for:** Users who need to play uncommon/legacy formats despite stability risks
 *
 * **Availability:** Only in withLibs build flavor
 */
object VlcEngine : PlayerEngine {
    override val isAvailable: Boolean = !BuildConfig.EXOPLAYER_ONLY
    override val isDefault: Boolean = false
    override val name: String = "VLC"
    override val img: DrawableResource = Res.drawable.vlc
    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl = VlcImpl(viewmodel)
}