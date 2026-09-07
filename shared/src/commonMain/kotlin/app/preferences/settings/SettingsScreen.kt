package app.preferences.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.LocalGlobalViewmodel
import app.i18n.strings
import app.uicomponents.LocalWidthClass
import app.uicomponents.WidthClass
import app.theme.Space
import app.uicomponents.controls.Field
import app.uicomponents.controls.SearchGlyph
import app.uicomponents.controls.VerticalRule
import app.uicomponents.frames.ScreenFrame

/**
 * The settings destination. Compact and medium widths: the category list, then a category.
 * Expanded widths: the list on the left at 280dp beside the category, no navigation state.
 * A search over every resolved entry sits above the list.
 */
@Composable
fun SettingsScreenUI(categoryKey: String?) {
    val backstack = LocalGlobalViewmodel.current.backstack
    val categories = SETTINGS_GLOBAL
    val widthClass = LocalWidthClass.current
    val expanded = widthClass == WidthClass.Expanded
    val density = SettingsDensity(showRowIcons = widthClass != WidthClass.Compact)

    var open by remember { mutableStateOf(categories.firstOrNull { it.key == categoryKey }) }
    var highlight by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val index = settingsIndex(categories)
    val hits = remember(query, index) { index.search(query) }
    val scroll = rememberScrollState()
    /* Through derivedStateOf, so this scope wakes when the answer flips rather than on every
     * pixel of scroll. Reading scroll.value straight into a parameter recomposed the whole
     * settings screen for the length of a fling. */
    val scrolled by remember { derivedStateOf { scroll.value > 0 } }

    val current = open ?: if (expanded) categories.first() else null
    val title = if (!expanded && current != null) current.title(strings) else strings.settingsTitle

    CompositionLocalProvider(LocalSettingsDensity provides density) {
        ScreenFrame(
            title = title,
            onBack = {
                if (!expanded && open != null) { open = null; highlight = null }
                else backstack.removeLastOrNull()
            },
            scrolled = scrolled,
        ) {
            if (expanded) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        SearchField(query) { query = it }
                        if (query.isNotBlank()) {
                            SettingsSearchResults(hits) { hit -> open = hit.category; highlight = hit.entry.pref.key; query = "" }
                        } else {
                            SettingsCategoryList(categories, selectedKey = current?.key) { open = it; highlight = null }
                        }
                    }
                    VerticalRule()
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll)) {
                        Box(Modifier.widthIn(max = density.contentMaxWidth)) {
                            if (current != null) SettingsCategoryBody(current, highlightKey = highlight)
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().widthIn(max = density.contentMaxWidth)) {
                        if (current == null) {
                            Column {
                                SearchField(query) { query = it }
                                if (query.isNotBlank()) {
                                    SettingsSearchResults(hits) { hit -> open = hit.category; highlight = hit.entry.pref.key; query = "" }
                                } else {
                                    SettingsCategoryList(categories) { open = it; highlight = null }
                                }
                            }
                        } else {
                            SettingsCategoryBody(current, highlightKey = highlight)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.gapTight)) {
        Field(
            value = query,
            onValueChange = onQuery,
            placeholder = strings.settingsSearchHint,
            leading = SearchGlyph,
            name = strings.settingsSearchHint,
        )
    }
}
