package app.room.ui.rightcards

import kotlinx.coroutines.delay
import app.uicomponents.LocalIsTelevision
import app.room.LocalRoomRailFocus
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.focusGroup
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
/** How many times, 60ms apart, a panel tries to hand focus in or back to the rail. */
private const val PANEL_FOCUS_TRIES = 8

@Composable
fun RoomSidePanels(modifier: Modifier = Modifier, tall: Boolean = false) {
    val viewmodel = LocalRoomViewmodel.current
    val ui = LocalRoomUiState.current
    val stateUserInfo by ui.tabCardUserInfo.collectAsState()
    val statePlaylist by ui.tabCardSharedPlaylist.collectAsState()
    val sharedPlaylists by viewmodel.protocol.supportsSharedPlaylists.collectAsState()
    val statePrefs by ui.tabCardRoomPreferences.collectAsState()
    val stateTracks by ui.tabCardTracks.collectAsState()
    val stateGestures by ui.tabCardGestures.collectAsState()
    val stateSeekTo by ui.tabCardSeekTo.collectAsState()
    val stateAddMedia by ui.tabCardAddMedia.collectAsState()
    val stateControls by ui.controlPanel.collectAsState()

    val density = LocalDensity.current
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val panelWidth = (windowWidth * 0.38f).coerceIn(320.dp, 420.dp).coerceAtMost(windowWidth)
    val shape = if (tall) RoundedCornerShape(topStart = Radius.panel, topEnd = Radius.panel) else Radius.panelShape

    val enter = if (tall) slideInVertically(Motion.move()) { it } else slideInHorizontally(Motion.move()) { it }
    val exit = if (tall) slideOutVertically(Motion.move()) { it } else slideOutHorizontally(Motion.move()) { it }

    /* A remote opens a panel and its controls are where it wants to be, so focus follows it in,
     * and back out to the rail when it closes. Spatial search alone skips a panel whose rows do
     * not line up with the rail cell that opened it. */
    val panelFocus = remember { FocusRequester() }
    val railFocus = LocalRoomRailFocus.current
    val remoteOrKeyboard = LocalIsTelevision.current || LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val openPanel = listOf(
        stateUserInfo, statePlaylist && sharedPlaylists, statePrefs,
        stateTracks, stateGestures, stateSeekTo, stateAddMedia,
    ).indexOfFirst { it }
    var seenPanel by remember { mutableStateOf(openPanel) }
    LaunchedEffect(openPanel) {
        if (openPanel != seenPanel && remoteOrKeyboard) {
            /* The panel slides in and out over a few frames, and the rail rebuilds as it goes, so
             * the target may not exist on the first ask. Try until it takes, then stop. */
            repeat(PANEL_FOCUS_TRIES) {
                delay(60)
                val target = if (openPanel >= 0) panelFocus else railFocus
                val direction = if (openPanel >= 0) FocusDirection.Enter else FocusDirection.Exit
                if (target != null && runCatching { target.requestFocus(direction) }.getOrDefault(false)) return@repeat
            }
        }
        seenPanel = openPanel
    }

    Column(modifier, horizontalAlignment = Alignment.End) {
        PanelSlot(
            Modifier
                .weight(1f)
                .then(if (tall) Modifier.fillMaxWidth() else Modifier.width(panelWidth))
                .focusRequester(panelFocus)
                .focusGroup(),
            enter,
            exit,
        ) {
            if (!viewmodel.isSoloMode) {
                AnimatedVisibility(stateUserInfo, Modifier.fillMaxHeight(), enter, exit) { CardUserInfo.UserInfoCard(shape) }
                AnimatedVisibility(statePlaylist && sharedPlaylists, Modifier.fillMaxHeight(), enter, exit) { CardSharedPlaylist.SharedPlaylistCard(shape) }
            }
            AnimatedVisibility(statePrefs, Modifier.fillMaxHeight(), enter, exit) { CardRoomPrefs.InRoomSettingsCard(shape) }
            // The tool panels below wrap their content instead of filling the dock.
            AnimatedVisibility(stateTracks, Modifier.fillMaxHeight(), enter, exit) { CardTracks.TracksPanel(shape) }
            AnimatedVisibility(stateGestures, enter = enter, exit = exit) { CardGestures.GesturesPanel(shape) }
            AnimatedVisibility(stateSeekTo, enter = enter, exit = exit) { CardSeekTo.SeekToPanel(shape) }
            AnimatedVisibility(stateAddMedia, enter = enter, exit = exit) { CardAddMedia.AddMediaPanel(shape) }
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
