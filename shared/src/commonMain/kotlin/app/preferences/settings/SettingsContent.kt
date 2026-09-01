package app.preferences.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.settings_search_no_results

/*
 * The settings content, shared by the global screen and the in-room panel: the category list,
 * one category's console list, and the search index over a resolved category set.
 */

/** One category as groups with headings, separated by rules. */
@Composable
fun SettingsCategoryBody(category: SettingCategory, modifier: Modifier = Modifier, highlightKey: String? = null) {
    Column(modifier.fillMaxWidth()) {
        category.groups.forEachIndexed { index, group ->
            if (index > 0) Rule()
            group.title?.let { GroupHeading(stringResource(it)) }
            group.entries.forEach { entry ->
                entry.Render(highlighted = entry.pref.key == highlightKey)
            }
        }
    }
}

/** One row per category, a chevron at the end. */
@Composable
fun SettingsCategoryList(
    categories: List<SettingCategory>,
    modifier: Modifier = Modifier,
    selectedKey: String? = null,
    onOpen: (SettingCategory) -> Unit,
) {
    val p = palette
    Column(modifier.fillMaxWidth()) {
        categories.forEach { category ->
            ListRow(onClick = { onOpen(category) }, selected = category.key == selectedKey) {
                Icon(category.icon, contentDescription = null, tint = p.inkDim, modifier = Modifier.size(Space.glyph))
                RowGap()
                RowLabel(stringResource(category.title))
                Chevron(ChevronDirection.Right)
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

/** The index over a resolved category set. Composable because titles are resources. */
@Composable
fun settingsIndex(categories: List<SettingCategory>): List<SettingsHit> = buildList {
    for (category in categories) {
        val categoryTitle = stringResource(category.title)
        for (entry in category.entries) {
            val cfg = entry.pref.config ?: continue
            add(
                SettingsHit(
                    category = category,
                    entry = entry,
                    categoryTitle = categoryTitle,
                    title = stringResource(cfg.title),
                    summary = stringResource(cfg.summary, *cfg.summaryFormatArgs),
                )
            )
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
                text = stringResource(Res.string.settings_search_no_results),
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
