package app.room.ui.statinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.uicomponents.controls.FontSizeRange
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import app.LocalRoomViewmodel
import app.protocol.models.ConnectionState
import app.protocol.network.ConnectionFailure
import app.theme.AutoSize
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.room.roomTopInsets
import app.theme.palette
import app.uicomponents.controls.controlStates
import app.utils.TopCutouts
import app.uicomponents.chromeSurface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import app.uicomponents.controls.Icon
import app.uicomponents.controls.LockGlyph
import app.uicomponents.controls.UnlockGlyph
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.Tag
import app.protocol.sync.AutoplayState
import app.subtitles.episodeOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import app.uicomponents.controls.TagPadding
import app.utils.generateClockstamp
import kotlinx.coroutines.delay
import kotlin.time.Clock



/** What the status line shows. [RoomStatusInfoSection] and [RoomStatusStrip] read it from the room. */
internal data class RoomStatus(
    val connectionState: ConnectionState,
    val encrypted: Boolean,
    val room: String,
    val state: String,
    val episode: String?,
)

/**
 * Reads the [RoomStatus] of the room. The state text is the readiness line or the user count when
 * connected, and the connection state otherwise. The episode comes from the file name.
 */
@Composable
private fun roomStatus(): RoomStatus {
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
    return RoomStatus(connectionState, encrypted, viewmodel.session.currentRoom, state, episode)
}

/**
 * The status line of the room (the group of people watching together) over the video: a 6dp
 * connection square, the encryption icon, the room name, and a state text (see [roomStatus]). A
 * reconnect button shows while the connection is down, and an episode tag shows when the file name
 * has one. The portrait room shows the same status in [RoomStatusStrip].
 */
@Composable
fun RoomStatusInfoSection(modifier: Modifier = Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val status = roomStatus()
    RoomStatusLine(
        connectionState = status.connectionState,
        encrypted = status.encrypted,
        room = status.room,
        state = status.state,
        episode = status.episode,
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
    Row(
        modifier = modifier
            // A screen reader announces changes to this line, such as a lost connection.
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .chromeSurface(Radius.panelShape)
            .heightIn(min = Space.rowCompact)
            .padding(horizontal = Space.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(connectionColor(connectionState), Radius.tightShape))
        if (connectionState == ConnectionState.CONNECTED) {
            RowGap(Space.gapTight)
            ConnectionLock(encrypted)
        }
        RowGap(Space.gapTight + 2.dp)
        // Weighted, so the row measures its fixed parts first and the episode tag keeps its width.
        RoomAndState(room, state, gap = Space.gapTight + 2.dp, modifier = Modifier.weight(1f, fill = false))
        // The app retries on its own after a delay. A user who knows that the server is back can
        // retry now, without leaving the room.
        if (isDown(connectionState)) {
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

/** The color of the connection square: green when connected, the accent while connecting, red when down. */
@Composable
private fun connectionColor(state: ConnectionState): Color {
    val p = palette
    return when (state) {
        ConnectionState.CONNECTED -> p.ok
        ConnectionState.CONNECTING, ConnectionState.SCHEDULING_RECONNECT -> p.accent
        ConnectionState.DISCONNECTED -> p.bad
    }
}

private fun isDown(state: ConnectionState): Boolean =
    state == ConnectionState.DISCONNECTED || state == ConnectionState.SCHEDULING_RECONNECT

/**
 * The lock shows whether the connection is encrypted. That tells the user whether anyone between
 * the user and the server can read the room.
 */
@Composable
private fun ConnectionLock(encrypted: Boolean) {
    val p = palette
    Icon(
        imageVector = if (encrypted) LockGlyph else UnlockGlyph,
        contentDescription = if (encrypted) strings.roomConnectionEncrypted else strings.roomConnectionPlaintext,
        tint = if (encrypted) p.ok else p.inkDim,
        modifier = Modifier.size(LockSide),
    )
}

/**
 * The status strip of the portrait room: the band above the stage, under the notch, in the bar
 * colour of the room ([app.theme.Palette.roomBar]). The clock sits at the start edge. The connection
 * square, the lock, the room name and the episode tag follow it. The state text sits at the end,
 * after the people glyph of the people tab while connected. While the connection is down, a tap on
 * the state text reconnects. A [solo] room has no status, so its strip shows the clock alone. Both
 * ends keep out of the camera cutouts that the platform reports ([TopCutouts]). Where none are
 * reported, a notch is taken to sit in the middle of the edge ([middleNotchShare]).
 */
@Composable
fun RoomStatusStrip(modifier: Modifier = Modifier, solo: Boolean = false) {
    val viewmodel = LocalRoomViewmodel.current
    val status = if (solo) null else roomStatus()
    val reported by TopCutouts.spans.collectAsState()
    val density = LocalDensity.current
    val inset = with(density) { roomTopInsets().getTop(density).toDp() }
    // The cutouts come in window pixels. The strip spans the window, but its own start counts.
    var left by remember { mutableFloatStateOf(0f) }
    val cutouts = remember(reported, left) { reported.map { (it.first - left)..(it.last - left) } }
    RoomStatusStripContent(
        status = status,
        clock = rememberStripClock(),
        cutouts = cutouts,
        middleNotch = middleNotchShare(inset),
        onReconnect = { viewmodel.networkManager.reconnectNow() },
        modifier = modifier.onGloballyPositioned { left = it.positionInWindow().x },
    )
}

/**
 * The time of day for the strip: hours and minutes in the 12-hour or 24-hour format of the device,
 * as in the chat timestamps, with no AM or PM. The text changes on each minute boundary, so the
 * strip recomposes once a minute and not in between.
 */
@Composable
private fun rememberStripClock(): String {
    var time by remember { mutableStateOf(generateClockstamp(period = false)) }
    LaunchedEffect(Unit) {
        while (true) {
            // A wake a moment early finds the same text, which recomposes nothing, and waits again.
            delay(MINUTE_MS - Clock.System.now().toEpochMilliseconds() % MINUTE_MS)
            time = generateClockstamp(period = false)
        }
    }
    return time
}

private const val MINUTE_MS = 60_000L

/** The test tag of the clock in the strip. */
internal const val STRIP_CLOCK = "strip-clock"

/**
 * The share of the strip's width that a notch in the middle of the top edge takes, guessed from the
 * height of the top inset where no platform reports the cutouts. An iPhone with an inset of 54pt or
 * more has the Dynamic Island, about a third of the width. The earlier iPhones with a notch have an
 * inset of 44pt to 50pt, and a notch of about half the width. A shorter inset is a small camera hole.
 */
private fun middleNotchShare(inset: Dp): Float = when {
    inset <= 0.dp -> 0f
    inset >= 54.dp -> 0.36f
    inset >= 40.dp -> 0.56f
    else -> 0.16f
}

/**
 * When the start or the end of the strip gets a free stretch narrower than this, as beside the
 * notch of an iPhone, the strip is compact. It then keeps smaller margins, and it drops the lock,
 * the episode tag and the people glyph, so the room name keeps a few letters. The strip is compact
 * too where the start part does not fit its stretch whole ([startFloor]).
 */
private val CompactStretch = 80.dp

/** The gap after the clock, and around the room name. */
private val PartGap = Space.gapTight + 2.dp

/** The side of the connection square. */
private val SquareSide = 6.dp

/** The side of the lock glyph ([ConnectionLock]), and the gap before it. */
private val LockSide = 14.dp
private val LockGap = Space.gapTight

/**
 * The status strip drawn from plain values. [status] is null in a solo room, whose strip shows the
 * [clock] alone. [cutouts] are spans of the strip in pixels. With none, [middleNotch] is the share
 * of the width that a notch in the middle takes, and 0 means no notch. The start part goes in the
 * first free stretch and the end part in the last one (see [freeStretches] and [CompactStretch]).
 */
@Composable
internal fun RoomStatusStripContent(
    status: RoomStatus?,
    clock: String,
    cutouts: List<ClosedFloatingPointRange<Float>>,
    middleNotch: Float,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A screen reader announces changes to this line, such as a lost connection.
    BoxWithConstraints(modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        val width = constraints.maxWidth.toFloat()
        val holes = cutouts.ifEmpty {
            if (middleNotch > 0f) listOf(width * (1f - middleNotch) / 2f..width * (1f + middleNotch) / 2f) else emptyList()
        }
        val density = LocalDensity.current
        val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
        // The parts that the start never gives up are the clock and the episode tag, so their widths count.
        val measurer = rememberTextMeasurer()
        val style = Type.value
        val clockPx = remember(clock, style, density) { measurer.measure(clock, style, maxLines = 1).size.width }
        val tagPx = remember(status?.episode, style, density) {
            status?.episode?.let { measurer.measure(it, style, maxLines = 1).size.width } ?: 0
        }
        val layout = remember(width, holes, density, ltr, status?.connectionState, clockPx, tagPx) {
            with(density) {
                fun around(margin: Dp, clearance: Dp) =
                    freeStretches(margin.toPx(), width - margin.toPx(), holes, clearance.toPx(), minimum = Space.touchMin.toPx())
                val roomy = around(Space.gutter, Space.gap)
                val narrowest = min(roomy.first().length(), roomy.last().length())
                val whole = startFloor(status, compact = false, clockPx, tagPx)
                val startStretch = if (ltr) roomy.first() else roomy.last()
                if (narrowest >= CompactStretch.toPx() && whole <= startStretch.length()) StripLayout(roomy, compact = false, whole)
                else StripLayout(around(Space.gap, Space.gapTight), compact = true, startFloor(status, compact = true, clockPx, tagPx))
            }
        }
        StripParts(status, clock, layout, onReconnect)
    }
}

/** Where the parts of the strip go: the free [stretches], whether the strip is [compact], and the least width of its start part. */
private class StripLayout(val stretches: List<ClosedFloatingPointRange<Float>>, val compact: Boolean, val startFloor: Int)

/**
 * The least width of the start part of the strip, in pixels: everything but the room name, which
 * gives way. [clockPx] and [tagPx] are the widths of the clock text and of the episode text.
 */
private fun Density.startFloor(status: RoomStatus?, compact: Boolean, clockPx: Int, tagPx: Int): Int {
    if (status == null) return clockPx
    var px = clockPx + PartGap.roundToPx() + SquareSide.roundToPx() + PartGap.roundToPx()
    if (!compact && status.connectionState == ConnectionState.CONNECTED) px += LockGap.roundToPx() + LockSide.roundToPx()
    if (!compact && status.episode != null) px += PartGap.roundToPx() + tagPx + TagPadding.roundToPx() * 2
    return px
}

/**
 * Places the start part of the strip in the first of the stretches and the end part in the last
 * one. When both share one stretch, the start part keeps the width that it needs, up to
 * [STRIP_START_SHARE] of the stretch, and the state text takes the rest. The start part never gets
 * less than its least width, so the clock and the episode tag always show whole.
 */
@Composable
private fun StripParts(status: RoomStatus?, clock: String, strip: StripLayout, onReconnect: () -> Unit) {
    Layout(
        content = {
            StripStart(status, clock, strip.compact)
            if (status != null) StripEnd(status, strip.compact, onReconnect)
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val clearance = Space.gap.roundToPx()
        val ltr = layoutDirection == LayoutDirection.Ltr
        val stretches = strip.stretches
        val startStretch = if (ltr) stretches.first() else stretches.last()
        val endStretch = if (ltr) stretches.last() else stretches.first()
        val loose = Constraints(maxHeight = constraints.maxHeight)
        val shared = startStretch == endStretch
        val endPart = measurables.getOrNull(1)
        val endRoom = when {
            endPart == null -> 0
            !shared -> endStretch.length()
            else -> {
                val wanted = measurables[0].maxIntrinsicWidth(constraints.maxHeight) + clearance
                val share = (endStretch.length() * (1f - STRIP_START_SHARE)).toInt()
                max(endStretch.length() - wanted, min(share, endStretch.length() - strip.startFloor - clearance))
            }
        }
        val end = endPart?.measure(loose.copy(maxWidth = endRoom.coerceIn(0, endStretch.length())))
        val startRoom = if (shared && end != null) startStretch.length() - end.width - clearance else startStretch.length()
        val start = measurables[0].measure(loose.copy(maxWidth = startRoom.coerceAtLeast(0)))
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else max(start.height, end?.height ?: 0)
        layout(width, height) {
            val startX = if (ltr) startStretch.start else startStretch.endInclusive - start.width
            start.place(startX.roundToInt(), (height - start.height) / 2)
            if (end != null) {
                val endX = if (ltr) endStretch.endInclusive - end.width else endStretch.start
                end.place(endX.roundToInt(), (height - end.height) / 2)
            }
        }
    }
}

/** The most of a shared stretch that the start of the strip takes when the state text needs the rest. */
private const val STRIP_START_SHARE = 0.4f

private fun ClosedFloatingPointRange<Float>.length(): Int = (endInclusive - start).toInt().coerceAtLeast(0)

/**
 * The stretches of [from] to [to] that no hole covers, with [clearance] kept on both sides of each
 * hole. A stretch narrower than [minimum] is dropped. When no stretch is left, the whole span stays.
 */
internal fun freeStretches(
    from: Float,
    to: Float,
    holes: List<ClosedFloatingPointRange<Float>>,
    clearance: Float,
    minimum: Float,
): List<ClosedFloatingPointRange<Float>> {
    var stretches = listOf(from..to)
    for (hole in holes) {
        val holeStart = hole.start - clearance
        val holeEnd = hole.endInclusive + clearance
        stretches = stretches.flatMap { stretch ->
            listOf(stretch.start..min(stretch.endInclusive, holeStart), max(stretch.start, holeEnd)..stretch.endInclusive)
                .filter { it.endInclusive - it.start >= minimum }
        }
    }
    return stretches.ifEmpty { listOf(from..to) }
}

/**
 * The start of the strip: the clock, then the connection square, the lock, the room name and the
 * episode tag. A [compact] strip drops the lock and the tag. With no [status] (a solo room), the
 * clock stands alone. The clock comes first and has no weight, so it keeps its width, and the room
 * name gives way. A screen reader skips the clock, or the live region would speak each minute.
 */
@Composable
private fun StripStart(status: RoomStatus?, clock: String, compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = clock,
            style = Type.value,
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = FontSizeRange(Type.value.fontSize, min = AutoSize.readableFloor),
            // The tag only lets a test find the clock. A screen reader does not speak it.
            modifier = Modifier.clearAndSetSemantics { testTag = STRIP_CLOCK },
        )
        if (status == null) return@Row
        RowGap(PartGap)
        Box(Modifier.size(SquareSide).background(connectionColor(status.connectionState), Radius.tightShape))
        if (!compact && status.connectionState == ConnectionState.CONNECTED) {
            RowGap(LockGap)
            ConnectionLock(status.encrypted)
        }
        RowGap(PartGap)
        // Weighted, so the row measures the clock and the episode tag first and the room name gives way.
        Text(
            text = status.room,
            style = Type.label,
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (!compact && status.episode != null) {
            RowGap(PartGap)
            Tag(status.episode)
        }
    }
}

/**
 * The end of the strip: the state text, after a people glyph while connected. While the connection
 * is down, the state text and a refresh glyph form the reconnect key. The app retries on its own
 * after a delay, and a tap retries now. A [compact] strip drops the people glyph, and while down it
 * shows the refresh glyph alone, which then carries the state text for a screen reader.
 */
@Composable
private fun StripEnd(status: RoomStatus, compact: Boolean, onReconnect: () -> Unit) {
    val p = palette
    val down = isDown(status.connectionState)
    val source = remember { MutableInteractionSource() }
    val reconnect = strings.roomReconnectNow
    Row(
        modifier = if (!down) Modifier else Modifier
            .controlStates(source, Radius.controlShape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = reconnect, onClick = onReconnect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact && status.connectionState == ConnectionState.CONNECTED) {
            // The glyph of the people tab. The text says the same, so a screen reader skips it.
            Icon(Icons.Filled.Groups, contentDescription = null, tint = p.inkDim, modifier = Modifier.size(16.dp))
            RowGap(Space.gapTight)
        }
        if (!(compact && down)) {
            Text(
                text = status.state,
                style = Type.value,
                color = p.inkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = FontSizeRange(Type.value.fontSize, min = AutoSize.readableFloor),
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (down) {
            if (!compact) RowGap(Space.gapTight)
            Icon(
                Icons.Filled.Refresh,
                contentDescription = if (compact) status.state else null,
                tint = p.accent,
                modifier = Modifier.size(Space.glyph),
            )
        }
    }
}

/**
 * A cut room name shows only when at least this many ems of its own text size are left for it:
 * about three letters and "…". A shorter stub tells the user nothing. A short name that fits whole
 * always shows.
 */
private const val ROOM_NAME_MIN_EMS = 3f

/**
 * The room name and the state text side by side, in the space that the row leaves them. The state
 * text comes first and keeps its size. The room name takes what is left and ends in "…". When less
 * than [ROOM_NAME_MIN_EMS] ems of its text size are left, the room name is left out. Only then does
 * the state text shrink to fit, and it ends in "…" only at [AutoSize.readableFloor].
 */
@Composable
private fun RoomAndState(room: String, state: String, gap: Dp, modifier: Modifier = Modifier) {
    val p = palette
    val roomStyle = Type.label
    Layout(
        content = {
            Text(room, style = roomStyle, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        val statePlaced = stateText.measure(loose.copy(maxWidth = constraints.maxWidth))
        val roomSpace = constraints.maxWidth - statePlaced.width - gapPx
        val roomMin = min(roomText.maxIntrinsicWidth(constraints.maxHeight), (roomStyle.fontSize * ROOM_NAME_MIN_EMS).roundToPx())
        if (roomSpace < roomMin) {
            // The room name would show too few letters, so the state text stands alone. The line
            // keeps the height of the room name, so it does not jump when the name comes back.
            val height = max(statePlaced.height, roomStyle.lineHeight.roundToPx())
            return@Layout layout(statePlaced.width, height) { statePlaced.placeRelative(0, (height - statePlaced.height) / 2) }
        }
        sideBySide(roomText.measure(loose.copy(maxWidth = roomSpace)), statePlaced, gapPx)
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
