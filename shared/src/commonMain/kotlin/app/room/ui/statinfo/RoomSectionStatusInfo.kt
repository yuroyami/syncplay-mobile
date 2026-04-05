package app.room.ui.statinfo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import app.LocalRoomViewmodel
import app.protocol.models.ConnectionState
import app.theme.Theming
import app.uicomponents.sairaFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_details_current_room
import syncplaymobile.shared.generated.resources.room_more_info_change_network_engine_msg
import syncplaymobile.shared.generated.resources.room_ping_connected
import syncplaymobile.shared.generated.resources.room_reconnect_button


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
            val connectionState by viewmodel.networkManager.state.collectAsState()

            Text(
                text = stringResource(Res.string.room_details_current_room, viewmodel.session.currentRoom) +
                        if (connectionState == ConnectionState.CONNECTED) " (${stringResource(Res.string.room_ping_connected)})" else "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )

            val userList by viewmodel.session.userList.collectAsState()
            val userListAlreadyPopulated by derivedStateOf { userList.isNotEmpty() }

            AnimatedVisibility(connectionState == ConnectionState.DISCONNECTED && userListAlreadyPopulated) {
                Column {
                    Button(
                        onClick = {
                            viewmodel.viewModelScope.launch(Dispatchers.IO) {
                                viewmodel.networkManager.reconnect()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = CircleShape
                    ) {
                        Text(text = stringResource(Res.string.room_reconnect_button), color = Color.White)
                    }

                    Text(
                        text = stringResource(Res.string.room_more_info_change_network_engine_msg),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(0.95f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                    )
                }
            }

            val osd by remember { viewmodel.osdMsg }
            if (osd.isNotEmpty()) Text(
                modifier = Modifier.fillMaxWidth(0.95f),
                fontSize = 11.sp,
                lineHeight = (Theming.USER_INFO_TXT_SIZE + 4).sp,
                color = MaterialTheme.colorScheme.primary,
                text = osd,
                fontFamily = FontFamily(sairaFont),
                textAlign = TextAlign.Center,
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
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}