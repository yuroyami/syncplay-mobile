package app.player.web

import app.player.PlayerEngine
import app.player.PlayerImpl
import app.room.RoomViewmodel
import org.jetbrains.compose.resources.DrawableResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.webplayer

/**
 * The browser's own `<video>` element, and the only engine (video player) that the web can have.
 *
 * The other engines all decode natively: ExoPlayer and mpv on Android, AVPlayer and VLCKit on iOS,
 * and KitePlayer through FFmpeg over JNI and cinterop. A page decodes nothing itself; it hands a
 * URL to the browser, and the browser plays whatever it was built to play.
 *
 * This trade decides what a web client is good for. The browser gives hardware decoding,
 * subtitles, audio tracks, picture-in-picture, fullscreen, and position events accurate enough
 * for the sync algorithm. The cost is any format the browser refuses. MP4 and WebM play
 * everywhere, MKV plays only in Chromium, and HEVC depends on the machine. Nothing here can
 * widen that list.
 */
internal object WebVideoEngine : PlayerEngine {

    override val name: String = "Browser"

    /** The only engine on this platform, so it has to be the default one. */
    override val isDefault: Boolean = true

    /** It is the browser's own player, in the same way that AVPlayer is iOS's own. */
    override val isSystem: Boolean = true

    override val isExperimental: Boolean = true

    override val isAvailable: Boolean = true

    override val img: DrawableResource = Res.drawable.webplayer

    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl = WebVideoImpl(viewmodel, this)
}

internal val webVideoEngine: PlayerEngine = WebVideoEngine
