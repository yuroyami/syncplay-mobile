package app.uicomponents.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.theme.Motion
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette

/**
 * Two to four hairline cells in one frame. The active cell fills with the accent at 16 percent
 * and carries a 2dp accent bottom edge. A selectable group for screen readers.
 */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = Space.rowCompact,
    autoSize: Boolean = false,
) {
    val p = palette
    Row(
        modifier = modifier
            .height(height)
            .clip(Radius.controlShape)
            .border(Space.hair, if (enabled) p.rule else p.disabled, Radius.controlShape)
            .selectableGroup(),
    ) {
        options.forEachIndexed { i, label ->
            if (i > 0) VerticalRule(color = if (enabled) p.rule else p.disabled)
            SegmentedCell(
                label = label,
                active = i == selected,
                enabled = enabled,
                autoSize = autoSize,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { if (i != selected) { Feedback.tick(); onSelect(i) } },
            )
        }
    }
}

@Composable
private fun SegmentedCell(label: String, active: Boolean, enabled: Boolean, autoSize: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    val fill by animateColorAsState(if (active) p.accent.copy(alpha = 0.16f) else p.accent.copy(alpha = 0f), Motion.quick(), label = "cell")
    val edge by animateColorAsState(if (active) p.accent else p.accent.copy(alpha = 0f), Motion.quick(), label = "edge")
    Box(
        modifier = modifier
            .selectable(selected = active, enabled = enabled, role = Role.RadioButton, interactionSource = source, indication = null, onClick = onClick)
            .hoverable(source, enabled)
            .drawBehind {
                drawRect(fill)
                val w = 2.dp.toPx()
                drawRect(edge, Offset(0f, size.height - w), Size(size.width, w))
            }
            .controlStates(source, Radius.controlShape, enabled = enabled)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source, enabled),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = Type.value,
            color = when {
                !enabled -> p.disabled
                active -> p.ink
                else -> p.inkDim
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Between the group and value sizes, in one-sp steps, when a label has to fit.
            autoSize = if (autoSize) TextAutoSize.StepBased(minFontSize = Type.group.fontSize, maxFontSize = Type.value.fontSize, stepSize = Type.group.fontSize / 11) else null,
        )
    }
}
