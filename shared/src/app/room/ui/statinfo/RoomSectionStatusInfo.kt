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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.uicomponents.controls.FontSizeRange
import kotlin.math.max
import kotlin.math.min
import app.LocalRoomViewmodel
import app.protocol.models.ConnectionState
import app.protocol.network.ConnectionFailure
import app.theme.AutoSize
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
import app.subtitles.episodeOf



/**
 * The status line of the room (the group of people watching together): a 6dp connection square,
 * the encryption icon, the room name, and a state text. The state text is the readiness line or
 * the user count when connected, and the connection state otherwise. A reconnect button shows
 * while the connection is down, and an episode tag shows when the file name has one.
 */
@Composable
fun RoomStatusInfoSection(modifier: Modifier = Modifier) {
    val viewmodel = LocalRoomViewmodel.current
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

    // Why the last attempt failed, until an attempt connects: a person can act on it.
    val lastFailure by viewmodel.networkManager.lastFailure.collectAsState()
    val failureText = when (lastFailure) {
        ConnectionFailure.NameNotFound -> strings.roomStatusFailureName
        ConnectionFailure.Refused -> strings.roomStatusFailureRefused
        ConnectionFailure.TimedOut -> strings.roomStatusFailureTimeout
        ConnectionFailure.Encryption -> strings.roomStatusFailureTls
        null -> null
    }
    val state = when (connectionState) {
        ConnectionState.CONNECTED -> readinessLine
            ?: strings.roomUserCount(totalUsers)
        ConnectionState.CONNECTING -> strings.roomConnecting
        ConnectionState.SCHEDULING_RECONNECT -> failureText ?: strings.roomReconnecting
        ConnectionState.DISCONNECTED -> failureText ?: strings.roomPingDisconnected
    }
    val media by viewmodel.playerManager.media.collectAsState()
    val episode = remember(media?.fileName) {
        media?.fileName?.let { episodeOf(it) }?.let { e ->
            "S" + e.season.toString().padStart(2, '0') + "E" + e.episode.toString().padStart(2, '0')
        }
    }

    RoomStatusLine(
        connectionState = connectionState,
        encrypted = encrypted,
        room = viewmodel.session.currentRoom,
        state = state,
        episode = episode,
        onReconnect = { viewmodel.networkManager.reconnectNow() },
        modifier = modifier,
    )
}

/**
 * The status line drawn from plain values. [RoomStatusInfoSection] reads these values from the
 * room. A golden passes them itself, so it can show a state that the room reaches only over time,
 * such as the autoplay countdown.
 */
@Composable
internal fun RoomStatusLine(
    connectionState: ConnectionState,
    encrypted: Boolean,
    room: String,
    state: String,
    episode: String?,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = palette
    val square = when (connectionState) {
        ConnectionState.CONNECTED -> p.ok
        ConnectionState.CONNECTING, ConnectionState.SCHEDULING_RECONNECT -> p.accent
        ConnectionState.DISCONNECTED -> p.bad
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
        // Weighted, so the row measures its fixed parts first and the episode tag keeps its width.
        RoomAndState(room, state, gap = Space.gapTight + 2.dp, modifier = Modifier.weight(1f, fill = false))
        // The app retries on its own after a delay. A user who knows that the server is back can
        // retry now, without leaving the room.
        if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.SCHEDULING_RECONNECT) {
            RowGap(Space.gapTight)
            GlyphButton(
                icon = Icons.Filled.Refresh,
                name = strings.roomReconnectNow,
                tint = p.accent,
                size = Space.glyph,
                onClick = onReconnect,
            )
        }
        if (episode != null) {
            RowGap(Space.gapTight + 2.dp)
            Tag(episode)
        }
    }
}

/** The share of the free space that the room name keeps when the state text needs more. */
private const val ROOM_NAME_SHARE = 0.6f

/**
 * The room name and the state text side by side, in the space that the row leaves them. The room
 * name keeps its whole width, up to 60% of that space ([ROOM_NAME_SHARE]). The state text takes
 * the rest. It shrinks to fit first, and it ends in "…" only at [AutoSize.readableFloor].
 */
@Composable
private fun RoomAndState(room: String, state: String, gap: Dp, modifier: Modifier = Modifier) {
    val p = palette
    Layout(
        content = {
            Text(room, style = Type.label, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = state,
                style = Type.value,
                color = p.inkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = FontSizeRange(Type.value.fontSize, min = AutoSize.readableFloor),
            )
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val (roomText, stateText) = measurables
        val gapPx = gap.roundToPx()
        val loose = Constraints(maxHeight = constraints.maxHeight)
        if (!constraints.hasBoundedWidth) {
            // An intrinsic measurement asks without a width limit: both texts take their full width.
            return@Layout sideBySide(roomText.measure(loose), stateText.measure(loose), gapPx)
        }
        val space = constraints.maxWidth
        val roomKeeps = min(roomText.maxIntrinsicWidth(constraints.maxHeight), (space * ROOM_NAME_SHARE).toInt())
        val statePlaced = stateText.measure(loose.copy(maxWidth = (space - roomKeeps - gapPx).coerceAtLeast(0)))
        val roomPlaced = roomText.measure(loose.copy(maxWidth = (space - statePlaced.width - gapPx).coerceAtLeast(0)))
        sideBySide(roomPlaced, statePlaced, gapPx)
    }
}

/** Places [first] and then [second] in one row, [gapPx] apart, both centered on the same line. */
private fun MeasureScope.sideBySide(first: Placeable, second: Placeable, gapPx: Int): MeasureResult {
    val height = max(first.height, second.height)
    return layout(first.width + gapPx + second.width, height) {
        first.placeRelative(0, (height - first.height) / 2)
        second.placeRelative(first.width + gapPx, (height - second.height) / 2)
    }
}
