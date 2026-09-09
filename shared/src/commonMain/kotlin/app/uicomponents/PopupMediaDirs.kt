package app.uicomponents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import app.i18n.strings
import app.uicomponents.controls.Icon
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.preferences.Preferences
import app.preferences.set
import app.preferences.settings.AskModal
import app.preferences.value
import app.preferences.watchPref
import app.room.sharedplaylist.MediaAccessRegistry
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.CloseGlyph
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.utils.appName
import app.utils.ioDispatcher
import com.eygraber.uri.Uri
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import kotlinx.coroutines.launch

object PopupMediaDirs {

    /**
     * The media folders editor, reached from settings and from the playlist panel: one row per
     * remembered folder with its name and path, an empty state, add and clear. Removing a folder
     * updates the preference and the registry together.
     */
    @Composable
    fun MediaDirsPopup(visibilityState: MutableState<Boolean>) {
        val p = palette
        val scope = rememberCoroutineScope { ioDispatcher }
        val dirs by Preferences.MEDIA_DIRECTORIES.watchPref()
        val askClear = remember { mutableStateOf(false) }

        val directoryPicker = rememberDirectoryPickerLauncher { directory ->
            directory ?: return@rememberDirectoryPickerLauncher
            scope.launch { MediaAccessRegistry.rememberDirectory(directory) }
        }

        Modal(
            open = visibilityState.value,
            onDismiss = { visibilityState.value = false },
            title = strings.mediaFolders,
            size = ModalSize.Panel,
            inset = false,
            actions = {
                SecondaryAction(strings.mediaDirectoriesClearAll, onClick = { askClear.value = true }, enabled = dirs.isNotEmpty())
                AccentAction(strings.mediaDirectoriesAddFolder, onClick = { directoryPicker.launch() })
            },
        ) {
            Text(
                text = strings.mediaFoldersBrief(appName),
                style = Type.note,
                color = p.inkDim,
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gap),
            )
            if (dirs.isEmpty()) {
                Text(
                    text = strings.mediaFoldersEmpty,
                    style = Type.note,
                    color = p.inkFaint,
                    modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gap),
                )
            }
            dirs.forEach { item ->
                // Android document ids carry a storage prefix before the path; the folder name is what matters.
                val name = (Uri.parseOrNull(item)?.pathSegments?.lastOrNull() ?: item)
                    .substringAfter("primary:").substringAfter("secondary:").substringAfterLast("/")
                ListRow(minHeight = Space.rowTall) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = p.inkDim, modifier = Modifier.size(Space.glyph))
                    RowGap()
                    Column(Modifier.weight(1f).padding(vertical = Space.gapTight)) {
                        Text(name, style = Type.label, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item, style = Type.note, color = p.inkDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    GlyphButton(CloseGlyph, name = strings.mediaDirectoriesDelete, tint = p.inkDim) {
                        scope.launch {
                            val paths = Preferences.MEDIA_DIRECTORIES.value().toMutableSet()
                            if (paths.remove(item)) {
                                Preferences.MEDIA_DIRECTORIES.set(paths)
                                MediaAccessRegistry.forgetDirectory(item)
                            }
                        }
                    }
                }
            }
        }

        AskModal(
            open = askClear,
            title = strings.mediaDirectoriesClearAll,
            text = strings.mediaDirectoriesClearAllConfirm,
            destructive = true,
            onYes = {
                scope.launch {
                    Preferences.MEDIA_DIRECTORIES.set(emptySet())
                    MediaAccessRegistry.clear()
                }
            },
        )
    }
}
