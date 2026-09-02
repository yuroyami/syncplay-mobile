package app.room.ui.bottombar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.LocalRoomViewmodel


@Composable
fun RoomBottomBarSection(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val hasVideo by viewmodel.hasVideo.collectAsState()

    Box(modifier.windowInsetsPadding(WindowInsets.safeGestures)) {
        val stateAddMedia = remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .zIndex(999f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasVideo) {
                RoomReadyButton()

                RoomSeekbar(modifier = Modifier.weight(1f).padding(horizontal = 4.dp))

                RoomControlPanelButton(modifier = Modifier, stateAddMedia)
            }

            RoomMediaAddButton(stateAddMedia)
        }
    }
}
