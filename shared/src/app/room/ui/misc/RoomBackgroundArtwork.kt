package app.room.ui.misc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import app.LocalRoomViewmodel
import app.theme.palette
import app.uicomponents.SynkplayLogo

/**
 * The background of the room screen while no video is loaded: the Synkplay logo at 35 percent
 * opacity. A room is the group of people watching together.
 */
@Composable
fun RoomBackgroundArtwork() {
    val viewmodel = LocalRoomViewmodel.current
    val isInPipMode by viewmodel.uiState.hasEnteredPipMode.collectAsState()
    val p = palette
    Box(Modifier.fillMaxSize().background(p.ground), contentAlignment = Alignment.Center) {
        SynkplayLogo(modifier = Modifier.size(if (isInPipMode) 40.dp else 128.dp).alpha(0.35f))
    }
}
