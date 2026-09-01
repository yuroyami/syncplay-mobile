package app.room.ui.bottombar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.LocalRoomViewmodel
import app.preferences.Preferences.SEEK_BACKWARD_JUMP
import app.preferences.Preferences.SEEK_FORWARD_JUMP
import app.preferences.watchPref
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.GlyphButton
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_jump_back
import syncplaymobile.shared.generated.resources.room_jump_forward

/** Rewind and forward, each showing its jump amount so it is visible without opening settings. */
@Composable
fun RoomBottomBarVideoControlRow(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val back by SEEK_BACKWARD_JUMP.watchPref()
    val forward by SEEK_FORWARD_JUMP.watchPref()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        JumpButton(Icons.Filled.FastRewind, stringResource(Res.string.room_jump_back, back), "$back s") {
            viewmodel.dispatcher.seekBckwd()
        }
        Spacer(Modifier.width(Space.gutter))
        JumpButton(Icons.Filled.FastForward, stringResource(Res.string.room_jump_forward, forward), "$forward s") {
            viewmodel.dispatcher.seekFrwrd()
        }
    }
}

@Composable
private fun JumpButton(icon: ImageVector, name: String, amount: String, onClick: () -> Unit) {
    val p = palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlyphButton(icon, name = name, onClick = onClick, size = Space.glyphLarge, tint = p.ink)
        Text(amount, style = Type.value, color = p.inkDim, maxLines = 1)
    }
}
