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
import androidx.compose.ui.unit.dp
import app.LocalGlobalViewmodel
import app.LocalRoomUiState
import app.preferences.Preferences.VIDEO_BACKGROUND_COLOR
import app.preferences.Preferences.HUD_AUTO_HIDE_SECONDS
import app.preferences.Preferences.ROOM_ALLOW_PORTRAIT
import app.preferences.flow
import app.preferences.watchPref
import app.room.ui.bottombar.BlackContrastUnderlay
import app.room.ui.bottombar.RoomBottomBarSection
import app.room.ui.chat.FadingMessageLayout
import app.room.ui.chat.RoomChatSection
import app.room.ui.misc.RoomBackgroundArtwork
import app.room.ui.misc.RoomGestureInterceptor
import app.room.ui.misc.RoomTransportKeys
import app.room.ui.rightcards.RoomSidePanels
import app.room.ui.statinfo.RoomStatusInfoSection
import app.room.ui.tabs.ManagedRoomModal
import app.room.ui.tabs.RoomRail
import app.room.ui.tabs.RoomUnlockableLayout
import app.theme.LocalPalette
import app.theme.Motion
import app.theme.Space
import app.uicomponents.GlassDemand
import app.uicomponents.LocalGlassDemand
import app.uicomponents.LocalGlassSuspended
import app.uicomponents.LocalHazeState
import app.uicomponents.frames.NoticeHost
import app.uicomponents.glassBackdropLayer
import app.utils.EnterRoomMode
import app.utils.platformCallback
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import app.preferences.settings.AskModal
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_leave_question
import syncplaymobile.shared.generated.resources.room_overflow_leave_room
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.roundToInt
import syncplaymobile.shared.generated.resources.room_untrusted_ask_always
import syncplaymobile.shared.generated.resources.room_untrusted_ask_once
import syncplaymobile.shared.generated.resources.room_untrusted_ask_body
import syncplaymobile.shared.generated.resources.room_untrusted_ask_title
import app.theme.palette
import app.theme.Type
import app.uicomponents.controls.Text
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.PrimaryAction
import app.uicomponents.frames.ModalSize
import app.uicomponents.frames.Modal

/**
 * Primary focus target for D-pad and TV use: the play key, or the add button before a file
 * loads. Focus lands on it whenever the HUD shows under keyboard input.
 */
val LocalRoomInitialFocus = compositionLocalOf<FocusRequester?> { null }

/** The room: the video layer, then the HUD on the [RoomFrame] docks, then the notices. */
@Composable
fun RoomScreenUI(viewmodel: RoomViewmodel) {
    // Phones stay landscape in the room; the tall arrangement is for windows taller than wide.
    // The room is held in landscape unless the user asked otherwise; the arrangement already
    // follows the window, so portrait lays out on its own once rotation is allowed.
    EnterRoomMode(portrait = ROOM_ALLOW_PORTRAIT.watchPref().value)

    val soloMode = remember { viewmodel.isSoloMode }
    val hasVideo by viewmodel.playerManager.hasVideo.collectAsState(initial = false)
    val isInPipMode by viewmodel.uiState.hasEnteredPipMode.collectAsState()
    val lockedMode by viewmodel.uiState.tabLock.collectAsState()
    val initialFocusRequester = remember { FocusRequester() }
    val window = LocalWindowInfo.current.containerSize
    val tall = window.height > window.width
    // A window under 480dp tall cannot hold the rail as a column beside the transport.
    val railHorizontal = tall || with(LocalDensity.current) { window.height.toDp() } < 480.dp

    /* The room's own glass backdrop: only the video layer and the artwork. Glass in the same
     * window cannot sample the backdrop it lives inside, so in-room chrome blurs this capture. */
    val roomHazeState = rememberHazeState()

    // Over video the palette is pinned dark; the theme supplies only accent, gradient and status.
    val videoPalette = LocalPalette.current.overVideo()

    // The room's panels sample the room's own capture, so they arm the room's own demand: on
    // the app-wide one they kept the whole-screen backdrop capturing for nothing.
    val roomGlassDemand = remember { GlassDemand() }
    CompositionLocalProvider(
        LocalRoomUiState provides viewmodel.uiState,
        LocalRoomInitialFocus provides initialFocusRequester,
        LocalHazeState provides roomHazeState,
        LocalGlassDemand provides roomGlassDemand,
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
                        modifier = Modifier
                            .fillMaxSize()
                            // Reported so picture-in-picture can morph out of the picture itself
                            // instead of appearing from nowhere.
                            .onGloballyPositioned { layout ->
                                val origin = layout.positionInWindow()
                                VideoBounds.report(
                                    left = origin.x.roundToInt(),
                                    top = origin.y.roundToInt(),
                                    right = (origin.x + layout.size.width).roundToInt(),
                                    bottom = (origin.y + layout.size.height).roundToInt(),
                                )
                            }
                            .alpha(if (hasVideo) 1f else 0f)
                            .background(Color(videoBackground)),
                        onPlayerReady = { platformCallback.mediaSessionInitialize() },
                    )
                }
            }

            when {
                isInPipMode -> Unit // PiP shows the video and nothing else.
                lockedMode -> RoomUnlockableLayout()
                else -> RoomHud(viewmodel, soloMode, hasVideo, tall, railHorizontal, initialFocusRequester)
            }

            if (!isInPipMode) {
                // Notices sit above the HUD and outside its alpha: they show while it is hidden.
                // They stack on the centre line, under the status line (and under the rail row
                // on a tall window).
                NoticeHost(
                    queue = viewmodel.notices,
                    modifier = Modifier.align(Alignment.TopCenter)
                        .windowInsetsPadding(roomTopInsets())
                        .padding(top = if (tall) Space.row * 2 + Space.gap * 2 else Space.row + Space.gap)
                        .padding(horizontal = Space.gutter),
                )
                if (!soloMode) FadingMessageLayout()
            }
        }

        LeaveRoomAsk(viewmodel)
        ManagedRoomModal()
        UntrustedUrlAsk(viewmodel)

        val globalViewmodel = LocalGlobalViewmodel.current
        // The roster opens once, on the first room of the session, so a newcomer sees who is here.
        // It waited on a magic delay and then opened on every room, including the second one in a
        // row. It now waits for the thing it was really waiting for, which is the roster having
        // someone in it, and only opens if this session has not seen a room yet.
        val firstRoom = remember { !globalViewmodel.hasEnteredRoomOnce }
        val roster by viewmodel.session.userList.collectAsState()
        LaunchedEffect(firstRoom, soloMode, roster.isNotEmpty()) {
            globalViewmodel.hasEnteredRoomOnce = true
            if (!firstRoom || soloMode || roster.isEmpty()) return@LaunchedEffect
            viewmodel.uiState.toggleUserInfo(true)
        }
    }
}

/**
 * The question a peer-pushed link from an unknown host raises. Refusing outright was a dead end:
 * the file never played and the only way forward was a settings field the user had to guess the
 * syntax of.
 */
@Composable
private fun UntrustedUrlAsk(viewmodel: RoomViewmodel) {
    val pending by viewmodel.playlistManager.pendingUntrusted.collectAsState()
    val asked = pending ?: return
    Modal(
        open = true,
        onDismiss = { viewmodel.playlistManager.dismissPendingUrl() },
        title = stringResource(Res.string.room_untrusted_ask_title, asked.domain),
        size = ModalSize.Ask,
        actions = {
            SecondaryAction(
                text = stringResource(Res.string.room_untrusted_ask_once),
                onClick = { viewmodel.playlistManager.allowPendingUrl(always = false) },
            )
            PrimaryAction(
                text = stringResource(Res.string.room_untrusted_ask_always, asked.domain),
                onClick = { viewmodel.playlistManager.allowPendingUrl(always = true) },
            )
        },
    ) {
        Text(
            text = stringResource(Res.string.room_untrusted_ask_body, asked.domain),
            style = Type.note,
            color = palette.inkDim,
        )
    }
}

/** The leave question, raised by the rail, system back or desktop Escape. */
@Composable
private fun LeaveRoomAsk(viewmodel: RoomViewmodel) {
    val ui = viewmodel.uiState
    val asking by ui.askLeave.collectAsState()
    if (!asking) return
    val open = remember { mutableStateOf(true) }
    AskModal(
        open = open,
        title = stringResource(Res.string.room_overflow_leave_room),
        text = stringResource(Res.string.room_leave_question),
        destructive = true,
        onYes = {
            ui.askLeave.value = false
            viewmodel.viewModelScope.launch(Dispatchers.Main) { viewmodel.goHome() }
        },
        onNo = { ui.askLeave.value = false },
    )
}

/** The HUD: everything that fades, on the frame's docks, with the gesture layer above it. */
@Composable
private fun RoomHud(
    viewmodel: RoomViewmodel,
    soloMode: Boolean,
    hasVideo: Boolean,
    tall: Boolean,
    railHorizontal: Boolean,
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

    // While the HUD is faded out its glass releases the capture; it re-arms as the fade begins.
    CompositionLocalProvider(LocalGlassSuspended provides (hudAlpha == 0f)) {
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
            railHorizontal = railHorizontal,
            status = if (soloMode) null else ({ RoomStatusInfoSection() }),
            rail = { RoomRail(horizontal = railHorizontal) },
            chat = if (soloMode) null else ({ RoomChatSection(modifier = Modifier.fillMaxSize()) }),
            side = { RoomSidePanels(Modifier.fillMaxSize(), tall = tall) },
            bottom = { RoomBottomBarSection(modifier = Modifier.fillMaxWidth()) },
            center = { RoomTransportKeys() },
        )
    }
    }

    /* Above the HUD: with the HUD hidden it takes the touches that would otherwise reach the
     * still-composed controls; with it visible it attaches no pointer input at all. */
    RoomGestureInterceptor(modifier = Modifier.fillMaxSize())
}

/**
 * The auto-hide policy: after the configured idle seconds the HUD hides, unless a panel or the keyboard
 * is open, the track is being scrubbed, a message is half-typed, or there is no video to see.
 */
@Composable
private fun HudAutoHide(viewmodel: RoomViewmodel, hudVisible: Boolean, keyboardOpen: Boolean, hasVideo: Boolean) {
    val idleSeconds by HUD_AUTO_HIDE_SECONDS.watchPref()
    val ui = viewmodel.uiState
    val activity by ui.hudActivity.collectAsState()
    val userInfo by ui.tabCardUserInfo.collectAsState()
    val playlist by ui.tabCardSharedPlaylist.collectAsState()
    val prefs by ui.tabCardRoomPreferences.collectAsState()
    val tracks by ui.tabCardTracks.collectAsState()
    val gestures by ui.tabCardGestures.collectAsState()
    val seekTo by ui.tabCardSeekTo.collectAsState()
    val addMedia by ui.tabCardAddMedia.collectAsState()
    val controls by ui.controlPanel.collectAsState()
    val gifs by ui.gifPanelVisible.collectAsState()
    val scrubbing by ui.scrubbing.collectAsState()
    val draft by ui.msg.collectAsState()
    val held = userInfo || playlist || prefs || tracks || gestures || seekTo || addMedia || controls || gifs || scrubbing || keyboardOpen || draft.isNotBlank()

    LaunchedEffect(idleSeconds, hudVisible, hasVideo, held, activity) {
        if (idleSeconds <= 0 || !hudVisible || !hasVideo || held) return@LaunchedEffect
        delay(idleSeconds * 1000L)
        ui.visibleHUD.value = false
    }
}
