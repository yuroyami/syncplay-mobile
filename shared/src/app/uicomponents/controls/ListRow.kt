package app.uicomponents.controls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import app.theme.Space
import app.theme.Type
import app.theme.palette

/**
 * The list row: full width, at least [Space.row] tall, with gutter padding. A screen reader
 * announces the whole row as one item. The row draws its own hover, focus, pressed and selected
 * states. The caller lays out the children; [RowLabel] and [RowValue] give the row its label and
 * its aligned value column.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    minHeight: Dp = Space.row,
    horizontalPadding: Dp = Space.gutter,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val interactive = onClick != null || onLongClick != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .then(
                if (interactive) Modifier
                    .combinedClickable(
                        interactionSource = source,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onLongClick = onLongClick,
                        onClick = { onClick?.let { Feedback.tick(); it() } },
                    )
                    .hoverable(source, enabled)
                else Modifier
            )
            .semantics(mergeDescendants = true) {}
            .controlStates(source, RectangleShape, selected = selected, enabled = enabled)
            .then(if (interactive) Modifier.pressFeedback(source, enabled) else Modifier)
            .graphicsLayer { if (!enabled) alpha = 0.38f }
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The row's label, in `label` type. It wraps as far as needed, so no meaning is lost. */
@Composable
fun RowScope.RowLabel(text: String, modifier: Modifier = Modifier, color: Color = palette.ink) {
    Text(
        text = text,
        style = Type.label,
        color = color,
        modifier = modifier.weight(1f),
    )
}

/**
 * The row's current value: end-aligned, in `value` type. With no [width], it takes the width its
 * text needs, up to 160dp, and wraps beyond that. So a long choice is never cut off, and whatever
 * follows it still sits at the row's edge. A fixed [width] (scaled with the text size) makes a
 * short, aligned column.
 */
@Composable
fun RowScope.RowValue(text: String, modifier: Modifier = Modifier, accent: Boolean = false, width: Dp? = null) {
    val p = palette
    Text(
        text = text,
        style = Type.value,
        color = if (accent) p.accent else p.inkDim,
        textAlign = TextAlign.End,
        modifier = if (width != null) modifier.width(width * LocalDensity.current.fontScale) else modifier.widthIn(max = Space.valueMax),
    )
}

/** A short gap inside a row, from the [Space] scale. */
@Composable
fun RowGap(width: Dp = Space.gap) {
    Spacer(Modifier.width(width))
}

/** A group heading in the gutter: `group` type, upper case, in the accent colour. */
@Composable
fun GroupHeading(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().heightIn(min = Space.groupHead).padding(horizontal = Space.gutter),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(text.uppercase(), style = Type.group, color = palette.accent)
    }
}
