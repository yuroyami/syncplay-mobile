package app.uicomponents

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable

// No bar on this platform: see Scrollbar.kt.

@Composable
internal actual fun BoxScope.ScrollbarFor(state: ScrollState, pointerOver: Boolean) = Unit

@Composable
internal actual fun BoxScope.ScrollbarFor(state: LazyListState, pointerOver: Boolean) = Unit

@Composable
internal actual fun BoxScope.ScrollbarFor(state: LazyGridState, pointerOver: Boolean) = Unit
