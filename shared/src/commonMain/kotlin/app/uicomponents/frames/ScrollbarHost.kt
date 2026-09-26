package app.uicomponents.frames

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.uicomponents.ScrollbarFor

/**
 * Holds a scrolling container, and on desktop a scrollbar over its end edge. The bar shows while
 * the content scrolls and while a mouse pointer is over the container, and a mouse can drag it.
 * Touch and TV platforms get no bar.
 *
 * [modifier] takes the size that the container had before, and the container goes in [content].
 */
@Composable
fun ScrollbarHost(state: ScrollState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) =
    HoverBox(modifier, content) { pointerOver -> ScrollbarFor(state, pointerOver) }

/** [ScrollbarHost] for a lazy list. */
@Composable
fun ScrollbarHost(state: LazyListState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) =
    HoverBox(modifier, content) { pointerOver -> ScrollbarFor(state, pointerOver) }

/** [ScrollbarHost] for a lazy grid. */
@Composable
fun ScrollbarHost(state: LazyGridState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) =
    HoverBox(modifier, content) { pointerOver -> ScrollbarFor(state, pointerOver) }

/** A box that knows whether a mouse pointer is over it, and draws [bar] above [content]. */
@Composable
private fun HoverBox(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
    bar: @Composable BoxScope.(pointerOver: Boolean) -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val pointerOver by hover.collectIsHoveredAsState()
    Box(modifier.hoverable(hover)) {
        content()
        bar(pointerOver)
    }
}
