package app.uicomponents.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.theme.Radius
import app.theme.Space
import app.theme.palette

/** A 22 x 16dp rectangle with a hairline. A rectangle, because a circle is chip language. */
@Composable
fun Swatch(
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    name: String? = null,
) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    val spoken = name ?: color.hex()
    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.touchTarget() else Modifier)
            .then(
                if (onClick != null) Modifier
                    .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                    .hoverable(source, enabled)
                    .controlStates(source, Radius.controlShape, enabled = enabled)
                    .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source, enabled)
                else Modifier
            )
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 22.dp, height = 16.dp)
                .clip(Radius.tightShape)
                .background(color)
                .border(Space.hair, p.rule, Radius.tightShape)
        )
    }
}

/** `#RRGGBB`, or `#AARRGGBB` when the colour is not opaque. */
fun Color.hex(): String {
    val argb = toArgb()
    val rgb = argb and 0xFFFFFF
    return if (alpha >= 0.999f) "#" + rgb.toString(16).uppercase().padStart(6, '0')
    else "#" + argb.toUInt().toString(16).uppercase().padStart(8, '0')
}
