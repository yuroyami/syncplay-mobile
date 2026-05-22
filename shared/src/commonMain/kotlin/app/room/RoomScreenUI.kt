package app.room

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.pointerInput
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

    CompositionLocalProvider(
        LocalRoomUiState provides viewmodel.uiState,
        LocalRoomInitialFocus provides initialFocusRequester,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            /* Room Background Artwork */
            if (!hasVideo) {
                RoomBackgroundArtwork()
            }

            /* Video Surface */
            val playerIsReady by viewmodel.playerManager.isPlayerReady.collectAsState()
            if (playerIsReady) {
                viewmodel.player.VideoPlayer(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (hasVideo) 1f else 0f), // Keeps composable alive even if hidden
                    onPlayerReady = {
                        platformCallback.mediaSessionInitialize()
                    }
                )
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

                Box(modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (isHUDVisible) 1f else 0f)
                    .then(if (isHUDVisible) Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                viewmodel.uiState.visibleHUD.value = false
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
                                /* Chat Section (Top-Left): Input and messages, extends to bottom bar */
                                RoomChatSection(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .fillMaxWidth(0.44f)
                                        .fillMaxHeight()
                                        .displayCutoutPadding()
                                        .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = if (hasVideo) 58.dp else 8.dp)
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

                            /* Tab Section (Top-Right): Tab buttons row */
                            RoomTabSection(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .fillMaxWidth(0.38f)
                                    .padding(8.dp)
                            )

                            /* Sliding Cards (Right side) */
                            RoomSectionSlidingCards(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxSize()
                                    .zIndex(10f)
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
                                        .statusBarsPadding()
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
                                    .statusBarsPadding()
                                    .padding(
                                        top = 56.dp,
                                        bottom = 58.dp,
                                        start = 6.dp,
                                        end = 6.dp,
                                    )
                                    .windowInsetsPadding(WindowInsets.safeGestures.only(WindowInsetsSides.Bottom)),
                                isPortrait = true
                            )

                            /* Chat Section (full width, from below top bar to above bottom bar) */
                            if (!isInPipMode && !soloMode) {
                                RoomChatSection(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                        .statusBarsPadding()
                                        .padding(start = 8.dp, end = 8.dp, top = 58.dp, bottom = if (hasVideo) 58.dp else 8.dp)
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
