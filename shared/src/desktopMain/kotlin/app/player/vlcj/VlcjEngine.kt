package app.player.vlcj

import app.player.PlayerEngine
import app.player.PlayerImpl
import app.room.RoomViewmodel
import org.jetbrains.compose.resources.DrawableResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.vlc

/**
 * libVLC engine for desktop via the vlcj JVM bindings. The libVLC natives ship inside the
 * app image (see BundledVlcDirectoryProvider); a system-installed VLC is only a fallback.
 * Sole and default engine on desktop.
 */
object VlcjEngine : PlayerEngine {
    override val isAvailable: Boolean = true
    override val isDefault: Boolean = true
    override val isExperimental: Boolean = false
    override val name: String = "VLC"
    override val img: DrawableResource = Res.drawable.vlc
    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl = VlcjImpl(viewmodel)
}
