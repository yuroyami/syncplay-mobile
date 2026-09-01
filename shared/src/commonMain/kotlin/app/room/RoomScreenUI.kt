package app.room

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import app.LocalGlobalViewmodel
import app.LocalRoomUiState
import app.preferences.Preferences.HUD_AUTO_HIDE
import app.preferences.Preferences.VIDEO_BACKGROUND_COLOR
import app.preferences.flow
import app.preferences.watchPref
import app.room.ui.bottombar.BlackContrastUnderlay
import app.room.ui.bottombar.PopupSeekToPosition.SeekToPositionPopup
import app.room.ui.bottombar.RoomBottomBarSection
import app.room.ui.chat.FadingMessageLayout
import app.room.ui.chat.RoomChatSection
import app.room.ui.misc.RoomBackgroundArtwork
import app.room.ui.misc.RoomGestureInterceptor
import app.room.ui.misc.RoomPlayButton
import app.room.ui.rightcards.RoomSidePanels
import app.room.ui.statinfo.RoomStatusInfoSection
import app.room.ui.tabs.ManagedRoomPopup
import app.room.ui.tabs.ManagedRoomPopupPurpose
import app.room.ui.tabs.RoomRail
import app.room.ui.tabs.RoomUnlockableLayout
import app.theme.LocalPalette
import app.theme.Motion
import app.theme.Space
import app.uicomponents.LocalHazeState
import app.uicomponents.frames.NoticeHost
import app.uicomponents.glassBackdropLayer
import app.utils.EnterRoomMode
import app.utils.platformCallback
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

/**
 * Primary focus target for D-pad and TV use: the play key, or the add button before a file
 * loads. Focus lands on it whenever the HUD shows under keyboard input.
 */
val LocalRoomInitialFocus = compositionLocalOf<FocusRequester?> { null }

/** The room: the video layer, then the HUD on the [RoomFrame] docks, then the notices. */
@Composable
fun RoomScreenUI(viewmodel: RoomViewmodel) {
    // Phones stay landscape in the room; the tall arrangement is for windows taller than wide.
    EnterRoomMode(false)

    val soloMode = remember { viewmodel.isSoloMode }
    val hasVideo by viewmodel.playerManager.hasVideo.collectAsState(initial = false)
    val isInPipMode by viewmodel.uiState.hasEnteredPipMode.collectAsState()
    val lockedMode by viewmodel.uiState.tabLock.collectAsState()
    val initialFocusRequester = remember { FocusRequester() }
    val window = LocalWindowInfo.current.containerSize
    val tall = window.height > window.width

    /* The room's own glass backdrop: only the video layer and the artwork. Glass in the same
     * window cannot sample the backdrop it lives inside, so in-room chrome blurs this capture. */
    val roomHazeState = rememberHazeState()

    // Over video the palette is pinned dark; the theme supplies only accent, gradient and status.
    val videoPalette = LocalPalette.current.overVideo()

    CompositionLocalProvider(
        LocalRoomUiState provides viewmodel.uiState,
        LocalRoomInitialFocus provides initialFocusRequester,
        LocalHazeState provides roomHazeState,
        LocalPalette provides videoPalette,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().glassBackdropLayer(roomHazeState)) {
                if (!hasVideo) RoomBackgroundArtwork()

                val playerIsReady by viewmodel.playerManager.isPlayerReady.collectAsState()
                if (playerIsReady) {
                    /* The letterbox behind the picture, black by default. Alpha keeps the surface
                     * composed while no video shows, and the colour sits after it in the chain so
                     * the no-video state stays invisible. */
                    val videoBackground by remember { VIDEO_BACKGROUND_COLOR.flow() }.collectAsState(initial = 0xFF000000.toInt())
                    viewmodel.player.VideoPlayer(
                        modifier = Modifier.fillMaxSize().alpha(if (hasVideo) 1f else 0f).background(Color(videoBackground)),
                        onPlayerReady = { platformCallback.mediaSessionInitialize() },
                    )
                }
            }

            when {
                isInPipMode -> Unit // PiP shows the video and nothing else.
                lockedMode -> RoomUnlockableLayout()
                else -> RoomHud(viewmodel, soloMode, hasVideo, tall, initialFocusRequester)
            }

            if (!isInPipMode) {
                // Notices sit above the HUD and outside its alpha: they show while it is hidden.
                NoticeHost(
                    queue = viewmodel.notices,
                    modifier = Modifier.align(Alignment.TopCenter)
                        .windowInsetsPadding(roomTopInsets())
                        .padding(top = Space.rowCompact + Space.gap)
                        .padding(horizontal = Space.gutter),
                )
                if (!soloMode) FadingMessageLayout()
            }
        }

        SeekToPositionPopup()
        ManagedRoomPopup(ManagedRoomPopupPurpose.CREATE_MANAGED_ROOM)
        ManagedRoomPopup(ManagedRoomPopupPurpose.IDENTIFY_AS_OPERATOR)

        val globalViewmodel = LocalGlobalViewmodel.current
        LaunchedEffect(null) {
            delay(600)
            if (!soloMode) viewmodel.uiState.toggleUserInfo(true)
            globalViewmodel.hasEnteredRoomOnce = true
        }
    }
}

/** The HUD: everything that fades, on the frame's docks, with the gesture layer above it. */
@Composable
private fun RoomHud(
    viewmodel: RoomViewmodel,
    soloMode: Boolean,
    hasVideo: Boolean,
    tall: Boolean,
    initialFocusRequester: FocusRequester,
) {
    val ui = viewmodel.uiState
    val isHUDVisible by ui.visibleHUD.collectAsState()
    val focusManager = LocalFocusManager.current
    val isKeyboardMode = LocalInputModeManager.current.inputMode == InputMode.Keyboard

    /* Under keyboard or D-pad input, focus lands on the primary control when the HUD shows; on
     * touch that would be jarring and could raise the soft keyboard. Focus drops on hide so the
     * composed-but-invisible controls keep no off-screen focus stop. */
    LaunchedEffect(isHUDVisible, hasVideo, isKeyboardMode) {
        if (isHUDVisible) {
            if (isKeyboardMode) {
                delay(150)
                runCatching { initialFocusRequester.requestFocus() }
            }
        } else {
            focusManager.clearFocus(force = true)
        }
    }

    // The HUD stays composed and fades: chat state survives a hide.
    val hudAlpha by animateFloatAsState(if (isHUDVisible) 1f else 0f, Motion.quick())
    val density = LocalDensity.current
    val isKeyboardOpen by rememberUpdatedState(WindowInsets.ime.getBottom(density) > 0)
    HudAutoHide(viewmodel, isHUDVisible, isKeyboardOpen, hasVideo)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(hudAlpha)
            .then(
                if (isHUDVisible) Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        // Typing: a stray tap only closes the keyboard. Otherwise it hides the HUD.
                        if (isKeyboardOpen) focusManager.clearFocus(force = true) else ui.visibleHUD.value = false
                    })
                } else Modifier
            )
            .pointerInput(Unit) {
                // Presses only: moves would restart the idle timer at pointer rate for nothing.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) ui.noteHudActivity()
                    }
                }
            }
            .onPreviewKeyEvent { ui.noteHudActivity(); false },
    ) {
        if (hasVideo) BlackContrastUnderlay()
        RoomFrame(
            tall = tall,
            topStart = if (soloMode) null else ({ RoomStatusInfoSection() }),
            rail = { RoomRail(horizontal = tall) },
            chat = if (soloMode) null else ({ RoomChatSection(modifier = Modifier.fillMaxSize()) }),
            side = { RoomSidePanels(Modifier.fillMaxSize(), tall = tall) },
            bottom = { RoomBottomBarSection(modifier = Modifier.fillMaxWidth()) },
            center = { RoomPlayButton(modifier = Modifier) },
        )
    }

    /* Above the HUD: with the HUD hidden it takes the touches that would otherwise reach the
     * still-composed controls; with it visible it attaches no pointer input at all. */
    RoomGestureInterceptor(modifier = Modifier.fillMaxSize())
}

/**
 * The auto-hide policy: after a few idle seconds the HUD hides, unless a panel or the keyboard
 * is open, the track is being scrubbed, a message is half-typed, or there is no video to see.
 */
@Composable
private fun HudAutoHide(viewmodel: RoomViewmodel, hudVisible: Boolean, keyboardOpen: Boolean, hasVideo: Boolean) {
    val autoHide by HUD_AUTO_HIDE.watchPref()
    val ui = viewmodel.uiState
    val activity by ui.hudActivity.collectAsState()
    val userInfo by ui.tabCardUserInfo.collectAsState()
    val playlist by ui.tabCardSharedPlaylist.collectAsState()
    val prefs by ui.tabCardRoomPreferences.collectAsState()
    val controls by ui.controlPanel.collectAsState()
    val gifs by ui.gifPanelVisible.collectAsState()
    val scrubbing by ui.scrubbing.collectAsState()
    val draft by ui.msg.collectAsState()
    val held = userInfo || playlist || prefs || controls || gifs || scrubbing || keyboardOpen || draft.isNotBlank()

    LaunchedEffect(autoHide, hudVisible, hasVideo, held, activity) {
        if (!autoHide || !hudVisible || !hasVideo || held) return@LaunchedEffect
        delay(Motion.hudIdleMs)
        ui.visibleHUD.value = false
    }
}
