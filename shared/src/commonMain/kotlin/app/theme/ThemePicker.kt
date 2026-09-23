package app.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import app.i18n.strings
import app.uicomponents.controls.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.LocalGlobalViewmodel
import app.LocalTheme
import app.Screen
import app.preferences.settings.AskModal
import app.uicomponents.controls.AddGlyph
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.GroupHeading
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.RowValue
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import syncplaymobile.shared.generated.resources.delete
import syncplaymobile.shared.generated.resources.edit

val availableThemes = listOf(TRINITY, DAYLIGHT, SILVER_LAKE, PYNCSLAY, GrayOLED, ALLEY_LAMP)

/**
 * The theme picker, in a modal. Each 54dp row shows a miniature of the app in that theme, the
 * theme name, and whether the theme is dark, light or AMOLED. The built-in themes come first.
 * Under the custom themes heading, one row opens the theme creator, and the custom themes follow,
 * newest first, each with its own edit and delete buttons. Delete asks for confirmation first.
 */
@Composable
fun ThemeMenu(visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return
    val globalViewmodel = LocalGlobalViewmodel.current
    val currentTheme = LocalTheme.current
    val customThemes by globalViewmodel.customThemes.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<SaveableTheme?>(null) }
    val askDelete = remember { mutableStateOf(false) }

    Modal(open = true, onDismiss = onDismiss, title = strings.themePopupSelectATheme, size = ModalSize.Panel, inset = false) {
        availableThemes.forEach { theme ->
            ThemeRow(theme, selected = currentTheme == theme, onClick = { globalViewmodel.changeTheme(theme) })
        }
        GroupHeading(strings.themePopupCustomThemes)
        ListRow(onClick = { onDismiss(); globalViewmodel.backstack.add(Screen.ThemeCreator()) }, minHeight = 54.dp) {
            Box(Modifier.size(72.dp, 40.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Icon(AddGlyph, contentDescription = null, tint = app.theme.palette.inkDim, modifier = Modifier.size(Space.glyph))
            }
            RowGap()
            RowLabel(strings.themePopupCustomizeButton)
        }
        customThemes.asReversed().forEach { theme ->
            ThemeRow(
                theme = theme,
                selected = currentTheme == theme,
                onClick = { globalViewmodel.changeTheme(theme) },
                onEdit = { onDismiss(); globalViewmodel.backstack.add(Screen.ThemeCreator(theme)) },
                onDelete = { toDelete = theme; askDelete.value = true },
            )
        }
    }

    AskModal(
        open = askDelete,
        title = strings.delete,
        text = strings.themeDeleteQuestion,
        destructive = true,
        onYes = { toDelete?.let { globalViewmodel.deleteTheme(it) }; toDelete = null },
        onNo = { toDelete = null },
    )
}

@Composable
private fun ThemeRow(
    theme: SaveableTheme,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val kind = when {
            theme.isAMOLED -> strings.themeValueAmoled
            theme.isDark -> strings.themeValueDark
            else -> strings.themeValueLight
        }
    ListRow(onClick = onClick, selected = selected, minHeight = 54.dp) {
        ThemeMiniature(theme, Modifier.size(72.dp, 40.dp).clip(Radius.controlShape))
        RowGap()
        RowLabel(theme.name)
        RowValue(kind, width = 60.dp)
        if (onEdit != null) GlyphButton(Icons.Filled.Edit, name = strings.edit, onClick = onEdit)
        if (onDelete != null) GlyphButton(Icons.Filled.Delete, name = strings.delete, onClick = onDelete)
    }
}
