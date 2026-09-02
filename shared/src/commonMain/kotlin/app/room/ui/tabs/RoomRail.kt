package app.room.ui.tabs

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
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material.icons.filled.Tune
import app.uicomponents.controls.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.theme.Radius
import app.theme.Space
import app.theme.palette
import app.uicomponents.chromeSurface
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.Rule
import app.uicomponents.controls.VerticalRule
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.pressFeedback
import app.utils.platformCallback
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_card_title_in_room_prefs
import syncplaymobile.shared.generated.resources.room_card_title_user_info
import syncplaymobile.shared.generated.resources.room_lock
import syncplaymobile.shared.generated.resources.room_managed_room
import syncplaymobile.shared.generated.resources.room_overflow_leave_room
import syncplaymobile.shared.generated.resources.room_overflow_pip
import syncplaymobile.shared.generated.resources.room_shared_playlist

private class RailCell(val icon: ImageVector, val name: String, val active: Boolean = false, val onClick: () -> Unit)

/**
 * The rail: 42dp cells on the chrome tier, panels first, then the room actions, every one always
 * reachable in one tap. Vertical at the end edge when the window is tall enough for the column,
 * a row at the top end otherwise, which is how a phone in landscape gets it.
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
        if (!solo && managedRooms) {
            add(RailCell(Icons.Filled.SupervisedUserCircle, stringResource(Res.string.room_managed_room)) { ui.managedRoom.value = true })
        }
        add(RailCell(Icons.AutoMirrored.Filled.Logout, stringResource(Res.string.room_overflow_leave_room)) { ui.askLeave.value = true })
    }

    if (horizontal) {
        Row(modifier.chromeSurface(Radius.panelShape), verticalAlignment = Alignment.CenterVertically) {
            panels.forEach { RailCell(it) }
            VerticalRule(Modifier.size(Space.hair, Space.row))
            actions.forEach { RailCell(it) }
        }
    } else {
        Column(modifier.chromeSurface(Radius.panelShape), horizontalAlignment = Alignment.CenterHorizontally) {
            panels.forEach { RailCell(it) }
            Rule(Modifier.size(Space.row, Space.hair))
            actions.forEach { RailCell(it) }
        }
    }
}

/** One cell: a glyph in a 42dp square, accent when its panel is open, the shared control states. */
@Composable
private fun RailCell(cell: RailCell) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(Space.row)
            .clickable(interactionSource = source, indication = null, role = Role.Button) { Feedback.tick(); cell.onClick() }
            .hoverable(source)
            .semantics { contentDescription = cell.name; selected = cell.active }
            .controlStates(source, RectangleShape, selected = cell.active)
            .pressFeedback(source),
        contentAlignment = Alignment.Center,
    ) {
        Icon(cell.icon, contentDescription = null, tint = if (cell.active) p.accent else p.ink, modifier = Modifier.size(Space.glyph))
    }
}
