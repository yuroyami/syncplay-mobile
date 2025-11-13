package com.yuroyami.syncplay.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yuroyami.syncplay.ui.theme.Theming

/**
 * Draws an adaptive gradient-tinted icon with optional shadow and click behavior.
 *
 * This composable creates a stylized icon that supports gradient tints and shadows.
 * If multiple colors are provided in [tintColors], a gradient brush is applied to the icon.
 * If only one color is provided, a solid color tint is used instead, since gradient brushes
 * are not applicable to single-color lists.
 *
 * A similar rule applies to [shadowColors]: if more than one color is provided,
 * a gradient shadow overlay is drawn behind the icon.
 *
 * The icon also supports click interactions via the [onClick] lambda.
 *
 * @param modifier The [Modifier] to be applied to this composable.
 * @param icon The [ImageVector] representing the icon graphic.
 * @param size The size of the icon in dp. Defaults to [Theming.ROOM_ICON_SIZE].
 * @param tintColors A list of colors used to tint the icon.
 * If multiple colors are given, a gradient will be applied.
 * @param shadowColors A list of colors used for rendering the icon shadow.
 * If empty, no shadow is drawn.
 * @param shadowOffset A [Pair] defining the X and Y offset of the shadow in dp.
 * @param alpha The opacity of the entire icon, where 1f is fully opaque and 0f is fully transparent.
 * @param onClick A lambda invoked when the icon is clicked. Defaults to a no-op.
 *
 * Example usage:
 * ```
 * FlexibleIcon(
 *     icon = Icons.Default.Favorite,
 *     tintColors = listOf(Color.Magenta, Color.Red),
 *     shadowColors = listOf(Color.Black.copy(alpha = 0.4f)),
 *     onClick = { println("Favorite clicked!") }
 * )
 * ```
 */
@Composable
fun FlexibleIcon(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    size: Int = Theming.ROOM_ICON_SIZE,
    tintColors: List<Color> = listOf(MaterialTheme.colorScheme.primary),
    shadowColors: List<Color> = Theming.SP_GRADIENT,
    shadowOffset: Pair<Int, Int> = Pair(1, 1),
    alpha: Float = 0.95f,
    onClick: () -> Unit = {},
) {
    IconButton(
        modifier = modifier
            .alpha(alpha)
            .wrapContentSize()
            .size(size.dp),
        onClick = { onClick() }
    ) {
        Box(
            modifier = Modifier
                .padding(4.dp)
                .wrapContentSize()
        ) {
            if (shadowColors.isNotEmpty()) {
                /** Shadow */
                val shadowModifier = Modifier
                    .size(size.dp)
                    .offset(x = shadowOffset.first.dp, y = shadowOffset.second.dp)
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = if (shadowColors.size == 1) shadowModifier else shadowModifier.gradientOverlay(shadowColors),
                    tint = if (shadowColors.size == 1) shadowColors.first() else Color.Black
                )
            }

            /** Foreground */
            val fgModifier = Modifier
                .size((size - 0.5).dp)
                .alpha(0.9f)

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = if (tintColors.size <= 1) fgModifier else fgModifier.gradientOverlay(tintColors),
                tint = tintColors.firstOrNull() ?: Color.White
            )
        }
    }
}
