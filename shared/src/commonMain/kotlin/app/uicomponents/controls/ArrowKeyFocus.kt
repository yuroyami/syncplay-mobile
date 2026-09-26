package app.uicomponents.controls

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import kotlinx.atomicfu.atomic

/**
 * Counts the focused controls that use the arrow keys themselves: text fields, sliders and
 * steppers. In practice the count is zero or one.
 *
 * The desktop window must handle the arrow keys before Compose uses them for focus movement, and a
 * preview key handler at that level cannot see what has focus. Each such control reports itself
 * here, so the window leaves the arrow keys to it.
 */
object ArrowKeyFocus {

    private val focusedControls = atomic(0)

    /** True while a control that uses the arrow keys has focus. */
    val isClaimed: Boolean get() = focusedControls.value > 0

    internal fun report(focused: Boolean) {
        if (focused) focusedControls.incrementAndGet() else focusedControls.decrementAndGet()
    }
}

/** Reports a control to [ArrowKeyFocus] while [source] says that it has focus. */
@Composable
internal fun ReportArrowKeyFocus(source: InteractionSource) {
    val focused by source.collectIsFocusedAsState()
    ReportArrowKeyFocus(focused)
}

/** Reports a control to [ArrowKeyFocus] while [focused] is true. */
@Composable
internal fun ReportArrowKeyFocus(focused: Boolean) {
    DisposableEffect(focused) {
        if (focused) ArrowKeyFocus.report(true)
        onDispose { if (focused) ArrowKeyFocus.report(false) }
    }
}
