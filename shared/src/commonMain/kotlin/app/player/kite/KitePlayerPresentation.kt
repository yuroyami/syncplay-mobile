package app.player.kite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import io.github.yuroyami.kiteplayer.KitePlayer
import io.github.yuroyami.kiteplayer.compose.KitePlayerSurface

/**
 * Presents a [KitePlayer] inside Compose.
 *
 * The shared player owns playback and the presentation owns only video output. Implementations
 * must call [onPresented] after their renderer/surface is attached to [player]; media loading is
 * deliberately suspended until that callback so decoder selection cannot race video output.
 * [player] is initially null while the composition creates it and is populated on recomposition.
 *
 * The interop implementation is common while Android and iOS each also supply an experimental
 * pure-Compose implementation (per-platform because the renderer library ships no jvm artifact
 * and Android uses its Window-scoped GPU overload). Keeping the presentation strategy here lets
 * all paths share all playback, synchronization and media-lifetime behavior.
 */
internal interface KitePlayerPresentation {
    @Composable
    fun Content(
        player: KitePlayer?,
        modifier: Modifier,
        onPresented: (KitePlayer) -> Unit,
    )
}

/**
 * TEMP DIAG (draw-truth hunt, 2026-08-25): a presentation that can answer for the renderer's own
 * presented/superseded/failed counters, the numbers the engine's stats deliberately do not carry.
 */
internal interface KiteProbeCapable {
    /** Never null: an unavailable probe must SAY so rather than vanish from the log line. */
    fun probe(): String
}

/** The shipping KitePlayer path: Compose hosts the platform-native video view. */
internal object KiteInteropPresentation : KitePlayerPresentation {
    @Composable
    override fun Content(
        player: KitePlayer?,
        modifier: Modifier,
        onPresented: (KitePlayer) -> Unit,
    ) {
        KitePlayerSurface(player = player, modifier = modifier)
        SideEffect {
            player?.let(onPresented)
        }
    }
}
