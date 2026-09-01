package app.preferences.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.cancel
import syncplaymobile.shared.generated.resources.save
import syncplaymobile.shared.generated.resources.setting_trusted_domains_summary
import syncplaymobile.shared.generated.resources.setting_trusted_domains_title
import syncplaymobile.shared.generated.resources.trusted_domains_add
import syncplaymobile.shared.generated.resources.trusted_domains_add_hint
import syncplaymobile.shared.generated.resources.trusted_domains_clear
import syncplaymobile.shared.generated.resources.trusted_domains_empty
import syncplaymobile.shared.generated.resources.trusted_domains_remove

/**
 * The trusted domains as a list: an inline add row, one hairline row per domain with remove.
 * Reads split on newline and comma, saves joined by newline, as the matcher expects.
 */
@Composable
fun TrustedDomainsPopup(visibilityState: MutableState<Boolean>) {
    if (!visibilityState.value) return
    val p = palette
    val scope = rememberCoroutineScope { Dispatchers.IO }
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
        title = stringResource(Res.string.setting_trusted_domains_title),
        size = ModalSize.Panel,
        inset = false,
        actions = {
            SecondaryAction(stringResource(Res.string.trusted_domains_clear), onClick = { domains.clear() }, enabled = domains.isNotEmpty())
            SecondaryAction(stringResource(Res.string.cancel), onClick = ::close)
            AccentAction(stringResource(Res.string.save), onClick = ::save)
        },
    ) {
        Text(
            text = stringResource(Res.string.setting_trusted_domains_summary),
            style = Type.note,
            color = p.inkDim,
            modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gap),
        )
        Row(Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gapTight), verticalAlignment = Alignment.CenterVertically) {
            Field(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = stringResource(Res.string.trusted_domains_add_hint),
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
                onImeAction = ::addDraft,
                showClear = false,
                name = stringResource(Res.string.trusted_domains_add),
            )
            GlyphButton(AddGlyph, name = stringResource(Res.string.trusted_domains_add), enabled = draft.isNotBlank()) { addDraft() }
        }
        if (domains.isEmpty()) {
            Text(
                text = stringResource(Res.string.trusted_domains_empty),
                style = Type.note,
                color = p.inkFaint,
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gap),
            )
        }
        domains.forEach { domain ->
            ListRow(horizontalPadding = Space.gutter) {
                RowLabel(domain)
                GlyphButton(CloseGlyph, name = stringResource(Res.string.trusted_domains_remove), tint = p.inkDim) { domains.remove(domain) }
            }
        }
    }
}
