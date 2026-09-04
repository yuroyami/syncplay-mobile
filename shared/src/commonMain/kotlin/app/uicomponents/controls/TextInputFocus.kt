package app.uicomponents.controls

import kotlinx.atomicfu.atomic

/**
 * How many text fields currently hold focus, which in practice is zero or one.
 *
 * The desktop window has to answer the arrow keys BEFORE Compose spends them on focus movement,
 * and a preview handler at that level cannot see what has focus. Every [Field] reports itself
 * here, so the window can leave the arrows alone while someone is typing.
 */
object TextInputFocus {

    private val focusedFields = atomic(0)

    val isTyping: Boolean get() = focusedFields.value > 0

    internal fun report(focused: Boolean) {
        if (focused) focusedFields.incrementAndGet() else focusedFields.decrementAndGet()
    }
}
