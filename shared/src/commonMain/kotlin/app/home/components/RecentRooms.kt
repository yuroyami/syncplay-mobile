package app.home.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.home.RecentJoin
import app.i18n.strings
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.FontSizeRange
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.Text
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.pressFeedback

/**
 * The recent rooms under the join key, newest first, in one row that scrolls sideways. A tap or
 * a remote press on an entry joins it. The cross beside it removes it from the list.
 */
@Composable
internal fun RecentRooms(
    entries: List<RecentJoin>,
    onPick: (RecentJoin) -> Unit,
    onForget: (RecentJoin) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.gapTight)) {
        Text(strings.homeRecentTitle.uppercase(), style = Type.group, color = palette.accent)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.gap)) {
            entries.forEach { entry -> RecentRoom(entry, onPick, onForget) }
        }
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
