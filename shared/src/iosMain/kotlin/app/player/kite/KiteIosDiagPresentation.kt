package app.player.kite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import app.utils.loggy
import io.github.yuroyami.kiteplayer.KitePlayer
import io.github.yuroyami.kiteplayer.mobile.installMobileRenderer
import io.github.yuroyami.kiteplayer.view.KitePlayerUIView

/**
 * TEMP DIAG (draw-truth hunt, 2026-08-25): the same UIKit hosting the library's own iOS surface
 * does, except the view is retained so [probe] can read the renderer's presented/superseded/failed
 * counters, the only numbers that say whether a frame actually reached the screen. Remove together
 * with the KiteCmd/KiteStats diagnostics once the wedge is root-caused.
 */
internal object KiteIosDiagPresentation : KitePlayerPresentation, KiteProbeCapable {

    private var view: KitePlayerUIView? = null

    override fun probe(): String = view?.let {
        "presented=${it.presentedFrames} superseded=${it.supersededFrames}" +
            " rfailed=${it.failedFrames} haspic=${it.hasPicture}"
    } ?: "probe=noview"

    @Composable
    override fun Content(
        player: KitePlayer?,
        modifier: Modifier,
        onPresented: (KitePlayer) -> Unit,
    ) {
        UIKitView(
            factory = {
                KitePlayerUIView().apply { installMobileRenderer() }.also {
                    view = it
                    loggy("KiteProbe: video view created")
                }
            },
            modifier = modifier,
            update = { v -> v.player = player },
            onRelease = { v ->
                v.release()
                if (view === v) view = null
            },
        )
        SideEffect {
            player?.let(onPresented)
        }
    }
}
