package app.uicomponents

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable

/*
 * A scrollbar over the end edge of the enclosing box, for the scrolling container in that box.
 * Only desktop draws one: it shows while the content scrolls and while pointerOver is true, and a
 * mouse can drag it. Touch and TV platforms draw nothing, because a finger or a remote needs no bar.
 * Callers use app.uicomponents.frames.ScrollbarHost, which supplies the box and pointerOver.
 */

@Composable
internal expect fun BoxScope.ScrollbarFor(state: ScrollState, pointerOver: Boolean)

@Composable
internal expect fun BoxScope.ScrollbarFor(state: LazyListState, pointerOver: Boolean)

@Composable
internal expect fun BoxScope.ScrollbarFor(state: LazyGridState, pointerOver: Boolean)
