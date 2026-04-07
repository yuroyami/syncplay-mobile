package app.player.mpv

import app.player.PlayerEngine
import app.player.PlayerImpl
import app.room.RoomViewmodel
import org.jetbrains.compose.resources.DrawableResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.mpv

/**
 * MPV (MPVKit) - Powerful open-source media player via libmpv on iOS.
 *
 * **Characteristics:**
 * - Extensive codec and container support (MKV, AVI, FLV, etc.)
 * - Hardware-accelerated decoding via VideoToolbox
 * - Metal rendering via MoltenVK (Vulkan)
 * - Full subtitle support (SRT, ASS, SSA, TTML, VTT)
 * - Chapter support
 * - Requires Swift bridging layer (libmpv is a C API)
 *
 * **Best for:** Users who want broad format support and advanced playback features.
 *
 * **Availability:** Requires MPVKit SPM package in the iosApp Xcode project.
 */
object MpvKitEngine : PlayerEngine {
    override val isAvailable: Boolean = instantiateMpvKitPlayer != null
    override val isDefault: Boolean = false
    override val name: String = "MPVKit"
    override val img: DrawableResource = Res.drawable.mpv

    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl {
        return instantiateMpvKitPlayer?.invoke(viewmodel)
            ?: throw IllegalStateException("MPVKit bridge not initialized. Ensure MPVKit SPM package is added and bridge is registered in iOSApp.swift")
    }
}
