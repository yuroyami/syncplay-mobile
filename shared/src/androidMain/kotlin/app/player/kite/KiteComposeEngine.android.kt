package app.player.kite

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import io.github.yuroyami.kiteplayer.KitePlayer
import io.github.yuroyami.kiteplayer.compose.KiteVideo
import io.github.yuroyami.kiteplayer.compose.KiteVideoState
import io.github.yuroyami.kiteplayer.compose.rememberKiteVideoState

/** Experimental pure-Compose sibling of the existing native-view KitePlayer engine. */
internal val kiteComposeEngine = KiteEngine(
    mediaResolver = AndroidKiteMediaResolver,
    name = "Kite Compose",
    presentation = KiteComposePresentation,
)

/**
 * Uses the exact host [android.view.Window], enabling KitePlayer's API 31+ GPU Compose path.
 * Media loading remains suspended until the renderer is attached after the first Compose frame.
 */
private object KiteComposePresentation : KitePlayerPresentation, KiteProbeCapable {
    private var probedState: KiteVideoState? = null

    override fun probe(): String = probedState?.let {
        val cost = it.frameCost
        "presented=${it.presentedFrames} superseded=${it.supersededFrames}" +
            " rfailed=${it.failedFrames}" +
            " drawAvgMs=${cost.averageNanos / 1_000_000} drawWorstMs=${cost.worstNanos / 1_000_000}"
    } ?: "probe=nostate"


    @Composable
    override fun Content(
        player: KitePlayer?,
        modifier: Modifier,
        onPresented: (KitePlayer) -> Unit,
    ) {
        val window = LocalActivity.current?.window ?: return
        val videoState = rememberKiteVideoState(window)
        val currentOnPresented by rememberUpdatedState(onPresented)

        KiteVideo(
            state = videoState,
            modifier = modifier,
        )
        SideEffect {
            probedState = videoState
        }

        LaunchedEffect(player, videoState) {
            val currentPlayer = player ?: return@LaunchedEffect
            withFrameNanos { }
            currentPlayer.attachRenderer(videoState.renderer)
            currentOnPresented(currentPlayer)
        }

        DisposableEffect(player, videoState) {
            onDispose {
                if (probedState === videoState) probedState = null
                try {
                    player?.detachRenderer()
                } catch (_: IllegalStateException) {
                    // Engine teardown may close the player before composition releases output.
                }
            }
        }
    }
}
