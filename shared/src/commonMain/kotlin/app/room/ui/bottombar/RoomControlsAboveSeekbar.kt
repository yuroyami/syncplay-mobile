package app.room.ui.bottombar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.LocalRoomViewmodel
import app.theme.Theming.ROOM_ICON_SIZE
import app.uicomponents.FlexibleIcon

/**
 * Minimal seek row above the seekbar: only FastRewind/FastForward.
 *
 * Chapter-skip and custom-skip buttons were moved out to keep this row compact and prevent
 * it from colliding with the chat area in portrait mode. Chapter skip is reachable from the
 * chapter dropdown in [RoomControlPanelCard]; precise seeks use the seek-to-position popup.
 */
@Composable
fun RoomBottomBarVideoControlRow(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlexibleIcon(
            icon = Icons.Filled.FastRewind,
            size = ROOM_ICON_SIZE + 6,
            shadowColors = listOf(Color.Black)
        ) {
            viewmodel.dispatcher.seekBckwd()
        }

        FlexibleIcon(
            icon = Icons.Filled.FastForward,
            size = ROOM_ICON_SIZE + 6,
            shadowColors = listOf(Color.Black)
        ) {
            viewmodel.dispatcher.seekFrwrd()
        }
    }
}
