package app.uicomponents.controls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import app.uicomponents.controls.Icon
import app.uicomponents.controls.Text
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import app.theme.Type
import app.uicomponents.chromeSurface
import app.utils.Platform
import app.utils.platform
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = Space.glyph,
    tint: Color = palette.ink,
    target: Dp = Space.touchMin,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    // Desktop only: the name under the glyph after 600 ms of hover. Touch never hovers.
    val hovered by source.collectIsHoveredAsState()
    var tooltip by remember { mutableStateOf(false) }
    LaunchedEffect(hovered) {
        tooltip = false
        if (hovered && platform == Platform.Desktop) {
            delay(600)
            tooltip = true
        }
    }
    val targetPx = with(LocalDensity.current) { target.roundToPx() }
    Box(
        modifier = modifier
            .size(target)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .hoverable(source, enabled)
            .semantics { contentDescription = name }
            .controlStates(source, Radius.controlShape, enabled = enabled)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source, enabled),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else p.disabled, modifier = Modifier.size(size))
        if (tooltip) {
            Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, targetPx + 4)) {
                Text(name, style = Type.note, color = Color.White, modifier = Modifier.chromeSurface(Radius.controlShape).padding(horizontal = Space.gapTight + 2.dp, vertical = 2.dp))
            }
        }
    }
}
