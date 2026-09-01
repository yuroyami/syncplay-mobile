package app.room

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.theme.Space

/** The room runs immersive, so hidden status bars report zero: the notch still needs the union. */
@Composable
fun roomTopInsets(): WindowInsets =
    WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top))

/**
 * The docks of the room, each padded for the notch and the gesture bars exactly once. Top: the
 * status line at the start and the rail at the end. Chat: below the status line. Side: panels and
 * the control strip beside the rail, or a full-width sheet on a tall window. Bottom: the
 * transport, which pads its own gesture inset. Center: the play key. The video underneath and the
 * notices above are not this frame's business.
 */
@Composable
fun RoomFrame(
    tall: Boolean,
    modifier: Modifier = Modifier,
    topStart: (@Composable BoxScope.() -> Unit)? = null,
    rail: (@Composable BoxScope.() -> Unit)? = null,
    chat: (@Composable BoxScope.() -> Unit)? = null,
    side: (@Composable BoxScope.() -> Unit)? = null,
    bottom: (@Composable BoxScope.() -> Unit)? = null,
    center: (@Composable BoxScope.() -> Unit)? = null,
) {
    val topInsets = roomTopInsets()
    val sideInsets = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
    val bottomInsets = WindowInsets.safeGestures.only(WindowInsetsSides.Bottom)
    val transport = Space.rowTall + Space.gapTight
    val railWidth = Space.row + Space.gapTight * 2

    Box(modifier.fillMaxSize()) {
        if (topStart != null) {
            Box(
                Modifier.align(Alignment.TopStart).focusGroup()
                    .windowInsetsPadding(topInsets)
                    .windowInsetsPadding(sideInsets)
                    .padding(start = Space.gapTight, top = Space.gapTight)
                    .then(if (tall) Modifier.fillMaxWidth(0.6f) else Modifier.fillMaxWidth(0.44f)),
            ) { topStart() }
        }
        if (rail != null) {
            Box(
                Modifier.align(Alignment.TopEnd).focusGroup()
                    .zIndex(12f)
                    .windowInsetsPadding(topInsets)
                    .windowInsetsPadding(sideInsets)
                    .padding(end = Space.gapTight, top = Space.gapTight),
            ) { rail() }
        }
        if (chat != null) {
            Box(
                Modifier.align(Alignment.TopStart).focusGroup()
                    .then(if (tall) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.44f))
                    .fillMaxHeight()
                    .windowInsetsPadding(topInsets)
                    .padding(top = Space.rowCompact + Space.gap, bottom = transport)
                    .windowInsetsPadding(bottomInsets),
            ) { chat() }
        }
        if (side != null) {
            Box(
                Modifier.align(Alignment.CenterEnd).focusGroup()
                    .then(if (tall) Modifier.fillMaxWidth() else Modifier)
                    .fillMaxHeight()
                    .zIndex(10f)
                    .windowInsetsPadding(topInsets)
                    .windowInsetsPadding(sideInsets)
                    .windowInsetsPadding(bottomInsets)
                    .padding(
                        top = if (tall) Space.row + Space.gap else Space.gapTight,
                        bottom = transport,
                        end = if (tall) 0.dp else railWidth,
                    ),
            ) { side() }
        }
        if (bottom != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().focusGroup()) { bottom() }
        }
        if (center != null) {
            Box(Modifier.align(Alignment.Center).focusGroup()) { center() }
        }
    }
}
