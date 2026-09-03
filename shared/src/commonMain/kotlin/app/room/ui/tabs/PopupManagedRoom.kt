package app.room.ui.tabs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import app.LocalRoomViewmodel
import app.protocol.WireMessage
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.Field
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Segmented
import app.uicomponents.controls.Text
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.utils.generateRoomPassword
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.cancel
import syncplaymobile.shared.generated.resources.okay
import syncplaymobile.shared.generated.resources.room_managed_room
import syncplaymobile.shared.generated.resources.room_managed_room_popup_create
import syncplaymobile.shared.generated.resources.room_managed_room_popup_pw_identify_as_operator
import syncplaymobile.shared.generated.resources.room_overflow_create_managed_room
import syncplaymobile.shared.generated.resources.room_overflow_identify_as_operator

/**
 * Managed rooms in one modal: a segmented choice between creating a room and identifying as
 * its operator, then the one field that choice needs. No chooser in front of it.
 */
@Composable
fun ManagedRoomModal() {
    val viewmodel = LocalRoomViewmodel.current
    val ui = viewmodel.uiState
    val open by ui.managedRoom.collectAsState()
    if (!open) return

    var create by remember { mutableStateOf(true) }
    var roomName by remember { mutableStateOf(viewmodel.session.currentRoom) }
    var password by remember { mutableStateOf("") }
    val input = if (create) roomName else password

    fun close() { ui.managedRoom.value = false }
    fun send() {
        close()
        val auth = if (create) {
            // Creating moves us to the minted room; the flag mutes the transition's own events.
            viewmodel.protocol.isRoomChanging = true
            WireMessage.controllerAuth(room = roomName, password = generateRoomPassword())
        } else {
            // Identifying stays in this room. The attempt is kept so a success can store it for
            // the re-identification every reconnect performs.
            val attempt = password.trim().uppercase()
            viewmodel.session.lastControlPasswordAttempt = attempt
            WireMessage.controllerAuth(room = viewmodel.session.currentRoom, password = attempt)
        }
        viewmodel.networkManager.sendAsync(auth)
    }

    Modal(
        open = true,
        onDismiss = ::close,
        title = stringResource(Res.string.room_managed_room),
        size = ModalSize.Ask,
        actions = {
            SecondaryAction(stringResource(Res.string.cancel), onClick = ::close)
            AccentAction(stringResource(Res.string.okay), onClick = ::send, enabled = input.isNotBlank())
        },
    ) {
        Segmented(
            options = listOf(stringResource(Res.string.room_overflow_create_managed_room), stringResource(Res.string.room_overflow_identify_as_operator)),
            selected = if (create) 0 else 1,
            onSelect = { create = it == 0 },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.gap))
        Text(
            text = stringResource(if (create) Res.string.room_managed_room_popup_create else Res.string.room_managed_room_popup_pw_identify_as_operator),
            style = Type.note,
            color = palette.inkDim,
        )
        Spacer(Modifier.height(Space.gap))
        if (create) {
            Field(value = roomName, onValueChange = { roomName = it }, imeAction = ImeAction.Done, onImeAction = { if (roomName.isNotBlank()) send() }, name = stringResource(Res.string.room_overflow_create_managed_room))
        } else {
            Field(value = password, onValueChange = { password = it }, imeAction = ImeAction.Done, onImeAction = { if (password.isNotBlank()) send() }, name = stringResource(Res.string.room_overflow_identify_as_operator))
        }
    }
}
