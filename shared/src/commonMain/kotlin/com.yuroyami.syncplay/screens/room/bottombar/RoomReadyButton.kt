package com.yuroyami.syncplay.screens.room.bottombar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.protocol.sending.Packet
import com.yuroyami.syncplay.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.ui.Paletting

@Composable
fun RowScope.RoomReadyButton() {
    val viewmodel = LocalViewmodel.current

    if (!viewmodel.isSoloMode) {
        var ready by remember { mutableStateOf(viewmodel.setReadyDirectly) }


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
                viewmodel.p.ready = b
                viewmodel.p.send<Packet.Readiness> {
                    isReady = b
                    manuallyInitiated = true
                }
            }) {
            when (ready) {
                true -> Row(verticalAlignment = CenterVertically) {
                    Icon(
                        modifier = Modifier.size(Paletting.USER_INFO_IC_SIZE.dp),
                        imageVector = if (ready) Icons.Filled.Check else Icons.Filled.Clear,
                        contentDescription = "",
                        tint = Paletting.ROOM_USER_READY_ICON


                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Ready", fontSize = 14.sp)
                    Spacer(Modifier.width(4.dp))

                }

                false -> Row(verticalAlignment = CenterVertically) {
                    Icon(
                        modifier = Modifier.size(Paletting.USER_INFO_IC_SIZE.dp),
                        imageVector = if (ready) Icons.Filled.Check else Icons.Filled.Clear,
                        contentDescription = "",
                        tint = Paletting.ROOM_USER_UNREADY_ICON

                    )
                    Spacer(Modifier.width(4.dp))

                    Text("Not Ready", fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))

                }
            }
        }
    }
}