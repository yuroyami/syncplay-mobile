package app.room.ui.tabs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.utils.generateRoomPassword
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.cancel
import syncplaymobile.shared.generated.resources.okay
import syncplaymobile.shared.generated.resources.room_managed_room_popup_create
import syncplaymobile.shared.generated.resources.room_managed_room_popup_pw_identify_as_operator
import syncplaymobile.shared.generated.resources.room_overflow_create_managed_room
import syncplaymobile.shared.generated.resources.room_overflow_identify_as_operator

enum class ManagedRoomPopupPurpose {
    CREATE_MANAGED_ROOM, IDENTIFY_AS_OPERATOR
}

/** One question, one field: the managed room's name, or the operator password. */
@Composable
fun ManagedRoomPopup(purpose: ManagedRoomPopupPurpose) {
    val viewmodel = LocalRoomViewmodel.current
    val state = when (purpose) {
        ManagedRoomPopupPurpose.CREATE_MANAGED_ROOM -> viewmodel.uiState.popupCreateManagedRoom
        ManagedRoomPopupPurpose.IDENTIFY_AS_OPERATOR -> viewmodel.uiState.popupIdentifyAsRoomOperator
    }
    val visible by state.collectAsState()
    if (!visible) return

    var input by remember {
        mutableStateOf(if (purpose == ManagedRoomPopupPurpose.CREATE_MANAGED_ROOM) viewmodel.session.currentRoom else "")
    }
    val title = stringResource(
        if (purpose == ManagedRoomPopupPurpose.CREATE_MANAGED_ROOM) Res.string.room_overflow_create_managed_room
        else Res.string.room_overflow_identify_as_operator
    )
    val note = stringResource(
        if (purpose == ManagedRoomPopupPurpose.CREATE_MANAGED_ROOM) Res.string.room_managed_room_popup_create
        else Res.string.room_managed_room_popup_pw_identify_as_operator
    )

    fun send() {
        state.value = false
        viewmodel.protocol.isRoomChanging = true
        val auth = when (purpose) {
            ManagedRoomPopupPurpose.CREATE_MANAGED_ROOM -> WireMessage.controllerAuth(room = input, password = generateRoomPassword())
            ManagedRoomPopupPurpose.IDENTIFY_AS_OPERATOR -> WireMessage.controllerAuth(password = input)
        }
        viewmodel.networkManager.sendAsync(auth)
    }

    Modal(
        open = true,
        onDismiss = { state.value = false },
        title = title,
        size = ModalSize.Ask,
        actions = {
            SecondaryAction(stringResource(Res.string.cancel), onClick = { state.value = false })
            AccentAction(stringResource(Res.string.okay), onClick = ::send, enabled = input.isNotBlank())
        },
    ) {
        Text(note, style = Type.note, color = palette.inkDim)
        Spacer(Modifier.height(Space.gap))
        Field(value = input, onValueChange = { input = it }, imeAction = ImeAction.Done, onImeAction = { if (input.isNotBlank()) send() }, name = title)
    }
}
