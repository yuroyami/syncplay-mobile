package app.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.delete
import syncplaymobile.shared.generated.resources.edit
import syncplaymobile.shared.generated.resources.theme_delete_question
import syncplaymobile.shared.generated.resources.theme_popup_custom_themes
import syncplaymobile.shared.generated.resources.theme_popup_customize_button
import syncplaymobile.shared.generated.resources.theme_popup_select_a_theme
import syncplaymobile.shared.generated.resources.theme_value_amoled
import syncplaymobile.shared.generated.resources.theme_value_dark
import syncplaymobile.shared.generated.resources.theme_value_light

val availableThemes = listOf(TRINITY, DAYLIGHT, SILVER_LAKE, PYNCSLAY, GrayOLED, ALLEY_LAMP)

/**
 * The theme picker: a list of 54dp rows, each a miniature of the app in that theme, the name,
 * and whether it is dark, light or amoled. Built-in themes first, custom ones after a heading,
 * newest first, with edit and delete on their own targets. Delete asks first.
 */
@Composable
fun ThemeMenu(visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return
    val globalViewmodel = LocalGlobalViewmodel.current
    val currentTheme = LocalTheme.current
    val customThemes by globalViewmodel.customThemes.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<SaveableTheme?>(null) }
    val askDelete = remember { mutableStateOf(false) }

    Modal(open = true, onDismiss = onDismiss, title = stringResource(Res.string.theme_popup_select_a_theme), size = ModalSize.Panel, inset = false) {
        availableThemes.forEach { theme ->
            ThemeRow(theme, selected = currentTheme == theme, onClick = { globalViewmodel.changeTheme(theme) })
        }
        GroupHeading(stringResource(Res.string.theme_popup_custom_themes))
        ListRow(onClick = { onDismiss(); globalViewmodel.backstack.add(Screen.ThemeCreator()) }, minHeight = 54.dp) {
            Box(Modifier.size(72.dp, 40.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Icon(AddGlyph, contentDescription = null, tint = app.theme.palette.inkDim, modifier = Modifier.size(Space.glyph))
            }
            RowGap()
            RowLabel(stringResource(Res.string.theme_popup_customize_button))
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
        title = stringResource(Res.string.delete),
        text = stringResource(Res.string.theme_delete_question),
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
    val kind = stringResource(
        when {
            theme.isAMOLED -> Res.string.theme_value_amoled
            theme.isDark -> Res.string.theme_value_dark
            else -> Res.string.theme_value_light
        }
    )
    ListRow(onClick = onClick, selected = selected, minHeight = 54.dp) {
        ThemeMiniature(theme, Modifier.size(72.dp, 40.dp).clip(Radius.controlShape))
        RowGap()
        RowLabel(theme.name)
        RowValue(kind, width = 60.dp)
        if (onEdit != null) GlyphButton(Icons.Filled.Edit, name = stringResource(Res.string.edit), onClick = onEdit)
        if (onDelete != null) GlyphButton(Icons.Filled.Delete, name = stringResource(Res.string.delete), onClick = onDelete)
    }
}
