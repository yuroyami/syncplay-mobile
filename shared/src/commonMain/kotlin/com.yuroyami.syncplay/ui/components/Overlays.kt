package com.yuroyami.syncplay.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.yuroyami.syncplay.ui.screens.theme.Theming.flexibleGradient

/** Adds a gradient overlay on the composable (Syncplay gradient by default) */
@Composable
fun Modifier.gradientOverlay(colors: List<Color> = flexibleGradient): Modifier {
    return this
        .graphicsLayer(alpha = 0.99f)
        .drawWithCache {
            onDrawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.linearGradient(
                        colors = colors
                    ), blendMode = BlendMode.SrcAtop
                )
            }
        }
}

/** Draws a solid fill that overlays the non-transparent parts of the composable. */
fun Modifier.solidOverlay(color: Color): Modifier {
    return this
        .graphicsLayer(alpha = 0.99f)
        .drawWithCache {
            onDrawWithContent {
                drawContent()
                drawRect(
                    color = color,
                    blendMode = BlendMode.SrcAtop
                )
            }
        }
}