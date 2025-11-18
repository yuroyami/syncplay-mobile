package com.yuroyami.syncplay.ui.screens.room.bottombar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.managers.protocol.creator.PacketOut
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.screens.theme.Theming

@Composable
fun RoomReadyButton() {
    val viewmodel = LocalRoomViewmodel.current

    if (!viewmodel.isSoloMode) {
        var ready by remember { viewmodel.session.ready }

        IconToggleButton(
            modifier = Modifier.width(112.dp).padding(4.dp),
            checked = ready,
            colors = IconButtonDefaults.iconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = Color.Black
            ),
            onCheckedChange = { b ->
                ready = b
                viewmodel.sessionManager.session.ready.value = b
                viewmodel.networkManager.sendAsync<PacketOut.Readiness> {
                    isReady = b
                    manuallyInitiated = true
                }
            }) {
            when (ready) {
                true -> Row(verticalAlignment = CenterVertically) {
                    Icon(
                        modifier = Modifier.size(Theming.USER_INFO_IC_SIZE.dp),
                        imageVector = if (ready) Icons.Filled.Check else Icons.Filled.Clear,
                        contentDescription = "",
                        tint = Theming.ROOM_USER_READY_ICON


                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Ready", fontSize = 14.sp) //TODO Localize
                    Spacer(Modifier.width(4.dp))

                }

                false -> Row(verticalAlignment = CenterVertically) {
                    Icon(
                        modifier = Modifier.size(Theming.USER_INFO_IC_SIZE.dp),
                        imageVector = if (ready) Icons.Filled.Check else Icons.Filled.Clear,
                        contentDescription = "",
                        tint = Theming.ROOM_USER_UNREADY_ICON

                    )
                    Spacer(Modifier.width(4.dp))

                    Text("Not Ready", fontSize = 13.sp) //TODO Localize
                    Spacer(Modifier.width(4.dp))

                }
            }
        }
    }
}