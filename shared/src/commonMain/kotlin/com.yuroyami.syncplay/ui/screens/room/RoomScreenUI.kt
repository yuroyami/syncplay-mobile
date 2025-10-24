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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yuroyami.syncplay.ui.screens.adam.LocalCardController
import com.yuroyami.syncplay.ui.screens.adam.LocalGlobalViewmodel
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
import com.yuroyami.syncplay.ui.screens.room.tabs.PopupChatHistory.ChatHistoryPopup
import com.yuroyami.syncplay.ui.screens.room.tabs.RoomTabSection
import com.yuroyami.syncplay.ui.screens.room.tabs.RoomUnlockableLayout
import com.yuroyami.syncplay.utils.HideSystemBars
import com.yuroyami.syncplay.utils.beginPingUpdate
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun RoomScreenUI(viewmodel: RoomViewmodel) {
    HideSystemBars() //This little trick will prevent the navigation bar from appearing when we show popup dialogs/dropdown menus.

    val soloMode = remember { viewmodel.isSoloMode }
    val hasVideo by viewmodel.playerManager.hasVideo.collectAsState(initial = false)
    val isHUDVisible by viewmodel.uiManager.visibleHUD.collectAsState()
    val isInPipMode by viewmodel.uiManager.hasEnteredPipMode.collectAsState()

    val cardController = remember { CardController() }
    val lockedMode by cardController.tabLock.collectAsState()

    val popupStateChatHistory = remember { mutableStateOf(false) }
    val popupStateSeekToPosition = remember { mutableStateOf(false) }

    CompositionLocalProvider(
        LocalCardController provides cardController
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            /* Room Background Artwork */
            if (!hasVideo) {
                RoomBackgroundArtwork()
            }

            /* Video Surface */
            viewmodel.playerManager.player?.VideoPlayer(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (hasVideo) 1f else 0f) //The video composable has to be alive at all times, we just hide it when there's no video
            )

            if (lockedMode) {
                /* A simple layout that has a hideable button that unlocks the screen after locking it */
                RoomUnlockableLayout()
            } else {
                /* Playback Gesture Interceptor */
                RoomGestureInterceptor(modifier = Modifier.fillMaxSize())

                AnimatedVisibility(
                    modifier = Modifier.fillMaxSize(),
                    visible = isHUDVisible,
                    enter = fadeIn(animationSpec = keyframes { durationMillis = 75 }), exit = fadeOut(animationSpec = keyframes { durationMillis = 75 })
                ) {
                    // We need to wrap all HUD elements in this Box because AnimatedVisibility breaks the original Box scope,
                    // which breaks our ability to position elements freely on the screen (e.g., topStart, bottomEnd, etc.).
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!isInPipMode && !soloMode) {
                            /* Chat Section (Top to the left): has the text input field and the chat messages */
                            RoomChatSection(
                                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(0.35f).padding(8.dp)
                            )

                            /* Status section (top center): Has connection status, ping, room name, episode and also occasional room OSD messages */
                            RoomStatusInfoSection(
                                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(0.28f).padding(8.dp)
                            )
                        }

                        /* Tab section (top to the right): Has the row of tabs (but not the actual cards that slide in when tabs are clicked) */
                        RoomTabSection(
                            modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.38f).padding(8.dp),
                            onShowChatHistory = {
                                popupStateChatHistory.value = true
                            }
                        )

                        /* Card section (to the right middle) */
                        RoomSectionSlidingCards(
                            modifier = Modifier.align(Alignment.CenterEnd)
                                .fillMaxSize()
                                .zIndex(10f)
                                .padding(
                                    top = 74.dp,
                                    bottom = 58.dp,
                                    end = 6.dp,
                                )
                        )

                        /* BottomBar: Ready Button - Seekbar (and the buttons above it) - Advanced Controls (like selecting tracks) - Media Add Button */
                        RoomBottomBarSection(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        )


                        /* In the dead center, we put the play button */
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
        if (!soloMode) ChatHistoryPopup(visibilityState = popupStateChatHistory)
        SeekToPositionPopup(visibilityState = popupStateSeekToPosition)

        LaunchedEffect(Unit) {
            if (!soloMode) {
                /* Starts ping updates when not in solo mode.
         * Uses withContext(Dispatchers.IO) to tie the ping coroutine to the composition scope,
         * ensuring that if composition is cancelled (room is exited), ping updating is cancelled as well. */
                withContext(Dispatchers.IO) {
                    viewmodel.beginPingUpdate()
                }
            }
        }

        val globalViewmodel = LocalGlobalViewmodel.current
        if (!globalViewmodel.hasEnteredRoomOnce) {
            LaunchedEffect(null) {
                delay(600)
                cardController.toggleUserInfo(true)
                globalViewmodel.hasEnteredRoomOnce = true
            }
        }
    }
}