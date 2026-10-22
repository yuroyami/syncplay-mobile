package app.room

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import app.utils.hiddenPointerIcon
import kotlinx.coroutines.flow.update

/**
 * The mouse pointer in the room. A move shows the controls and restarts their idle timer. While
 * [hidden] is true, the pointer shows nothing, on the platforms that have [hiddenPointerIcon].
 */
internal fun Modifier.roomPointer(ui: RoomUiStateManager, hidden: Boolean): Modifier {
    val icon = hiddenPointerIcon
    return pointerInput(ui) {
        awaitPointerEventScope {
            var last: Offset? = null
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                // Only a mouse hovers, so a finger that drags never counts.
                val mouse = event.changes.firstOrNull { it.type == PointerType.Mouse } ?: continue
                /* A hover event carries no previous position, so the last one is kept here. A move
                 * to the same place is not the person: the scene sends one when the layout under a
                 * still pointer changes, and that must not bring back the controls it just hid. */
                if (event.type == PointerEventType.Move && last != null && mouse.position != last) ui.notePointerMoved()
                last = mouse.position
            }
        }
    }.then(if (hidden && icon != null) Modifier.pointerHoverIcon(icon, overrideDescendants = true) else Modifier)
}

/** Holds the controls on screen while a mouse pointer rests on this area. */
internal fun Modifier.holdsHudWhileHovered(ui: RoomUiStateManager): Modifier = pointerInput(ui) {
    var inside = false
    try {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.none { it.type == PointerType.Mouse }) continue
                val entered = when (event.type) {
                    PointerEventType.Enter -> true
                    PointerEventType.Exit -> false
                    else -> continue
                }
                if (entered != inside) {
                    inside = entered
                    ui.hoveredControls.update { if (entered) it + 1 else it - 1 }
                }
            }
        }
    } finally {
        // The area can leave the screen under a resting pointer, and then no Exit arrives.
        if (inside) ui.hoveredControls.update { it - 1 }
    }
}
