package app.uicomponents

import androidx.compose.foundation.layout.wrapContentWidth
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.theme.Theming

/** The wordmark: the brand face in the theme's three seeds. Identity only, never body text. */
@Composable
fun SyncplayishText(
    modifier: Modifier = Modifier,
    string: String,
    size: Float,
    colorStops: List<Color>? = null,
    textAlign: TextAlign = TextAlign.Start,
) {
    val colors = colorStops ?: Theming.flexibleGradient
    Text(
        modifier = modifier.wrapContentWidth(),
        text = string,
        textAlign = textAlign,
        maxLines = 1,
        style = TextStyle(
            brush = Brush.linearGradient(colors = colors),
            fontFamily = FontFamily(syncplayFont),
            fontSize = size.sp,
            letterSpacing = (size * 0.02f).sp,
        ),
    )
}
