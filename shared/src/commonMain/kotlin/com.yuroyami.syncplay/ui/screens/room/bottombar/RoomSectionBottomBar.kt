package com.yuroyami.syncplay.ui.screens.room.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yuroyami.syncplay.ui.screens.adam.LocalViewmodel


@Composable
fun RoomBottomBarSection(modifier: Modifier) {
    val viewmodel = LocalViewmodel.current
    val hasVideo by viewmodel.hasVideo.collectAsState()

    Box(modifier) {
        if (hasVideo) BottomBarBlackUnderlay(modifier = Modifier.align(Alignment.BottomCenter))

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom
        ) {
            //if (hasVideo) {
            //Ready Toggle Button
            RoomReadyButton()

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Bottom
            ) {
                RoomVideoSeekbar(modifier = Modifier.fillMaxWidth())

                RoomBottomBarVideoControlRow(modifier = Modifier.fillMaxWidth())
            }

            RoomAdvancedControlButton()
            //}

            RoomMediaAdderButton()
        }
    }
}

@Composable
fun BottomBarBlackUnderlay(modifier: Modifier) {
    val blacky = Color.Black.copy(alpha = 0.7F)

    Box(
        modifier = modifier
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