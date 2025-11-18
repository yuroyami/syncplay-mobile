package com.yuroyami.syncplay.ui.screens.room.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yuroyami.syncplay.ui.components.gradientOverlay
import com.yuroyami.syncplay.ui.screens.adam.LocalCardController
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.screens.theme.Theming
import com.yuroyami.syncplay.ui.screens.theme.Theming.flexibleGradient
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun RoomUnlockableLayout() {
    val viewmodel = LocalRoomViewmodel.current
    val cardController = LocalCardController.current

    val lockedMode by cardController.tabLock.collectAsState()
    val isInPipMode by viewmodel.uiManager.hasEnteredPipMode.collectAsState()

    if (lockedMode) {
        var unlockButtonVisibility by remember { mutableStateOf(true) }

        Box(
            Modifier.fillMaxSize()
                .padding(top = 10.dp, end = 74.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        unlockButtonVisibility = !unlockButtonVisibility
                    }
            )
        ) {
            /** Unlock Card */
            if (unlockButtonVisibility && !isInPipMode) {
                LaunchedEffect(null) {
                    delay(2200.milliseconds)
                    unlockButtonVisibility = false
                }

                Card(
                    modifier = Modifier
                        .alpha(0.5f)
                        .width(64.dp).aspectRatio(1f)
                        .align(Alignment.TopEnd)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = Theming.SP_ORANGE)
                        ) {
                            cardController.tabLock.value = false
                            viewmodel.uiManager.visibleHUD.value = true
                        },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.linearGradient(colors = flexibleGradient)
                    ),
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Filled.NoEncryption,
                            contentDescription = "",
                            modifier = Modifier.size(32.dp).align(Alignment.Center)
                                .gradientOverlay()
                        )
                    }
                }
            }
        }
    }
}