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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.theme.Space
import app.utils.isTelevision

/** The room runs immersive, so hidden status bars report zero: the notch still needs the union. */
@Composable
fun roomTopInsets(): WindowInsets =
    WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top))

/**
 * The docks of the room, each padded for the notch and the gesture bars exactly once. The rail
 * at the top end, vertical when the window is tall enough and a row when it is not; the status
 * line on the top centre. Chat owns the start corner from the top down. Side: panels and the control
 * strip beside the rail, under it when the rail is a row, or a full-width sheet on a tall
 * window. Bottom: the transport, which pads its own gesture inset. Center: the play key. The
 * video underneath and the notices above are not this frame's business.
 */
@Composable
fun RoomFrame(
    tall: Boolean,
    railHorizontal: Boolean,
    modifier: Modifier = Modifier,
    status: (@Composable BoxScope.() -> Unit)? = null,
    rail: (@Composable BoxScope.() -> Unit)? = null,
    chat: (@Composable BoxScope.() -> Unit)? = null,
    side: (@Composable BoxScope.() -> Unit)? = null,
    bottom: (@Composable BoxScope.() -> Unit)? = null,
    center: (@Composable BoxScope.() -> Unit)? = null,
) {
    // A television cuts off the outer edge of the picture, and reports no insets saying so, so the
    // room keeps its chrome inside the margin Android TV asks for (5 percent of a 960x540dp screen).
    val tvSafe = if (isTelevision()) WindowInsets(left = 48.dp, top = 27.dp, right = 48.dp, bottom = 27.dp)
                 else WindowInsets(0)
    val topInsets = roomTopInsets().union(tvSafe.only(WindowInsetsSides.Top))
    val sideInsets = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal).union(tvSafe.only(WindowInsetsSides.Horizontal))
    val bottomInsets = WindowInsets.safeGestures.only(WindowInsetsSides.Bottom).union(tvSafe.only(WindowInsetsSides.Bottom))
    val transport = Space.rowTall + Space.gapTight
    val density = LocalDensity.current
    var railWidth by remember { mutableStateOf(0.dp) }

    Box(modifier.fillMaxSize()) {
        if (rail != null) {
            Box(
                Modifier.align(Alignment.TopEnd).focusGroup()
                    .zIndex(12f)
                    .windowInsetsPadding(topInsets)
                    .windowInsetsPadding(sideInsets)
                    .padding(end = Space.gapTight, top = Space.gapTight)
                    .onSizeChanged { railWidth = with(density) { it.width.toDp() } },
            ) { rail() }
        }
        // The status line sits on the exact centre, under the rail row on a tall window; the
        // chat stays narrow enough (36 percent) that the two never meet.
        if (status != null) {
            Box(
                Modifier.align(Alignment.TopCenter).focusGroup()
                    .windowInsetsPadding(topInsets)
                    .padding(top = if (tall) Space.row + Space.gap else Space.gapTight)
                    .then(if (tall) Modifier.fillMaxWidth(0.6f) else Modifier.fillMaxWidth(0.26f)),
                contentAlignment = Alignment.TopCenter,
            ) { status() }
        }
        if (chat != null) {
            Box(
                Modifier.align(Alignment.TopStart).focusGroup()
                    .then(if (tall) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.36f))
                    .fillMaxHeight()
                    .windowInsetsPadding(topInsets)
                    .padding(top = if (tall) Space.row + Space.gap else Space.gapTight, bottom = transport)
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
                        top = if (railHorizontal) Space.row + Space.gap else Space.gapTight,
                        bottom = transport,
                        end = if (tall) 0.dp else if (railHorizontal) Space.gapTight else railWidth + Space.gap,
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
