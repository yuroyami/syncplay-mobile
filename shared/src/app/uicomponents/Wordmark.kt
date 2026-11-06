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
import app.theme.palette

/**
 * The wordmark: the brand name in the brand font, in ink. The logo beside it carries the colour.
 * [colorStops], when given, paint the name from start to end instead. It is for identity only,
 * never body text, so [size] is in dp: the system text scale is for reading, and a logo is not read.
 */
@Composable
fun SyncplayishText(
    modifier: Modifier = Modifier,
    string: String,
    size: Float,
    colorStops: List<Color>? = null,
    textAlign: TextAlign = TextAlign.Start,
) {
    val ink = palette.ink
    val fontSize = with(LocalDensity.current) { size.dp.toSp() }
    val base = TextStyle(fontFamily = FontFamily(syncplayFont), fontSize = fontSize, letterSpacing = fontSize * 0.02f)
    Text(
        modifier = modifier.wrapContentWidth(),
        text = string,
        textAlign = textAlign,
        maxLines = 1,
        style = if (colorStops == null) base.copy(color = ink) else base.copy(brush = Brush.horizontalGradient(colorStops)),
    )
}
