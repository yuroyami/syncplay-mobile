package app.room.ui.statinfo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.LocalRoomViewmodel
import app.protocol.models.ConnectionState
import app.theme.Theming
import app.uicomponents.sairaFont
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_details_current_room
import syncplaymobile.shared.generated.resources.room_details_user_count
import syncplaymobile.shared.generated.resources.room_ping_disconnected


@Composable
fun RoomStatusInfoSection(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current

    // Top-center overlay: room name, user count / connection state, OSD messages.
    // TODO: suppress while in PiP mode.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        if (!viewmodel.isSoloMode) {
            val connectionState by viewmodel.networkManager.state.collectAsState()
            val userList by viewmodel.session.userList.collectAsState()

            /* userList from the server already includes ourselves (ListResponse keys every room
             * member by username). Fall back to 1 while connected but not yet populated, so the
             * count doesn't flash "0 users" during join. */
            val totalUsers = when {
                userList.isNotEmpty() -> userList.size
                connectionState == ConnectionState.CONNECTED -> 1
                else -> 0
            }

            /* When connected, show the user count as the status (it already conveys room state).
             * Only the disconnected state gets an explicit label, since the count goes stale the
             * moment the socket drops. */
            val parenthesized = if (connectionState == ConnectionState.CONNECTED) {
                stringResource(Res.string.room_details_user_count, totalUsers)
            } else {
                stringResource(Res.string.room_ping_disconnected)
            }

            Text(
                text = stringResource(Res.string.room_details_current_room, viewmodel.session.currentRoom) +
                        " ($parenthesized)",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )

            /* No manual reconnect button: NetworkManager.reconnect() already retries on its own
             * whenever the socket drops, so the button only duplicated automatic behavior. */

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