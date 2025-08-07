package com.yuroyami.syncplay.ui.screens.room.slidingcards

import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.yuroyami.syncplay.ui.screens.adam.LocalCardController
import com.yuroyami.syncplay.ui.screens.adam.LocalScreenSize
import com.yuroyami.syncplay.ui.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.ui.screens.room.bottombar.RoomControlPanelCard
import com.yuroyami.syncplay.ui.screens.room.slidingcards.CardRoomPrefs.InRoomSettingsCard
import com.yuroyami.syncplay.ui.screens.room.slidingcards.CardSharedPlaylist.SharedPlaylistCard
import com.yuroyami.syncplay.ui.screens.room.slidingcards.CardUserInfo.UserInfoCard
import com.yuroyami.syncplay.ui.theme.Paletting
import com.yuroyami.syncplay.ui.utils.FreeAnimatedVisibility

@Composable
fun RoomSectionSlidingCards(modifier: Modifier) {
    val viewmodel = LocalViewmodel.current
    val cardController = LocalCardController.current
    val screenSize = LocalScreenSize.current

    val stateUserInfo by cardController.tabCardUserInfo.collectAsState()
    val stateSharedPlaylist by cardController.tabCardSharedPlaylist.collectAsState()
    val stateRoomPreferences by cardController.tabCardRoomPreferences.collectAsState()

    val stateControlPanel by cardController.controlPanel.collectAsState()

    /* The cards below */
    Column(modifier) {
        val wght = Modifier.weight(1f)

        Box {
            /** User-info card (toggled on and off) */
            if (!viewmodel.isSoloMode) {
                FreeAnimatedVisibility(
                    modifier = Modifier.then(wght),
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
                    modifier = Modifier.then(wght),
                    enter = slideInHorizontally(initialOffsetX = { (screenSize.widthPx * 1.3).toInt() }),
                    exit = slideOutHorizontally(targetOffsetX = { (screenSize.widthPx * 1.3).toInt() }),
                    visible = stateSharedPlaylist
                ) {
                    SharedPlaylistCard()
                }
            }

            /** In-room card (toggled on and off) */
            FreeAnimatedVisibility(
                modifier = Modifier.then(wght),
                enter = slideInHorizontally(initialOffsetX = { (screenSize.widthPx * 1.3).toInt() }),
                exit = slideOutHorizontally(targetOffsetX = { (screenSize.widthPx * 1.3).toInt() }),
                visible = stateRoomPreferences
            ) {
                InRoomSettingsCard()
            }
        }

        FreeAnimatedVisibility(
            enter = expandVertically(),
            exit = shrinkVertically(),
            visible = stateControlPanel
        ) {
            Surface(
                modifier = Modifier.height(64.dp).fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map {
                        it.copy(alpha = 0.5f)
                    })
                ),
            ) {
                RoomControlPanelCard(Modifier.height(64.dp))
            }
        }
    }
}