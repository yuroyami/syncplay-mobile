package app.room.ui.rightcards

import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.room.ui.bottombar.RoomControlPanelCard
import app.uicomponents.FreeAnimatedVisibility
import app.uicomponents.screenWidthPx

@Composable
fun RoomSectionSlidingCards(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val cardController = LocalRoomUiState.current
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
                    CardUserInfo.UserInfoCard()
                }
            }

            /** Shared Playlist card (toggled on and off) */
            if (!viewmodel.isSoloMode) {
                FreeAnimatedVisibility(
                    enter = inTransition, exit = outTransition,
                    visible = stateSharedPlaylist,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    CardSharedPlaylist.SharedPlaylistCard()
                }
            }

            /** In-room card (toggled on and off) */
            FreeAnimatedVisibility(
                enter = inTransition, exit = outTransition,
                visible = stateRoomPreferences,
                modifier = Modifier.fillMaxHeight()
            ) {
                CardRoomPrefs.InRoomSettingsCard()
            }
        }

        FreeAnimatedVisibility(
            enter = expandIn(),
            exit = shrinkVertically(),
            visible = stateControlPanel,
            modifier = Modifier.align(Alignment.End).padding(end = 6.dp)
        ) {
            Surface(
                modifier = Modifier.height(46.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
            ) {
                RoomControlPanelCard(modifier = Modifier.height(45.dp))
            }
        }
    }
}