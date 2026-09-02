package app.uicomponents.controls

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.chromeSurface
import app.utils.Platform
import app.utils.platform
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.help_tip
import kotlinx.coroutines.delay

/**
 * A question mark in an 18dp hairline square, sitting inline after a label. A tap opens a card on
 * the chrome tier under it with the explanation; a tap anywhere else closes it. On desktop a
 * hover opens it after a moment. The card is the only place the long words live, so the form
 * stays quiet.
 */
@Composable
fun HelpTip(text: String, modifier: Modifier = Modifier) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    var open by remember { mutableStateOf(false) }
    val name = stringResource(Res.string.help_tip)
    LaunchedEffect(hovered) {
        if (hovered && platform == Platform.Desktop) {
            delay(400)
            open = true
        }
    }
    val below = with(LocalDensity.current) { 26.dp.roundToPx() }

    Box(
        modifier = modifier
            .size(Space.glyph)
            .clickable(interactionSource = source, indication = null, role = Role.Button) { open = !open }
            .hoverable(source)
            .semantics { contentDescription = name }
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(18.dp).border(Space.hair, if (open) p.accent else p.rule, Radius.tightShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("?", style = Type.group, color = if (open) p.accent else p.inkDim)
        }
        if (open) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, below),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Box(Modifier.widthIn(max = 300.dp).chromeSurface(Radius.panelShape).padding(Space.gap)) {
                    Text(text, style = Type.note, color = Color.White)
                }
            }
        }
    }
}
