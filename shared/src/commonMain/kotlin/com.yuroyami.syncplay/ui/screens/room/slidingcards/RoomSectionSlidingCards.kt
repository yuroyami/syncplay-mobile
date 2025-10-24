package com.yuroyami.syncplay.ui.screens.room.slidingcards

import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.yuroyami.syncplay.ui.screens.adam.LocalCardController
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.screens.room.bottombar.RoomControlPanelCard
import com.yuroyami.syncplay.ui.screens.room.slidingcards.CardRoomPrefs.InRoomSettingsCard
import com.yuroyami.syncplay.ui.screens.room.slidingcards.CardSharedPlaylist.SharedPlaylistCard
import com.yuroyami.syncplay.ui.screens.room.slidingcards.CardUserInfo.UserInfoCard
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.ui.utils.FreeAnimatedVisibility
import com.yuroyami.syncplay.ui.utils.screenWidthPx

@Composable
fun RoomSectionSlidingCards(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val cardController = LocalCardController.current
    val stateUserInfo by cardController.tabCardUserInfo.collectAsState()
    val stateSharedPlaylist by cardController.tabCardSharedPlaylist.collectAsState()
    val stateRoomPreferences by cardController.tabCardRoomPreferences.collectAsState()

    val stateControlPanel by cardController.controlPanel.collectAsState()

    /* The cards below */
    Column(modifier) {
        Box(modifier = Modifier.fillMaxWidth(0.37f).weight(1f).align(Alignment.End).padding(4.dp)) {
            val screenW = screenWidthPx
            val inTransition = slideInHorizontally(initialOffsetX = { (screenW * 1.3).toInt() })
            val outTransition = slideOutHorizontally(targetOffsetX = { (screenW * 1.3).toInt() })

            /** User-info card (toggled on and off) */
            if (!viewmodel.isSoloMode) {
                FreeAnimatedVisibility(
                    enter = inTransition, exit = outTransition,
                    visible = stateUserInfo,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    UserInfoCard()
                }
            }

            /** Shared Playlist card (toggled on and off) */
            if (!viewmodel.isSoloMode) {
                FreeAnimatedVisibility(
                    enter = inTransition, exit = outTransition,
                    visible = stateSharedPlaylist,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    SharedPlaylistCard()
                }
            }

            /** In-room card (toggled on and off) */
            FreeAnimatedVisibility(
                enter = inTransition, exit = outTransition,
                visible = stateRoomPreferences,
                modifier = Modifier.fillMaxHeight()
            ) {
                InRoomSettingsCard()
            }
        }

        val h = 50.dp

        FreeAnimatedVisibility(
            enter = expandIn(),
            exit = shrinkVertically(),
            visible = stateControlPanel,
            modifier = Modifier.align(Alignment.End).padding(end = 6.dp)
        ) {
            Surface(
                modifier = Modifier.height(h),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(colors = Theming.SP_GRADIENT.map {
                        it.copy(alpha = 0.5f)
                    })
                ),
            ) {

                RoomControlPanelCard(modifier = Modifier.height(h-1.dp), height = h)
            }
        }
    }
}