package com.yuroyami.syncplay.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.managers.datastore.valueAsState
import com.yuroyami.syncplay.models.MessagePalette
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.ui.theme.Theming.backgroundGradient
import org.jetbrains.compose.resources.Font
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Helvetica_Regular
import syncplaymobile.shared.generated.resources.Res

/** Creates a flexible fancy text. It's basically a text that takes a list of colors to apply gradient or solid overlays,
 * For example, if you pass a filling list of one color, then a solid filling will be used, otherwise, gradient.
 * */
@Composable
fun FlexibleText(
    modifier: Modifier = Modifier,
    text: String,
    size: Float,
    font: Font? = null,
    textAlign: TextAlign = TextAlign.Start,
    fillingColors: List<Color> = listOf(MaterialTheme.colorScheme.primary),
    strokeColors: List<Color> = listOf(),
    strokeWidth: Float = 2f,
    shadowColors: List<Color> = listOf(),
    shadowOffset: Pair<Int, Int> = Pair(2, 2),
    shadowSize: Float = 6f,
) {
    Box(modifier = modifier) {
        /** Shadow */
        if (shadowColors.isNotEmpty()) {
            Text(
                text = text,
                modifier = if (shadowColors.size == 1) Modifier else Modifier.gradientOverlay(shadowColors),
                style = TextStyle(
                    color = Color.Transparent,
                    shadow = Shadow(
                        color = if (shadowColors.size == 1) shadowColors.first() else Color.Black, Offset(4f, 4f), blurRadius = shadowSize
                    ),
                    textAlign = textAlign,
                    fontFamily = font?.let { FontFamily(it) } ?: FontFamily.Default,
                    fontSize = size.sp,
                )
            )
        }


        /** Stroke */
        if (strokeColors.isNotEmpty()) {
            Text(
                text = text,
                style = if (strokeColors.size == 1) {
                    TextStyle(
                        color = strokeColors.first(),
                        drawStyle = Stroke(
                            miter = 10f,
                            width = strokeWidth,
                            join = StrokeJoin.Round
                        ),
                        textAlign = textAlign,
                        fontFamily = font?.let { FontFamily(it) } ?: FontFamily.Default,
                        fontSize = size.sp,
                    )
                } else {
                    TextStyle(
                        brush = Brush.linearGradient(colors = strokeColors),
                        drawStyle = Stroke(
                            miter = 10f,
                            width = strokeWidth,
                            join = StrokeJoin.Round
                        ),
                        textAlign = textAlign,
                        fontFamily = font?.let { FontFamily(it) } ?: FontFamily.Default,
                        fontSize = size.sp,
                    )
                }
            )
        }

        /** Filling */
        if (fillingColors.isNotEmpty()) {
            Text(
                text = text,
                style = if (fillingColors.size <= 1) {
                    TextStyle(
                        color = fillingColors.first(),
                        textAlign = textAlign,
                        fontFamily = font?.let { FontFamily(it) } ?: FontFamily.Default,
                        fontSize = size.sp,
                    )
                } else {
                    TextStyle(
                        brush = Brush.linearGradient(colors = fillingColors),
                        textAlign = textAlign,
                        fontFamily = font?.let { FontFamily(it) } ?: FontFamily.Default,
                        fontSize = size.sp,
                    )
                }
            )
        }
    }
}


/** Creates a text with syncplay style */
@Composable
fun SyncplayishText(
    modifier: Modifier = Modifier,
    string: String,
    size: Float,
    colorStops: List<Color> = Theming.SP_GRADIENT,
    stroke: Color = Theming.SP_PALE,
    shadow: Color = Theming.SP_INTENSE_PINK,
    textAlign: TextAlign = TextAlign.Start,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            modifier = Modifier.wrapContentWidth(),
            text = string,
            textAlign = textAlign,
            maxLines = 1,
            style = TextStyle(
                color = stroke,
                drawStyle = Stroke(
                    miter = 10f,
                    width = 2f,
                    join = StrokeJoin.Round
                ),
                shadow = Shadow(
                    color = shadow,
                    offset = Offset(0f, 10f),
                    blurRadius = 5f
                ),
                fontFamily = FontFamily(Font(Res.font.Directive4_Regular)),
                fontSize = size.sp,
            )
        )
        Text(
            modifier = Modifier.wrapContentWidth(),
            text = string,
            textAlign = textAlign,
            maxLines = 1,
            style = TextStyle(
                brush = Brush.linearGradient(
                    colors = colorStops
                ),
                fontFamily = FontFamily(Font(Res.font.Directive4_Regular)),
                fontSize = size.sp,
            )
        )
    }
}