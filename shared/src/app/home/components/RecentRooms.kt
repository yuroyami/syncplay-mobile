package app.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.home.RecentJoin
import app.i18n.strings
import app.theme.Motion
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.LocalIsTelevision
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.FontSizeRange
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.Icon
import app.uicomponents.controls.Text
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.pressFeedback
import kotlinx.coroutines.delay

/**
 * The recent rooms, newest first, in a layer over the form, right above the join row. The layer is
 * a popup anchored on the join row, so it takes no room in the form: opening and closing it moves
 * nothing on the home screen. It is [width] wide, as wide as the join row, and its ground is
 * opaque, so the fields under it do not show through. The row of rooms scrolls sideways.
 *
 * A tap or a remote press on an entry joins it, and the cross beside it removes it from the list.
 * A tap outside the layer, Back and Escape call [onDismiss]. A tap on the recent rooms key counts as
 * a tap outside. Under a remote or a keyboard, focus enters the first entry when the layer opens.
 */
@Composable
internal fun RecentRoomsLayer(
    open: Boolean,
    entries: List<RecentJoin>,
    width: Dp,
    onPick: (RecentJoin) -> Unit,
    onForget: (RecentJoin) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = palette
    // The layer stays composed until its exit animation ends.
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = open
    if (!shown.currentState && !shown.targetState) return
    val gap = with(LocalDensity.current) { Space.gapTight.roundToPx() }
    val remoteOrKeyboard = LocalIsTelevision.current || LocalInputModeManager.current.inputMode == InputMode.Keyboard
    Popup(
        popupPositionProvider = remember(gap) { AboveAnchor(gap) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val firstEntry = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            if (!remoteOrKeyboard) return@LaunchedEffect
            // The row composes its entries over a frame or two, so the first one may not exist yet.
            repeat(ENTRY_FOCUS_TRIES) {
                if (runCatching { firstEntry.requestFocus(FocusDirection.Enter) }.getOrDefault(false)) return@LaunchedEffect
                delay(60)
            }
        }
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(Motion.move()) + slideInVertically(Motion.move()) { it / 4 },
            exit = fadeOut(Motion.quick()) + slideOutVertically(Motion.quick()) { it / 4 },
        ) {
            LazyRow(
                modifier = Modifier
                    .width(width)
                    // On Android, the popup closes on Back by itself. Escape closes it here.
                    .onPreviewKeyEvent { event ->
                        val escape = event.key == Key.Escape && event.type == KeyEventType.KeyDown
                        if (escape) onDismiss()
                        escape
                    }
                    .clip(Radius.panelShape)
                    .background(p.panel.compositeOver(p.ground))
                    .border(Space.hair, p.rule, Radius.panelShape),
                contentPadding = PaddingValues(Space.gapTight),
                horizontalArrangement = Arrangement.spacedBy(Space.gap),
            ) {
                itemsIndexed(entries, key = { _, join -> "${join.user}@${join.room}@${join.server}" }) { index, entry ->
                    RecentRoom(entry, onPick, onForget, if (index == 0) firstEntry else null)
                }
            }
        }
    }
}

/** How many times, 60 ms apart, focus tries to enter the first entry of the layer. */
private const val ENTRY_FOCUS_TRIES = 8

/**
 * Places the layer right above its anchor, [gapPx] from it, with the anchor's start edge. When the
 * window has no room above the anchor, the layer goes under it. Each bound has a floor of zero, so
 * a window smaller than the layer cannot make `coerceIn` throw.
 */
internal class AboveAnchor(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val start = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right - popupContentSize.width
        val x = start.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val above = anchorBounds.top - gapPx - popupContentSize.height
        val y = if (above >= 0) above else (anchorBounds.bottom + gapPx).coerceAtMost(maxOf(0, windowSize.height - popupContentSize.height))
        return IntOffset(x, y)
    }
}

/**
 * The recent rooms key at the end of the join row: a history glyph. A tap opens the layer of recent
 * rooms above the join row. While the layer is open, the glyph and the rim take the accent, and a
 * screen reader hears the key as selected.
 */
@Composable
internal fun RecentRoomsKey(open: Boolean, focusRequester: FocusRequester? = null, onToggle: () -> Unit) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    val name = strings.homeRecentTitle
    Box(
        modifier = Modifier
            .size(Space.touchMin)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(Radius.controlShape)
            .background(p.ground)
            .border(Space.hair, if (open) p.accent else p.rule, Radius.controlShape)
            .clickable(interactionSource = source, indication = null, role = Role.Button) { Feedback.tick(); onToggle() }
            .hoverable(source)
            .semantics {
                contentDescription = name
                selected = open
            }
            .controlStates(source, Radius.controlShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.History, contentDescription = null, tint = if (open) p.accent else p.ink, modifier = Modifier.size(Space.glyph))
    }
}

@Composable
private fun RecentRoom(entry: RecentJoin, onPick: (RecentJoin) -> Unit, onForget: (RecentJoin) -> Unit, focusRequester: FocusRequester?) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    val name = strings.homeRecentJoin(entry.room, entry.server)
    Row(
        modifier = Modifier
            .clip(Radius.controlShape)
            .border(Space.hair, p.rule, Radius.controlShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .clickable(interactionSource = source, indication = null, role = Role.Button) { onPick(entry) }
                .semantics(mergeDescendants = true) { contentDescription = name }
                .controlStates(source, Radius.controlShape)
                .pressFeedback(source)
                // The cap grows with the text size, and each line shrinks before it is cut.
                .widthIn(max = 200.dp * LocalDensity.current.fontScale)
                .padding(start = Space.gap, end = Space.gapTight, top = Space.gapTight, bottom = Space.gapTight),
        ) {
            Text(entry.room, style = Type.value, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, autoSize = FontSizeRange(Type.value.fontSize))
            Text(entry.server, style = Type.note, color = p.inkDim, maxLines = 1, overflow = TextOverflow.Ellipsis, autoSize = FontSizeRange(Type.note.fontSize))
        }
        GlyphButton(Icons.Filled.Close, name = strings.homeRecentForget(entry.room), tint = p.inkDim) { onForget(entry) }
    }
}
