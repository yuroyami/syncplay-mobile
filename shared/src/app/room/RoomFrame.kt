package app.room

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
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
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.theme.Space
import app.theme.Type
import app.uicomponents.LocalIsTelevision

/**
 * The top insets of the room screen: the status bars and the top of the display cutout (the
 * notch). A room is the group of people watching together. The room screen hides the system bars,
 * so the status bars report zero. The cutout inset still keeps content clear of the notch.
 */
@Composable
fun roomTopInsets(): WindowInsets =
    WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top))

/** The least height of the status strip of the portrait room, for a screen without a notch. */
private val PortraitStripMin = 28.dp

/**
 * The top insets of the portrait room. With a [strip] (the status strip above the stage, which
 * holds the room's status line), they are as tall as the notch, and at least as tall as the room
 * name with a margin, so the strip also has a place on a screen without a notch. Without a strip
 * (solo mode), they are the notch alone.
 */
@Composable
fun portraitTopInsets(strip: Boolean): WindowInsets {
    if (!strip) return roomTopInsets()
    val line = with(LocalDensity.current) { Type.label.lineHeight.toDp() }
    return roomTopInsets().union(WindowInsets(top = maxOf(PortraitStripMin, line + Space.gapTight + 2.dp)))
}

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
 * [controls] goes on the rail, the status line, the bottom bar and the center keys: the docks
 * that hold only controls. The chat and the panels are left out, because they cover the video.
 *
 * The video under the docks and the notices over them are not part of this frame.
 */
@Composable
fun RoomFrame(
    tall: Boolean,
    railHorizontal: Boolean,
    modifier: Modifier = Modifier,
    controls: Modifier = Modifier,
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
                    .onSizeChanged { railWidth = with(density) { it.width.toDp() } }
                    .then(controls),
            ) { rail() }
        }
        // The status line sits on the exact center, under the rail row on a tall window. On a
        // wide window the chat takes 36 percent and the status line 26 percent, so they never meet.
        if (status != null) {
            Box(
                Modifier.align(Alignment.TopCenter).focusGroup()
                    .windowInsetsPadding(topInsets)
                    .padding(top = if (tall) Space.row + Space.gap else Space.gapTight)
                    .then(if (tall) Modifier.fillMaxWidth(0.6f) else Modifier.fillMaxWidth(0.26f))
                    .then(controls),
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
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().focusGroup().then(controls)) { bottom() }
        }
        if (center != null) {
            Box(Modifier.align(Alignment.Center).focusGroup().then(controls)) { center() }
        }
    }
}

/** The height of the tab row under the stage of the portrait room. */
val PortraitTabRowHeight = Space.touchMin

/**
 * The bounds of the stage, the video area of the room in a window taller than wide: the full width
 * in 16:9, under [topInsets] ([portraitTopInsets]). The video layer and the controls over it both
 * take these bounds.
 */
fun Modifier.portraitStage(topInsets: WindowInsets): Modifier =
    fillMaxWidth().windowInsetsPadding(topInsets).aspectRatio(16f / 9f)

/**
 * The room in a window taller than wide, top to bottom: the [strip] (the status strip, see
 * [portraitTopInsets]), the [stage] (see [portraitStage]), the [tabs], the main box ([main]: chat,
 * or one page in place of chat) and the [composer] (the chat input row). With the keyboard open, the
 * tab row folds away, and the composer rides on the keyboard. [lower] goes on everything under the
 * stage. [mainTop] moves the main box down by that many pixels, for the notices that sit over its
 * top edge.
 */
@Composable
fun RoomPortraitFrame(
    keyboardOpen: Boolean,
    modifier: Modifier = Modifier,
    lower: Modifier = Modifier,
    mainTop: () -> Int = { 0 },
    strip: (@Composable BoxScope.() -> Unit)? = null,
    stage: @Composable BoxScope.() -> Unit,
    tabs: @Composable () -> Unit,
    main: @Composable BoxScope.() -> Unit,
    composer: (@Composable () -> Unit)? = null,
) {
    // Above the keyboard while it is open, and above the bottom gesture area otherwise.
    val bottomInsets = WindowInsets.ime.union(WindowInsets.safeGestures).only(WindowInsetsSides.Bottom)
    val top = portraitTopInsets(strip = strip != null)
    Column(modifier.fillMaxSize()) {
        // Black, like the frame of the screen around the notch.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(top).background(Color.Black)) { strip?.invoke(this) }
        // The strip takes the top insets, so nothing in the stage pads for the notch again.
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).consumeWindowInsets(top)) { stage() }
        Column(lower.weight(1f).fillMaxWidth().windowInsetsPadding(bottomInsets)) {
            if (!keyboardOpen) tabs()
            Box(Modifier.weight(1f).fillMaxWidth().pushedDown(mainTop)) { main() }
            composer?.invoke()
        }
    }
}

/**
 * Moves the content down by [top] pixels and makes it that much shorter. [top] is read while
 * laying out, so a change moves the content and recomposes nothing.
 */
private fun Modifier.pushedDown(top: () -> Int): Modifier = layout { measurable, constraints ->
    val shift = top().coerceIn(0, constraints.maxHeight)
    val placeable = measurable.measure(constraints.offset(vertical = -shift))
    layout(placeable.width, placeable.height + shift) { placeable.place(0, shift) }
}
