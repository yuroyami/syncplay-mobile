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

/**
 * KitePlayer on desktop, and the only engine there.
 *
 * It is not called "Kite Compose" the way the mobile experiment is, because on desktop there is
 * nothing to distinguish it FROM. Android and iOS can host a real platform video view, so they get
 * a choice between that and the pure-Compose renderer. The JVM cannot: KitePlayer's native-surface
 * path compiles there but draws an empty box, because there is no desktop equivalent of a
 * SurfaceView or an AVSampleBufferDisplayLayer to hand it. So the pure-Compose renderer is not the
 * experimental sibling here, it is the whole picture.
 *
 * Default as well as only: [app.preferences.Preferences.PLAYER_ENGINE] resolves its initial value
 * by asking which engine is the default, so exactly one on this platform has to say yes.
 */
internal val desktopKiteEngine = KiteEngine(
    mediaResolver = DesktopKiteMediaResolver,
    presentation = KiteDesktopPresentation,
    isDefault = true,
)

/**
 * Frames arrive through KiteCodec's CPU converter and become one Skia raster each, which is what
 * the JVM target has: no Metal reader, no MediaCodec, no GPU upload path. Media loading stays
 * suspended until the renderer is attached after the first Compose frame, so decoder selection
 * cannot race video output.
 */
private object KiteDesktopPresentation : KitePlayerPresentation {
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
