package app.player.kite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import io.github.yuroyami.kiteplayer.KitePlayer
import io.github.yuroyami.kiteplayer.compose.KiteVideo
import io.github.yuroyami.kiteplayer.compose.rememberKiteVideoState

/** Experimental pure-Compose sibling of the existing native-view KitePlayer engine. */
internal val kiteComposeEngine = KiteEngine(
    mediaResolver = IosKiteMediaResolver,
    name = "Kite Compose",
    presentation = KiteComposePresentation,
)

/**
 * Uses the portable renderer state: frames arrive through KitePlayer's Metal reader (VideoToolbox
 * CVPixelBuffers or uploaded planes) with a measured CPU fallback. Media loading remains
 * suspended until the renderer is attached after the first Compose frame.
 */
private object KiteComposePresentation : KitePlayerPresentation {
    @Composable
    override fun Content(
        player: KitePlayer?,
        modifier: Modifier,
        onPresented: (KitePlayer) -> Unit,
    ) {
        val videoState = rememberKiteVideoState()
        val currentOnPresented by rememberUpdatedState(onPresented)

        KiteVideo(
            state = videoState,
            modifier = modifier,
        )

        LaunchedEffect(player, videoState) {
            val currentPlayer = player ?: return@LaunchedEffect
            withFrameNanos { }
            currentPlayer.attachRenderer(videoState.renderer)
            currentOnPresented(currentPlayer)
        }

        DisposableEffect(player, videoState) {
            onDispose {
                try {
                    player?.detachRenderer()
                } catch (_: IllegalStateException) {
                    // Engine teardown may close the player before composition releases output.
                }
            }
        }
    }
}
