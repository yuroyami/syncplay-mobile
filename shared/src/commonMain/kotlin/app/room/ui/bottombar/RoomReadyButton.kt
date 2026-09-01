package app.room.ui.bottombar

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.LocalRoomViewmodel
import app.protocol.WireMessage
import app.theme.Space
import app.uicomponents.controls.Tag
import app.uicomponents.controls.Tone
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_not_ready
import syncplaymobile.shared.generated.resources.room_ready

/** Readiness as a tag: hairline when not ready, filled green when ready. Absent in solo mode. */
@Composable
fun RoomReadyButton() {
    val viewmodel = LocalRoomViewmodel.current
    if (viewmodel.isSoloMode) return

    var ready by remember { viewmodel.session.ready }

    Tag(
        text = stringResource(if (ready) Res.string.room_ready else Res.string.room_not_ready),
        tone = Tone.Ok,
        filled = ready,
        modifier = Modifier.padding(horizontal = Space.gapTight),
        onToggle = { b ->
            ready = b
            viewmodel.session.ready.value = b
            viewmodel.networkManager.sendAsync(WireMessage.readiness(isReady = b, manuallyInitiated = true))
        },
    )
}
