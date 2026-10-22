package app.preferences.settings

import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import app.i18n.strings
import app.uicomponents.controls.Icon
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import app.preferences.Preferences
import app.preferences.watchPref
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.Chevron
import app.uicomponents.controls.ChevronDirection
import app.uicomponents.controls.GroupHeading
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.Rule

/*
 * The settings content, shared by the global screen and the in-room panel: the category list,
 * the rows of one category, and the search index over a resolved category set.
 */

/**
 * One category as groups with headings, separated by rules (thin divider lines). A row that shows
 * its explanation gets an inset rule above and below it, one rule between two such neighbours,
 * and none where the full-width group rule already separates them.
 */
@Composable
fun SettingsCategoryBody(category: SettingCategory, modifier: Modifier = Modifier, highlightKey: String? = null) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val showAll by Preferences.SHOW_SETTING_DESCRIPTIONS.watchPref()
    val inline = LocalSettingsDensity.current.showInlineExplanations
    CompositionLocalProvider(LocalExpandedSettings provides expanded) {
        Column(modifier.fillMaxWidth()) {
            var previousOpen = false
            var afterGroupRule = false
            category.groups.forEachIndexed { index, group ->
                if (index > 0) {
                    Rule()
                    previousOpen = false
                    afterGroupRule = true
                }
                group.title?.let { GroupHeading(it(strings)) }
                group.entries.forEach { entry ->
                    val open = showAll || inline || expanded[entry.pref.key] == true
                    if ((open || previousOpen) && !afterGroupRule) InsetRule()
                    entry.Render(highlighted = entry.pref.key == highlightKey)
                    previousOpen = open
                    afterGroupRule = false
                }
            }
            if (previousOpen) InsetRule()
        }
    }
}

/** The inset rule: a thin divider line that stops short of the edges. */
@Composable
private fun InsetRule() {
    Rule(Modifier.padding(horizontal = Space.gutter))
}

/** One row per category, a chevron at the end. */
@Composable
fun SettingsCategoryList(
    categories: List<SettingCategory>,
    modifier: Modifier = Modifier,
    selectedKey: String? = null,
    selectedFocus: FocusRequester? = null,
    columns: Int = 1,
    onOpen: (SettingCategory) -> Unit,
) {
    val p = palette
    Column(modifier.fillMaxWidth()) {
        categories.chunked(columns.coerceAtLeast(1)).forEach { rowOf ->
            Row(Modifier.fillMaxWidth()) {
                rowOf.forEach { category ->
                    ListRow(
                        modifier = Modifier.weight(1f).then(
                            if (selectedFocus != null && category.key == selectedKey) Modifier.focusRequester(selectedFocus) else Modifier,
                        ),
                        onClick = { onOpen(category) },
                        selected = category.key == selectedKey,
                        horizontalPadding = if (columns > 1) Space.gap else Space.gutter,
                    ) {
                        Icon(category.icon, contentDescription = null, tint = p.inkDim, modifier = Modifier.size(Space.glyph))
                        RowGap()
                        RowLabel(category.title(strings))
                        if (columns == 1) Chevron(ChevronDirection.Right)
                    }
                }
                repeat(columns - rowOf.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A search hit, with its strings already resolved. */
class SettingsHit(
    val category: SettingCategory,
    val entry: SettingEntry,
    val categoryTitle: String,
    val title: String,
    val summary: String,
)

/**
 * The search index over a resolved category set. It is composable because it reads the strings of
 * the current language.
 *
 * The index is remembered on the two inputs that it depends on. Without that, every recomposition
 * of the settings screen (every pixel of scroll included) would walk every category and preference
 * and build a new [SettingsHit] for each. The search below could then never reuse its result,
 * because the list that it keys on would be a new object each time.
 */
@Composable
fun settingsIndex(categories: List<SettingCategory>): List<SettingsHit> {
    val resolved = strings
    return remember(categories, resolved) {
        buildList {
            for (category in categories) {
                val categoryTitle = category.title(resolved)
                for (entry in category.entries) {
                    val cfg = entry.pref.config ?: continue
                    add(
                        SettingsHit(
                            category = category,
                            entry = entry,
                            categoryTitle = categoryTitle,
                            title = cfg.title(resolved),
                            summary = cfg.summary?.invoke(resolved).orEmpty(),
                        )
                    )
                }
            }
        }
    }
}

fun List<SettingsHit>.search(query: String): List<SettingsHit> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return filter { it.title.contains(q, ignoreCase = true) || it.summary.contains(q, ignoreCase = true) || it.categoryTitle.contains(q, ignoreCase = true) }
}

/** Results as `Category › Setting`, one line of summary under each. */
@Composable
fun SettingsSearchResults(hits: List<SettingsHit>, modifier: Modifier = Modifier, onOpen: (SettingsHit) -> Unit) {
    val p = palette
    Column(modifier.fillMaxWidth()) {
        if (hits.isEmpty()) {
            Text(
                text = strings.settingsSearchNoResults,
                style = Type.note,
                color = p.inkDim,
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gap),
            )
            return@Column
        }
        hits.forEach { hit ->
            ListRow(onClick = { onOpen(hit) }, minHeight = Space.rowTall) {
                Column(Modifier.weight(1f)) {
                    Text("${hit.categoryTitle} › ${hit.title}", style = Type.label, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(hit.summary, style = Type.note, color = p.inkDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RowGap()
                Chevron(ChevronDirection.Right)
            }
        }
    }
}
