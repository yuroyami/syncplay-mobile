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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            .pressFeedback(source, enabled),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(horizontal = Space.gutter), contentAlignment = Alignment.Center) {
            Text(text, style = Type.label, color = p.ground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing()
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
    Row(
        modifier = modifier
            .height(Space.row)
            .clip(Radius.controlShape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .hoverable(source, enabled)
            .controlStates(source, Radius.controlShape, enabled = enabled)
            .pressFeedback(source, enabled),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).height(Space.row).background(if (enabled) p.bad else p.disabled))
        Box(Modifier.weight(1f).padding(horizontal = Space.gutter), contentAlignment = Alignment.CenterStart) {
            Text(text, style = Type.label, color = if (enabled) p.bad else p.disabled, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
