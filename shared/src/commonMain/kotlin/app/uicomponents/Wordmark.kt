package app.uicomponents

import androidx.compose.foundation.layout.wrapContentWidth
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.theme.Theming

/**
 * The wordmark: the brand name in the brand font, coloured with the theme's three seed colours
 * unless [colorStops] names others. It is for identity only, never body text, so [size] is in dp:
 * the system text scale is for reading, and a logo is not read.
 */
@Composable
fun SyncplayishText(
    modifier: Modifier = Modifier,
    string: String,
    size: Float,
    colorStops: List<Color>? = null,
    textAlign: TextAlign = TextAlign.Start,
) {
    val colors = colorStops ?: Theming.flexibleGradient
    val fontSize = with(LocalDensity.current) { size.dp.toSp() }
    Text(
        modifier = modifier.wrapContentWidth(),
        text = string,
        textAlign = textAlign,
        maxLines = 1,
        style = TextStyle(
            brush = Brush.linearGradient(colors = colors),
            fontFamily = FontFamily(syncplayFont),
            fontSize = fontSize,
            letterSpacing = fontSize * 0.02f,
        ),
    )
}
