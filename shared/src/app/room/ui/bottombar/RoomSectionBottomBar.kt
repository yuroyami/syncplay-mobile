package app.room.ui.bottombar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.i18n.strings
import app.preferences.Preferences.ROOM_ORIENTATION
import app.preferences.set
import app.room.RoomOrientation
import app.theme.Space
import app.uicomponents.controls.GlyphButton
import app.utils.platformCallback
import kotlinx.coroutines.launch

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
 * The full-screen key. On a phone or a tablet, a tap turns the room to the other view and stores it
 * ([ROOM_ORIENTATION]), so the next join opens in that view too. Its glyph shows both views: the
 * view that the room is in, faint, and the view that the tap turns to ([TurnToLandscapeGlyph],
 * [TurnToPortraitGlyph]). [tall] is true in the portrait room. On the desktop, a tap switches the
 * window between full screen and windowed. A browser shows no key
 * ([app.PlatformCallback.showsRoomViewKey]).
 */
@Composable
fun RoomFullScreenKey(tall: Boolean, modifier: Modifier = Modifier) {
    platformCallback.windowFullScreen?.let { fullScreen ->
        WindowFullScreenKey(fullScreen.collectAsState().value, modifier)
        return
    }
    val scope = rememberCoroutineScope()
    // The key follows the window shape, so it stays true where the system ignores the request.
    val next = if (tall) RoomOrientation.Landscape else RoomOrientation.Portrait
    GlyphButton(
        icon = if (tall) TurnToLandscapeGlyph else TurnToPortraitGlyph,
        name = if (tall) strings.roomFullScreen else strings.roomFullScreenExit,
        size = Space.glyphLarge,
        modifier = modifier.videoKeyTile(),
        onClick = { scope.launch { ROOM_ORIENTATION.set(next.name) } },
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
