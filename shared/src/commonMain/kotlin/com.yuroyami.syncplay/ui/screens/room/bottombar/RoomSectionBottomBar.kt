package com.yuroyami.syncplay.ui.screens.room.bottombar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yuroyami.syncplay.ui.screens.adam.LocalGlobalViewmodel
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel


@Composable
fun RoomBottomBarSection(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val hasVideo by viewmodel.hasVideo.collectAsState()

    Box(modifier) {
        val globalVm = LocalGlobalViewmodel.current
        val stateAddMedia = remember { mutableStateOf(!globalVm.hasEnteredRoomOnce) }

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).zIndex(999f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom
        ) {
            if (hasVideo) {
                //Ready Toggle Button
                RoomReadyButton()

                RoomSeekbar(modifier = Modifier.weight(1f).padding(horizontal = 4.dp))

                RoomControlPanelButton(modifier = Modifier.align(CenterVertically).offset(y = (-1).dp), stateAddMedia)
            }

            RoomMediaAddButton(stateAddMedia)
        }

        if (hasVideo) {
            RoomBottomBarVideoControlRow(
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(2f).padding(bottom = 52.dp)
            )
        }
    }
}
