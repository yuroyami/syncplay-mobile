package app.room.ui.bottombar

import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import app.theme.Motion
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
    val addCardOpen by LocalRoomUiState.current.mediaAddExpanded.collectAsState()

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
            }

            // The view key ends the seek bar, as in the portrait stage, before the room keys (#249).
            if (platformCallback.showsRoomViewKey) CoveredByAddCard(covered = !hasVideo && addCardOpen) { RoomFullScreenKey(tall = false) }

            if (hasVideo) RoomControlPanelButton(modifier = Modifier)

            RoomMediaAddButton()
        }
    }
}

/**
 * A key that the add card grows over. While [covered], the key fades out. Once it is gone, an
 * empty box of its size keeps its place, so the key leaves focus and the screen reader.
 */
@Composable
private fun CoveredByAddCard(covered: Boolean, key: @Composable () -> Unit) {
    val alpha by animateFloatAsState(if (covered) 0f else 1f, Motion.quick(), label = "covered")
    var size by remember { mutableStateOf(DpSize.Zero) }
    val density = LocalDensity.current
    if (covered && alpha == 0f) {
        Spacer(Modifier.size(size))
    } else {
        Box(Modifier.alpha(alpha).onSizeChanged { size = with(density) { DpSize(it.width.toDp(), it.height.toDp()) } }) { key() }
    }
}

/**
 * The full-screen key. On a phone or a tablet, a tap switches the room between a held landscape and
 * the other view of the "Room orientation" setting, and [RoomOrientation.key] names what the tap
 * does. Its glyph shows the view that the tap turns to. [tall] is true in the portrait room. On the
 * desktop, a tap switches the window between full screen and windowed. A browser shows no key
 * ([app.PlatformCallback.showsRoomViewKey]).
 */
@Composable
fun RoomFullScreenKey(tall: Boolean, modifier: Modifier = Modifier) {
    platformCallback.windowFullScreen?.let { fullScreen ->
        WindowFullScreenKey(fullScreen.collectAsState().value, modifier)
        return
    }
    val ui = LocalRoomUiState.current
    val orientation = RoomOrientation.of(ROOM_ORIENTATION.watchPref().value)
    val switchedUnder by ui.orientationSwitch.collectAsState()
    val key = orientation.key(switched = switchedUnder == orientation, tall = tall)
    GlyphButton(
        icon = when (key) {
            RoomViewKey.FullScreen -> Icons.Filled.StayCurrentLandscape
            RoomViewKey.ExitFullScreen -> Icons.Filled.StayCurrentPortrait
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
        modifier = modifier.videoKeyTile(),
        onClick = { ui.toggleOrientationSwitch(orientation) },
    )
}

/** The desktop form of the full-screen key: it switches the window between full screen and windowed. */
@Composable
private fun WindowFullScreenKey(fullScreen: Boolean, modifier: Modifier) {
    GlyphButton(
        icon = if (fullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
        name = if (fullScreen) strings.roomFullScreenExit else strings.roomFullScreen,
        size = Space.glyphLarge,
        modifier = modifier.videoKeyTile(),
        onClick = { platformCallback.toggleWindowFullScreen() },
    )
}
