package app.uicomponents.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette

enum class Tone { Neutral, Accent, Ok, Warn, Bad }

/**
 * A tag: a rectangle with a thin border that holds a state word or a badge, at least 22dp tall,
 * in `value` type. It is [filled] when the state is on (ready, connected). With [onToggle] it
 * becomes a toggle with a 48dp target. [autoSize] lets a word in a narrow cell shrink, down to the
 * auto-size floor, before it is cut.
 */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Neutral,
    filled: Boolean = false,
    onToggle: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    autoSize: Boolean = false,
) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    val color: Color = when (tone) {
        Tone.Neutral -> p.inkDim
        Tone.Accent -> p.accent
        Tone.Ok -> p.ok
        Tone.Warn -> p.warn
        Tone.Bad -> p.bad
    }.let { if (enabled) it else p.disabled }
    val edge = if (tone == Tone.Neutral) p.rule else color

    Box(
        modifier = modifier
            .then(if (onToggle != null) Modifier.touchTarget() else Modifier)
            .then(
                if (onToggle != null) Modifier
                    .toggleable(value = filled, enabled = enabled, role = Role.Button, interactionSource = source, indication = null) {
                        Feedback.light(); onToggle(it)
                    }
                    .hoverable(source, enabled)
                    .controlStates(source, Radius.controlShape, enabled = enabled)
                    .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source, enabled)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                // A minimum: bigger system text makes the tag taller, not clipped.
                .heightIn(min = 22.dp)
                .clip(Radius.controlShape)
                .background(if (filled) color else Color.Transparent)
                .border(Space.hair, edge, Radius.controlShape)
                .padding(horizontal = Space.gapTight + 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = Type.value,
                color = if (filled) p.inkOn(color) else color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = if (autoSize) FontSizeRange(Type.value.fontSize) else null,
            )
        }
    }
}
