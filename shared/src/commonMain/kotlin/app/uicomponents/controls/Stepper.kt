package app.uicomponents.controls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette

/**
 * `‹ value ›` in place, for option sets under five. Changing a choice never opens anything.
 * Role Slider with discrete steps; the current option is spoken by name. Left and right keys
 * step it; the arrows carry 48dp targets around 14dp chevrons.
 */
@Composable
fun Stepper(
    options: List<String>,
    index: Int,
    onIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    wrap: Boolean = false,
    name: String? = null,
) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    val last = options.lastIndex
    val current = index.coerceIn(0, maxOf(0, last))
    val latestIndex by rememberUpdatedState(onIndex)

    fun step(delta: Int) {
        if (options.isEmpty()) return
        val next = when {
            wrap -> ((current + delta) % options.size + options.size) % options.size
            else -> (current + delta).coerceIn(0, last)
        }
        if (next != current) { Feedback.tick(); latestIndex(next) }
    }

    val canBack = enabled && options.isNotEmpty() && (wrap || current > 0)
    val canForward = enabled && options.isNotEmpty() && (wrap || current < last)

    Row(
        modifier = modifier
            .width(Space.valueCol + 32.dp * 2)
            .height(Space.row)
            .progressSemantics(current.toFloat(), 0f..maxOf(0, last).toFloat(), maxOf(0, last - 1))
            .semantics {
                stateDescription = options.getOrNull(current) ?: ""
                if (name != null) contentDescription = name
                setProgress { target -> latestIndex(target.toInt().coerceIn(0, last)); true }
            }
            .focusable(enabled, source)
            .hoverable(source, enabled)
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { step(-1); true }
                    Key.DirectionRight -> { step(1); true }
                    else -> false
                }
            }
            .controlStates(source, Radius.controlShape, enabled = enabled),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperArrow(ChevronDirection.Left, canBack) { step(-1) }
        Text(
            text = options.getOrNull(current) ?: "",
            style = Type.value,
            color = if (enabled) p.accent else p.disabled,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        StepperArrow(ChevronDirection.Right, canForward) { step(1) }
    }
}

@Composable
private fun StepperArrow(direction: ChevronDirection, enabled: Boolean, onClick: () -> Unit) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .width(32.dp)
            .height(Space.row)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (direction == ChevronDirection.Left) "Previous" else "Next" }
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source, enabled),
        contentAlignment = Alignment.Center,
    ) {
        Chevron(direction, color = if (enabled) p.inkDim else p.disabled)
    }
}
