package app.uicomponents.controls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.theme.Radius
import app.theme.Space
import app.theme.palette

/**
 * A glyph in a 48dp target, no background at rest, press feedback only. [name] is required:
 * a glyph without a spoken name cannot be written. It is also the desktop tooltip text.
 */
@Composable
fun GlyphButton(
    icon: ImageVector,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = Space.glyph,
    tint: Color = palette.ink,
    target: Dp = Space.touchMin,
    focusRequester: FocusRequester? = null,
) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(target)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .hoverable(source, enabled)
            .semantics { contentDescription = name }
            .controlStates(source, Radius.controlShape, enabled = enabled)
            .pressFeedback(source, enabled),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else p.disabled, modifier = Modifier.size(size))
    }
}
