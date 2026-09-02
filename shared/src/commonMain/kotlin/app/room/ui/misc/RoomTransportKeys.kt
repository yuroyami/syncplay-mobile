package app.room.ui.misc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.LocalRoomViewmodel
import app.preferences.Preferences.SEEK_BACKWARD_JUMP
import app.preferences.Preferences.SEEK_FORWARD_JUMP
import app.preferences.watchPref
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.chromeSurface
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.Icon
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.Text
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.pressFeedback
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_jump_back
import syncplaymobile.shared.generated.resources.room_jump_forward

/**
 * The transport at the centre of the video: the play key on the exact centre, the two jump keys
 * under it. The top padding equals the jump row plus its gap, so the column's centre stays on
 * the play key.
 */
@Composable
fun RoomTransportKeys(modifier: Modifier = Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val hasVideo by viewmodel.hasVideo.collectAsState()
    if (!hasVideo) return
    val back by SEEK_BACKWARD_JUMP.watchPref()
    val forward by SEEK_FORWARD_JUMP.watchPref()

    Column(modifier.padding(top = Space.row + Space.gap), horizontalAlignment = Alignment.CenterHorizontally) {
        RoomPlayButton(modifier = Modifier)
        Spacer(Modifier.height(Space.gap))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.gapTight), verticalAlignment = Alignment.CenterVertically) {
            JumpKey(Icons.Filled.FastRewind, stringResource(Res.string.room_jump_back, back), "$back s") {
                viewmodel.dispatcher.seekBckwd()
            }
            JumpKey(Icons.Filled.FastForward, stringResource(Res.string.room_jump_forward, forward), "$forward s") {
                viewmodel.dispatcher.seekFrwrd()
            }
        }
    }
}

/** A 42dp chrome cell: the glyph and the jump amount, so the amount is visible without settings. */
@Composable
private fun JumpKey(icon: ImageVector, name: String, amount: String, onClick: () -> Unit) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .height(Space.row)
            .chromeSurface(Radius.controlShape)
            .clip(Radius.controlShape)
            .clickable(interactionSource = source, indication = null, role = Role.Button) { Feedback.tick(); onClick() }
            .hoverable(source)
            .semantics { contentDescription = name }
            .controlStates(source, Radius.controlShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source)
            .padding(horizontal = Space.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = p.ink, modifier = Modifier.size(Space.glyph))
        RowGap(Space.gapTight)
        Text(amount, style = Type.value, color = p.inkDim, maxLines = 1)
    }
}
