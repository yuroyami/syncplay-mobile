package app.room

import app.room.ui.chat.LocalChatMediaSize
import app.room.ui.chat.chatMediaCellSize

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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
import app.i18n.strings
import app.preferences.Preferences.VIDEO_BACKGROUND_COLOR
import app.preferences.Preferences.HUD_AUTO_HIDE_SECONDS
import app.preferences.Preferences.ROOM_ALLOW_PORTRAIT
import app.preferences.flow
import app.preferences.watchPref
import app.room.ui.bottombar.BlackContrastUnderlay
import app.room.ui.bottombar.TopContrastUnderlay
import app.room.ui.bottombar.RoomBottomBarSection
import app.room.ui.chat.FadingMessageLayout
import app.room.ui.chat.RoomChatSection
import app.room.ui.misc.KeyboardNotchShield
import app.room.ui.misc.RoomBackgroundArtwork
import app.room.ui.misc.RoomGestureInterceptor
import app.room.ui.misc.RoomTransportKeys
import app.room.ui.rightcards.RoomSidePanels
import app.room.ui.statinfo.RoomStatusInfoSection
import app.room.ui.tabs.ManagedRoomModal
import app.room.ui.tabs.RoomRail
import app.room.ui.tabs.RoomUnlockableLayout
import app.theme.LocalPalette
import app.theme.LocalSurfacePalette
import app.theme.Motion
import app.theme.Space
import app.uicomponents.GlassDemand
import app.uicomponents.LocalGlassDemand
import app.uicomponents.LocalGlassSuspended
import app.uicomponents.LocalHazeState
import app.uicomponents.LocalIsTelevision
import app.uicomponents.LocalScreenReaderActive
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.roundToInt
import app.theme.palette
import app.theme.Type
import app.uicomponents.controls.Text
import app.uicomponents.controls.ProgressBar
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.PrimaryAction
import app.uicomponents.frames.ModalSize
import app.uicomponents.frames.Modal
import app.uicomponents.DroppedMedia
import app.uicomponents.MediaDropOverlay
import app.uicomponents.MediaDropTarget
import app.uicomponents.mediaDropTarget
import app.utils.timestampFromMillis
import androidx.compose.ui.text.font.FontFamily
import app.protocol.network.CertificatePins
import app.preferences.Preferences
import app.preferences.value
import app.uicomponents.PopupMediaDirs.MediaDirsPopup
import app.utils.ioDispatcher
import app.utils.mediaFileKitType
import androidx.compose.runtime.setValue
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher

/**
 * The main focus target for D-pad and TV input: the play button, or the add button before a file
 * loads. Focus moves to it each time the HUD (the controls over the video) shows under keyboard
 * input.
 */
val LocalRoomInitialFocus = compositionLocalOf<FocusRequester?> { null }

/**
 * The rail cell that gets focus back when a panel closes: the cell that opened the panel, or else
 * the first cell. The rail is the strip of buttons that opens the panels.
 */
val LocalRoomRailFocus = compositionLocalOf<FocusRequester?> { null }

/**
 * The room screen. A room is the group of people watching together. The screen has three layers:
 * the video, the HUD on the [RoomFrame] docks (the areas that hold the controls), and the notices.
 */
@Composable
fun RoomScreenUI(viewmodel: RoomViewmodel) {
    // The room stays in landscape unless the user allows portrait. The layout follows the window
    // shape, so a portrait window gets the tall layout with no extra code.
    EnterRoomMode(portrait = ROOM_ALLOW_PORTRAIT.watchPref().value)

    val soloMode = remember { viewmodel.isSoloMode }
    val hasVideo by viewmodel.playerManager.hasVideo.collectAsState(initial = false)
    val isInPipMode by viewmodel.uiState.hasEnteredPipMode.collectAsState()
    val lockedMode by viewmodel.uiState.tabLock.collectAsState()
    val pointerHidden by viewmodel.uiState.pointerHidden.collectAsState()
    val initialFocusRequester = remember { FocusRequester() }
    val railFocusRequester = remember { FocusRequester() }
    val measuredChatMediaSize by viewmodel.uiState.chatMediaSizeDp.collectAsState()
    val window = LocalWindowInfo.current.containerSize
    val tall = window.height > window.width
    // A window under 480dp tall cannot fit the rail as a column together with the bottom bar.
    val railHorizontal = tall || with(LocalDensity.current) { window.height.toDp() } < 480.dp

    /* The backdrop for the room's glass (the frosted blur behind panels): only the video layer and
     * the artwork. A glass surface cannot blur a backdrop that contains the surface itself, so
     * the room's controls blur this capture. */
    val roomHazeState = rememberHazeState()

    // Over video the palette is always dark. The theme supplies only the accent, gradient and
    // status colors.
    val videoPalette = LocalPalette.current.overVideo()

    // The room's panels blur the room's own capture, so they register demand on the room's own
    // GlassDemand. Demand on the app-wide one would keep the whole-screen capture running for
    // nothing.
    val roomGlassDemand = remember { GlassDemand() }
    CompositionLocalProvider(
        LocalChatMediaSize provides if (measuredChatMediaSize > 0f) measuredChatMediaSize.dp else chatMediaCellSize(with(LocalDensity.current) {
            window.width.toDp() * if (tall) 1f else 0.36f
        }),
        LocalRoomUiState provides viewmodel.uiState,
        LocalRoomInitialFocus provides initialFocusRequester,
        LocalRoomRailFocus provides railFocusRequester,
        LocalHazeState provides roomHazeState,
        LocalGlassDemand provides roomGlassDemand,
        LocalPalette provides videoPalette,
        LocalSurfacePalette provides videoPalette,
    ) {
        // A file or link dropped onto the room opens like one picked with the add key (desktop).
        val drop = remember(viewmodel) { MediaDropTarget(viewmodel::onMediaDrop) }
        Box(Modifier.fillMaxSize().roomPointer(viewmodel.uiState, pointerHidden).mediaDropTarget(drop)) {
            Box(Modifier.matchParentSize().glassBackdropLayer(roomHazeState)) {
                if (!hasVideo) RoomBackgroundArtwork()

                val playerIsReady by viewmodel.playerManager.isPlayerReady.collectAsState()
                if (playerIsReady) {
                    /* The letterbox color behind the picture, black by default. The alpha keeps the
                     * surface composed while no video shows. The background comes after the alpha
                     * in the chain, so the background is invisible too while there is no video. */
                    val videoBackground by remember { VIDEO_BACKGROUND_COLOR.flow() }.collectAsState(initial = 0xFF000000.toInt())
                    viewmodel.player.VideoPlayer(
                        modifier = Modifier
                            .fillMaxSize()
                            // Reported so that the picture-in-picture animation starts from the
                            // video's own rectangle.
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
                        onPlayerReady = { platformCallback.mediaSessionInitialize(viewmodel) },
                    )
                }
            }

            when {
                isInPipMode -> Unit // PiP shows the video and nothing else.
                lockedMode -> RoomUnlockableLayout()
                else -> RoomHud(viewmodel, soloMode, hasVideo, tall, railHorizontal, initialFocusRequester)
            }

            if (!isInPipMode) {
                // Notices and unseen chat lines share one column on the center line, under the
                // status line (and under the rail row on a tall window). The room's notices come
                // first and chat lines follow. Both sit above the HUD and outside its alpha, so
                // they show while the HUD is hidden. The center is where a viewer is looking.
                Column(
                    modifier = Modifier.align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(roomTopInsets())
                        .padding(top = if (tall) Space.row * 2 + Space.gap * 2 else Space.row + Space.gap)
                        .padding(horizontal = Space.gutter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.gapTight),
                ) {
                    NoticeHost(queue = viewmodel.notices)
                    if (!soloMode) FadingMessageLayout()
                }
            }

            MediaDropOverlay(drop) { media ->
                when (media) {
                    is DroppedMedia.File -> strings.roomDropPlayFile(media.name)
                    is DroppedMedia.Link -> strings.roomDropPlayLink
                }
            }
        }

        LeaveRoomAsk(viewmodel)
        ManagedRoomModal()
        UntrustedUrlAsk(viewmodel)
        CertificateAsk(viewmodel)
        MissingFileAsk(viewmodel)
        ResumeAsk(viewmodel)
        PlaylistRestoreAsk(viewmodel)

        val globalViewmodel = LocalGlobalViewmodel.current
        // The user list opens once, in the first room since the app started, so a newcomer sees
        // who is here. It waits for the roster to have someone in it, not for a fixed delay.
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
 * Asks whether to play a link from a host that is not trusted, when another user added the link.
 * Without this question, the file would never play, and the only way forward would be a settings
 * field with a syntax to guess.
 */
@Composable
private fun UntrustedUrlAsk(viewmodel: RoomViewmodel) {
    val pending by viewmodel.playlistManager.pendingUntrusted.collectAsState()
    val asked = pending ?: return
    Modal(
        open = true,
        onDismiss = { viewmodel.playlistManager.dismissPendingUrl() },
        title = strings.roomUntrustedAskTitle(asked.domain),
        size = ModalSize.Ask,
        actions = {
            SecondaryAction(
                text = strings.roomUntrustedAskOnce,
                onClick = { viewmodel.playlistManager.allowPendingUrl(always = false) },
            )
            PrimaryAction(
                text = strings.roomUntrustedAskAlways(asked.domain),
                onClick = { viewmodel.playlistManager.allowPendingUrl(always = true) },
            )
        },
    ) {
        Text(
            text = strings.roomUntrustedAskBody(asked.domain),
            style = Type.note,
            color = palette.inkDim,
        )
    }
}

/**
 * Asks whether to trust a server certificate that no authority signed, such as the one of a room
 * hosted in the app. The person compares the fingerprint with the host's hosting panel. A changed
 * certificate for a server that was trusted before gets a warning instead. Nothing connects until
 * the person answers.
 */
@Composable
internal fun CertificateAsk(viewmodel: RoomViewmodel) {
    val network = viewmodel.networkManager
    val asked by network.untrustedCertificate.collectAsState()
    val certificate = asked ?: return
    val changed = certificate.pinned != null
    Modal(
        open = true,
        onDismiss = {},
        dismissable = false,
        title = if (changed) strings.roomTlsChangedTitle else strings.roomTlsTrustTitle,
        size = ModalSize.Ask,
        actions = {
            SecondaryAction(
                text = strings.roomOverflowLeaveRoom,
                onClick = {
                    network.untrustedCertificate.value = null
                    viewmodel.viewModelScope.launch(Dispatchers.Main) { viewmodel.goHome() }
                },
            )
            PrimaryAction(
                text = strings.roomTlsTrust,
                onClick = {
                    network.untrustedCertificate.value = null
                    viewmodel.viewModelScope.launch {
                        CertificatePins.pin(certificate.host, certificate.port, certificate.fingerprint)
                        network.reconnectNow()
                    }
                },
            )
        },
    ) {
        Text(
            text = if (changed) strings.roomTlsChangedBody(certificate.host) else strings.roomTlsTrustBody(certificate.host),
            style = Type.note,
            color = if (changed) palette.warn else palette.inkDim,
        )
        Text(
            text = CertificatePins.fingerprintLines(certificate.fingerprint),
            style = Type.value.copy(fontFamily = FontFamily.Monospace),
            color = palette.ink,
            modifier = Modifier.padding(top = Space.gap),
        )
    }
}

/**
 * Offers a way forward when the picked playlist entry is not on this device: pick the file by
 * hand, or set the media folders. After the folders change, the entry is tried again.
 */
@Composable
private fun MissingFileAsk(viewmodel: RoomViewmodel) {
    val playlist = viewmodel.playlistManager
    val missing by playlist.missingFile.collectAsState()
    val foldersOpen = remember { mutableStateOf(false) }
    val picker = rememberFilePickerLauncher(type = mediaFileKitType) { file ->
        file ?: return@rememberFilePickerLauncher
        viewmodel.viewModelScope.launch(ioDispatcher) { playlist.locateMissingFile(file) }
    }
    // The folders when the list opened, so closing it retries only after a change.
    var foldersAtOpen by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(foldersOpen.value) {
        if (foldersOpen.value) {
            foldersAtOpen = Preferences.MEDIA_DIRECTORIES.value()
        } else {
            val before = foldersAtOpen ?: return@LaunchedEffect
            foldersAtOpen = null
            if (Preferences.MEDIA_DIRECTORIES.value() != before) {
                viewmodel.viewModelScope.launch(ioDispatcher) { playlist.retrySelectedEntry() }
            }
        }
    }
    MediaDirsPopup(foldersOpen)

    val asked = missing ?: return
    Modal(
        open = true,
        onDismiss = { playlist.dismissMissingFile() },
        title = strings.roomSharedPlaylistMissingTitle,
        size = ModalSize.Ask,
        actions = {
            SecondaryAction(
                text = strings.roomSharedPlaylistButtonSetMediaDirectories,
                onClick = { playlist.dismissMissingFile(); foldersOpen.value = true },
            )
            PrimaryAction(
                text = strings.roomSharedPlaylistFindFile,
                onClick = { playlist.dismissMissingFile(); picker.launch() },
            )
        },
    ) {
        Text(
            text = if (asked.noFolders) strings.roomSharedPlaylistNoDirectories else strings.roomSharedPlaylistNotFound(asked.name),
            style = Type.note,
            color = palette.inkDim,
        )
    }
}

/** Offers to restore the playlist when the room comes back from a dropped connection without it. */
@Composable
private fun PlaylistRestoreAsk(viewmodel: RoomViewmodel) {
    val offer by viewmodel.playlistManager.restoreOffer.collectAsState()
    if (offer == null) return
    val playlist = viewmodel.playlistManager
    Modal(
        open = true,
        onDismiss = { playlist.dismissRestoreOffer() },
        title = strings.roomSharedPlaylistRestoreTitle,
        size = ModalSize.Ask,
        actions = {
            SecondaryAction(text = strings.no, onClick = { playlist.dismissRestoreOffer() })
            PrimaryAction(text = strings.roomSharedPlaylistRestore, onClick = { playlist.restoreLostPlaylist() })
        },
    ) {
        Text(text = strings.roomSharedPlaylistRestoreBody, style = Type.note, color = palette.inkDim)
    }
}

/** In solo mode, asks whether to continue a file from where the user left it. */
@Composable
private fun ResumeAsk(viewmodel: RoomViewmodel) {
    val point by viewmodel.resume.offer.collectAsState()
    val offered = point ?: return
    Modal(
        open = true,
        onDismiss = { viewmodel.resume.startOver() },
        title = strings.roomResumeTitle,
        size = ModalSize.Ask,
        actions = {
            SecondaryAction(
                text = strings.roomResumeRestart,
                onClick = { viewmodel.resume.startOver() },
            )
            PrimaryAction(
                text = strings.roomResumeContinue,
                onClick = { viewmodel.resume.continueFromOffer() },
            )
        },
    ) {
        Text(
            text = strings.roomResumeBody(offered.fileName, timestampFromMillis(offered.positionMs)),
            style = Type.note,
            color = palette.inkDim,
        )
    }
}

/**
 * Asks whether to leave the room. The rail, the system back action and Escape on desktop raise
 * the question.
 */
@Composable
private fun LeaveRoomAsk(viewmodel: RoomViewmodel) {
    val ui = viewmodel.uiState
    val asking by ui.askLeave.collectAsState()
    if (!asking) return
    val open = remember { mutableStateOf(true) }
    AskModal(
        open = open,
        title = strings.roomOverflowLeaveRoom,
        text = strings.roomLeaveQuestion,
        destructive = true,
        onYes = {
            ui.askLeave.value = false
            viewmodel.viewModelScope.launch(Dispatchers.Main) { viewmodel.goHome() }
        },
        onNo = { ui.askLeave.value = false },
    )
}

/** The HUD: every control that fades, on the [RoomFrame] docks, with the gesture layer above. */
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
    val playerIsReady by viewmodel.playerManager.isPlayerReady.collectAsState()
    val isHUDVisible by ui.visibleHUD.collectAsState()
    val focusManager = LocalFocusManager.current
    /* A television counts as keyboard input from the first frame. Compose only switches the input
     * mode to keyboard after the first key press, and the HUD can appear before that. */
    val isKeyboardMode = LocalIsTelevision.current || LocalInputModeManager.current.inputMode == InputMode.Keyboard

    /* Under keyboard or D-pad input, focus moves to the main control when the HUD shows. On touch
     * that would be jarring and could raise the soft keyboard. Focus is cleared on hide, so the
     * hidden controls (still composed) keep no focus. */
    LaunchedEffect(isHUDVisible, hasVideo, isKeyboardMode, playerIsReady) {
        if (isHUDVisible) {
            if (isKeyboardMode && playerIsReady) {
                delay(150)
                runCatching { initialFocusRequester.requestFocus() }
            }
        } else {
            focusManager.clearFocus(force = true)
        }
    }

    // The HUD stays composed and only fades, so the chat state survives a hide.
    val hudAlpha = animateFloatAsState(if (isHUDVisible) 1f else 0f, Motion.quick())
    val density = LocalDensity.current
    /* hudHidden uses derivedStateOf, so the glass suspension wakes this scope only when the value
     * flips, not on every frame of the fade. The keyboard flag must not use derivedStateOf.
     * WindowInsets.ime is snapshot state on Android, but maybe not on other platforms. There, a
     * derived read would freeze at its first value, and the HUD would auto-hide over an open
     * keyboard. */
    val hudHidden by remember { derivedStateOf { hudAlpha.value == 0f } }
    val isKeyboardOpen by rememberUpdatedState(WindowInsets.ime.getBottom(density) > 0)
    HudAutoHide(viewmodel, isHUDVisible, isKeyboardOpen, hasVideo)

    // While the HUD is fully faded out, its glass releases the capture. The capture starts again
    // as soon as the fade-in begins.
    CompositionLocalProvider(LocalGlassSuspended provides hudHidden) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            /* Modifier.alpha, not a graphicsLayer block. A graphicsLayer block would defer the
             * state read, but it keeps a full-screen offscreen layer under the whole HUD (glass
             * surfaces included) for the life of the room. alpha() clips to the layer and skips
             * the layer at 1f, which is the usual state. */
            .alpha(hudAlpha.value)
            .then(
                if (isHUDVisible) Modifier.pointerInput(playerIsReady) {
                    detectTapGestures(onTap = {
                        // While typing, a stray tap only closes the keyboard. Otherwise the tap
                        // hides the HUD.
                        if (isKeyboardOpen) focusManager.clearFocus(force = true)
                        else if (playerIsReady) ui.visibleHUD.value = false
                    })
                } else Modifier
            )
            .pointerInput(Unit) {
                // Presses only. Counting moves would restart the idle timer on every pointer event.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) ui.noteHudActivity()
                    }
                }
            }
            .onPreviewKeyEvent { ui.noteHudActivity(); false },
    ) {
        if (hasVideo) {
            BlackContrastUnderlay()
            TopContrastUnderlay()
        }
        RoomFrame(
            tall = tall,
            railHorizontal = railHorizontal,
            controls = Modifier.holdsHudWhileHovered(ui),
            status = if (soloMode || !playerIsReady) null else ({ RoomStatusInfoSection() }),
            rail = { RoomRail(horizontal = railHorizontal) },
            chat = if (soloMode) null else ({ RoomChatSection(modifier = Modifier.fillMaxSize()) }),
            // Room creation waits until the previous player engine has shut down. Until the new
            // player is ready, chat and navigation stay usable, but the player tools are not
            // composed.
            side = if (playerIsReady) ({ RoomSidePanels(Modifier.fillMaxSize(), tall = tall) }) else null,
            bottom = if (playerIsReady) ({ RoomBottomBarSection(modifier = Modifier.fillMaxWidth()) }) else null,
            center = { if (playerIsReady) RoomTransportKeys() else ProgressBar(progress = null) },
        )
    }
    }

    /* The gesture layer sits above the HUD. While the HUD is hidden, the gesture layer takes the
     * touches that would otherwise reach the hidden controls. While the HUD is visible, the
     * gesture layer attaches no pointer input. */
    if (playerIsReady) RoomGestureInterceptor(modifier = Modifier.fillMaxSize())
    // Above both: the notch strip beside an open keyboard. A stray thumb there closes the keyboard.
    KeyboardNotchShield(keyboardOpen = isKeyboardOpen)
}

/**
 * Runs [autoHideHud] for the room. The idle timer runs only while video plays. Open panels, menus
 * and dialogs, the keyboard, scrubbing, an unsent message and a mouse pointer that rests on a
 * control hold the HUD open. After each release or playback restart, the timer starts again from
 * the full delay. It also decides when the room hides the mouse pointer.
 */
@Composable
private fun HudAutoHide(viewmodel: RoomViewmodel, hudVisible: Boolean, keyboardOpen: Boolean, hasVideo: Boolean) {
    val idleSeconds by HUD_AUTO_HIDE_SECONDS.watchPref()
    val ui = viewmodel.uiState
    val isPlaying by viewmodel.playerManager.isNowPlaying.collectAsState()
    val isBuffering by viewmodel.playerManager.isBuffering.collectAsState()
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
    val railMenu by ui.railActionsExpanded.collectAsState()
    val askLeave by ui.askLeave.collectAsState()
    val managedRoom by ui.managedRoom.collectAsState()
    val hovered by ui.hoveredControls.collectAsState()
    val held = userInfo || playlist || prefs || tracks || gestures || seekTo || addMedia || controls || gifs || scrubbing ||
        keyboardOpen || draft.isNotBlank() || railMenu || askLeave || managedRoom || hovered > 0

    val screenReader = LocalScreenReaderActive.current

    val state by rememberUpdatedState(
        HudAutoHideState(idleSeconds, hudVisible, hasVideo, isPlaying, isBuffering, held, activity, screenReader)
    )
    LaunchedEffect(ui) {
        autoHideHud(snapshotFlow { state }) { ui.visibleHUD.value = it }
    }
    LaunchedEffect(ui) {
        snapshotFlow { state.hidesPointer }.collect { ui.pointerHidden.value = it }
    }
    // Lock mode and picture-in-picture remove this timer. The pointer must not stay hidden there.
    DisposableEffect(ui) {
        onDispose { ui.pointerHidden.value = false }
    }
}
