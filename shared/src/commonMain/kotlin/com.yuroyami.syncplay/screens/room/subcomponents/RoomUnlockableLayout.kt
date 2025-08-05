package com.yuroyami.syncplay.screens.room.subcomponents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yuroyami.syncplay.components.ComposeUtils.gradientOverlay
import com.yuroyami.syncplay.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.ui.Paletting


@Composable
fun RoomUnlockableLayout(lockState: MutableState<Boolean>) {
    val viewmodel = LocalViewmodel.current
    val isInPipMode by viewmodel.hasEnteredPipMode.collectAsState()

    if (lockState.value) {
        val unlockButtonVisibility = remember { mutableStateOf(false) }

        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    unlockButtonVisibility.value = !unlockButtonVisibility.value
                }
            )
        ) {
            /** Unlock Card */
            if (unlockButtonVisibility.value && !isInPipMode) {
                Card(
                    modifier = Modifier.width(48.dp).alpha(0.5f).aspectRatio(1f)
                        .align(Alignment.TopEnd)
                        .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Paletting.SP_ORANGE)
                    ) {
                        lockState.value = false
                        viewmodel.visibleHUD.value = true
                    },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT)
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