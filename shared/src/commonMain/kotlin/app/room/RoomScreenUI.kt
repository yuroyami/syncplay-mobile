package app.room

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.LocalGlobalViewmodel
import app.LocalRoomUiState
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
import app.room.ui.tabs.ChatHistoryPopup
import app.room.ui.tabs.ManagedRoomPopup
import app.room.ui.tabs.ManagedRoomPopupPurpose
import app.room.ui.tabs.RoomTabSection
import app.room.ui.tabs.RoomUnlockableLayout
import app.utils.HideSystemBars
import app.utils.platformCallback
import kotlinx.coroutines.delay

/**
 * Composable that represents the entire room screen UI.
 *
 * @param viewmodel The [RoomViewmodel] providing all room-related state and event handling.
 */
@Composable
fun RoomScreenUI(viewmodel: RoomViewmodel) {
    HideSystemBars() // Prevents the navigation bar from reappearing when popups/menus are shown.

    val soloMode = remember { viewmodel.isSoloMode }
    val hasVideo by viewmodel.playerManager.hasVideo.collectAsState(initial = false)
    val isInPipMode by viewmodel.uiState.hasEnteredPipMode.collectAsState()

    val lockedMode by viewmodel.uiState.tabLock.collectAsState()

    CompositionLocalProvider(LocalRoomUiState provides viewmodel.uiState) {
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
                        platformCallback.initializeMediaSession(viewmodel.player)
                    }
                )
            }

            if (lockedMode) {
                /* Simple unlock layout shown when screen is locked */
                RoomUnlockableLayout()
            } else {
                /* Gesture Interceptor for playback control */
                RoomGestureInterceptor(modifier = Modifier.fillMaxSize())

                val isHUDVisible by viewmodel.uiState.visibleHUD.collectAsState()
                if (isHUDVisible) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (hasVideo) {
                            BlackContrastUnderlay()
                        }

                        if (!isInPipMode && !soloMode) {
                            /* Chat Section (Top-Left): Input and messages */
                            RoomChatSection(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .fillMaxWidth(0.35f)
                                    .displayCutoutPadding()
                                    .padding(8.dp)

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
                                .windowInsetsPadding(WindowInsets.safeGestures.only(WindowInsetsSides.Bottom))
                        )

                        /* Bottom Bar: Playback and advanced controls */
                        RoomBottomBarSection(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        )

                        /* Central Play Button */
                        RoomPlayButton(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            if (!soloMode) {
                FadingMessageLayout()
            }
        }

        /** Popups */
        if (!soloMode) ChatHistoryPopup()
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
