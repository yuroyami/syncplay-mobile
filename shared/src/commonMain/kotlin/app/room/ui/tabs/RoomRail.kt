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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.preferences.settings.AskModal
import app.theme.Radius
import app.theme.Space
import app.theme.palette
import app.uicomponents.chromeSurface
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.Rule
import app.uicomponents.controls.VerticalRule
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.pressFeedback
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.utils.platformCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_card_title_in_room_prefs
import syncplaymobile.shared.generated.resources.room_card_title_user_info
import syncplaymobile.shared.generated.resources.room_leave_question
import syncplaymobile.shared.generated.resources.room_lock
import syncplaymobile.shared.generated.resources.room_managed_room
import syncplaymobile.shared.generated.resources.room_overflow_create_managed_room
import syncplaymobile.shared.generated.resources.room_overflow_identify_as_operator
import syncplaymobile.shared.generated.resources.room_overflow_leave_room
import syncplaymobile.shared.generated.resources.room_overflow_pip
import syncplaymobile.shared.generated.resources.room_overflow_title
import syncplaymobile.shared.generated.resources.room_shared_playlist

private class RailCell(val icon: ImageVector, val name: String, val active: Boolean = false, val onClick: () -> Unit)

/**
 * The rail: 42dp cells on the chrome tier, panels first, then the room actions. Vertical at the
 * end edge on a wide window, a row in the top bar on a tall one. When the window is too short for
 * every cell the actions fold behind a More cell, so nothing ever overlaps the transport.
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

    val askLeave = remember { mutableStateOf(false) }
    var managedChooser by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }

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
            add(RailCell(Icons.Filled.SupervisedUserCircle, stringResource(Res.string.room_managed_room)) { managedChooser = true })
        }
        add(RailCell(Icons.AutoMirrored.Filled.Logout, stringResource(Res.string.room_overflow_leave_room)) { askLeave.value = true })
    }

    // Fold the actions behind More when the window cannot hold every cell beside the transport.
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val extent: Dp = with(density) { (if (horizontal) window.width else window.height).toDp() }
    val needed = Space.row * (panels.size + actions.size) + Space.rowTall * 2 + Space.gutter * 2
    val folded = extent < needed
    val trailing = if (folded) listOf(RailCell(Icons.Filled.MoreVert, stringResource(Res.string.room_overflow_title)) { moreOpen = true }) else actions

    if (horizontal) {
        Row(modifier.chromeSurface(Radius.panelShape), verticalAlignment = Alignment.CenterVertically) {
            panels.forEach { RailCell(it) }
            VerticalRule(Modifier.size(Space.hair, Space.row))
            trailing.forEach { RailCell(it) }
        }
    } else {
        Column(modifier.chromeSurface(Radius.panelShape), horizontalAlignment = Alignment.CenterHorizontally) {
            panels.forEach { RailCell(it) }
            Rule(Modifier.size(Space.row, Space.hair))
            trailing.forEach { RailCell(it) }
        }
    }

    Modal(open = moreOpen, onDismiss = { moreOpen = false }, title = stringResource(Res.string.room_overflow_title), size = ModalSize.Ask, inset = false) {
        actions.forEach { cell ->
            ListRow(onClick = { moreOpen = false; cell.onClick() }) {
                Icon(cell.icon, contentDescription = null, tint = palette.inkDim, modifier = Modifier.size(Space.glyph))
                RowGap()
                RowLabel(cell.name)
            }
        }
    }

    Modal(open = managedChooser, onDismiss = { managedChooser = false }, title = stringResource(Res.string.room_managed_room), size = ModalSize.Ask, inset = false) {
        ListRow(onClick = { managedChooser = false; ui.popupCreateManagedRoom.value = true }) {
            Icon(Icons.Filled.SupervisedUserCircle, contentDescription = null, tint = palette.inkDim, modifier = Modifier.size(Space.glyph))
            RowGap()
            RowLabel(stringResource(Res.string.room_overflow_create_managed_room))
        }
        ListRow(onClick = { managedChooser = false; ui.popupIdentifyAsRoomOperator.value = true }) {
            Icon(Icons.Filled.SupervisorAccount, contentDescription = null, tint = palette.inkDim, modifier = Modifier.size(Space.glyph))
            RowGap()
            RowLabel(stringResource(Res.string.room_overflow_identify_as_operator))
        }
    }

    AskModal(
        open = askLeave,
        title = stringResource(Res.string.room_overflow_leave_room),
        text = stringResource(Res.string.room_leave_question),
        destructive = true,
        onYes = { viewmodel.viewModelScope.launch(Dispatchers.Main) { viewmodel.goHome() } },
    )
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
