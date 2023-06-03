package app.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.activities.WatchActivity
import app.ui.Paletting
import app.utils.ComposeUtils
import app.utils.ComposeUtils.gradientOverlay

object MyComposables {


    @Composable
    fun RoomTab(icon: ImageVector, visibilityState: Boolean, onClick: () -> Unit) {
        Card(
            modifier = Modifier
                .width(48.dp)
                .aspectRatio(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(color = Paletting.SP_ORANGE)
                ) {
                    onClick.invoke()

                },
            shape = RoundedCornerShape(6.dp),
            border = if (visibilityState) {
                null
            } else {
                BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT))
            },
            colors = CardDefaults.cardColors(containerColor = if (visibilityState) Color.Transparent else MaterialTheme.colorScheme.tertiaryContainer),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = if (visibilityState) {
                            Brush.linearGradient(colors = Paletting.SP_GRADIENT)
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


    @Composable
    fun WatchActivity.AddVideoButton(modifier: Modifier, onClick: () -> Unit) {
        if (hasVideoG.value) {
            ComposeUtils.FancyIcon2(
                modifier = modifier,
                icon = Icons.Filled.AddToQueue, size = Paletting.ROOM_ICON_SIZE + 6, shadowColor = Color.Black,
                onClick = {
                    onClick.invoke()
                })
        } else {
            Surface(
                modifier = modifier.width(150.dp).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT)),
                shadowElevation = 12.dp,
                tonalElevation = 4.dp,
                onClick = { onClick.invoke() },
                contentColor = Color.DarkGray
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            tint = Color.DarkGray, imageVector = Icons.Filled.AddToQueue, contentDescription = "",
                            modifier = Modifier.size(32.dp).gradientOverlay() //.align(Alignment.Center)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(modifier = Modifier.gradientOverlay(),
                            text = "Add media", fontSize = 18.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}