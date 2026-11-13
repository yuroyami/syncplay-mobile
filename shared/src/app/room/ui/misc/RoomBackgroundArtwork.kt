package app.room.ui.misc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import app.LocalRoomViewmodel
import app.theme.Space
import app.theme.palette
import app.uicomponents.SynkplayLogo

/**
 * The background of the room screen while no video is loaded: the Synkplay logo at 35 percent
 * opacity. A room is the group of people watching together. In the [stage] of the portrait room,
 * the logo shrinks to fit between the status line and the add key, and a hairline marks the
 * bottom edge of the stage.
 */
@Composable
fun RoomBackgroundArtwork(stage: Boolean = false) {
    val viewmodel = LocalRoomViewmodel.current
    val isInPipMode by viewmodel.uiState.hasEnteredPipMode.collectAsState()
    val p = palette
    if (stage) {
        Box(
            Modifier.fillMaxSize()
                .background(p.ground)
                .drawBehind { drawRect(p.rule, Offset(0f, size.height - Space.hair.toPx()), Size(size.width, Space.hair.toPx())) }
                // Clear of the status line above and the add key below.
                .padding(top = Space.gapTight + Space.touchMin, bottom = Space.gap + Space.touchMin + Space.gapTight),
            contentAlignment = Alignment.Center,
        ) {
            SynkplayLogo(Modifier.sizeIn(maxWidth = 80.dp, maxHeight = 80.dp).aspectRatio(1f, matchHeightConstraintsFirst = true).alpha(0.35f))
        }
        return
    }
    Box(Modifier.fillMaxSize().background(p.ground), contentAlignment = Alignment.Center) {
        SynkplayLogo(modifier = Modifier.size(if (isInPipMode) 40.dp else 128.dp).alpha(0.35f))
    }
}
