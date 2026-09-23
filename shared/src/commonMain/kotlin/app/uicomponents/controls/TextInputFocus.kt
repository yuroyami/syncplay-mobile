package app.uicomponents.controls

import kotlinx.atomicfu.atomic

/**
 * Counts the text fields that hold focus, which in practice is zero or one.
 *
 * The desktop window must handle the arrow keys before Compose uses them for focus movement, and a
 * preview key handler at that level cannot see what has focus. Every [Field] reports itself here,
 * so the window leaves the arrow keys alone while the user types.
 */
object TextInputFocus {

    private val focusedFields = atomic(0)

    val isTyping: Boolean get() = focusedFields.value > 0

    internal fun report(focused: Boolean) {
        if (focused) focusedFields.incrementAndGet() else focusedFields.decrementAndGet()
    }
}
