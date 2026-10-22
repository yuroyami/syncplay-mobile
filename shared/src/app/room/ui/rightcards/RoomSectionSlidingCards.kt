package app.room.ui.rightcards

import kotlinx.coroutines.delay
import app.uicomponents.LocalIsTelevision
import app.room.LocalRoomRailFocus
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
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

/**
 * A plain box, so the AnimatedVisibility calls of the panels resolve to the top-level function,
 * not to the ColumnScope version inside the column of [RoomSidePanels].
 */
@Composable
private fun PanelSlot(modifier: Modifier, enter: EnterTransition, exit: ExitTransition, content: @Composable () -> Unit) {
    Box(modifier) { content() }
}

/**
 * How many times, 60 ms apart, focus tries to move into a panel, or back to the rail (the strip
 * of buttons that opens the panels).
 */
private const val PANEL_FOCUS_TRIES = 8

/** The moves a direction key or Tab makes. An open panel keeps focus against these. */
private val directionKeys = setOf(
    FocusDirection.Up, FocusDirection.Down, FocusDirection.Left, FocusDirection.Right,
    FocusDirection.Next, FocusDirection.Previous,
)

/**
 * The contents of the side dock (the side area of the screen that holds the panels). The dock
 * shows one panel at a time, at a real reading width: 38 percent of the window, kept between
 * 320dp and 420dp. The panel slides in from the right. Compose does not mirror this slide for
 * right-to-left layouts, where the dock is on the left. On a tall window, the panel is a
 * full-width sheet that rises from the bottom. The control strip sits under the panel.
 */
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

    /* A remote user who opens a panel wants its controls, so focus moves into the panel. The open
     * panel holds focus the way a dialog does: a direction that would leave it keeps focus inside,
     * and Back closes it. Otherwise a remote could leave the panel and find no key that leads back.
     * The spatial focus search alone also skips a panel whose rows do not line up with the button
     * that opened it. */
    val panelFocus = remember { FocusRequester() }
    val railFocus = LocalRoomRailFocus.current
    val remoteOrKeyboard = LocalIsTelevision.current || LocalInputModeManager.current.inputMode == InputMode.Keyboard
    /* Each panel, with the control that takes focus back when it closes. The control strip closes
     * when one of its panels opens, so the strip's own button takes focus back. The rail keeps its
     * focus target on the cell of the panel that was open last. */
    val panels = listOf(
        stateUserInfo to railFocus,
        (statePlaylist && sharedPlaylists) to railFocus,
        statePrefs to railFocus,
        stateTracks to ui.controlsFocus,
        stateGestures to ui.controlsFocus,
        stateSeekTo to ui.controlsFocus,
        stateAddMedia to ui.mediaKeyFocus,
    )
    val openPanel = panels.indexOfFirst { it.first }
    val holdFocus by rememberUpdatedState(openPanel >= 0)
    var panelHasFocus by remember { mutableStateOf(false) }
    var seenPanel by remember { mutableStateOf(openPanel) }
    LaunchedEffect(openPanel) {
        val closed = seenPanel
        seenPanel = openPanel
        // A panel that closes while focus is elsewhere, after a tap for example, takes no focus back.
        val handBack = openPanel < 0 && panelHasFocus
        if (openPanel != closed && remoteOrKeyboard && (openPanel >= 0 || handBack)) {
            val targets = if (openPanel >= 0) {
                listOf(panelFocus to FocusDirection.Enter)
            } else {
                listOfNotNull(panels.getOrNull(closed)?.second, railFocus).distinct().map { it to FocusDirection.Exit }
            }
            /* The panel slides in and out over a few frames, and the rail rebuilds during the
             * slide, so a target may not exist at the first request. So ask again, 60 ms apart, up
             * to PANEL_FOCUS_TRIES times. Stop at the first request that lands: a later one would
             * undo a key pressed meanwhile. */
            run tries@{
                repeat(PANEL_FOCUS_TRIES) {
                    delay(60)
                    if (targets.any { (target, direction) -> runCatching { target.requestFocus(direction) }.getOrDefault(false) }) return@tries
                }
            }
        }
    }

    Column(modifier, horizontalAlignment = Alignment.End) {
        PanelSlot(
            Modifier
                .weight(1f)
                .then(if (tall) Modifier.fillMaxWidth() else Modifier.width(panelWidth))
                .focusRequester(panelFocus)
                .onFocusChanged { panelHasFocus = it.hasFocus }
                .focusProperties {
                    onExit = { if (holdFocus && requestedFocusDirection in directionKeys) cancelFocusChange() }
                }
                .focusGroup(),
            enter,
            exit,
        ) {
            if (!viewmodel.isSoloMode) {
                AnimatedVisibility(stateUserInfo, Modifier.fillMaxHeight(), enter, exit) { CardUserInfo.UserInfoCard(shape) }
                AnimatedVisibility(statePlaylist && sharedPlaylists, Modifier.fillMaxHeight(), enter, exit) { CardSharedPlaylist.SharedPlaylistCard(shape) }
            }
            AnimatedVisibility(statePrefs, Modifier.fillMaxHeight(), enter, exit) { CardRoomPrefs.InRoomSettingsCard(shape) }
            // Of the tool panels below, only the tracks panel fills the dock. The others wrap their
            // content.
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
