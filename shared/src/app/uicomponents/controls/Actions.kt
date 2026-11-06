package app.uicomponents.controls

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.focus.focusRequester
import app.theme.BrandSpan
import app.theme.Motion
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.brandBrush
import app.theme.palette
import app.uicomponents.frames.LocalModalActionEntry

/**
 * The primary action: at least 48dp tall, filled with the brand ramp, with the label in the ramp's
 * ink. While [waiting] is true (the server is starting, the theme is saving), the key rounds into a
 * capsule and ignores clicks. It keeps its focus, so a remote does not lose its place.
 */
@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    waiting: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    // Inside a modal this is the confirming action, where a remote lands when there is no field.
    val entry = LocalModalActionEntry.current
    val wait by animateFloatAsState(if (waiting) 1f else 0f, Motion.move(), label = "waiting")
    val shape = waitingShape(wait)
    val live = enabled && !waiting
    Row(
        modifier = modifier
            .then(entry?.let { Modifier.focusRequester(it) } ?: Modifier)
            .heightIn(min = 48.dp)
            .clip(shape)
            .drawBehind {
                if (enabled) drawRect(brandBrush(p.brandField, size.width, BrandSpan.toPx())) else drawRect(p.disabled)
            }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = {
                if (!waiting) { Feedback.tick(); onClick() }
            })
            .hoverable(source, live)
            .controlStates(source, shape, enabled = enabled, focusRing = p.brandInk, filled = true)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source, live),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            // The label is centred on the whole bar, and the trailing glyph sits over its end.
            ActionLabel(
                text,
                color = if (enabled) p.brandInk else p.ground,
                modifier = Modifier.padding(horizontal = if (trailing != null) Space.touchMin else Space.gutter),
            )
            if (trailing != null) Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
        }
    }
}

/**
 * The confirming action of a panel or a full modal: at least 42dp tall, filled with the accent,
 * with a label colour that reads on that fill.
 */
@Composable
fun AccentAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    // Inside a modal this is the confirming action, where a remote lands when there is no field.
    val entry = LocalModalActionEntry.current
    Box(
        modifier = modifier
            .then(entry?.let { Modifier.focusRequester(it) } ?: Modifier)
            .heightIn(min = Space.row)
            .clip(Radius.controlShape)
            .background(if (enabled) p.accent else p.disabled)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = { Feedback.tick(); onClick() })
            .hoverable(source, enabled)
            .controlStates(source, Radius.controlShape, enabled = enabled, focusRing = p.inkOn(p.accent), filled = true)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source, enabled)
            .padding(horizontal = Space.gutter),
        contentAlignment = Alignment.Center,
    ) {
        ActionLabel(text, color = if (enabled) p.inkOn(p.accent) else p.ground)
    }
}

/** A secondary action: at least 42dp tall, a thin border, label in ink. */
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

/** Two related secondary actions, side by side while each has a readable width, else stacked. */
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

/**
 * A destructive action: the shape of a secondary action, with the border and the label in `bad`.
 * Never a red filled button.
 */
@Composable
fun DestructiveAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    val tone = if (enabled) p.bad else p.disabled
    Box(
        modifier = modifier
            .heightIn(min = Space.row)
            .clip(Radius.controlShape)
            .border(Space.hair, tone.copy(alpha = 0.55f), Radius.controlShape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = { Feedback.tick(); onClick() })
            .hoverable(source, enabled)
            .controlStates(source, Radius.controlShape, enabled = enabled)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source, enabled)
            .padding(horizontal = Space.gutter),
        contentAlignment = Alignment.Center,
    ) {
        ActionLabel(text, color = tone)
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

/**
 * The label of an action. It can wrap and shrink in a narrow button, and larger system text
 * makes the button taller.
 */
@Composable
private fun ActionLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    val style = Type.label.copy(lineHeight = 1.25.em, textAlign = TextAlign.Center)
    val minFontSize = Type.group.fontSize
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        // Measure before choosing the label's height. Foundation autosize in a wrapping button
        // can otherwise size its parent using a different font from the one it finally draws.
        val labelConstraints = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        /* Remembered on everything the answer depends on. Every action in the app runs this.
         * Without remember, it would lay the label out up to nine times per composition, each time
         * with a new style copy and annotated string, for a result that changes only with the text
         * or the box. Nine candidates also overflow the measurer's own cache, so that cache does
         * not help. */
        val fontSize = remember(text, labelConstraints, style, minFontSize, measurer) {
            (0..8).map { lerp(style.fontSize, minFontSize, it / 8f) }.firstOrNull { candidate ->
                val layout = measurer.measure(AnnotatedString(text), style.copy(fontSize = candidate), constraints = labelConstraints)
                layout.lineCount <= 2 && !layout.hasVisualOverflow
            } ?: minFontSize
        }
        // At the smallest readable size, the label wraps to more lines instead of being cut.
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
