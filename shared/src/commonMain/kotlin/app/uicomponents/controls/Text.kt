package app.uicomponents.controls

import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import app.theme.AutoSize
import app.theme.Type
import app.theme.palette

/**
 * The sizes a label may take when it has to fit its width: [max] down to [min], half a point at a
 * time. `Text` shrinks first and cuts only when the floor still overflows, so a caller that names
 * only a maximum gets the guarantee that the label fits unless it cannot fit at [AutoSize.floor].
 */
@Immutable
class FontSizeRange(val max: TextUnit, val min: TextUnit = AutoSize.floor)

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
    autoSize: FontSizeRange? = null,
) {
    val merged = appTextStyle(color, style, textAlign)
    if (autoSize == null) {
        BasicText(text = text, modifier = modifier, style = merged, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines)
        return
    }
    /* Foundation's auto-size never shrinks a label that an ellipsis has already cut: the cut
     * text counts as fitting. So the search runs with a clip, and only when even the floor
     * overflows is the label drawn again at the floor with the caller's overflow. It returns
     * to sizing the moment the floor fits whole, so a wider window grows it back. */
    var atFloor by remember { mutableStateOf(false) }
    if (!atFloor) {
        BasicText(
            text = text,
            modifier = modifier,
            style = merged,
            onTextLayout = { if (it.hasVisualOverflow) atFloor = true },
            overflow = TextOverflow.Clip,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            autoSize = TextAutoSize.StepBased(minFontSize = autoSize.min, maxFontSize = autoSize.max, stepSize = AutoSize.step),
        )
    } else {
        BasicText(
            text = text,
            modifier = modifier,
            style = merged.copy(fontSize = autoSize.min),
            onTextLayout = { if (!it.hasVisualOverflow && (0 until it.lineCount).none(it::isLineEllipsized)) atFloor = false },
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
        )
    }
}

/** [Text] for a sentence with styled spans, such as a name in its own colour. No auto-size. */
@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = Type.note,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = appTextStyle(color, style, textAlign),
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
    )
}

/** The caller's colour, else the style's colour or brush, else the palette's ink. */
@Composable
@ReadOnlyComposable
private fun appTextStyle(color: Color, style: TextStyle, textAlign: TextAlign?): TextStyle {
    val resolved = when {
        color.isSpecified -> color
        style.brush != null || style.color.isSpecified -> Color.Unspecified
        else -> palette.ink
    }
    return style.merge(TextStyle(color = resolved, textAlign = textAlign ?: TextAlign.Unspecified))
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
