package app.room.ui.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HearingDisabled
import app.uicomponents.controls.Icon
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.LocalRoomViewmodel
import app.preferences.Preferences.SUBTITLE_SEARCH_LANG
import app.preferences.set
import app.preferences.watchPref
import app.subtitles.SubtitleDownloadResult
import app.subtitles.SubtitleResult
import app.subtitles.SubtitleSearch
import app.subtitles.subtitleSearchLanguages
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.CheckGlyph
import app.uicomponents.controls.Chevron
import app.uicomponents.controls.ChevronDirection
import app.uicomponents.controls.Field
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.ProgressBar
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.RowValue
import app.uicomponents.controls.SearchGlyph
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_selected_sub_error
import syncplaymobile.shared.generated.resources.room_sub_search_downloads
import syncplaymobile.shared.generated.resources.room_sub_search_hint
import syncplaymobile.shared.generated.resources.room_sub_search_language
import syncplaymobile.shared.generated.resources.room_sub_search_no_results
import syncplaymobile.shared.generated.resources.room_sub_search_title
import syncplaymobile.shared.generated.resources.room_subs_download_failed
import syncplaymobile.shared.generated.resources.room_subs_downloaded_remaining
import syncplaymobile.shared.generated.resources.room_subs_quota_reached

/**
 * The OpenSubtitles search: a hairline field, a language row, the progress bar on the rim while
 * searching, and results as rows. Failures render inside the modal, because a notice would sit
 * behind it, and the modal stays open so another result can be tried.
 */
@Composable
fun SubtitleSearchModal(open: Boolean, onDismiss: () -> Unit) {
    if (!open) return
    val viewmodel = LocalRoomViewmodel.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val p = palette

    val initialQuery = remember { viewmodel.media?.fileName?.let { SubtitleSearch.cleanMediaName(it) } ?: "" }
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<SubtitleResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf<Int?>(null) }
    var downloadedOk by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showLanguages by remember { mutableStateOf(false) }

    /* The language filter persists (default English); "all" drops the filter entirely. */
    val languageCode by SUBTITLE_SEARCH_LANG.watchPref()
    val languageName = remember(languageCode) {
        subtitleSearchLanguages.firstOrNull { it.second == languageCode }?.first ?: languageCode.uppercase()
    }

    fun search() {
        if (query.isBlank()) return
        focusManager.clearFocus() // the keyboard would cover the results
        scope.launch(Dispatchers.IO) {
            searching = true
            results = SubtitleSearch.search(query, languageCode)
            searching = false
        }
    }

    /* Search on open and again whenever the language changes, keeping the current query. */
    LaunchedEffect(languageCode) {
        if (query.isNotBlank()) {
            searching = true
            results = SubtitleSearch.search(query, languageCode)
            searching = false
        }
    }

    Modal(open = true, onDismiss = onDismiss, title = stringResource(Res.string.room_sub_search_title), size = ModalSize.Panel, inset = false) {
        if (searching) ProgressBar(progress = null)

        Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.gapTight)) {
            Field(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(Res.string.room_sub_search_hint),
                leading = SearchGlyph,
                imeAction = ImeAction.Search,
                onImeAction = { search() },
                name = stringResource(Res.string.room_sub_search_hint),
            )
        }

        ListRow(onClick = { showLanguages = true }) {
            RowLabel(stringResource(Res.string.room_sub_search_language))
            RowGap()
            RowValue(languageName)
            RowGap(Space.gapTight)
            Chevron(ChevronDirection.Right)
        }

        error?.let { message ->
            Row(Modifier.fillMaxWidth().padding(vertical = Space.gapTight), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(2.dp).height(Space.rowCompact).background(p.bad))
                Spacer(Modifier.width(Space.gutter - 2.dp))
                Text(message, style = Type.note, color = p.bad, modifier = Modifier.weight(1f).padding(end = Space.gutter))
            }
        }

        if (!searching && results.isEmpty()) {
            Text(
                text = stringResource(Res.string.room_sub_search_no_results),
                style = Type.note,
                color = p.inkDim,
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gap),
            )
        }

        results.forEach { result ->
            val busy = downloading != null || downloadedOk != null
            ListRow(
                enabled = !busy,
                minHeight = Space.rowTall,
                onClick = {
                    error = null
                    downloading = result.fileId
                    scope.launch(Dispatchers.IO) {
                        when (val outcome = SubtitleSearch.download(result.fileId)) {
                            is SubtitleDownloadResult.Success -> {
                                val injected = viewmodel.player.loadSubtitleFromPath(outcome.path, outcome.fileName)
                                downloading = null
                                if (injected) {
                                    // The tracks panel stays open behind this, so its list must follow.
                                    viewmodel.media?.let { viewmodel.player.analyzeTracks(it) }
                                    // Free plan keys allow a handful of downloads a day; searches are unlimited.
                                    viewmodel.dispatchOSD { getString(Res.string.room_subs_downloaded_remaining, outcome.remaining) }
                                    downloadedOk = result.fileId
                                    delay(1000) // let the check land before the modal leaves
                                    onDismiss()
                                } else {
                                    error = getString(Res.string.room_selected_sub_error)
                                }
                            }
                            is SubtitleDownloadResult.QuotaExceeded -> {
                                downloading = null
                                error = getString(Res.string.room_subs_quota_reached, outcome.resetTime)
                            }
                            SubtitleDownloadResult.Failed -> {
                                downloading = null
                                error = getString(Res.string.room_subs_download_failed)
                            }
                        }
                    }
                },
            ) {
                when {
                    downloadedOk == result.fileId -> Icon(CheckGlyph, contentDescription = null, tint = p.ok, modifier = Modifier.size(Space.glyph))
                    downloading == result.fileId -> Box(Modifier.size(Space.glyph)) { ProgressBar(progress = null, modifier = Modifier.align(Alignment.Center)) }
                    else -> Icon(Icons.Filled.Download, contentDescription = null, tint = p.inkDim, modifier = Modifier.size(Space.glyph))
                }
                RowGap()
                Column(Modifier.weight(1f)) {
                    Text(result.releaseInfo.ifBlank { result.filename }, style = Type.label, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${result.language.uppercase()} · ${result.downloadCount} ${stringResource(Res.string.room_sub_search_downloads)}",
                            style = Type.value,
                            color = p.inkDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (result.hearingImpaired) {
                            Spacer(Modifier.width(Space.gapTight))
                            Icon(Icons.Filled.HearingDisabled, contentDescription = null, tint = p.inkDim, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }

    Modal(
        open = showLanguages,
        onDismiss = { showLanguages = false },
        title = stringResource(Res.string.room_sub_search_language),
        size = ModalSize.Panel,
        inset = false,
    ) {
        subtitleSearchLanguages.forEach { (name, code) ->
            ListRow(
                selected = code == languageCode,
                onClick = {
                    showLanguages = false
                    scope.launch { SUBTITLE_SEARCH_LANG.set(code) }
                },
            ) { RowLabel(name) }
        }
    }
}
