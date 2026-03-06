package com.yuroyami.app.room.ui.statinfo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalRoomViewmodel
import app.protocol.models.PingService.Companion.ConnectionState
import app.theme.Theming
import app.uicomponents.sairaFont
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_details_current_room
import syncplaymobile.shared.generated.resources.room_ping_connected
import syncplaymobile.shared.generated.resources.room_ping_disconnected


@Composable
fun RoomStatusInfoSection(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current

    /* Top-Center info: Overall info (PING + ROOMNAME + OSD Messages) */
    //TODO if (!pipModeObserver) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
        ) {
            if (!viewmodel.isSoloMode) {
                val connectionState by viewmodel.protocolManager.pingService.connectionState.collectAsState(ConnectionState.Disconnected)

                Row(verticalAlignment = CenterVertically) {
                    Text(
                        text = when (connectionState) {
                            is ConnectionState.Disconnected -> stringResource(Res.string.room_ping_disconnected)
                            is ConnectionState.Connected -> {
                                val pingo by derivedStateOf { (connectionState as ConnectionState.Connected).pingMs }
                                stringResource(Res.string.room_ping_connected, pingo.toString())
                            }
                        },
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f,1f), blurRadius = 1f))
                    )
                    Spacer(Modifier.width(4.dp))

                    PingIndicator(connectionState)
                }

                Text(
                    text = stringResource(Res.string.room_details_current_room, viewmodel.session.currentRoom),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f,1f), blurRadius = 1f))
                )

//TODO                var showReconnectButton by remember { mutableStateOf(false) }
//                LaunchedEffect(pingo, viewmodel.session.userList.value.isNotEmpty()) {
//                    if (pingo == null && viewmodel.session.userList.value.isNotEmpty()) {
//                        delay(3000)
//                        // Check again after delay in case pingo changed
//                        if (pingo == null) showReconnectButton = true
//                    } else {
//                        showReconnectButton = false
//                    }
//                }

//                AnimatedVisibility(showReconnectButton) {
//                    Button(
//                        onClick = {
//                            viewmodel.viewModelScope.launch(Dispatchers.IO) {
//                                viewmodel.networkManager.connect()
//                            }
//                        }, colors = ButtonDefaults.buttonColors(
//                            containerColor = Color.Red
//                        ), shape = CircleShape
//                    ) {
//                        Text(text = stringResource(Res.string.room_reconnect_button), color = Color.White)
//                    }
//                }
//
//
//                AnimatedVisibility(pingo == null && viewmodel.session.userList.value.isNotEmpty()) {
//                    Text(
//                        text = stringResource(Res.string.room_more_info_change_network_engine_msg),
//                        color = Color.White,
//                        modifier = Modifier.fillMaxWidth(0.95f),
//                        textAlign = TextAlign.Center,
//                        fontSize = 12.sp,
//                        style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f,1f), blurRadius = 1f))
//                    )
//                }
//            }

            val osd by remember { viewmodel.osdMsg }
            if (osd.isNotEmpty()) Text(
                modifier = Modifier.fillMaxWidth(0.95f),
                fontSize = 11.sp,
                lineHeight = (Theming.USER_INFO_TXT_SIZE + 4).sp,
                color = Theming.SP_PALE,
                text = osd,
                fontFamily = FontFamily(sairaFont),
                textAlign = TextAlign.Center,
                style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f,1f), blurRadius = 1f))
            )
            if (osd.isEmpty()) viewmodel.media?.let {
                val filename = it.fileName.lowercase()
                if (filename.contains(Regex("(s|season)(\\d{1,2})(e|episode)(\\d{1,2})"))) {
                    val season =
                        Regex("(s|season)(\\d{1,2})").find(filename)?.groupValues?.get(
                            2
                        )?.toInt() ?: 0
                    val episode =
                        Regex("(e|episode)(\\d{1,2})").find(filename)?.groupValues?.get(
                            2
                        )?.toInt() ?: 0
                    Text(
                        text = "S${season}E${episode}",
                        color = Theming.SP_PALE,
                        style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f,1f), blurRadius = 1f))
                    )
                }
            }
        }
    }
}