package app.room.ui.bottombar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.i18n.strings
import app.theme.Space
import app.uicomponents.controls.GlyphButton

/** The bottom bar of the landscape room: the Ready toggle, the seek bar and its keys. */
@Composable
fun RoomBottomBarSection(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val hasVideo by viewmodel.hasVideo.collectAsState()
    val held by viewmodel.uiState.landscapeHold.collectAsState()

    Box(modifier.windowInsetsPadding(WindowInsets.safeGestures)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .zIndex(999f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasVideo) {
                RoomReadyButton()

                RoomSeekbar(modifier = Modifier.weight(1f).padding(horizontal = 4.dp))

                RoomControlPanelButton(modifier = Modifier)
            }

            RoomMediaAddButton()

            // While the full-screen key holds the room in landscape, the same key ends the hold.
            if (held) RoomFullScreenKey()
        }
    }
}

/**
 * The full-screen key. In the portrait room it holds the room in landscape. While the room is
 * held, it shows as the exit key and ends the hold. It shows only where the app can turn the
 * screen ([app.PlatformCallback.canHoldLandscape]).
 */
@Composable
fun RoomFullScreenKey(modifier: Modifier = Modifier) {
    val ui = LocalRoomUiState.current
    val held by ui.landscapeHold.collectAsState()
    GlyphButton(
        icon = if (held) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
        name = if (held) strings.roomFullScreenExit else strings.roomFullScreen,
        size = Space.glyphLarge,
        modifier = modifier,
        onClick = { ui.toggleLandscapeHold() },
    )
}
