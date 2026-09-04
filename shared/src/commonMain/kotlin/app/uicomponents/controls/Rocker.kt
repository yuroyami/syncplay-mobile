package app.uicomponents.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.theme.Motion
import app.theme.Radius
import app.theme.Space
import app.theme.palette

/**
 * A hardware rocker: 38 x 20dp, hard edged, the knob sitting left or right. The row's value
 * column already spells the state, so the control only shows which side it is on.
 * Role Switch; [name] is spoken when the rocker stands alone rather than inside a merged row.
 */
@Composable
fun Rocker(
    on: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    name: String? = null,
) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    val knobX by animateDpAsState(if (on) 21.dp else 2.dp, Motion.quick(), label = "knob")
    val fill by animateColorAsState(if (on) p.accent.copy(alpha = 0.28f) else p.trackOff, Motion.quick(), label = "fill")
    val edge by animateColorAsState(if (on) p.accent else p.rule, Motion.quick(), label = "edge")
    val knob by animateColorAsState(if (on) p.accent else p.inkFaint, Motion.quick(), label = "knobColor")

    Box(
        modifier = modifier
            .touchTarget()
            .toggleable(
                value = on,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = source,
                indication = null,
                onValueChange = { Feedback.light(); onChange(it) },
            )
            .hoverable(source, enabled)
            .semantics { if (name != null) contentDescription = name }
            .controlStates(source, Radius.controlShape, enabled = enabled)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source, enabled),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 38.dp, height = 20.dp)
                .clip(Radius.controlShape)
                .background(if (enabled) fill else p.disabled.copy(alpha = 0.12f))
                .border(Space.hair, if (enabled) edge else p.disabled, Radius.controlShape)
        ) {
            Box(
                Modifier
                    .offset(x = knobX, y = 3.dp)
                    .size(width = 15.dp, height = 14.dp)
                    .clip(Radius.tightShape)
                    .background(if (enabled) knob else p.disabled)
            )
        }
    }
}
