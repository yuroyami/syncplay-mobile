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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.theme.Space
import app.theme.Type
import app.theme.palette

/**
 * The list row: full width, `row` tall at minimum, gutter padding, one announcement. Hover,
 * focus, pressed and selected are drawn by the row itself. Children are laid out by the caller;
 * [RowLabel] and [RowValue] give the channel strip its label and its aligned value column.
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
                        onClick = { onClick?.invoke() },
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

/** The name of the thing, in `label` type; a long one wraps to a second line instead of hiding. */
@Composable
fun RowScope.RowLabel(text: String, modifier: Modifier = Modifier, color: Color = palette.ink) {
    Text(
        text = text,
        style = Type.label,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.weight(1f),
    )
}

/**
 * The current value, end aligned, `value` type. With no [width] it takes what its text needs up
 * to 160dp and wraps past that, so a long choice is never cut off and whatever follows it still
 * sits at the row's edge; a fixed width pins a short column.
 */
@Composable
fun RowScope.RowValue(text: String, modifier: Modifier = Modifier, accent: Boolean = false, width: Dp? = null) {
    val p = palette
    Text(
        text = text,
        style = Type.value,
        color = if (accent) p.accent else p.inkDim,
        textAlign = TextAlign.End,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = if (width != null) modifier.width(width) else modifier.widthIn(max = Space.valueMax),
    )
}

/** A short gap inside a row, on the ladder. */
@Composable
fun RowGap(width: Dp = Space.gap) {
    Spacer(Modifier.width(width))
}

/** Group heading in the gutter: `group` type, uppercase, in the accent. */
@Composable
fun GroupHeading(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(Space.groupHead).padding(horizontal = Space.gutter),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(text.uppercase(), style = Type.group, color = palette.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
