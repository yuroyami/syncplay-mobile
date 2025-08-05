package com.yuroyami.syncplay.screens.room

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.ui.Paletting
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.syncplay_logo_gradient

/** The Syncplay artwork that is displayed in the video frame when no video is loaded */
@Composable
fun RoomArtwork() {
    val viewmodel = LocalViewmodel.current
    val isInPipMode by viewmodel.hasEnteredPipMode.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = Brush.linearGradient(colors = Paletting.backgroundGradient)),
    ) {
        Column(
            modifier = Modifier
                .wrapContentWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Image(
                painter = painterResource(Res.drawable.syncplay_logo_gradient), contentDescription = "",
                modifier = Modifier.height(if (isInPipMode) 40.dp else 84.dp).aspectRatio(1f)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Box(modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    modifier = Modifier.wrapContentWidth(),
                    text = "Syncplay",
                    style = TextStyle(
                        color = Paletting.SP_PALE,
                        drawStyle = Stroke(miter = 10f, width = 2f, join = StrokeJoin.Round
                        ),
                        shadow = Shadow(
                            color = Paletting.SP_INTENSE_PINK,
                            offset = Offset(0f, 10f),
                            blurRadius = 5f
                        ),
                        fontFamily = FontFamily(Font(Res.font.Directive4_Regular))
                    ),
                    fontSize = if (isInPipMode) 8.sp else 26.sp,
                )
                Text(
                    modifier = Modifier.wrapContentWidth(),
                    text = "Syncplay",
                    style = TextStyle(
                        brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT),
                        fontFamily = FontFamily(Font(Res.font.Directive4_Regular))
                    ),
                    fontSize = if (isInPipMode) 8.sp else 26.sp,
                )
            }
        }
    }
}