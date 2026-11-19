package app.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.home.RecentJoin
import app.i18n.strings
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.FontSizeRange
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.Icon
import app.uicomponents.controls.Text
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.pressFeedback

/**
 * The recent rooms, newest first, in one row that scrolls sideways. The row opens above the join
 * row, from the recent rooms key at the end of that row. A tap or a remote press on an entry joins
 * it. The cross beside it removes it from the list.
 */
@Composable
internal fun RecentRoomsStrip(
    entries: List<RecentJoin>,
    onPick: (RecentJoin) -> Unit,
    onForget: (RecentJoin) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.gap)) {
        items(entries, key = { "${it.user}@${it.room}@${it.server}" }) { entry -> RecentRoom(entry, onPick, onForget) }
    }
}

/**
 * The recent rooms key at the end of the join row: a history glyph. A tap opens the row of recent
 * rooms above the join row, and a second tap closes it. While the row is open, the glyph and the
 * rim take the accent, and a screen reader hears the key as selected.
 */
@Composable
internal fun RecentRoomsKey(open: Boolean, onToggle: () -> Unit) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    val name = strings.homeRecentTitle
    Box(
        modifier = Modifier
            .size(Space.touchMin)
            .clip(Radius.controlShape)
            .background(p.ground)
            .border(Space.hair, if (open) p.accent else p.rule, Radius.controlShape)
            .clickable(interactionSource = source, indication = null, role = Role.Button) { Feedback.tick(); onToggle() }
            .hoverable(source)
            .semantics {
                contentDescription = name
                selected = open
            }
            .controlStates(source, Radius.controlShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.History, contentDescription = null, tint = if (open) p.accent else p.ink, modifier = Modifier.size(Space.glyph))
    }
}

@Composable
private fun RecentRoom(entry: RecentJoin, onPick: (RecentJoin) -> Unit, onForget: (RecentJoin) -> Unit) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    val name = strings.homeRecentJoin(entry.room, entry.server)
    Row(
        modifier = Modifier
            .clip(Radius.controlShape)
            .border(Space.hair, p.rule, Radius.controlShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .clickable(interactionSource = source, indication = null, role = Role.Button) { onPick(entry) }
                .semantics(mergeDescendants = true) { contentDescription = name }
                .controlStates(source, Radius.controlShape)
                .pressFeedback(source)
                // The cap grows with the text size, and each line shrinks before it is cut.
                .widthIn(max = 200.dp * LocalDensity.current.fontScale)
                .padding(start = Space.gap, top = Space.gapTight, bottom = Space.gapTight),
        ) {
            Text(entry.room, style = Type.value, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, autoSize = FontSizeRange(Type.value.fontSize))
            Text(entry.server, style = Type.note, color = p.inkDim, maxLines = 1, overflow = TextOverflow.Ellipsis, autoSize = FontSizeRange(Type.note.fontSize))
        }
        GlyphButton(Icons.Filled.Close, name = strings.homeRecentForget(entry.room), tint = p.inkDim) { onForget(entry) }
    }
}
