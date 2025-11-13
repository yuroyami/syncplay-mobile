package com.yuroyami.syncplay.managers.player

import com.yuroyami.syncplay.managers.player.avplayer.AvPlayer
import com.yuroyami.syncplay.managers.player.vlc.VlcPlayer
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import org.jetbrains.compose.resources.DrawableResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.swift
import syncplaymobile.shared.generated.resources.vlc

/**
 * Sealed interface defining available media player engines for iOS.
 *
 * Provides two player options with different characteristics:
 * - **AVPlayer**: Apple's native player (limited features but highly stable)
 * - **VLC (VLCKit)**: Third-party player with extensive format support (default)
 *
 * All players are available on iOS regardless of build configuration.
 */
sealed interface ApplePlayerEngine: PlayerEngine {

    /**
     * AVPlayer - Apple's native media player framework.
     *
     * **Characteristics:**
     * - Native iOS framework, deeply integrated with system
     * - Most stable and efficient (optimized by Apple)
     * - Limited codec/format support (mainly MP4, HLS, m3u8)
     * - Best battery life and hardware acceleration
     * - Supports Picture-in-Picture natively
     * - NO subtitle format support
     *
     * **Best for:** Users who primarily watch MP4/HLS content and prioritize
     * stability and battery life over broad format support.
     *
     * **Note:** Not the default despite stability due to limited format support.
     */
    object AVPlayer: ApplePlayerEngine {
        override val isAvailable: Boolean = true
        override val isDefault: Boolean = false
        override val name: String = "AVPlayer"
        override val img: DrawableResource = Res.drawable.swift

        override fun instantiate(viewmodel: RoomViewmodel): BasePlayer = AvPlayer(viewmodel)
    }

    /**
     * VLC (VLCKit) - VLC's iOS framework with extensive codec support.
     *
     * **Characteristics:**
     * - Widest format support (MKV, AVI, FLV, and many others)
     * - Supports most subtitle formats (SRT, SSA, ASS, etc.)
     * - More battery consumption than AVPlayer
     * - Larger app size due to bundled codecs
     * - Default player for iOS
     *
     * **Best for:** Users who need to play various file formats and subtitle types.
     * The iOS version is more stable than its Android counterpart.
     */
    object VLC: ApplePlayerEngine {
        override val isAvailable: Boolean = true
        override val isDefault: Boolean = true
        override val name: String = "VLCKit" //We name this one VLCKit to differentiate it from Android's VLC when saving to datastore
        override val img: DrawableResource = Res.drawable.vlc

        override fun instantiate(viewmodel: RoomViewmodel): BasePlayer = VlcPlayer(viewmodel)
    }

}