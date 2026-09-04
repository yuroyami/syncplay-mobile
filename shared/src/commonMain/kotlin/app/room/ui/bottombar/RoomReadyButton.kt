package app.room.ui.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.LocalRoomViewmodel
import app.protocol.WireMessage
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.Text
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.pressFeedback
import app.uicomponents.controls.touchTarget
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_not_ready
import syncplaymobile.shared.generated.resources.room_ready

/**
 * Readiness as a 36dp cell in the transport: a 6dp square in the readiness colour and the state
 * word, hairline when not ready, green-tinted when ready. Absent in solo mode.
 */
@Composable
fun RoomReadyButton() {
    val viewmodel = LocalRoomViewmodel.current
    if (viewmodel.isSoloMode) return

    var ready by remember { viewmodel.session.ready }
    val p = palette
    val source = remember { MutableInteractionSource() }
    // One width for both words, so flipping the state never reshapes the seekbar beside it.
    val readyLabel = stringResource(Res.string.room_ready)
    val notReadyLabel = stringResource(Res.string.room_not_ready)
    val measurer = rememberTextMeasurer()
    val labelStyle = Type.label
    val cellWidth = with(LocalDensity.current) {
        maxOf(measurer.measure(readyLabel, labelStyle).size.width, measurer.measure(notReadyLabel, labelStyle).size.width).toDp()
    } + Space.gap * 2 + 6.dp + Space.gapTight + 2.dp

    Row(
        modifier = Modifier
            .padding(horizontal = Space.gapTight)
            .toggleable(value = ready, role = Role.Button, interactionSource = source, indication = null) { b ->
                Feedback.light()
                ready = b
                viewmodel.session.ready.value = b
                viewmodel.networkManager.sendAsync(WireMessage.readiness(isReady = b, manuallyInitiated = true))
            }
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source)
            .touchTarget()
            .height(Space.rowCompact)
            .width(cellWidth)
            .clip(Radius.controlShape)
            .background(if (ready) p.ok.copy(alpha = 0.18f) else Color.Transparent)
            .border(Space.hair, if (ready) p.ok else p.rule, Radius.controlShape)
            .controlStates(source, Radius.controlShape)
            .padding(horizontal = Space.gap),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(if (ready) p.ok else p.bad, Radius.tightShape))
        RowGap(Space.gapTight + 2.dp)
        Text(
            text = if (ready) readyLabel else notReadyLabel,
            style = Type.label,
            color = p.ink,
            maxLines = 1,
        )
    }
}
