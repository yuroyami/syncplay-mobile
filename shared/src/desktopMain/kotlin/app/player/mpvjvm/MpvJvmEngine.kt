package app.player.mpvjvm

import app.player.PlayerEngine
import app.player.PlayerImpl
import app.room.RoomViewmodel
import org.jetbrains.compose.resources.DrawableResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.mpv

/**
 * libmpv engine for desktop via a JNA binding and mpv's software render API.
 * Same "mpv" name as the mobile engines so the PLAYER_ENGINE preference is portable.
 * Available whenever libmpv is loadable — from the app bundle (fetchMpvNatives) or a system
 * install; greys out in the engine picker otherwise. VLC stays the desktop default because its
 * natives are guaranteed-bundled on macOS/Windows.
 */
object MpvJvmEngine : PlayerEngine {
    override val isAvailable: Boolean get() = MpvNativeLoader.library != null
    override val isDefault: Boolean = false
    override val isExperimental: Boolean = true
    override val name: String = "mpv"
    override val img: DrawableResource = Res.drawable.mpv
    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl = MpvJvmImpl(viewmodel)
}
