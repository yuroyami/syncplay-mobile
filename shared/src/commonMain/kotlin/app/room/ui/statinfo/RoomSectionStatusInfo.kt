package app.room.ui.statinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.LocalRoomViewmodel
import app.protocol.models.ConnectionState
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.chromeSurface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.Tag
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_connecting
import syncplaymobile.shared.generated.resources.room_connection_encrypted
import syncplaymobile.shared.generated.resources.room_connection_plaintext
import syncplaymobile.shared.generated.resources.room_user_count
import syncplaymobile.shared.generated.resources.room_ping_disconnected
import syncplaymobile.shared.generated.resources.room_reconnect_now
import syncplaymobile.shared.generated.resources.room_reconnecting

private val EPISODE = Regex("(?:s|season)(\\d{1,2})(?:e|episode)(\\d{1,2})")

/**
 * The status line: a 6dp connection square, the room name, the user count or the connection
 * state, and the episode tag when the file name carries one. Notices live elsewhere now.
 */
@Composable
fun RoomStatusInfoSection(modifier: Modifier = Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val p = palette
    val connectionState by viewmodel.networkManager.state.collectAsState()
    val userList by viewmodel.session.userList.collectAsState()
    val encrypted by viewmodel.networkManager.encrypted.collectAsState()

    // The server's list includes us; while joining, show one instead of a flash of zero.
    val totalUsers = when {
        userList.isNotEmpty() -> userList.size
        connectionState == ConnectionState.CONNECTED -> 1
        else -> 0
    }
    val square = when (connectionState) {
        ConnectionState.CONNECTED -> p.ok
        ConnectionState.CONNECTING, ConnectionState.SCHEDULING_RECONNECT -> p.accent
        ConnectionState.DISCONNECTED -> p.bad
    }
    val state = when (connectionState) {
        ConnectionState.CONNECTED -> pluralStringResource(Res.plurals.room_user_count, totalUsers, totalUsers)
        ConnectionState.CONNECTING -> stringResource(Res.string.room_connecting)
        ConnectionState.SCHEDULING_RECONNECT -> stringResource(Res.string.room_reconnecting)
        ConnectionState.DISCONNECTED -> stringResource(Res.string.room_ping_disconnected)
    }
    val media by viewmodel.playerManager.media.collectAsState()
    val episode = remember(media?.fileName) {
        media?.fileName?.lowercase()?.let { EPISODE.find(it) }?.let { m ->
            "S" + m.groupValues[1].padStart(2, '0') + "E" + m.groupValues[2].padStart(2, '0')
        }
    }

    Row(
        modifier = modifier
            // Connection changes are read out as they happen.
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .chromeSurface(Radius.panelShape)
            .heightIn(min = Space.rowCompact)
            .padding(horizontal = Space.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(square, Radius.tightShape))
        // Whether anyone between you and the server can read the room, said plainly.
        if (connectionState == ConnectionState.CONNECTED) {
            RowGap(Space.gapTight)
            Icon(
                imageVector = if (encrypted) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = stringResource(
                    if (encrypted) Res.string.room_connection_encrypted
                    else Res.string.room_connection_plaintext
                ),
                tint = if (encrypted) p.ok else p.inkDim,
                modifier = Modifier.size(14.dp),
            )
        }
        RowGap(Space.gapTight + 2.dp)
        Text(
            text = viewmodel.session.currentRoom,
            style = Type.label,
            color = p.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        RowGap(Space.gapTight + 2.dp)
        Text(state, style = Type.value, color = p.inkDim, maxLines = 1)
        // Waiting out the backoff is the common case, but a user who knows the server just came
        // back should not have to leave the room to try again.
        if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.SCHEDULING_RECONNECT) {
            RowGap(Space.gapTight)
            GlyphButton(
                icon = Icons.Filled.Refresh,
                name = stringResource(Res.string.room_reconnect_now),
                tint = p.accent,
                size = Space.glyph,
            ) { viewmodel.networkManager.reconnectNow() }
        }
        if (episode != null) {
            RowGap(Space.gapTight + 2.dp)
            Tag(episode)
        }
    }
}
