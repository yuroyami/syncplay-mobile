package app.room.ui.statinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import app.i18n.roomUserCount
import app.i18n.strings
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
import androidx.compose.material.icons.filled.Refresh
import app.uicomponents.controls.Icon
import app.uicomponents.controls.LockGlyph
import app.uicomponents.controls.UnlockGlyph
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.Tag
import app.protocol.sync.AutoplayState

private val EPISODE = Regex("(?:s|season)(\\d{1,2})(?:e|episode)(\\d{1,2})")

/**
 * The status line of the room (the group of people watching together): a 6dp connection square,
 * the encryption icon, the room name, and a state text. The state text is the readiness line or
 * the user count when connected, and the connection state otherwise. A reconnect button shows
 * while the connection is down, and an episode tag shows when the file name has one.
 */
@Composable
fun RoomStatusInfoSection(modifier: Modifier = Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val p = palette
    val connectionState by viewmodel.networkManager.state.collectAsState()
    val userList by viewmodel.session.userList.collectAsState()
    val encrypted by viewmodel.networkManager.encrypted.collectAsState()
    val autoplay by viewmodel.readiness.state.collectAsState()
    val readiness by viewmodel.readiness.summary.collectAsState()

    // The user list of the server includes the local user. Before the list arrives, show 1, not 0.
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
    /* When connected, the line shows the autoplay countdown or who the room is waiting for. That
     * is more useful than a user count, which the roster (the list of users in the room) already
     * shows. */
    val readinessLine: String? = when {
        connectionState != ConnectionState.CONNECTED -> null
        autoplay is AutoplayState.CountingDown ->
            strings.roomStartingIn((autoplay as AutoplayState.CountingDown).secondsLeft)
        readiness.alone -> null
        readiness.notReady.size == 1 -> strings.roomWaitingForOne(readiness.notReady.single())
        readiness.notReady.size > 1 -> strings.roomWaitingForMany(readiness.notReady.size)
        else -> strings.roomEveryoneReady(readiness.participantCount)
    }

    val state = when (connectionState) {
        ConnectionState.CONNECTED -> readinessLine
            ?: strings.roomUserCount(totalUsers)
        ConnectionState.CONNECTING -> strings.roomConnecting
        ConnectionState.SCHEDULING_RECONNECT -> strings.roomReconnecting
        ConnectionState.DISCONNECTED -> strings.roomPingDisconnected
    }
    val media by viewmodel.playerManager.media.collectAsState()
    val episode = remember(media?.fileName) {
        media?.fileName?.lowercase()?.let { EPISODE.find(it) }?.let { m ->
            "S" + m.groupValues[1].padStart(2, '0') + "E" + m.groupValues[2].padStart(2, '0')
        }
    }

    Row(
        modifier = modifier
            // A screen reader announces changes to this line, such as a lost connection.
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .chromeSurface(Radius.panelShape)
            .heightIn(min = Space.rowCompact)
            .padding(horizontal = Space.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(square, Radius.tightShape))
        // The lock shows whether the connection is encrypted. That tells the user whether anyone
        // between the user and the server can read the room.
        if (connectionState == ConnectionState.CONNECTED) {
            RowGap(Space.gapTight)
            Icon(
                imageVector = if (encrypted) LockGlyph else UnlockGlyph,
                contentDescription = if (encrypted) strings.roomConnectionEncrypted
                    else strings.roomConnectionPlaintext,
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
        // The app retries on its own after a delay. A user who knows that the server is back can
        // retry now, without leaving the room.
        if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.SCHEDULING_RECONNECT) {
            RowGap(Space.gapTight)
            GlyphButton(
                icon = Icons.Filled.Refresh,
                name = strings.roomReconnectNow,
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
