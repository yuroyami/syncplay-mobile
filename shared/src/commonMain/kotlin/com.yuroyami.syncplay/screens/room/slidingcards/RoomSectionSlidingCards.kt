package com.yuroyami.syncplay.screens.room.slidingcards

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.yuroyami.syncplay.components.FreeAnimatedVisibility
import com.yuroyami.syncplay.components.cards.CardRoomPrefs.InRoomSettingsCard
import com.yuroyami.syncplay.components.cards.CardSharedPlaylist.SharedPlaylistCard
import com.yuroyami.syncplay.components.cards.CardUserInfo.UserInfoCard
import com.yuroyami.syncplay.screens.adam.LocalScreenSize
import com.yuroyami.syncplay.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.screens.room.tabs.TabController

@Composable
fun RoomSectionSlidingCards(modifier: Modifier, tabController: TabController) {
    val screenSize = LocalScreenSize.current
    val viewmodel = LocalViewmodel.current

    val stateUserInfo by tabController.tabCardUserInfo.collectAsState()
    val stateSharedPlaylist by tabController.tabCardSharedPlaylist.collectAsState()
    val stateRoomPreferences by tabController.tabCardRoomPreferences.collectAsState()

    /* The cards below */
    Box(modifier) {
        /** User-info card (toggled on and off) */
        if (!viewmodel.isSoloMode) {
            FreeAnimatedVisibility(
                modifier = Modifier.fillMaxSize(),
                enter = slideInHorizontally(initialOffsetX = { (screenSize.widthPx * 1.3).toInt() }),
                exit = slideOutHorizontally(targetOffsetX = { (screenSize.widthPx * 1.3).toInt() }),
                visible = stateUserInfo
            ) {
                UserInfoCard()
            }
        }

        /** Shared Playlist card (toggled on and off) */
        if (!viewmodel.isSoloMode) {
            FreeAnimatedVisibility(
                modifier = Modifier.fillMaxSize(),
                enter = slideInHorizontally(initialOffsetX = { (screenSize.widthPx * 1.3).toInt() }),
                exit = slideOutHorizontally(targetOffsetX = { (screenSize.widthPx * 1.3).toInt() }),
                visible = stateSharedPlaylist
            ) {
                SharedPlaylistCard()
            }
        }

        /** In-room card (toggled on and off) */
        FreeAnimatedVisibility(
            modifier = Modifier.fillMaxSize(),
            enter = slideInHorizontally(initialOffsetX = { (screenSize.widthPx * 1.3).toInt() }),
            exit = slideOutHorizontally(targetOffsetX = { (screenSize.widthPx * 1.3).toInt() }),
            visible = stateRoomPreferences
        ) {
            InRoomSettingsCard()
        }
    }
}