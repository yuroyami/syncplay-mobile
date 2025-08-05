package com.yuroyami.syncplay.screens.room

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yuroyami.syncplay.components.ComposeUtils.gradientOverlay
import com.yuroyami.syncplay.ui.Paletting


@Composable
fun RoomTabSection(modifier: Modifier) {

}

@Composable
fun RoomTab(icon: ImageVector, visibilityState: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(48.dp)
            .aspectRatio(1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Paletting.SP_ORANGE)
            ) {
                onClick.invoke()

            },
        shape = RoundedCornerShape(6.dp),
        border = if (visibilityState) {
            null
        } else {
            BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map { it.copy(alpha = 0.5f) }))
        },
        colors = CardDefaults.cardColors(containerColor = if (visibilityState) Color.Transparent else MaterialTheme.colorScheme.tertiaryContainer.copy(0.5f)),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (visibilityState) {
                        Brush.linearGradient(colors = Paletting.SP_GRADIENT.map { it.copy(alpha = 0.5f) })
                    } else {
                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                )
        ) {
            if (visibilityState) {
                Icon(
                    tint = Color.DarkGray,
                    imageVector = icon,
                    contentDescription = "",
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = "",
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center)
                        .gradientOverlay()
                )
            }

        }
    }
}