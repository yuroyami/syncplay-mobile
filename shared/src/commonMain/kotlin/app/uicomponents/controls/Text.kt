package app.uicomponents.controls

import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.theme.Type
import app.theme.palette

/**
 * The app's text, on the foundation text with the app's roles: `note` unless told otherwise, the
 * palette's ink unless the style or the caller carries a colour or a brush. No Material.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = Type.note,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
) {
    val resolved = when {
        color.isSpecified -> color
        style.brush != null || style.color.isSpecified -> Color.Unspecified
        else -> palette.ink
    }
    val merged = style.merge(TextStyle(color = resolved, textAlign = textAlign ?: TextAlign.Unspecified))
    BasicText(text = text, modifier = modifier, style = merged, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines)
}

/** A vector glyph tinted in one colour, the palette's ink by default. No Material. */
@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = palette.ink,
) {
    Image(
        painter = rememberVectorPainter(imageVector),
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = if (tint.isSpecified) ColorFilter.tint(tint) else null,
    )
}
