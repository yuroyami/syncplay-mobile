package com.yuroyami.syncplay.ui.screens.room.statinfo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.theme.Theming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
                val pingo by viewmodel.ping.collectAsState()

                Row(verticalAlignment = CenterVertically) {
                    Text(
                        text = if (pingo == null) stringResource(Res.string.room_ping_disconnected) else stringResource(
                            Res.string.room_ping_connected,
                            pingo.toString()
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(4.dp))

                    PingIndicator(pingo)
                }

                Text(
                    text = stringResource(Res.string.room_details_current_room, viewmodel.session.currentRoom),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )

                var showReconnectButton by remember { mutableStateOf(false) }
                LaunchedEffect(pingo, viewmodel.session.userList.value.isNotEmpty()) {
                    if (pingo == null && viewmodel.session.userList.value.isNotEmpty()) {
                        delay(3000)
                        // Check again after delay in case pingo changed
                        if (pingo == null) showReconnectButton = true
                    } else {
                        showReconnectButton = false
                    }
                }

                AnimatedVisibility(showReconnectButton) {
                    Button(
                        onClick = {
                            viewmodel.viewModelScope.launch(Dispatchers.IO) {
                                viewmodel.networkManager.connect()
                            }
                        }, colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red
                        ), shape = CircleShape
                    ) {
                        Text(text = "Reconnect", color = Color.White)
                    }
                }


                AnimatedVisibility(pingo == null && viewmodel.session.userList.value.isNotEmpty()) {

                    Text(
                        text = "Try changing network engine in Settings > Network to Ktor if you're experiencing connection issues.",
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(0.7f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp
                    )
                }
            }

            val osd by remember { viewmodel.osdManager.osdMsg }
            if (osd.isNotEmpty()) Text(
                modifier = Modifier.fillMaxWidth(0.3f),
                fontSize = 11.sp,
                lineHeight = (Theming.USER_INFO_TXT_SIZE + 4).sp,
                color = Theming.SP_PALE,
                text = osd,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.W300
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
                    )
                }
            }
        }
    //}
}