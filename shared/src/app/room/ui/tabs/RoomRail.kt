package app.room.ui.tabs

import app.room.LocalRoomRailFocus
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.i18n.strings
import app.room.PortraitTabRowHeight
import app.room.ui.bottombar.PortraitAddKey
import app.room.ui.bottombar.RoomReadyButton
import app.theme.Motion
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.LocalIsTelevision
import app.uicomponents.chromeSurface
import app.uicomponents.controls.Chevron
import app.uicomponents.controls.ChevronDirection
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.Icon
import app.uicomponents.controls.MoreGlyph
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.Rule
import app.uicomponents.controls.Text
import app.uicomponents.controls.VerticalRule
import app.uicomponents.frames.PanelFrame
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.pressFeedback
import app.home.InviteLink
import kotlinx.coroutines.flow.collectLatest
import app.room.RoomViewmodel
import app.room.RoomUiStateManager
import app.i18n.Localization
import app.utils.platformCallback
import app.uicomponents.controls.touchTarget

/** One cell. A value: an unchanged cell compares equal, so Compose skips it on a recomposition. */
private data class RailCell(val icon: ImageVector, val name: String, val active: Boolean = false, val onClick: () -> Unit)

/**
 * The rail's click actions, made once for each room. A cell that is built again then holds the
 * same action and still compares equal. The share action reads the room name when it runs.
 */
private class RailClicks(ui: RoomUiStateManager, viewmodel: RoomViewmodel) {
    val prefs = { ui.toggleRoomPreferences() }
    val playlist = { ui.toggleSharedPlaylist() }
    val people = { ui.toggleUserInfo() }
    val lock = {
        ui.tabLock.value = true
        ui.visibleHUD.value = false
    }
    val pip = { platformCallback.onPictureInPicture(true) }

    // The whole room in one link: the link carries the server, the port and the password, so
    // nobody has to read out five fields.
    val share = {
        viewmodel.joinConfig?.let { config ->
            val message = Localization.strings.roomShareInviteMessage(viewmodel.session.currentRoom)
            platformCallback.shareText(message + "\n" + InviteLink.shareUrl(config))
        }
        Unit
    }
    val managed = { ui.managedRoom.value = true }
    val leave = { ui.askLeave.value = true }
    val more = { ui.railActionsExpanded.value = true }

    // The More tab of the portrait room opens and closes its page.
    val morePage = { ui.toggleMorePage() }
}

/**
 * The tab row of the portrait room, under the video: the Ready toggle, then one tab for each page.
 * The tabs open the people list, the shared playlist, the in-room preferences and the More page.
 * They show under the same conditions as the rail cells, and they take the look of a rail cell.
 * The add media page opens from the add key at the end of the seek row ([PortraitAddKey]).
 */
@Composable
fun RoomTabRow(modifier: Modifier = Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val ui = LocalRoomUiState.current
    val solo = viewmodel.isSoloMode
    val p = palette
    val playerIsReady by viewmodel.playerManager.isPlayerReady.collectAsState()
    val hasVideo by viewmodel.hasVideo.collectAsState()
    val sharedPlaylists by viewmodel.protocol.supportsSharedPlaylists.collectAsState()
    val stateUserInfo by ui.tabCardUserInfo.collectAsState()
    val statePlaylist by ui.tabCardSharedPlaylist.collectAsState()
    val statePrefs by ui.tabCardRoomPreferences.collectAsState()
    val more by ui.railActionsExpanded.collectAsState()

    val clicks = remember(ui, viewmodel) { RailClicks(ui, viewmodel) }
    val tabs = buildList {
        if (!solo) add(RailCell(Icons.Filled.Groups, strings.roomCardTitleUserInfo, stateUserInfo, clicks.people))
        if (!solo && playerIsReady && sharedPlaylists) {
            add(RailCell(Icons.AutoMirrored.Filled.PlaylistPlay, strings.roomSharedPlaylist, statePlaylist, clicks.playlist))
        }
        if (playerIsReady) add(RailCell(Icons.Filled.Tune, strings.roomCardTitleInRoomPrefs, statePrefs, clicks.prefs))
        add(RailCell(MoreGlyph, strings.roomRailMore, more, clicks.morePage))
    }
    // A closing panel gives focus back to the tab whose page was open last, as on the rail.
    val railFocus = LocalRoomRailFocus.current
    val openTab = tabs.firstOrNull { it.active }?.name
    var lastOpenTab by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(openTab) { if (openTab != null) lastOpenTab = openTab }
    val focusTab = tabs.indexOfFirst { it.name == (openTab ?: lastOpenTab) }.coerceAtLeast(0)

    Row(
        modifier
            .fillMaxWidth()
            .height(PortraitTabRowHeight)
            .drawBehind { drawRect(p.rule, Offset(0f, size.height - Space.hair.toPx()), Size(size.width, Space.hair.toPx())) }
            .padding(horizontal = Space.gapTight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasVideo && !solo) {
            RoomReadyButton()
            VerticalRule(Modifier.size(Space.hair, Space.glyphLarge))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { index, cell -> RailCell(cell, horizontal = true, focusRequester = railFocus.takeIf { index == focusTab }) }
        }
    }
}

/**
 * The More page of the portrait room: one row for each room action that the rail keeps behind its
 * More cell, plus the screen lock, under the same conditions as on the rail. A row closes the page,
 * then runs its action. A page is what the main box under the video shows in place of chat.
 */
@Composable
fun RoomMorePage(modifier: Modifier, shape: Shape) {
    val viewmodel = LocalRoomViewmodel.current
    val ui = LocalRoomUiState.current
    val solo = viewmodel.isSoloMode
    val playerIsReady by viewmodel.playerManager.isPlayerReady.collectAsState()
    val managedRooms by viewmodel.protocol.supportsManagedRooms.collectAsState()

    val clicks = remember(ui, viewmodel) { RailClicks(ui, viewmodel) }
    val rows = buildList {
        add(RailCell(Icons.Filled.Lock, strings.roomLock, onClick = clicks.lock))
        if (playerIsReady && viewmodel.player.supportsPictureInPicture && platformCallback.supportsPictureInPicture) {
            add(RailCell(Icons.Filled.PictureInPicture, strings.roomOverflowPip, onClick = clicks.pip))
        }
        if (!solo) add(RailCell(Icons.Filled.Share, strings.roomShareInvite, onClick = clicks.share))
        if (!solo && managedRooms) add(RailCell(Icons.Filled.SupervisedUserCircle, strings.roomManagedRoom, onClick = clicks.managed))
        add(RailCell(Icons.AutoMirrored.Filled.Logout, strings.roomOverflowLeaveRoom, onClick = clicks.leave))
    }
    PanelFrame(title = strings.roomRailMore, modifier = modifier, shape = shape, onClose = { ui.toggleMorePage(false) }) {
        rows.forEach { row ->
            PageRow(row.icon, row.name) {
                ui.toggleMorePage(false)
                row.onClick()
            }
        }
    }
}

/**
 * One 48dp row of a page in the portrait room: a glyph, the name, an optional [value] at the end,
 * and a chevron when the row [opens] another page or a dialog.
 */
@Composable
internal fun PageRow(icon: ImageVector, name: String, value: String? = null, opens: Boolean = false, onClick: () -> Unit) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Space.touchMin)
            .clickable(interactionSource = source, indication = null, role = Role.Button) { Feedback.tick(); onClick() }
            .hoverable(source)
            .controlStates(source, RectangleShape)
            .pressFeedback(source)
            .padding(horizontal = Space.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = p.ink, modifier = Modifier.size(Space.glyph))
        RowGap(Space.gap)
        Text(name, style = Type.label, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (value != null) {
            RowGap(Space.gap)
            Text(value, style = Type.value, color = p.inkDim, maxLines = 1)
        }
        if (opens) {
            RowGap(Space.gapTight)
            Chevron(ChevronDirection.Right)
        }
    }
}

/**
 * The rail: the strip of buttons that opens the panels, made of 42dp cells. A room is the group
 * of people watching together. The panel cells come first. The room actions (picture in
 * picture, share invite, managed room, leave) start folded behind one More cell. The first tap
 * unfolds them with a slide, and they fold again after six seconds without input. The rail is a
 * column at the end edge in a landscape window at least 480dp tall, and a row at the top end
 * otherwise.
 */
@Composable
fun RoomRail(modifier: Modifier = Modifier, horizontal: Boolean = false) {
    val viewmodel = LocalRoomViewmodel.current
    val ui = LocalRoomUiState.current
    val solo = viewmodel.isSoloMode
    val playerIsReady by viewmodel.playerManager.isPlayerReady.collectAsState()

    val stateUserInfo by ui.tabCardUserInfo.collectAsState()
    val statePlaylist by ui.tabCardSharedPlaylist.collectAsState()
    val statePrefs by ui.tabCardRoomPreferences.collectAsState()
    val managedRooms by viewmodel.protocol.supportsManagedRooms.collectAsState()
    val sharedPlaylists by viewmodel.protocol.supportsSharedPlaylists.collectAsState()
    val expanded by ui.railActionsExpanded.collectAsState()
    val managing by ui.managedRoom.collectAsState()
    val leaving by ui.askLeave.collectAsState()
    LaunchedEffect(expanded, managing, leaving) {
        if (!expanded || managing || leaving) return@LaunchedEffect
        // Each input in the room starts the six seconds again. The count is collected here, not
        // read in composition, so an input does not recompose the rail.
        ui.hudActivity.collectLatest {
            delay(6_000)
            ui.railActionsExpanded.value = false
        }
    }
    // Starts at the stored value, so a rail rebuilt after a rotation does not replay the unfold.
    val unfolded = remember { MutableTransitionState(expanded) }
    unfolded.targetState = expanded

    /* The More cell leaves as the actions arrive, and the actions leave when they fold again.
     * Each takes focus with it. With a remote, focus follows: onto the first action on unfold,
     * and back onto More on fold. This happens only when the state flips, so a rail rebuilt
     * after a rotation takes no focus. */
    // Where a closing panel sends focus back: the cell whose panel was open last, or else the first.
    val railFocus = LocalRoomRailFocus.current
    val moreFocus = remember { FocusRequester() }
    val firstActionFocus = remember { FocusRequester() }
    val remoteOrKeyboard = LocalIsTelevision.current || LocalInputModeManager.current.inputMode == InputMode.Keyboard
    var seenExpanded by remember { mutableStateOf(expanded) }
    var actionsFocused by remember { mutableStateOf(false) }
    var moreFocused by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded != seenExpanded && remoteOrKeyboard) {
            val target = when {
                expanded && moreFocused -> firstActionFocus
                !expanded && actionsFocused -> moreFocus
                else -> null
            }
            if (target != null) {
                // Wait 50 ms first, because the cell must exist before it can take focus.
                delay(50)
                target.requestFocus()
            }
        }
        seenExpanded = expanded
    }

    val clicks = remember(ui, viewmodel) { RailClicks(ui, viewmodel) }
    val panels = buildList {
        if (playerIsReady) {
            add(RailCell(Icons.Filled.Tune, strings.roomCardTitleInRoomPrefs, statePrefs, clicks.prefs))
        }
        if (!solo) {
            if (playerIsReady && sharedPlaylists) {
                add(RailCell(Icons.AutoMirrored.Filled.PlaylistPlay, strings.roomSharedPlaylist, statePlaylist, clicks.playlist))
            }
            add(RailCell(Icons.Filled.Groups, strings.roomCardTitleUserInfo, stateUserInfo, clicks.people))
        }
        add(RailCell(Icons.Filled.Lock, strings.roomLock, onClick = clicks.lock))
    }
    val actions = buildList {
        if (playerIsReady && viewmodel.player.supportsPictureInPicture && platformCallback.supportsPictureInPicture) {
            add(RailCell(Icons.Filled.PictureInPicture, strings.roomOverflowPip, onClick = clicks.pip))
        }
        if (!solo) add(RailCell(Icons.Filled.Share, strings.roomShareInvite, onClick = clicks.share))
        if (!solo && managedRooms) {
            add(RailCell(Icons.Filled.SupervisedUserCircle, strings.roomManagedRoom, onClick = clicks.managed))
        }
        add(RailCell(Icons.AutoMirrored.Filled.Logout, strings.roomOverflowLeaveRoom, onClick = clicks.leave))
    }
    val more = RailCell(MoreGlyph, strings.roomRailMore, onClick = clicks.more)
    /* The rail's focus target sits on the cell whose panel is open. After that panel closes, it
     * stays on the same cell, so Back returns focus to the cell that opened the panel. */
    val openCell = panels.firstOrNull { it.active }?.name
    var lastOpenCell by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(openCell) { if (openCell != null) lastOpenCell = openCell }
    val activeCell = panels.indexOfFirst { it.name == (openCell ?: lastOpenCell) }.coerceAtLeast(0)

    if (horizontal) {
        Row(modifier.chromeSurface(Radius.panelShape), verticalAlignment = Alignment.CenterVertically) {
            panels.forEachIndexed { index, cell -> RailCell(cell, horizontal = true, focusRequester = railFocus.takeIf { index == activeCell }) }
            VerticalRule(Modifier.size(Space.hair, Space.row))
            AnimatedVisibility(!expanded, enter = expandHorizontally(Motion.move()), exit = shrinkHorizontally(Motion.move())) {
                RailCell(more, horizontal = true, focusRequester = moreFocus, onFocus = { moreFocused = it })
            }
            AnimatedVisibility(
                visibleState = unfolded,
                enter = expandHorizontally(Motion.move()) + fadeIn(Motion.move()),
                exit = shrinkHorizontally(Motion.move()) + fadeOut(Motion.move()),
            ) {
                Row(Modifier.onFocusChanged { actionsFocused = it.hasFocus }, verticalAlignment = Alignment.CenterVertically) {
                    actions.forEachIndexed { index, cell -> RailCell(cell, horizontal = true, focusRequester = firstActionFocus.takeIf { index == 0 }) }
                }
            }
        }
    } else {
        Column(modifier.chromeSurface(Radius.panelShape), horizontalAlignment = Alignment.CenterHorizontally) {
            panels.forEachIndexed { index, cell -> RailCell(cell, horizontal = false, focusRequester = railFocus.takeIf { index == activeCell }) }
            Rule(Modifier.size(Space.row, Space.hair))
            AnimatedVisibility(!expanded, enter = expandVertically(Motion.move()), exit = shrinkVertically(Motion.move())) {
                RailCell(more, horizontal = false, focusRequester = moreFocus, onFocus = { moreFocused = it })
            }
            AnimatedVisibility(
                visibleState = unfolded,
                enter = expandVertically(Motion.move()) + fadeIn(Motion.move()),
                exit = shrinkVertically(Motion.move()) + fadeOut(Motion.move()),
            ) {
                Column(Modifier.onFocusChanged { actionsFocused = it.hasFocus }, horizontalAlignment = Alignment.CenterHorizontally) {
                    actions.forEachIndexed { index, cell -> RailCell(cell, horizontal = false, focusRequester = firstActionFocus.takeIf { index == 0 }) }
                }
            }
        }
    }
}

/**
 * One rail cell: a glyph in a 42dp square. An open panel tints the cell and draws a 2dp accent
 * edge along the bottom of a row rail, or along the left edge of a column rail. In a left-to-right
 * layout, that left edge faces the panel.
 */
@Composable
private fun RailCell(cell: RailCell, horizontal: Boolean, focusRequester: FocusRequester? = null, onFocus: ((Boolean) -> Unit)? = null) {
    val p = palette
    val accent = p.accent
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(Space.row)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (onFocus != null) Modifier.onFocusChanged { onFocus(it.isFocused) } else Modifier)
            .clickable(interactionSource = source, indication = null, role = Role.Button) { Feedback.tick(); cell.onClick() }
            .touchTarget()
            .hoverable(source)
            .semantics { contentDescription = cell.name; selected = cell.active }
            .controlStates(source, RectangleShape)
            .pressFeedback(source)
            .drawWithContent {
                if (cell.active) drawRect(accent.copy(alpha = 0.08f))
                drawContent()
                if (cell.active) {
                    val w = 2.dp.toPx()
                    if (horizontal) drawRect(accent, Offset(0f, size.height - w), Size(size.width, w))
                    else drawRect(accent, Offset(0f, 0f), Size(w, size.height))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(cell.icon, contentDescription = null, tint = if (cell.active) p.accent else p.ink, modifier = Modifier.size(Space.glyph))
    }
}
