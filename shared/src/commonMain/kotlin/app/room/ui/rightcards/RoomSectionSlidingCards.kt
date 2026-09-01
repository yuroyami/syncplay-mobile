package app.room.ui.rightcards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.room.ui.bottombar.RoomControlPanelCard
import app.theme.Motion
import app.theme.Radius
import app.theme.Space
import app.uicomponents.chromeSurface

/** A plain box, so the panels' AnimatedVisibility resolves outside the dock's column scope. */
@Composable
private fun PanelSlot(modifier: Modifier, enter: EnterTransition, exit: ExitTransition, content: @Composable () -> Unit) {
    Box(modifier) { content() }
}

/**
 * The side dock's contents: one panel at a time at a real reading width, clamped between 320dp
 * and 420dp at 38 percent of the window, sliding in from the end edge. On a tall window the panel
 * is a full-width sheet rising from the bottom. The control strip sits under the panel.
 */
@Composable
fun RoomSidePanels(modifier: Modifier = Modifier, tall: Boolean = false) {
    val viewmodel = LocalRoomViewmodel.current
    val ui = LocalRoomUiState.current
    val stateUserInfo by ui.tabCardUserInfo.collectAsState()
    val statePlaylist by ui.tabCardSharedPlaylist.collectAsState()
    val statePrefs by ui.tabCardRoomPreferences.collectAsState()
    val stateControls by ui.controlPanel.collectAsState()

    val density = LocalDensity.current
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val panelWidth = (windowWidth * 0.38f).coerceIn(320.dp, 420.dp).coerceAtMost(windowWidth)
    val shape = if (tall) RoundedCornerShape(topStart = Radius.panel, topEnd = Radius.panel) else Radius.panelShape

    val enter = if (tall) slideInVertically(Motion.move()) { it } else slideInHorizontally(Motion.move()) { it }
    val exit = if (tall) slideOutVertically(Motion.move()) { it } else slideOutHorizontally(Motion.move()) { it }

    Column(modifier, horizontalAlignment = Alignment.End) {
        PanelSlot(Modifier.weight(1f).then(if (tall) Modifier.fillMaxWidth() else Modifier.width(panelWidth)), enter, exit) {
            if (!viewmodel.isSoloMode) {
                AnimatedVisibility(stateUserInfo, Modifier.fillMaxHeight(), enter, exit) { CardUserInfo.UserInfoCard(shape) }
                AnimatedVisibility(statePlaylist, Modifier.fillMaxHeight(), enter, exit) { CardSharedPlaylist.SharedPlaylistCard(shape) }
            }
            AnimatedVisibility(statePrefs, Modifier.fillMaxHeight(), enter, exit) { CardRoomPrefs.InRoomSettingsCard(shape) }
        }
        AnimatedVisibility(stateControls, enter = expandVertically(Motion.move()), exit = shrinkVertically(Motion.move())) {
            Box(
                Modifier.padding(top = Space.gapTight)
                    .height(Space.rowTall)
                    .chromeSurface(Radius.panelShape)
                    .padding(horizontal = Space.gapTight),
            ) {
                RoomControlPanelCard(modifier = Modifier.fillMaxHeight())
            }
        }
    }
}
