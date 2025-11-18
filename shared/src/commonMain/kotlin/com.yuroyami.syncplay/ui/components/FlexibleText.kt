package com.yuroyami.syncplay.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.ui.screens.theme.Theming
import org.jetbrains.compose.resources.Font
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res

/**
 * Draws a highly customizable text element supporting gradient fills, strokes, and shadows.
 *
 * This composable renders a text string with layered visual effects, allowing flexible combinations
 * of fill colors, stroke outlines, and shadows. It automatically determines whether to use a solid
 * or gradient fill/stroke depending on the number of colors provided.
 *
 * @param modifier Modifier to apply to this composable.
 * @param text The [AnnotatedString] to display.
 * @param size Font size in sp units.
 * @param font The custom [Font] to use, or `null` for default.
 * @param textAlign The horizontal alignment of the text.
 * @param fillingColors List of colors used to fill the text. If multiple, a gradient is applied.
 * @param strokeColors List of colors used for outlining the text. If empty, no stroke is drawn.
 * @param strokeWidth The width of the stroke outline, in dp.
 * @param shadowColors List of colors used for shadow rendering. If multiple, a gradient shadow is used.
 * @param shadowOffset Offset of the shadow in dp.
 * @param shadowSize Blur radius of the shadow.
 * @param lineHeight Spacing between lines, in sp.
 * @param overflow Text overflow behavior when content exceeds available width.
 */
@Composable
fun FlexibleAnnotatedText(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    size: Float,
    font: Font? = null,
    fontWeight: FontWeight = FontWeight.W400,
    textAlign: TextAlign = TextAlign.Start,
    fillingColors: List<Color> = listOf(MaterialTheme.colorScheme.primary),
    strokeColors: List<Color> = listOf(),
    strokeWidth: Float = 2f,
    shadowColors: List<Color> = listOf(),
    shadowOffset: Offset = Offset(4f, 4f),
    shadowSize: Float = 6f,
    lineHeight: Float = size + 2,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    Box(modifier = modifier) {

        /** Shadow layer */
        if (shadowColors.isNotEmpty()) {
            Text(
                text = text,
                modifier = if (shadowColors.size == 1) Modifier else Modifier.gradientOverlay(shadowColors),
                style = TextStyle(
                    color = Color.Transparent,
                    shadow = Shadow(
                        color = if (shadowColors.size == 1) shadowColors.first() else Color.Black,
                        offset = shadowOffset,
                        blurRadius = shadowSize
                    ),
                    textAlign = textAlign,
                    fontFamily = font?.let { FontFamily(it) } ?: FontFamily.Default,
                    fontSize = size.sp,
                ),
                lineHeight = lineHeight.sp,
                overflow = overflow,
                fontWeight = fontWeight
            )
        }

        /** Stroke layer */
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
                        fontSize = size.sp
                    )
                },
                lineHeight = lineHeight.sp,
                overflow = overflow,
                fontWeight = fontWeight
            )
        }

        /** Fill layer */
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
                },
                lineHeight = lineHeight.sp,
                overflow = overflow,
                fontWeight = fontWeight
            )
        }
    }
}

/**
 * Convenience wrapper around [FlexibleAnnotatedText] that accepts a plain string.
 *
 * This is a simpler version of [FlexibleAnnotatedText] for use when annotation
 * support isn’t needed.
 *
 * @param text The string to display.
 * @param size Font size in sp.
 * @param font Optional [Font] to apply.
 * @param textAlign The horizontal text alignment.
 * @param fillingColors List of fill colors. Gradient applied if more than one.
 * @param strokeColors List of stroke colors. Gradient applied if more than one.
 * @param strokeWidth Stroke width, in dp.
 * @param shadowColors Colors for shadow rendering.
 * @param shadowOffset Offset of the shadow, in dp.
 * @param shadowSize Blur radius of the shadow.
 * @param lineHeight Line height in sp.
 * @param overflow Behavior when text overflows its container.
 */
@Composable
fun FlexibleText(
    modifier: Modifier = Modifier,
    text: String,
    size: Float,
    font: Font? = null,
    textAlign: TextAlign = TextAlign.Start,
    fontWeight: FontWeight = FontWeight.W400,
    fillingColors: List<Color> = listOf(MaterialTheme.colorScheme.primary),
    strokeColors: List<Color> = listOf(),
    strokeWidth: Float = 2f,
    shadowColors: List<Color> = listOf(),
    shadowOffset: Offset = Offset(4f, 4f),
    shadowSize: Float = 6f,
    lineHeight: Float = size + 3,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    FlexibleAnnotatedText(
        text = AnnotatedString(text),
        modifier = modifier,
        size = size,
        font = font,
        textAlign = textAlign,
        fontWeight = fontWeight,
        fillingColors = fillingColors,
        strokeColors = strokeColors,
        strokeWidth = strokeWidth,
        shadowColors = shadowColors,
        shadowOffset = shadowOffset,
        shadowSize = shadowSize,
        lineHeight = lineHeight,
        overflow = overflow
    )
}

/**
 * Renders a stylized Syncplay-branded text with gradient fill, stroke, and shadow.
 *
 * This composable produces the signature “Syncplay” text look using pre-defined
 * theme gradients and stroke/shadow colors. It’s primarily used for headers and
 * branding elements in the Syncplay UI.
 *
 * Example:
 * ```
 * SyncplayishText(
 *     string = "SYNCPLAY",
 *     size = 32f
 * )
 * ```
 *
 * @param string The text content to display.
 * @param size Font size in sp.
 * @param colorStops List of gradient colors for text fill.
 * @param stroke Color of the stroke outline.
 * @param shadow Color of the drop shadow.
 * @param textAlign Text alignment within its bounds.
 */
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
        // Stroke + shadow layer
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
        // Foreground gradient layer
        Text(
            modifier = Modifier.wrapContentWidth(),
            text = string,
            textAlign = textAlign,
            maxLines = 1,
            style = TextStyle(
                brush = Brush.linearGradient(colors = colorStops),
                fontFamily = FontFamily(Font(Res.font.Directive4_Regular)),
                fontSize = size.sp,
            )
        )
    }
}