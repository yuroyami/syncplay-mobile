package app.uicomponents.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette

/** The one gradient on a screen: 48dp, the brand field, label in the ground colour. */
@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(Radius.controlShape)
            .background(Brush.horizontalGradient(if (enabled) p.brandField else listOf(p.disabled, p.disabled)))
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .hoverable(source, enabled)
            .controlStates(source, Radius.controlShape, enabled = enabled)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source, enabled),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            // The label is centred on the whole bar; the trailing glyph sits over its end.
            Text(
                text,
                style = Type.label,
                color = p.ground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = if (trailing != null) Space.touchMin else Space.gutter),
            )
            if (trailing != null) Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
        }
    }
}

/** The confirming action of a panel or full modal: 42dp, filled with the accent, label in ground. */
@Composable
fun AccentAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(Space.row)
            .clip(Radius.controlShape)
            .background(if (enabled) p.accent else p.disabled)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .hoverable(source, enabled)
            .controlStates(source, Radius.controlShape, enabled = enabled)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source, enabled)
            .padding(horizontal = Space.gutter),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.label, color = p.ground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 42dp, a hairline border, label in ink. */
@Composable
fun SecondaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(Space.row)
            .clip(Radius.controlShape)
            .border(Space.hair, if (enabled) p.rule else p.disabled, Radius.controlShape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .hoverable(source, enabled)
            .controlStates(source, Radius.controlShape, enabled = enabled)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source, enabled)
            .padding(horizontal = Space.gutter),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.label, color = if (enabled) p.ink else p.disabled, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A 2dp stripe in `bad` on the start edge and the label in `bad`. Never a red filled button. */
@Composable
fun DestructiveAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    val stub = if (enabled) p.bad else p.disabled
    // No weighted child here: a weight would stretch this row across the whole action bar.
    Row(
        modifier = modifier
            .height(Space.row)
            .clip(Radius.controlShape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .hoverable(source, enabled)
            .controlStates(source, Radius.controlShape, enabled = enabled)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source, enabled)
            .drawBehind { drawRect(stub, size = Size(2.dp.toPx(), size.height)) },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(start = Space.gutter + 2.dp, end = Space.gutter), contentAlignment = Alignment.Center) {
            Text(text, style = Type.label, color = stub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A 36 x 3dp bar in a 24dp strip, the handle of a draggable sheet. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(36.dp).height(3.dp).clip(Radius.tightShape).background(palette.inkFaint))
    }
}
