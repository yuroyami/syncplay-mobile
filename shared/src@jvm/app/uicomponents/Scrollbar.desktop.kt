package app.uicomponents

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import app.theme.Motion
import app.theme.palette

@Composable
internal actual fun BoxScope.ScrollbarFor(state: ScrollState, pointerOver: Boolean) =
    DrawnScrollbar(rememberScrollbarAdapter(state), state.maxValue > 0, state.isScrollInProgress, pointerOver)

@Composable
internal actual fun BoxScope.ScrollbarFor(state: LazyListState, pointerOver: Boolean) =
    DrawnScrollbar(rememberScrollbarAdapter(state), state.canScrollBackward || state.canScrollForward, state.isScrollInProgress, pointerOver)

@Composable
internal actual fun BoxScope.ScrollbarFor(state: LazyGridState, pointerOver: Boolean) =
    DrawnScrollbar(rememberScrollbarAdapter(state), state.canScrollBackward || state.canScrollForward, state.isScrollInProgress, pointerOver)

/**
 * The bar in the style of the drawn glyphs: a thin ink line with square ends. It fades in while
 * the content scrolls, while a pointer is over the container, and while the bar itself is hovered
 * or dragged. Content that fits gets no bar at all.
 */
@Composable
private fun BoxScope.DrawnScrollbar(adapter: ScrollbarAdapter, overflows: Boolean, scrolling: Boolean, pointerOver: Boolean) {
    if (!overflows) return
    val p = palette
    val interaction = remember { MutableInteractionSource() }
    val barHovered by interaction.collectIsHoveredAsState()
    val barDragged by interaction.collectIsDraggedAsState()
    val alpha by animateFloatAsState(if (scrolling || pointerOver || barHovered || barDragged) 1f else 0f, Motion.quick())
    // A box that matches the container, so the bar never changes the container's size.
    Box(Modifier.matchParentSize()) {
        VerticalScrollbar(
            adapter = adapter,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 2.dp).alpha(alpha),
            style = ScrollbarStyle(
                minimalHeight = 24.dp,
                thickness = 4.dp,
                shape = RectangleShape,
                hoverDurationMillis = 150,
                unhoverColor = p.inkDim,
                hoverColor = p.ink,
            ),
            interactionSource = interaction,
        )
    }
}
