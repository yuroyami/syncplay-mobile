package com.yuroyami.syncplay.screens.room

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yuroyami.syncplay.components.cards.CardRoomPrefs.InRoomSettingsCard
import com.yuroyami.syncplay.components.cards.CardSharedPlaylist.SharedPlaylistCard
import com.yuroyami.syncplay.components.cards.CardUserInfo.UserInfoCard
import com.yuroyami.syncplay.screens.adam.LocalViewmodel

@Composable
fun RoomSectionSlidingCards(modifier: Modifier) {
    val viewmodel = LocalViewmodel.current

    /* The cards below */
    val cardWidth = 0.36f
    val cardHeight = 0.72f
    Box(modifier) {
        /** User-info card (toggled on and off) */
        if (!viewmodel.isSoloMode) {
            FreeAnimatedVisibility(
                modifier = Modifier.fillMaxWidth(cardWidth)
                    .fillMaxHeight(cardHeight),
                enter = slideInHorizontally(initialOffsetX = { (screensizeinfo.widthPx * 1.3).toInt() }),
                exit = slideOutHorizontally(targetOffsetX = { (screensizeinfo.widthPx * 1.3).toInt() }),
                visible = !inroomprefsVisibility.value && userinfoVisibility.value && !sharedplaylistVisibility.value
            ) {
                UserInfoCard()
            }
        }

        /** Shared Playlist card (toggled on and off) */
        if (!viewmodel.isSoloMode) {
            FreeAnimatedVisibility(
                modifier = Modifier.fillMaxWidth(cardWidth)
                    .fillMaxHeight(cardHeight),
                enter = slideInHorizontally(initialOffsetX = { (screensizeinfo.widthPx * 1.3).toInt() }),
                exit = slideOutHorizontally(targetOffsetX = { (screensizeinfo.widthPx * 1.3).toInt() }),
                visible = !inroomprefsVisibility.value && !userinfoVisibility.value && sharedplaylistVisibility.value
            ) {
                SharedPlaylistCard()
            }
        }

        /** In-room card (toggled on and off) */
        FreeAnimatedVisibility(
            modifier = Modifier.fillMaxWidth(cardWidth)
                .fillMaxHeight(cardHeight),
            enter = slideInHorizontally(initialOffsetX = { (screensizeinfo.widthPx * 1.3).toInt() }),
            exit = slideOutHorizontally(targetOffsetX = { (screensizeinfo.widthPx * 1.3).toInt() }),
            visible = inroomprefsVisibility.value && !userinfoVisibility.value && !sharedplaylistVisibility.value
        ) {
            InRoomSettingsCard()
        }
    }
}