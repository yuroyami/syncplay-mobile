package app.preferences.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import app.i18n.strings
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.preferences.Preferences
import app.preferences.set
import app.preferences.value
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.AddGlyph
import app.uicomponents.controls.CloseGlyph
import app.uicomponents.controls.Field
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.utils.ioDispatcher
import kotlinx.coroutines.launch
import syncplaymobile.shared.generated.resources.cancel
import syncplaymobile.shared.generated.resources.save

/**
 * The trusted domains as a list: an inline add row, one hairline row per domain with remove.
 * Reads split on newline and comma, saves joined by newline, as the matcher expects.
 */
@Composable
fun TrustedDomainsPopup(visibilityState: MutableState<Boolean>) {
    if (!visibilityState.value) return
    val p = palette
    val scope = rememberCoroutineScope { ioDispatcher }
    val domains = remember {
        mutableStateListOf<String>().apply {
            addAll(Preferences.TRUSTED_DOMAINS.value().split("\n", ",").map { it.trim() }.filter { it.isNotEmpty() }.distinct())
        }
    }
    var draft by remember { mutableStateOf("") }

    fun addDraft() {
        val d = draft.trim()
        if (d.isNotEmpty() && d !in domains) domains.add(d)
        draft = ""
    }
    fun close() { visibilityState.value = false }
    fun save() {
        addDraft()
        val joined = domains.joinToString("\n")
        scope.launch { Preferences.TRUSTED_DOMAINS.set(joined) }
        close()
    }

    Modal(
        open = true,
        onDismiss = ::close,
        title = strings.settingTrustedDomainsTitle,
        size = ModalSize.Panel,
        inset = false,
        actions = {
            SecondaryAction(strings.trustedDomainsClear, onClick = { domains.clear() }, enabled = domains.isNotEmpty())
            SecondaryAction(strings.cancel, onClick = ::close)
            AccentAction(strings.save, onClick = ::save)
        },
    ) {
        Text(
            text = strings.settingTrustedDomainsSummary,
            style = Type.note,
            color = p.inkDim,
            modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gap),
        )
        Row(Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gapTight), verticalAlignment = Alignment.CenterVertically) {
            Field(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = strings.trustedDomainsAddHint,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
                onImeAction = ::addDraft,
                showClear = false,
                name = strings.trustedDomainsAdd,
            )
            GlyphButton(AddGlyph, name = strings.trustedDomainsAdd, enabled = draft.isNotBlank()) { addDraft() }
        }
        if (domains.isEmpty()) {
            Text(
                text = strings.trustedDomainsEmpty,
                style = Type.note,
                color = p.inkFaint,
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gap),
            )
        }
        domains.forEach { domain ->
            ListRow(horizontalPadding = Space.gutter) {
                RowLabel(domain)
                GlyphButton(CloseGlyph, name = strings.trustedDomainsRemove, tint = p.inkDim) { domains.remove(domain) }
            }
        }
    }
}
