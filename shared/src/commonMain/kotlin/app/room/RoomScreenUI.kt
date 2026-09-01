package app.room

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.background
import app.preferences.Preferences.VIDEO_BACKGROUND_COLOR
import app.preferences.flow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.LocalGlobalViewmodel
import app.LocalRoomUiState
import app.room.RoomUiStateManager.Companion.RoomOrientation
import app.room.ui.bottombar.BlackContrastUnderlay
import app.room.ui.bottombar.PopupSeekToPosition.SeekToPositionPopup
import app.room.ui.bottombar.RoomBottomBarSection
import app.room.ui.chat.FadingMessageLayout
import app.room.ui.chat.RoomChatSection
import app.room.ui.misc.RoomBackgroundArtwork
import app.room.ui.misc.RoomGestureInterceptor
import app.room.ui.misc.RoomPlayButton
import app.room.ui.rightcards.RoomSectionSlidingCards
import app.room.ui.statinfo.RoomStatusInfoSection
import app.room.ui.tabs.ManagedRoomPopup
import app.room.ui.tabs.ManagedRoomPopupPurpose
import app.room.ui.tabs.RoomTabSection
import app.room.ui.tabs.RoomUnlockableLayout
import app.utils.EnterRoomMode
import app.utils.platformCallback
import kotlinx.coroutines.delay
import dev.chrisbanes.haze.rememberHazeState
import app.uicomponents.glassBackdropLayer
import app.uicomponents.LocalHazeState

/** Primary focus target for D-pad/TV navigation in the Room. Bound by either RoomPlayButton
 * (when a video is loaded) or AddVideoButton (when no video yet). RoomScreenUI calls
 * `requestFocus()` on it whenever the HUD becomes visible so remote users always land on
 * the most useful control. */
val LocalRoomInitialFocus = compositionLocalOf<FocusRequester?> { null }

/**
 * Composable that represents the entire room screen UI.
 *
 * @param viewmodel The [RoomViewmodel] providing all room-related state and event handling.
 */
@Composable
fun RoomScreenUI(viewmodel: RoomViewmodel) {
    val orientation by viewmodel.uiState.roomOrientation.collectAsState()
    val isPortrait = orientation == RoomOrientation.PORTRAIT

    /* Applies room-mode windowing (hidden chrome + orientation lock) and re-fires on
     * orientation toggle. Single source of truth — avoids the iOS rotation race that
     * occurs when two geometry-update calls are issued back-to-back. */
    EnterRoomMode(isPortrait)

    val soloMode = remember { viewmodel.isSoloMode }
    val hasVideo by viewmodel.playerManager.hasVideo.collectAsState(initial = false)
    val isInPipMode by viewmodel.uiState.hasEnteredPipMode.collectAsState()

    val lockedMode by viewmodel.uiState.tabLock.collectAsState()

    /* Primary focus target for D-pad/TV: when the HUD becomes visible we land focus on
     * the play button (or the media-add button if no video is loaded yet). Bound below
     * inside RoomPlayButton / RoomMediaAddButton via LocalRoomInitialFocus. */
    val initialFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    /* The room's own glass backdrop: just the video layer (and the no-video artwork). Glass in
     * the SAME window cannot sample the app-wide backdrop it lives inside, so in-room chrome
     * blurs this scoped capture instead; the swap of LocalHazeState below routes it here. */
    val roomHazeState = rememberHazeState()

    CompositionLocalProvider(
        LocalRoomUiState provides viewmodel.uiState,
        LocalRoomInitialFocus provides initialFocusRequester,
        LocalHazeState provides roomHazeState,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().glassBackdropLayer(roomHazeState)) {
                /* Room Background Artwork */
                if (!hasVideo) {
                    RoomBackgroundArtwork()
                }

                /* Video Surface */
                val playerIsReady by viewmodel.playerManager.isPlayerReady.collectAsState()
                if (playerIsReady) {
                    /* The backdrop behind the picture (the letterbox bars). KiteVideo's letterbox is
                     * transparent by design, so without this the app theme (gray) showed through.
                     * Black by default, user-colorable. Placed AFTER alpha in the chain so the
                     * no-video state stays fully invisible. */
                    val videoBackground by remember { VIDEO_BACKGROUND_COLOR.flow() }
                        .collectAsState(initial = 0xFF000000.toInt())
                    viewmodel.player.VideoPlayer(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (hasVideo) 1f else 0f) // Keeps composable alive even if hidden
                            .background(Color(videoBackground)),
                        onPlayerReady = {
                            platformCallback.mediaSessionInitialize()
                        }
                    )
                }
            }

            if (lockedMode) {
                /* Simple unlock layout shown when screen is locked */
                RoomUnlockableLayout()
            } else {
                val isHUDVisible by viewmodel.uiState.visibleHUD.collectAsState()

                /* D-pad/TV input detection — drives the focus-landing effect below so a
                 * remote-control user's focus lands on the primary control when the HUD shows. */
                val inputModeManager = LocalInputModeManager.current
                val isKeyboardMode = inputModeManager.inputMode == InputMode.Keyboard

                /* Land D-pad focus on the primary control whenever the HUD becomes visible.
                 * Only kicks in under Keyboard/D-pad input mode — on touch devices, auto-
                 * grabbing focus would be jarring and the soft keyboard could pop. Drop
                 * focus on HUD hide so the invisible-but-composed elements don't keep an
                 * off-screen focus stop. */
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

                /* Fade instead of hard-flipping alpha: the single most-used transition in the app.
                 * The HUD stays composed either way (deliberate: preserves chat state). */
                val hudAlpha by animateFloatAsState(
                    targetValue = if (isHUDVisible) 1f else 0f,
                    animationSpec = tween(durationMillis = 150)
                )

                /* Two-stage background-tap dismissal. Read through rememberUpdatedState because
                 * the pointerInput(Unit) below never restarts, so a plain capture would go stale. */
                val density = LocalDensity.current
                val isKeyboardOpen by rememberUpdatedState(WindowInsets.ime.getBottom(density) > 0)

                Box(modifier = Modifier
                    .fillMaxSize()
                    .alpha(hudAlpha)
                    .then(if (isHUDVisible) Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                if (isKeyboardOpen) {
                                    /* Stage 1 — typing: a stray background tap only closes the
                                     * keyboard (drops focus). The HUD must survive it. */
                                    focusManager.clearFocus(force = true)
                                } else {
                                    /* Stage 2 — no keyboard: tap hides the HUD. */
                                    viewmodel.uiState.visibleHUD.value = false
                                }
                            })
                        }
                    else Modifier)
                ) {
                        if (hasVideo) {
                            BlackContrastUnderlay()
                        }

                        if (!isPortrait) {
                            /* ===== LANDSCAPE LAYOUT ===== */
                            if (!isInPipMode && !soloMode) {
                                /* Chat Section (Top-Left): Input and messages, extends to bottom bar.
                                 * Horizontal cutout inset + side margins are applied INSIDE the section
                                 * (per row) so the strip beside a camera notch still belongs to the
                                 * section's tap-shield instead of dismissing the keyboard/HUD. */
                                RoomChatSection(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .fillMaxWidth(0.44f)
                                        .fillMaxHeight()
                                        .padding(top = 8.dp, bottom = if (hasVideo) 58.dp else 8.dp)
                                        .then(if (hasVideo) Modifier.windowInsetsPadding(WindowInsets.safeGestures.only(WindowInsetsSides.Bottom)) else Modifier)
                                )

                                /* Status Section (Top-Center): Connection info, room name, etc. */
                                RoomStatusInfoSection(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth(0.28f)
                                        .padding(8.dp)
                                )
                            }

                            /* Tab Section (Top-Right): Tab buttons row. Cutout padding keeps the
                             * buttons reachable on devices with a right-side camera notch. */
                            RoomTabSection(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .fillMaxWidth(0.38f)
                                    .displayCutoutPadding()
                                    .padding(8.dp)
                            )

                            /* Sliding Cards (Right side) */
                            RoomSectionSlidingCards(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxSize()
                                    .zIndex(10f)
                                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                                    .padding(
                                        top = 74.dp,
                                        bottom = 58.dp,
                                        end = 6.dp,
                                    )
                                    .windowInsetsPadding(WindowInsets.safeGestures.only(WindowInsetsSides.Bottom)),
                                isPortrait = false
                            )
                        } else {
                            /* ===== PORTRAIT LAYOUT ===== */
                            /* Top bar: Status info (left) + Tabs (right) */
                            if (!isInPipMode) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        /* statusBars alone is NOT enough: the room runs immersive, and
                                         * hidden bars report zero insets while the camera cutout is
                                         * still physically there. Union keeps the row below the notch. */
                                        .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top)))
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    if (!soloMode) {
                                        RoomStatusInfoSection(
                                            modifier = Modifier.weight(1f).padding(top = 8.dp)
                                        )
                                    }
                                    RoomTabSection(
                                        modifier = Modifier
                                            .then(if (soloMode) Modifier.fillMaxWidth() else Modifier.weight(1f))
                                            .padding(top = 0.dp)
                                    )
                                }
                            }

                            /* Sliding Cards (full width, below top bar) */
                            RoomSectionSlidingCards(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxSize()
                                    .zIndex(10f)
                                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top)))
                                    .padding(
                                        top = 56.dp,
                                        bottom = 58.dp,
                                        start = 6.dp,
                                        end = 6.dp,
                                    )
                                    .windowInsetsPadding(WindowInsets.safeGestures.only(WindowInsetsSides.Bottom)),
                                isPortrait = true
                            )

                            /* Chat Section (full width, from below top bar to above bottom bar).
                             * Side margins/cutout live inside the section (see landscape note). */
                            if (!isInPipMode && !soloMode) {
                                RoomChatSection(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                        .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top)))
                                        .padding(top = 58.dp, bottom = if (hasVideo) 58.dp else 8.dp)
                                        .then(if (hasVideo) Modifier.windowInsetsPadding(WindowInsets.safeGestures.only(WindowInsetsSides.Bottom)) else Modifier)
                                )
                            }
                        }

                        /* Bottom Bar: Playback and advanced controls */
                        RoomBottomBarSection(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        )

                        /* Central Play Button */
                        RoomPlayButton(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                /* Gesture Interceptor — placed on TOP of the HUD so that, when the HUD is
                 * hidden (alpha=0), it intercepts touches that would otherwise fall through
                 * to the still-composed HUD elements (chat input, buttons, seekbar etc.).
                 * When the HUD is visible, it attaches no pointer-input modifiers, leaving
                 * touches to flow through to the HUD beneath. */
                RoomGestureInterceptor(modifier = Modifier.fillMaxSize())
            }

            if (!soloMode) {
                FadingMessageLayout()
            }
        }

        /** Popups */
        SeekToPositionPopup()
        ManagedRoomPopup(ManagedRoomPopupPurpose.CREATE_MANAGED_ROOM)
        ManagedRoomPopup(ManagedRoomPopupPurpose.IDENTIFY_AS_OPERATOR)

        val globalViewmodel = LocalGlobalViewmodel.current

        LaunchedEffect(null) {
            delay(600)
            viewmodel.uiState.toggleUserInfo(true)
            globalViewmodel.hasEnteredRoomOnce = true
        }
    }
}
