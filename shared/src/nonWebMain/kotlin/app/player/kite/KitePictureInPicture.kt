package app.player.kite

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.yuroyami.kiteplayer.KitePlayer
import kotlinx.coroutines.flow.StateFlow

/**
 * Picture-in-picture for KitePlayer where the small window needs its own video layer, which is
 * iOS. The system window takes its picture only from a sample buffer layer, so the video moves
 * into one while the window is open, and back to the usual renderer when it closes.
 */
internal interface KitePictureInPicture {

    /** True while [Surface] draws the video in place of the usual renderer. */
    val drawing: StateFlow<Boolean>

    /** The video while the window opens or shows. The engine composes it while [drawing] is true. */
    @Composable
    fun Surface(player: KitePlayer, modifier: Modifier)

    /** Moves the picture into the sample buffer layer and opens the window. */
    fun start()

    /** Closes the window. The picture goes back to the usual renderer. */
    fun stop()

    /** Closes the window and lets go of the layer, with the engine. */
    fun close()
}

/**
 * The platform's helper, or null where the window needs no layer of its own. [onActive] follows
 * the window: true while it shows.
 */
internal expect fun kitePictureInPicture(onActive: (Boolean) -> Unit): KitePictureInPicture?
