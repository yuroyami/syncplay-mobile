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
import app.uicomponents.LocalIsTelevision

/**
 * The top insets of the room screen: the status bars and the top of the display cutout (the
 * notch). A room is the group of people watching together. The room screen hides the system bars,
 * so the status bars report zero. The cutout inset still keeps content clear of the notch.
 */
@Composable
fun roomTopInsets(): WindowInsets =
    WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top))

/**
 * Places the docks of the room screen (the areas that hold the controls) and pads each dock for
 * the window insets once:
 * - [rail]: the strip of buttons that opens the panels, at the top end. It is a column, or a row
 *   when [railHorizontal] is true.
 * - [status]: the status line, on the top center.
 * - [chat]: the start side, from the top down.
 * - [side]: the panels and the control strip, beside a column rail or under a row rail. On a
 *   [tall] window, a full-width sheet.
 * - [bottom]: the bottom bar, which pads its own gesture inset.
 * - [center]: the play button and the jump keys.
 *
 * The video under the docks and the notices over them are not part of this frame.
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
    // A television cuts off the outer edge of the picture and reports no insets for it. The room
    // keeps its controls inside the margin that Android TV asks for (5 percent of 960x540dp).
    val tvSafe = if (LocalIsTelevision.current) WindowInsets(left = 48.dp, top = 27.dp, right = 48.dp, bottom = 27.dp)
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
        // The status line sits on the exact center, under the rail row on a tall window. On a
        // wide window the chat takes 36 percent and the status line 26 percent, so they never meet.
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
