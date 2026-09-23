package app.player.kite

import app.player.PlayerEngine
import app.player.PlayerImpl
import app.room.RoomViewmodel
import io.github.yuroyami.kiteplayer.KitePlayerPlatform
import org.jetbrains.compose.resources.DrawableResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.kiteplayer

/**
 * KitePlayer, one of the engines (the video players the app can drive). KitePlayer is written in
 * Kotlin Multiplatform, decodes through FFmpeg (KiteFFmpeg) and renders through the platform's
 * own output.
 *
 * It is the only engine here whose implementation is shared: [KiteImpl] is written once and runs
 * unchanged on Android, iOS and desktop. The engine itself is common code; only the audio device,
 * the video surface and the hardware decoder differ per platform. Hardware decode is MediaCodec
 * on Android and VideoToolbox on iOS, both inside FFmpeg, with a measured software fallback
 * instead of a silent failure.
 *
 * The video shows through KitePlayerVideo: one composable that hosts either the native view or
 * the pure-Compose renderer. The in-room [app.preferences.Preferences.KITE_COMPOSE_RENDERER]
 * toggle switches between them while media plays.
 *
 * KitePlayer is experimental: it has no stable release and no full qualification on physical
 * hardware. Its subtitle support covers SubRip and WebVTT, embedded or loaded as external files
 * during playback; styled ASS subtitles are listed as tracks but not drawn. It supports speed
 * (0.25x to 4x, pitch preserved), chapters, aspect modes, and subtitle and audio delays at
 * runtime.
 */
@Suppress("KotlinConstantConditions")
internal class KiteEngine(
    private val mediaResolver: KiteMediaResolver,
    /** Desktop passes true: it is the only engine there, so it must also be the default one. */
    override val isDefault: Boolean = false,
    /**
     * Desktop passes true. KitePlayer's JVM native view is an AWT canvas, and macOS sends a click
     * to the topmost native view. So the room's controls, which all sit over the video, would be
     * drawn but never take a click. The Compose canvas is the only path the room can use there,
     * whatever KITE_COMPOSE_RENDERER and the frosted-glass setting say.
     */
    val forcesComposeCanvas: Boolean = false,
) : PlayerEngine {

    override val name: String = "KitePlayer"

    /**
     * False in the `exoOnly` Android flavor, which ships no native player libraries: that build
     * removes `libkitecodec_jni.so` from its jniLibs, so the engine picker would offer an entry
     * that cannot load. Always true on iOS, which has no exoOnly flavor.
     */
    override val isAvailable: Boolean
        get() = KitePlayerPlatform.isAvailable

    override val isExperimental: Boolean = true
    override val img: DrawableResource = Res.drawable.kiteplayer

    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl =
        KiteImpl(viewmodel, this, mediaResolver)
}
