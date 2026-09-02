package app.room.ui.misc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import app.LocalRoomViewmodel
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.SynkplayLogo
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_waiting_for_file

/** The room ground before a file loads: the mark at 12 percent and one line, nothing to admire. */
@Composable
fun RoomBackgroundArtwork() {
    val viewmodel = LocalRoomViewmodel.current
    val isInPipMode by viewmodel.uiState.hasEnteredPipMode.collectAsState()
    val p = palette
    Box(Modifier.fillMaxSize().background(p.ground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SynkplayLogo(modifier = Modifier.size(if (isInPipMode) 40.dp else 84.dp).alpha(0.12f))
            if (!isInPipMode) {
                Spacer(Modifier.height(Space.gap))
                Text(stringResource(Res.string.room_waiting_for_file), style = Type.note, color = p.inkDim)
            }
        }
    }
}
