package com.yuroyami.syncplay.ui.screens.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.yuroyami.syncplay.ui.screens.adam.LocalCardController
import com.yuroyami.syncplay.ui.screens.adam.LocalGlobalViewmodel
import com.yuroyami.syncplay.ui.screens.room.bottombar.BlackContrastUnderlay
import com.yuroyami.syncplay.ui.screens.room.bottombar.PopupSeekToPosition.SeekToPositionPopup
import com.yuroyami.syncplay.ui.screens.room.bottombar.RoomBottomBarSection
import com.yuroyami.syncplay.ui.screens.room.chat.FadingMessageLayout
import com.yuroyami.syncplay.ui.screens.room.chat.RoomChatSection
import com.yuroyami.syncplay.ui.screens.room.misc.RoomBackgroundArtwork
import com.yuroyami.syncplay.ui.screens.room.misc.RoomGestureInterceptor
import com.yuroyami.syncplay.ui.screens.room.misc.RoomPlayButton
import com.yuroyami.syncplay.ui.screens.room.slidingcards.RoomSectionSlidingCards
import com.yuroyami.syncplay.ui.screens.room.statinfo.RoomStatusInfoSection
import com.yuroyami.syncplay.ui.screens.room.tabs.CardController
import com.yuroyami.syncplay.ui.screens.room.tabs.ChatHistoryPopup
import com.yuroyami.syncplay.ui.screens.room.tabs.ManagedRoomPopup
import com.yuroyami.syncplay.ui.screens.room.tabs.ManagedRoomPopupPurpose
import com.yuroyami.syncplay.ui.screens.room.tabs.RoomTabSection
import com.yuroyami.syncplay.ui.screens.room.tabs.RoomUnlockableLayout
import com.yuroyami.syncplay.utils.HideSystemBars
import com.yuroyami.syncplay.utils.beginPingUpdate
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
    val isHUDVisible by viewmodel.uiManager.visibleHUD.collectAsState()
    val isInPipMode by viewmodel.uiManager.hasEnteredPipMode.collectAsState()

    val cardController = remember { CardController() }
    val lockedMode by cardController.tabLock.collectAsState()

    CompositionLocalProvider(LocalCardController provides cardController) {
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
                        .alpha(if (hasVideo) 1f else 0f) // Keeps composable alive even if hidden
                )
            }

            if (lockedMode) {
                /* Simple unlock layout shown when screen is locked */
                RoomUnlockableLayout()

            } else {
                /* Gesture Interceptor for playback control */
                RoomGestureInterceptor(modifier = Modifier.fillMaxSize())

                AnimatedVisibility(
                    modifier = Modifier.fillMaxSize(),
                    visible = isHUDVisible,
                    enter = fadeIn(animationSpec = keyframes { durationMillis = 75 }),
                    exit = fadeOut(animationSpec = keyframes { durationMillis = 75 })
                ) {
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

        LaunchedEffect(Unit) {
            if (!soloMode) {
                // Starts ping updates for multiplayer mode and cancels when room is exited
                runCatching {
                    withContext(Dispatchers.IO) {
                        viewmodel.beginPingUpdate()
                    }
                }
            }
        }

        val globalViewmodel = LocalGlobalViewmodel.current

        LaunchedEffect(null) {
            delay(600)
            cardController.toggleUserInfo(true)
            globalViewmodel.hasEnteredRoomOnce = true
        }
    }
}
