package app.room

import app.room.ui.chat.LocalChatMediaSize
import app.room.ui.chat.chatMediaCellSize

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Size
import app.LocalRoomViewmodel
import app.room.ui.bottombar.RoomControlsPage
import app.room.ui.bottombar.RoomFullScreenKey
import app.room.ui.chat.PageNewMessages
import app.room.ui.chat.RoomGifPage
import app.room.ui.rightcards.LocalRoomPage
import app.theme.BrandSpan
import app.theme.brandBrush
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import app.preferences.Preferences.ROOM_ORIENTATION
import app.preferences.flow
import app.preferences.watchPref
import app.room.ui.bottombar.AddVideoButton
import app.room.ui.bottombar.PortraitAddKey
import app.room.ui.bottombar.BlackContrastUnderlay
import app.room.ui.bottombar.TopContrastUnderlay
import app.room.ui.bottombar.RoomBottomBarSection
import app.room.ui.bottombar.RoomControlPanelButton
import app.room.ui.bottombar.RoomSeekbar
import app.room.ui.chat.ChatLinePlaces
import app.room.ui.chat.FadingMessageLayout
import app.room.ui.chat.HiddenHudChatLines
import app.room.ui.chat.LocalChatLinePlaces
import app.room.ui.chat.RoomChatInput
import app.room.ui.chat.RoomChatPane
import app.room.ui.chat.RoomChatSection
import app.room.ui.misc.KeyboardNotchShield
import app.room.ui.misc.RoomBackgroundArtwork
import app.room.ui.misc.RoomGestureInterceptor
import app.room.ui.misc.RoomTransportKeys
import app.room.ui.rightcards.RoomSidePanels
import app.room.ui.statinfo.RoomStatusInfoSection
import app.room.ui.statinfo.RoomStatusStrip
import app.room.models.LocalPersonShades
import app.room.models.personShades
import app.room.ui.tabs.ManagedRoomModal
import app.room.ui.tabs.RoomMorePage
import app.room.ui.tabs.RoomRail
import app.room.ui.tabs.RoomTabRow
import app.room.ui.tabs.RoomUnlockableLayout
import app.theme.LocalPalette
import app.theme.LocalSurfacePalette
import app.theme.Motion
import app.theme.Radius
import app.theme.Space
import app.uicomponents.GlassDemand
import app.uicomponents.LocalGlassDemand
import app.uicomponents.LocalGlassSuspended
import app.uicomponents.LocalHazeState
import app.uicomponents.LocalIsTelevision
import app.uicomponents.awaitFrameTime
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
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import app.preferences.settings.AskModal
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.roundToInt
import kotlin.math.min
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
    // The setting picks the view, and the full-screen key can switch to the other one (#249).
    // The layout follows the window shape, so a portrait window gets the tall layout.
    val orientation = RoomOrientation.of(ROOM_ORIENTATION.watchPref().value)
    val switchedUnder by viewmodel.uiState.orientationSwitch.collectAsState()
    EnterRoomMode(orientation.rotation(switched = switchedUnder == orientation))

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
    // A window taller than wide gets the portrait layout: the video on top, and chat under it.
    // Picture-in-picture shows the video alone, whatever the shape of its window.
    val portrait = tall && !isInPipMode
    // The main box under the video shows one page at a time, so the open panels follow that rule.
    SideEffect { viewmodel.uiState.onePage = portrait }
    // Notices under the stage push the main box down, with a gap above and under them.
    val noticeInset = remember { NoticeInset(if (portrait && viewmodel.uiState.visibleHUD.value) 1f else 0f) }
    val noticeGaps = with(LocalDensity.current) { (Space.gapTight * 2).roundToPx() }
    val mainBoxTop = remember(noticeInset, noticeGaps) { { noticeInset.px(noticeGaps) } }

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
    // Each person keeps one shade in every chat view. The map changes only when someone new speaks
    // or is named, so a new line from a known person recomposes nothing here.
    val chatLog = viewmodel.session.messageSequence.collectAsState()
    val shades by remember(chatLog) { derivedStateOf { personShades(chatLog.value) } }
    CompositionLocalProvider(
        LocalPersonShades provides shades,
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
        // In portrait, the room's dark ground fills the space under the video.
        val ground = if (portrait) Modifier.background(videoPalette.ground) else Modifier
        Box(Modifier.fillMaxSize().then(ground).roomPointer(viewmodel.uiState, pointerHidden).mediaDropTarget(drop)) {
            /* In portrait, the video layer takes the bounds of the stage. It keeps this place in the
             * composition in both layouts, so a rotation keeps the engine and its surface. */
            val videoBounds = if (portrait) Modifier.portraitStage(portraitTopInsets()) else Modifier.matchParentSize()
            Box(videoBounds.glassBackdropLayer(roomHazeState)) {
                if (!hasVideo) RoomBackgroundArtwork(stage = portrait)

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
                        onPlayerReady = {
                            platformCallback.mediaSessionInitialize(viewmodel)
                            viewmodel.uiState.watchBackgroundSoundReadiness()
                        },
                    )
                }
            }

            // Where the chat lines sit, shared by the chat list and the lines shown while the HUD is hidden.
            val chatLinePlaces = remember(viewmodel) { ChatLinePlaces() }
            CompositionLocalProvider(LocalChatLinePlaces provides chatLinePlaces) {
                when {
                    isInPipMode -> Unit // PiP shows the video and nothing else.
                    lockedMode -> RoomUnlockableLayout()
                    else -> RoomHud(viewmodel, soloMode, hasVideo, tall, railHorizontal, portrait, initialFocusRequester, mainBoxTop)
                }

                if (!isInPipMode) RoomNotices(viewmodel, soloMode, portrait, noticeInset)
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

/**
 * The notices and the unseen chat lines, in one column on the center line, where a viewer is
 * looking. The notices come first. The column sits above the HUD (the controls over the video)
 * and outside its fade, so it shows while the HUD is hidden. In landscape it sits under the status
 * line.
 *
 * In portrait, the notices sit at the top center of the stage (the video area) while its controls
 * are hidden. While the controls show, the stage has no free row between the Controls key and the
 * play key, so the notices glide under the stage. There they push the main box down through
 * [inset], so they cover no chat line. No chat lines fade in, because chat is on screen.
 */
@Composable
private fun BoxScope.RoomNotices(viewmodel: RoomViewmodel, soloMode: Boolean, portrait: Boolean, inset: NoticeInset) {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val controlsShown by viewmodel.uiState.visibleHUD.collectAsState()
    // The push follows the glide of the notices, and it is written outside composition.
    LaunchedEffect(portrait, controlsShown) {
        animate(inset.under, if (portrait && controlsShown) 1f else 0f, animationSpec = Motion.move()) { value, _ -> inset.under = value }
    }
    val keyboardOpen = WindowInsets.ime.getBottom(density) > 0
    val stageHeight = with(density) { window.width.toDp() } * 9f / 16f
    val target = when {
        !portrait -> Space.row + Space.gap
        !controlsShown -> Space.gapTight
        // Under the stage, and under the tab row unless the keyboard folded it away.
        keyboardOpen -> stageHeight + Space.gapTight
        else -> stageHeight + PortraitTabRowHeight + Space.gapTight
    }
    val top by animateDpAsState(target, Motion.move())
    Column(
        modifier = Modifier.align(Alignment.TopCenter)
            .fillMaxWidth()
            .windowInsetsPadding(if (portrait) portraitTopInsets() else roomTopInsets())
            .padding(top = top)
            .padding(horizontal = Space.gutter)
            .onSizeChanged { inset.height = it.height },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.gapTight),
    ) {
        NoticeHost(queue = viewmodel.notices)
        // Only while the chat list is not there to give the lines a place, such as under the GIF drawer.
        if (!soloMode && !portrait && LocalChatLinePlaces.current?.listShown != true) FadingMessageLayout()
    }
}

/**
 * How far the notices push the main box of the portrait room down. The values are read while
 * laying out, so a notice that grows, shrinks or glides recomposes nothing.
 */
@Stable
private class NoticeInset(under: Float) {
    /** The height of the notices, in pixels. */
    var height by mutableIntStateOf(0)

    /** 1 while the notices sit under the stage (the video area), 0 while they sit inside it. */
    var under by mutableFloatStateOf(under)

    /** The height of the notices plus [gaps], which grow in with the first pixels of a notice. */
    fun px(gaps: Int): Int = ((height + min(height, gaps)) * under).roundToInt()
}

/**
 * The HUD: every control that fades, on the [RoomFrame] docks, with the gesture layer above. In
 * [portrait], the HUD covers only the stage of [RoomPortraitFrame]. The tab row, the main box and
 * the composer sit under the stage and never fade. [mainBoxTop] moves the main box down for the
 * notices over its top edge.
 */
@Composable
private fun RoomHud(
    viewmodel: RoomViewmodel,
    soloMode: Boolean,
    hasVideo: Boolean,
    tall: Boolean,
    railHorizontal: Boolean,
    portrait: Boolean,
    initialFocusRequester: FocusRequester,
    mainBoxTop: () -> Int,
) {
    val ui = viewmodel.uiState
    val playerIsReady by viewmodel.playerManager.isPlayerReady.collectAsState()
    val isHUDVisible by ui.visibleHUD.collectAsState()
    val focusManager = LocalFocusManager.current
    val hiddenHudFocus = remember { FocusRequester() }
    /* A television counts as keyboard input from the first frame. Compose only switches the input
     * mode to keyboard after the first key press, and the HUD can appear before that. */
    val isKeyboardMode = LocalIsTelevision.current || LocalInputModeManager.current.inputMode == InputMode.Keyboard

    /* Under keyboard or D-pad input, focus moves to the main control when the HUD shows. On touch
     * that would be jarring and could raise the soft keyboard. On hide, keyboard focus leaves the
     * hidden controls (still composed) for the video layer, which handles the wake keys, and touch
     * only clears it. In portrait, focus can rest under the stage, in the composer for example. A
     * show then leaves it there, and a hide moves focus only when it sits inside the HUD. */
    var stageFocused by remember { mutableStateOf(false) }
    var lowerFocused by remember { mutableStateOf(false) }
    LaunchedEffect(isHUDVisible, hasVideo, isKeyboardMode, playerIsReady) {
        if (isHUDVisible) {
            if (isKeyboardMode && playerIsReady && !lowerFocused) {
                awaitFrameTime(150)
                runCatching { initialFocusRequester.requestFocus() }
            }
        } else if (!portrait || stageFocused) {
            focusManager.clearFocus(force = true)
            if (isKeyboardMode && hasVideo && playerIsReady) {
                runCatching { hiddenHudFocus.requestFocus() }
            }
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
    HudAutoHide(viewmodel, isHUDVisible, isKeyboardOpen, hasVideo, portrait)

    // The faded layer: the whole screen, or only the stage in portrait.
    val layer = Modifier
        /* Modifier.alpha, not a graphicsLayer block. A graphicsLayer block would defer the state
         * read, but it keeps a full-screen offscreen layer under the whole HUD (glass surfaces
         * included) for the life of the room. alpha() clips to the layer and skips the layer at
         * 1f, which is the usual state. */
        .alpha(hudAlpha.value)
        .then(
            if (isHUDVisible) Modifier.pointerInput(playerIsReady) {
                detectTapGestures(onTap = {
                    // While typing, a stray tap only closes the keyboard. Otherwise the tap hides
                    // the HUD.
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
        .onPreviewKeyEvent { ui.noteHudActivity(); false }
    val controls = Modifier.holdsHudWhileHovered(ui)

    // While the HUD is fully faded out, its glass releases the capture. The capture starts again
    // as soon as the fade-in begins.
    CompositionLocalProvider(LocalGlassSuspended provides hudHidden) {
    if (portrait) {
        RoomPortraitFrame(
            keyboardOpen = isKeyboardOpen,
            lower = Modifier.onFocusChanged { lowerFocused = it.hasFocus },
            mainTop = mainBoxTop,
            // The status line sits in the strip above the stage, so it never fades (#236). A solo
            // room has no status, so its strip shows the clock alone.
            strip = { RoomStatusStrip(Modifier.matchParentSize(), solo = soloMode) },
            stage = {
                Box(Modifier.matchParentSize().onFocusChanged { stageFocused = it.hasFocus }.then(layer)) {
                    if (hasVideo) {
                        BlackContrastUnderlay()
                        TopContrastUnderlay()
                    }
                    PortraitStageControls(playerIsReady, hasVideo, initialFocusRequester, controls)
                }
                // While the controls are hidden, a thin line along the bottom edge shows the position.
                if (hasVideo) StageProgressLine(Modifier.align(Alignment.BottomCenter)) { 1f - hudAlpha.value }
                // The gesture layer covers the stage only, so the chat under it keeps its touches.
                if (playerIsReady) RoomGestureInterceptor(modifier = Modifier.matchParentSize(), hiddenHudFocus = hiddenHudFocus, screenEdges = false)
            },
            tabs = { RoomTabRow() },
            main = { PortraitMainBox(viewmodel, soloMode, playerIsReady) },
            composer = if (soloMode) null else ({ RoomChatInput(Modifier.fillMaxWidth()) }),
        )
    } else {
    Box(modifier = Modifier.fillMaxSize().then(layer)) {
        if (hasVideo) {
            BlackContrastUnderlay()
            TopContrastUnderlay()
        }
        RoomFrame(
            tall = tall,
            railHorizontal = railHorizontal,
            controls = controls,
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
    }

    // Outside the faded layer: a new chat line shows at its place in the list while the HUD is hidden.
    if (!portrait && !soloMode) HiddenHudChatLines { hudAlpha.value }
    /* The gesture layer sits above the HUD. While the HUD is hidden, the gesture layer takes the
     * touches that would otherwise reach the hidden controls. While the HUD is visible, the
     * gesture layer attaches no pointer input. */
    if (!portrait && playerIsReady) RoomGestureInterceptor(modifier = Modifier.fillMaxSize(), hiddenHudFocus = hiddenHudFocus)
    // Above both: the notch strip beside an open keyboard. A stray thumb there closes the keyboard.
    KeyboardNotchShield(keyboardOpen = isKeyboardOpen)
}

/**
 * The HUD over the stage of the portrait room: the Controls key at the top end, the play and jump
 * keys in one row in the middle, and the seek row at the bottom. The seek row holds, from start to
 * end, the full-screen key, the seek bar and the add key ([PortraitAddKey]). Before a file loads,
 * the large add key takes the place of the seek bar, under the logo, and the full-screen key keeps
 * its corner at the start. The status line sits in the strip above the stage ([RoomStatusStrip]).
 */
@Composable
private fun BoxScope.PortraitStageControls(
    playerIsReady: Boolean,
    hasVideo: Boolean,
    initialFocusRequester: FocusRequester,
    controls: Modifier,
) {
    val ui = LocalRoomUiState.current
    val controlsPage by ui.controlPanel.collectAsState()
    if (playerIsReady) {
        Box(Modifier.align(Alignment.TopEnd).focusGroup().then(controls)) { RoomControlPanelButton(Modifier, active = controlsPage) }
    }
    when {
        !playerIsReady -> Box(Modifier.align(Alignment.Center).focusGroup().then(controls)) { ProgressBar(progress = null) }
        hasVideo -> {
            Box(Modifier.align(Alignment.Center).focusGroup().then(controls)) { RoomTransportKeys(row = true) }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().focusGroup().then(controls).padding(horizontal = Space.gapTight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (platformCallback.showsRoomViewKey) RoomFullScreenKey(tall = true)
                RoomSeekbar(modifier = Modifier.weight(1f))
                PortraitAddKey()
            }
        }
        // Before a file loads, the add key is the main control. It sits low in the stage, under the
        // logo, and opens the add media page.
        else -> {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = Space.gap).focusGroup().then(controls)) {
                AddVideoButton(
                    modifier = Modifier.focusRequester(ui.mediaKeyFocus).focusRequester(initialFocusRequester),
                    expanded = true,
                    onClick = { ui.toggleAddMedia(true) },
                )
            }
            // The full-screen key keeps its corner, or a room with no file has no way back to landscape (#249).
            if (platformCallback.showsRoomViewKey) {
                Box(Modifier.align(Alignment.BottomStart).padding(start = Space.gapTight).focusGroup().then(controls)) {
                    RoomFullScreenKey(tall = true)
                }
            }
        }
    }
}

/**
 * The 2dp position line along the bottom edge of the stage, shown while the room controls are
 * hidden. The played part takes the brand ramp, and the rest is white at 12 percent. It has no
 * touch target and no semantics. The position is read while drawing, so a position update redraws
 * the line and recomposes nothing. [alpha] is read while drawing too.
 */
@Composable
private fun StageProgressLine(modifier: Modifier, alpha: () -> Float) {
    val viewmodel = LocalRoomViewmodel.current
    val p = palette
    val manager = viewmodel.playerManager
    val hudVisible by viewmodel.uiState.visibleHUD.collectAsState()
    // Collected only while the controls are hidden, as the seek bar collects only while they show.
    val position = remember { mutableLongStateOf(manager.timeCurrentMillis.value) }
    LaunchedEffect(hudVisible) {
        if (!hudVisible) manager.timeCurrentMillis.collect { position.longValue = it }
    }
    val duration = manager.timeFullMillis.collectAsState()
    Box(
        modifier
            .fillMaxWidth()
            .height(StageLineHeight)
            .drawBehind {
                val shown = alpha()
                if (shown <= 0f) return@drawBehind
                drawRect(Color.White, alpha = shown * TRACK_ALPHA)
                val total = duration.value
                val played = if (total > 0L) (position.longValue.toFloat() / total).coerceIn(0f, 1f) else 0f
                if (played > 0f) {
                    drawRect(brandBrush(p.brandField, size.width, BrandSpan.toPx()), size = Size(size.width * played, size.height), alpha = shown)
                }
            },
    )
}

/** The height of the position line of the stage. */
private val StageLineHeight = 2.dp

/** The unplayed part of the position line: white at 12 percent. */
private const val TRACK_ALPHA = 0.12f

/**
 * The main box of the portrait room, under the tab row: chat, or one page in place of chat. A page
 * is one of the room's panels, the GIF drawer, the Controls page or the More page. The message list
 * stays composed under a page. Glass has no video to sample here, so the pages use the plain tonal
 * panel and keep no capture running.
 */
@Composable
private fun BoxScope.PortraitMainBox(viewmodel: RoomViewmodel, soloMode: Boolean, playerIsReady: Boolean) {
    val ui = viewmodel.uiState
    val chatSupported by viewmodel.protocol.supportsChat.collectAsState()
    val more by ui.railActionsExpanded.collectAsState()
    val controls by ui.controlPanel.collectAsState()
    val gifs by ui.gifPanelVisible.collectAsState()
    val panels = listOf(
        ui.tabCardUserInfo, ui.tabCardSharedPlaylist, ui.tabCardRoomPreferences, ui.tabCardTracks,
        ui.tabCardGestures, ui.tabCardSeekTo, ui.tabCardAddMedia,
    ).map { it.collectAsState().value }
    val chat = !soloMode && chatSupported
    val pageOpen = more || (chat && gifs) || (playerIsReady && (controls || panels.any { it }))
    val sheet = RoundedCornerShape(topStart = Radius.panel, topEnd = Radius.panel)
    // Each step scrolls the chat to its newest line, after the marker over a page is tapped.
    var newestRequests by remember { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalHazeState provides null, LocalGlassSuspended provides true, LocalRoomPage provides true) {
        // Chat lines count as seen only while no page covers them.
        if (chat) RoomChatPane(showing = !pageOpen, newestRequests = newestRequests, modifier = Modifier.fillMaxSize())
        else RoomBackgroundArtwork()
        if (playerIsReady) RoomSidePanels(Modifier.fillMaxSize(), tall = true)
        PortraitPage(open = playerIsReady && controls, sheet, returnFocus = ui.controlsFocus) { page ->
            RoomControlsPage(page, sheet)
        }
        PortraitPage(open = more, sheet, returnFocus = LocalRoomRailFocus.current) { page -> RoomMorePage(page, sheet) }
        if (chat) PortraitPage(open = gifs, sheet, returnFocus = ui.chatFocus) { page -> RoomGifPage(page, sheet) }
        if (chat) {
            PageNewMessages(pageOpen) {
                ui.showChat()
                newestRequests++
            }
        }
    }
}

/**
 * One page of the main box that is not a side panel. It slides up as the panels do, on the room
 * ground in its [shape], so chat does not show through its tonal panel. Under a remote or a
 * keyboard, focus enters the page when it opens, and goes back to [returnFocus] when the page
 * closes with focus inside it.
 */
@Composable
private fun PortraitPage(open: Boolean, shape: Shape, returnFocus: FocusRequester?, content: @Composable (Modifier) -> Unit) {
    val remoteOrKeyboard = LocalIsTelevision.current || LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val pageFocus = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    var wasOpen by remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        val target = when {
            !remoteOrKeyboard || open == wasOpen -> null
            open -> pageFocus
            hasFocus -> returnFocus
            else -> null
        }
        wasOpen = open
        if (target == null) return@LaunchedEffect
        // The page slides in over a few frames, so the target may not exist at the first try.
        repeat(PAGE_FOCUS_TRIES) {
            if (runCatching { target.requestFocus(if (open) FocusDirection.Enter else FocusDirection.Exit) }.getOrDefault(false)) return@LaunchedEffect
            delay(60)
        }
    }
    AnimatedVisibility(
        visible = open,
        modifier = Modifier.fillMaxSize(),
        enter = slideInVertically(Motion.move()) { it },
        exit = slideOutVertically(Motion.move()) { it },
    ) {
        content(Modifier.fillMaxSize().background(palette.ground, shape).focusRequester(pageFocus).onFocusChanged { hasFocus = it.hasFocus }.focusGroup())
    }
}

/** How many times, 60 ms apart, focus tries to move into a page or back out of it. */
private const val PAGE_FOCUS_TRIES = 8

/**
 * Runs [autoHideHud] for the room. The idle timer runs only while video plays. Open panels, menus
 * and dialogs, the keyboard, scrubbing, an unsent message and a mouse pointer that rests on a
 * control hold the HUD open. In [portrait], chat and the pages sit under the stage, so only
 * scrubbing, the dialogs and a resting mouse pointer hold it. After each release or playback
 * restart, the timer starts again from the full delay. It also decides when the room hides the
 * mouse pointer.
 */
@Composable
private fun HudAutoHide(viewmodel: RoomViewmodel, hudVisible: Boolean, keyboardOpen: Boolean, hasVideo: Boolean, portrait: Boolean) {
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
    val pagesAndChat = userInfo || playlist || prefs || tracks || gestures || seekTo || addMedia || controls || gifs ||
        keyboardOpen || draft.isNotBlank() || railMenu
    val held = (!portrait && pagesAndChat) || scrubbing || askLeave || managedRoom || hovered > 0

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
