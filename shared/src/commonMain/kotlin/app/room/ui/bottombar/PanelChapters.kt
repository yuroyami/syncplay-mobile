package app.room.ui.bottombar

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import app.LocalRoomViewmodel
import app.theme.Space
import app.theme.palette
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.RowValue
import app.uicomponents.controls.Rule
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.utils.timestampFromMillis
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_chapters
import syncplaymobile.shared.generated.resources.room_chapters_jump
import syncplaymobile.shared.generated.resources.room_chapters_skip

/**
 * The chapter list, opened by a long press on the track. It reads the chapters the seekbar
 * already analysed and never re-analyses: every engine clears the list first, which would blank
 * the marks.
 */
@Composable
fun ChaptersModal(open: Boolean, onDismiss: () -> Unit) {
    if (!open) return
    val viewmodel = LocalRoomViewmodel.current
    val chapters = viewmodel.media?.chapters ?: emptyList()
    val p = palette

    Modal(open = true, onDismiss = onDismiss, title = stringResource(Res.string.room_chapters), size = ModalSize.Panel, inset = false) {
        ListRow(onClick = {
            Feedback.tick()
            onDismiss()
            viewmodel.viewModelScope.launch {
                viewmodel.player.skipChapter()
                viewmodel.dispatchOSD { getString(Res.string.room_chapters_skip) }
            }
        }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardTab, contentDescription = null, tint = p.inkDim, modifier = Modifier.size(Space.glyph))
            RowGap()
            RowLabel(stringResource(Res.string.room_chapters_skip))
        }
        Rule()
        chapters.forEach { chapter ->
            ListRow(onClick = {
                Feedback.tick()
                onDismiss()
                viewmodel.viewModelScope.launch {
                    viewmodel.player.jumpToChapter(chapter)
                    viewmodel.dispatchOSD { getString(Res.string.room_chapters_jump, chapter.name) }
                }
            }) {
                RowLabel(chapter.name)
                RowGap()
                RowValue(timestampFromMillis(chapter.timeOffsetMillis))
            }
        }
    }
}
