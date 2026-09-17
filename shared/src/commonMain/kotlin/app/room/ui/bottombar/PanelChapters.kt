package app.room.ui.bottombar

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import app.i18n.Localization
import app.i18n.strings
import app.uicomponents.controls.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import app.LocalRoomViewmodel
import app.theme.Space
import app.theme.palette
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.RowValue
import app.uicomponents.controls.Rule
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.utils.timestampFromMillis
import kotlinx.coroutines.launch

/**
 * The chapter list, opened by a long press on the track. It reads the chapters the seekbar
 * already analysed and never re-analyses: every engine clears the list first, which would blank
 * the marks.
 */
@Composable
fun ChaptersModal(open: Boolean, onDismiss: () -> Unit) {
    if (!open) return
    val viewmodel = LocalRoomViewmodel.current
    val media by viewmodel.playerManager.media.collectAsState()
    val chapters = media?.chapters ?: emptyList()
    val p = palette

    Modal(open = true, onDismiss = onDismiss, title = strings.roomChapters, size = ModalSize.Panel, inset = false) {
        ListRow(onClick = {
            onDismiss()
            viewmodel.viewModelScope.launch {
                if (media == null || viewmodel.playerManager.media.value !== media) return@launch
                viewmodel.player.skipChapter()
                viewmodel.dispatchOSD { Localization.strings.roomChaptersSkip }
            }
        }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardTab, contentDescription = null, tint = p.inkDim, modifier = Modifier.size(Space.glyph))
            RowGap()
            RowLabel(strings.roomChaptersSkip)
        }
        Rule()
        chapters.forEach { chapter ->
            ListRow(onClick = {
                onDismiss()
                viewmodel.viewModelScope.launch {
                    if (media == null || viewmodel.playerManager.media.value !== media) return@launch
                    viewmodel.player.jumpToChapter(chapter)
                    viewmodel.dispatchOSD { Localization.strings.roomChaptersJump(chapter.name) }
                }
            }) {
                RowLabel(chapter.name)
                RowGap()
                RowValue(timestampFromMillis(chapter.timeOffsetMillis))
            }
        }
    }
}
