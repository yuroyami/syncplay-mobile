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
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.ScreenRotation
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.i18n.strings
import app.preferences.Preferences.ROOM_ORIENTATION
import app.preferences.watchPref
import app.room.RoomOrientation
import app.room.RoomViewKey
import app.theme.Space
import app.uicomponents.controls.GlyphButton
import app.utils.platformCallback

/** The bottom bar of the landscape room: the Ready toggle, the seek bar and its keys. */
@Composable
fun RoomBottomBarSection(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val hasVideo by viewmodel.hasVideo.collectAsState()

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

            // The key that leaves the landscape view, or that stops the room from turning (#249).
            if (platformCallback.canHoldLandscape) RoomFullScreenKey(tall = false)
        }
    }
}

/**
 * The full-screen key. A tap switches the room between a held landscape and the other view of the
 * "Room orientation" setting, and [RoomOrientation.key] names what the tap does. [tall] is true
 * in the portrait room. The key shows only where the app can turn the screen
 * ([app.PlatformCallback.canHoldLandscape]).
 */
@Composable
fun RoomFullScreenKey(tall: Boolean, modifier: Modifier = Modifier) {
    val ui = LocalRoomUiState.current
    val orientation = RoomOrientation.of(ROOM_ORIENTATION.watchPref().value)
    val switchedUnder by ui.orientationSwitch.collectAsState()
    val key = orientation.key(switched = switchedUnder == orientation, tall = tall)
    GlyphButton(
        icon = when (key) {
            RoomViewKey.FullScreen -> Icons.Filled.Fullscreen
            RoomViewKey.ExitFullScreen -> Icons.Filled.FullscreenExit
            RoomViewKey.LockRotation -> Icons.Filled.ScreenLockRotation
            RoomViewKey.UnlockRotation -> Icons.Filled.ScreenRotation
        },
        name = when (key) {
            RoomViewKey.FullScreen -> strings.roomFullScreen
            RoomViewKey.ExitFullScreen -> strings.roomFullScreenExit
            RoomViewKey.LockRotation -> strings.roomRotationLock
            RoomViewKey.UnlockRotation -> strings.roomRotationUnlock
        },
        size = Space.glyphLarge,
        modifier = modifier,
        onClick = { ui.toggleOrientationSwitch(orientation) },
    )
}
