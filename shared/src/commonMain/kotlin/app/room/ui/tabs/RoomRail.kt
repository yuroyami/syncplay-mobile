package app.room.ui.tabs

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.theme.Motion
import app.theme.Radius
import app.theme.Space
import app.theme.palette
import app.uicomponents.chromeSurface
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.Icon
import app.uicomponents.controls.MoreGlyph
import app.uicomponents.controls.Rule
import app.uicomponents.controls.VerticalRule
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.pressFeedback
import app.home.InviteLink
import app.utils.platformCallback
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_card_title_in_room_prefs
import syncplaymobile.shared.generated.resources.room_card_title_user_info
import syncplaymobile.shared.generated.resources.room_lock
import syncplaymobile.shared.generated.resources.room_managed_room
import syncplaymobile.shared.generated.resources.room_overflow_leave_room
import syncplaymobile.shared.generated.resources.room_overflow_pip
import syncplaymobile.shared.generated.resources.room_share_invite
import syncplaymobile.shared.generated.resources.room_share_invite_message
import syncplaymobile.shared.generated.resources.room_rail_more
import syncplaymobile.shared.generated.resources.room_shared_playlist
import app.uicomponents.controls.touchTarget

private class RailCell(val icon: ImageVector, val name: String, val active: Boolean = false, val onClick: () -> Unit)

/**
 * The rail: 42dp cells on the chrome tier. The panel cells come first. The room actions (PiP,
 * managed room, leave) start folded behind one More cell; the first tap unfolds them with a
 * slide and they stay out for the rest of the room session. Vertical at the end edge when the
 * window is tall enough for the column, a row at the top end otherwise.
 */
@Composable
fun RoomRail(modifier: Modifier = Modifier, horizontal: Boolean = false) {
    val viewmodel = LocalRoomViewmodel.current
    val ui = LocalRoomUiState.current
    val solo = viewmodel.isSoloMode

    val stateUserInfo by ui.tabCardUserInfo.collectAsState()
    val statePlaylist by ui.tabCardSharedPlaylist.collectAsState()
    val statePrefs by ui.tabCardRoomPreferences.collectAsState()
    val managedRooms by viewmodel.protocol.supportsManagedRooms.collectAsState()
    val inviteMessage = stringResource(Res.string.room_share_invite_message, viewmodel.session.currentRoom)
    val expanded by ui.railActionsExpanded.collectAsState()
    // Starts at the session's value, so a rebuilt rail (rotation) does not replay the unfold.
    val unfolded = remember { MutableTransitionState(expanded) }
    unfolded.targetState = expanded

    val panels = buildList {
        add(RailCell(Icons.Filled.Tune, stringResource(Res.string.room_card_title_in_room_prefs), statePrefs) { ui.toggleRoomPreferences() })
        if (!solo) {
            add(RailCell(Icons.AutoMirrored.Filled.PlaylistPlay, stringResource(Res.string.room_shared_playlist), statePlaylist) { ui.toggleSharedPlaylist() })
            add(RailCell(Icons.Filled.Groups, stringResource(Res.string.room_card_title_user_info), stateUserInfo) { ui.toggleUserInfo() })
        }
        add(RailCell(Icons.Filled.Lock, stringResource(Res.string.room_lock)) {
            ui.tabLock.value = true
            ui.visibleHUD.value = false
        })
    }
    val actions = buildList {
        if (viewmodel.player.supportsPictureInPicture) {
            add(RailCell(Icons.Filled.PictureInPicture, stringResource(Res.string.room_overflow_pip)) { platformCallback.onPictureInPicture(true) })
        }
        if (!solo) {
            // The room as one line: the link carries the server, the port and the password, so
            // nobody has to read five fields down a phone line.
            add(RailCell(Icons.Filled.Share, stringResource(Res.string.room_share_invite)) {
                viewmodel.joinConfig?.let { config ->
                    platformCallback.shareText(inviteMessage + "\n" + InviteLink.build(config))
                }
            })
        }
        if (!solo && managedRooms) {
            add(RailCell(Icons.Filled.SupervisedUserCircle, stringResource(Res.string.room_managed_room)) { ui.managedRoom.value = true })
        }
        add(RailCell(Icons.AutoMirrored.Filled.Logout, stringResource(Res.string.room_overflow_leave_room)) { ui.askLeave.value = true })
    }
    val more = RailCell(MoreGlyph, stringResource(Res.string.room_rail_more)) { ui.railActionsExpanded.value = true }

    if (horizontal) {
        Row(modifier.chromeSurface(Radius.panelShape), verticalAlignment = Alignment.CenterVertically) {
            panels.forEach { RailCell(it, horizontal = true) }
            VerticalRule(Modifier.size(Space.hair, Space.row))
            AnimatedVisibility(!expanded, enter = expandHorizontally(Motion.move()), exit = shrinkHorizontally(Motion.move())) {
                RailCell(more, horizontal = true)
            }
            AnimatedVisibility(
                visibleState = unfolded,
                enter = expandHorizontally(Motion.move()) + fadeIn(Motion.move()),
                exit = shrinkHorizontally(Motion.move()) + fadeOut(Motion.move()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) { actions.forEach { RailCell(it, horizontal = true) } }
            }
        }
    } else {
        Column(modifier.chromeSurface(Radius.panelShape), horizontalAlignment = Alignment.CenterHorizontally) {
            panels.forEach { RailCell(it, horizontal = false) }
            Rule(Modifier.size(Space.row, Space.hair))
            AnimatedVisibility(!expanded, enter = expandVertically(Motion.move()), exit = shrinkVertically(Motion.move())) {
                RailCell(more, horizontal = false)
            }
            AnimatedVisibility(
                visibleState = unfolded,
                enter = expandVertically(Motion.move()) + fadeIn(Motion.move()),
                exit = shrinkVertically(Motion.move()) + fadeOut(Motion.move()),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { actions.forEach { RailCell(it, horizontal = false) } }
            }
        }
    }
}

/**
 * One cell: a glyph in a 42dp square. An open panel tints the cell and draws a 2dp accent edge
 * along the bottom of a row rail, or along the start edge of a column rail, facing its panel.
 */
@Composable
private fun RailCell(cell: RailCell, horizontal: Boolean) {
    val p = palette
    val accent = p.accent
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(Space.row)
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
