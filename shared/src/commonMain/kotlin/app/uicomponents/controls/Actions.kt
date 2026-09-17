package app.uicomponents.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.lerp
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette

/** The primary action: at least 48dp, the brand field, label in the ground colour. */
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
            .heightIn(min = 48.dp)
            .clip(Radius.controlShape)
            .background(Brush.horizontalGradient(if (enabled) p.brandField else listOf(p.disabled, p.disabled)))
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = { Feedback.tick(); onClick() })
            .hoverable(source, enabled)
            .controlStates(source, Radius.controlShape, enabled = enabled)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source, enabled),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            // The label is centred on the whole bar; the trailing glyph sits over its end.
            ActionLabel(
                text,
                color = p.ground,
                modifier = Modifier.padding(horizontal = if (trailing != null) Space.touchMin else Space.gutter),
            )
            if (trailing != null) Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
        }
    }
}

/** The confirming action of a panel or full modal: at least 42dp, accent fill, label read off that fill. */
@Composable
fun AccentAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .heightIn(min = Space.row)
            .clip(Radius.controlShape)
            .background(if (enabled) p.accent else p.disabled)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = { Feedback.tick(); onClick() })
            .hoverable(source, enabled)
            .controlStates(source, Radius.controlShape, enabled = enabled)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source, enabled)
            .padding(horizontal = Space.gutter),
        contentAlignment = Alignment.Center,
    ) {
        ActionLabel(text, color = if (enabled) p.inkOn(p.accent) else p.ground)
    }
}

/** At least 42dp, a hairline border, label in ink. */
@Composable
fun SecondaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .heightIn(min = Space.row)
            .clip(Radius.controlShape)
            .border(Space.hair, if (enabled) p.rule else p.disabled, Radius.controlShape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = { Feedback.tick(); onClick() })
            .hoverable(source, enabled)
            .controlStates(source, Radius.controlShape, enabled = enabled)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source, enabled)
            .padding(horizontal = Space.gutter),
        contentAlignment = Alignment.Center,
    ) {
        ActionLabel(text, color = if (enabled) p.ink else p.disabled)
    }
}

/** Related links stay beside each other only while each has a readable width. */
@Composable
fun SecondaryActionPair(
    firstText: String,
    onFirstClick: () -> Unit,
    secondText: String,
    onSecondClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val minimumPairWidth = 288.dp * LocalDensity.current.fontScale + Space.gap
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < minimumPairWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.gapTight)) {
                SecondaryAction(firstText, onFirstClick, Modifier.fillMaxWidth())
                SecondaryAction(secondText, onSecondClick, Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.gap)) {
                SecondaryAction(firstText, onFirstClick, Modifier.weight(1f))
                SecondaryAction(secondText, onSecondClick, Modifier.weight(1f))
            }
        }
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
            .heightIn(min = Space.row)
            .clip(Radius.controlShape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = { Feedback.tick(); onClick() })
            .hoverable(source, enabled)
            .controlStates(source, Radius.controlShape, enabled = enabled)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source, enabled)
            .drawBehind { drawRect(stub, size = Size(2.dp.toPx(), size.height)) },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(start = Space.gutter + 2.dp, end = Space.gutter), contentAlignment = Alignment.Center) {
            ActionLabel(text, color = stub)
        }
    }
}

/** A completed result in the action area, with no click target or button semantics. */
@Composable
fun ActionStatus(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = Space.row)
            .clip(Radius.controlShape)
            .background(color.copy(alpha = 0.12f))
            .border(Space.hair, color, Radius.controlShape)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = Space.gutter),
        contentAlignment = Alignment.Center,
    ) {
        ActionLabel(text, color = color)
    }
}

/** Labels can wrap and shrink in narrow buttons; larger system text can also grow the button. */
@Composable
private fun ActionLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    val style = Type.label.copy(lineHeight = 1.25.em, textAlign = TextAlign.Center)
    val minFontSize = Type.group.fontSize
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        // Measure before choosing the label's height. Foundation autosize in a wrapping button
        // can otherwise size its parent using a different font from the one it finally draws.
        val labelConstraints = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        /* Remembered on everything the answer depends on. Every action in the app runs this, and
         * unremembered it laid the label out up to nine times per composition, each with its own
         * style copy and annotated string, for a result that only moves when the text or the box
         * does. Nine candidates also overflow the measurer's own cache, so nothing there caught it. */
        val fontSize = remember(text, labelConstraints, style, minFontSize, measurer) {
            (0..8).map { lerp(style.fontSize, minFontSize, it / 8f) }.firstOrNull { candidate ->
                val layout = measurer.measure(AnnotatedString(text), style.copy(fontSize = candidate), constraints = labelConstraints)
                layout.lineCount <= 2 && !layout.hasVisualOverflow
            } ?: minFontSize
        }
        // At the readable floor, wrapping further is preferable to truncating the action.
        Text(text, color = color, style = style.copy(fontSize = fontSize))
    }
}

/** A 36 x 3dp bar in a 24dp strip, the handle of a draggable sheet. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(36.dp).height(3.dp).clip(Radius.tightShape).background(palette.inkFaint))
    }
}
