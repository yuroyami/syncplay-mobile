package app.room.ui.misc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.theme.Theming.flexibleGradient
import app.uicomponents.sairaFont
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_brightness
import syncplaymobile.shared.generated.resources.room_volume
import app.uicomponents.PillIconBadge
import app.uicomponents.darkGlassPill

/** Which knob a swipe gesture is currently moving. */
enum class GestureValueKind { VOLUME, BRIGHTNESS }

/**
 * One frame of swipe-gesture feedback.
 *
 * @param display the number shown to the user (percent of "normal", so a boosting engine can read 150).
 * @param fraction 0..1 fill of the level bar.
 * @param normalMark where the 100% mark sits on the bar (< 1f only on engines that boost past 100%).
 */
data class GestureValue(
    val kind: GestureValueKind,
    val display: Int,
    val fraction: Float,
    val normalMark: Float = 1f,
)

private val BOOST_WARM = Color(0xFFFFC24B)
private val BOOST_HOT = Color(0xFFFF7A3D)

/**
 * Center-top readout for the volume/brightness swipe gestures.
 *
 * Pass the live value while a drag is in progress and `null` when it ends; the pill lingers
 * briefly, then fades out on its own. Over-video chrome, so its colors are fixed (dark glass +
 * white content) instead of theme-driven; only the level fill follows the theme accent.
 */
@Composable
fun RoomGestureValueHud(active: GestureValue?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<GestureValue?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(active) {
        if (active != null) {
            shown = active
            visible = true
        } else if (shown != null) {
            delay(700)
            visible = false
        }
    }

    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
            .padding(top = 20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)) +
                    scaleIn(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), initialScale = 0.88f) +
                    slideInVertically(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)) { -it / 2 },
            exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.94f) +
                    slideOutVertically(tween(220)) { -it / 3 }
        ) {
            shown?.let { GestureValuePill(it) }
        }
    }
}

@Composable
private fun GestureValuePill(value: GestureValue) {
    val fill by animateFloatAsState(
        targetValue = value.fraction.coerceIn(0f, 1f),
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
    )
    val accent = flexibleGradient
    val muted = value.kind == GestureValueKind.VOLUME && value.display == 0

    Row(
        modifier = Modifier
            .darkGlassPill()
            .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        /* Icon badge: swaps between tiers (mute → low → high) as the value moves. */
        PillIconBadge {
            Crossfade(targetState = value.iconFor(), animationSpec = tween(160)) { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (muted) Color.White.copy(alpha = 0.55f) else Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(Modifier.size(12.dp))

        Column {
            Text(
                text = stringResource(
                    if (value.kind == GestureValueKind.VOLUME) Res.string.room_volume else Res.string.room_brightness
                ).uppercase(),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(7.dp))

            Canvas(modifier = Modifier.size(width = 130.dp, height = 7.dp)) {
                val radius = size.height / 2f
                val corner = CornerRadius(radius, radius)
                val capped = { w: Float -> w.coerceIn(size.height, size.width) }

                drawRoundRect(color = Color.White.copy(alpha = 0.22f), cornerRadius = corner)

                val fillWidth = size.width * fill
                if (fillWidth > 0.5f) {
                    // Brush spans the whole track so the colors don't shift while the fill grows.
                    val accentBrush = Brush.horizontalGradient(accent, startX = 0f, endX = size.width)
                    // Faked bloom: an oversized, faint copy of the fill sitting behind it.
                    drawRoundRect(
                        brush = accentBrush,
                        alpha = 0.16f,
                        topLeft = Offset(0f, -size.height * 0.6f),
                        size = Size(capped(fillWidth), size.height * 2.2f),
                        cornerRadius = CornerRadius(size.height, size.height)
                    )
                    drawRoundRect(brush = accentBrush, size = Size(capped(fillWidth), size.height), cornerRadius = corner)
                }

                /* Boost engines (VLC, 0..200): mark where 100% sits and paint anything past it warm. */
                val markX = size.width * value.normalMark
                if (value.normalMark < 1f) {
                    if (fill > value.normalMark) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                listOf(BOOST_WARM, BOOST_HOT), startX = markX, endX = size.width
                            ),
                            topLeft = Offset(markX, 0f),
                            size = Size(capped(fillWidth - markX), size.height),
                            cornerRadius = corner
                        )
                    }
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.45f),
                        topLeft = Offset(markX - radius * 0.35f, 0f),
                        size = Size(radius * 0.7f, size.height)
                    )
                }

                /* Knob at the fill head, with a soft halo so it reads over bright frames. */
                val knobX = capped(fillWidth) - radius
                drawCircle(Color.Black.copy(alpha = 0.35f), radius = radius * 1.9f, center = Offset(knobX, radius))
                drawCircle(Color.White, radius = radius * 1.5f, center = Offset(knobX, radius))
            }
        }

        Spacer(Modifier.size(12.dp))

        Text(
            text = buildAnnotatedString {
                append("${value.display}")
                withStyle(SpanStyle(fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))) { append("%") }
            },
            color = if (muted) Color.White.copy(alpha = 0.6f) else Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily(sairaFont),
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 52.dp)
        )
    }
}

private fun GestureValue.iconFor(): ImageVector = when (kind) {
    GestureValueKind.VOLUME -> when {
        display == 0 -> Icons.AutoMirrored.Filled.VolumeOff
        display < 25 -> Icons.AutoMirrored.Filled.VolumeMute
        display < 65 -> Icons.AutoMirrored.Filled.VolumeDown
        else -> Icons.AutoMirrored.Filled.VolumeUp
    }

    GestureValueKind.BRIGHTNESS -> when {
        display < 25 -> Icons.Filled.BrightnessLow
        display < 65 -> Icons.Filled.Brightness5
        else -> Icons.Filled.Brightness7
    }
}
