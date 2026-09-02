package app.room.ui.bottombar

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.room.LocalRoomInitialFocus
import app.theme.Space
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.PrimaryAction
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_button_desc_add

/** The add key in the transport. It opens the add-media side panel (CardAddMedia). */
@Composable
fun RoomMediaAddButton() {
    val viewmodel = LocalRoomViewmodel.current
    val ui = LocalRoomUiState.current
    val hasVideo by viewmodel.hasVideo.collectAsState()

    // Before a file loads this is the room's primary control, so it claims the initial D-pad focus.
    val initialFocus = LocalRoomInitialFocus.current
    AddVideoButton(
        modifier = Modifier.padding(Space.gapTight).then(if (!hasVideo && initialFocus != null) Modifier.focusRequester(initialFocus) else Modifier),
        expanded = !hasVideo,
        onClick = { ui.toggleAddMedia() },
    )
}

/** Collapsed to a glyph once a file plays; the primary action of the room before that. */
@Composable
fun AddVideoButton(modifier: Modifier, expanded: Boolean, onClick: () -> Unit) {
    if (!expanded) {
        GlyphButton(Icons.Filled.AddToQueue, name = stringResource(Res.string.room_button_desc_add), modifier = modifier, size = Space.glyphLarge, onClick = onClick)
    } else {
        PrimaryAction(stringResource(Res.string.room_button_desc_add), onClick = onClick, modifier = modifier.width(180.dp))
    }
}
