package com.yuroyami.syncplay.screens.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yuroyami.syncplay.screens.room.subcomponents.RoomAdvancedControlButton
import com.yuroyami.syncplay.screens.room.subcomponents.RoomBottomBarVideoControlRow
import com.yuroyami.syncplay.screens.room.subcomponents.RoomMediaAdderButton
import com.yuroyami.syncplay.screens.room.subcomponents.RoomReadyButton
import com.yuroyami.syncplay.screens.room.subcomponents.RoomVideoSeekbar


@Composable
fun RoomBottomBarSection(modifier: Modifier) {
    Box(modifier) {
        BottomBarBlackUnderlay()

        Row(modifier = Modifier.fillMaxWidth()) {
            //Ready Toggle Button
            RoomReadyButton()

            Column(verticalArrangement = Arrangement.Bottom) {
                RoomVideoSeekbar()

                RoomBottomBarVideoControlRow(modifier = Modifier)
            }

            RoomAdvancedControlButton()

            RoomMediaAdderButton()
        }
    }
}

@Composable
fun BottomBarBlackUnderlay() {
    Box(Modifier.fillMaxSize()) {
        val blacky = Color.Black.copy(alpha = 0.8F)

        Box(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(84.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, blacky, blacky, blacky)
                    )
                )
                .clickable(false) {}
        )
    }
}