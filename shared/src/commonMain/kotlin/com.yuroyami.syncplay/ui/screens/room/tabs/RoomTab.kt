package com.yuroyami.syncplay.ui.screens.room.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composeunstyled.buildModifier
import com.yuroyami.syncplay.ui.theme.Paletting
import com.yuroyami.syncplay.ui.utils.gradientOverlay

@Composable
fun RowScope.RoomTab(icon: ImageVector, visibilityState: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .padding(3.dp)
            /*.clickable(
                interactionSource = null,
                indication = ripple(color = Paletting.SP_ORANGE),
                onClick = onClick
            )*/,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(
            width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT)
        ).takeUnless { visibilityState },
        colors = CardDefaults.cardColors(containerColor = if (visibilityState) Color.Transparent else MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = if (visibilityState) Paletting.SP_GRADIENT else listOf(Color.Transparent, Color.Transparent)
                    )
                )
        ) {
            Icon(
                tint = if (visibilityState) Color.DarkGray else LocalContentColor.current,
                imageVector = icon,
                contentDescription = null,
                modifier = buildModifier {
                    add(Modifier.fillMaxSize().padding(8.dp).align(Alignment.Center))
                    if (!visibilityState) add(Modifier.gradientOverlay())
                }
            )
        }
    }
}