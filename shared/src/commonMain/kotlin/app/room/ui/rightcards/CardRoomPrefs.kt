package app.room.ui.rightcards

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import app.i18n.strings
import app.theme.Radius
import app.LocalRoomViewmodel
import app.preferences.settings.LocalSettingsDensity
import app.preferences.settings.LocalInlineEditor
import app.preferences.settings.InlineEditorHost
import app.preferences.settings.InlineEditorPage
import app.preferences.settings.SettingCategory
import app.preferences.settings.SettingsCategoryBody
import app.preferences.settings.SettingsCategoryList
import app.preferences.settings.SettingsDensity
import app.preferences.settings.roomSettings
import app.uicomponents.controls.BackGlyph
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.ProgressBar
import app.uicomponents.frames.PanelFrame

object CardRoomPrefs {

    /**
     * The settings panel of the room (the group of people watching together). It shows the room
     * settings in the same category list as the global settings screen, inside a panel frame.
     */
    @Composable
    fun InRoomSettingsCard(shape: Shape = Radius.panelShape) {
        val viewmodel = LocalRoomViewmodel.current
        var categories: List<SettingCategory>? by remember { mutableStateOf(null) }
        var open by remember { mutableStateOf<SettingCategory?>(null) }
        // Nested pages (chat colors, then one color) stack inline here, so the chat stays in view.
        val pages = remember { mutableStateListOf<InlineEditorPage>() }
        val host = remember { InlineEditorHost { page -> pages.add(page) } }

        LaunchedEffect(Unit) {
            categories = roomSettings(viewmodel.player.configurableSettings(), viewmodel.player.supportsAudioVisualization)
        }

        val title = pages.lastOrNull()?.title ?: open?.title?.invoke(strings) ?: strings.roomCardTitleInRoomPrefs
        PanelFrame(
            title = title,
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            scrollable = pages.lastOrNull()?.scrollable != false,
            actions = {
                if (open != null || pages.isNotEmpty()) {
                    GlyphButton(BackGlyph, name = strings.actionBack) {
                        if (pages.isNotEmpty()) pages.removeAt(pages.lastIndex) else open = null
                    }
                }
            },
        ) {
            val list = categories
            if (list == null) {
                ProgressBar(progress = null)
            } else {
                CompositionLocalProvider(
                    LocalSettingsDensity provides SettingsDensity(showRowIcons = false),
                    LocalInlineEditor provides host,
                ) {
                    val page = pages.lastOrNull()
                    val current = open
                    when {
                        page != null -> InRoomNestedPage(page.content)
                        current == null -> SettingsCategoryList(list, columns = 2) { open = it }
                        else -> SettingsCategoryBody(current)
                    }
                }
            }
        }
    }
}

/**
 * Shows one nested page in the body slot of the panel. A list page uses the scroll of the panel.
 * A fitted editor turns that scroll off, so its child gets the real available height. Pages never
 * nest scrolls.
 */
@Composable
internal fun InRoomNestedPage(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) { content() }
}
