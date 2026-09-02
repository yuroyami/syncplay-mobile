package app.room.ui.rightcards

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import app.theme.Radius
import app.LocalRoomViewmodel
import app.preferences.settings.LocalSettingsDensity
import app.preferences.settings.LocalInlineEditor
import app.preferences.settings.InlineEditorHost
import app.preferences.settings.SettingCategory
import app.preferences.settings.SettingsCategoryBody
import app.preferences.settings.SettingsCategoryList
import app.preferences.settings.SettingsDensity
import app.preferences.settings.roomSettings
import app.uicomponents.controls.BackGlyph
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.ProgressBar
import app.uicomponents.frames.PanelFrame
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.action_back
import syncplaymobile.shared.generated.resources.room_card_title_in_room_prefs

object CardRoomPrefs {

    /** In-room settings: the same console list as the global screen, inside the panel frame. */
    @Composable
    fun InRoomSettingsCard(shape: Shape = Radius.panelShape) {
        val viewmodel = LocalRoomViewmodel.current
        var categories: List<SettingCategory>? by remember { mutableStateOf(null) }
        var open by remember { mutableStateOf<SettingCategory?>(null) }
        // Nested pages (chat colours, one colour) stack here, inline, so the chat stays in view.
        val pages = remember { mutableStateListOf<Pair<String, @Composable () -> Unit>>() }
        val host = remember { InlineEditorHost { title, content -> pages.add(title to content) } }

        LaunchedEffect(Unit) {
            categories = roomSettings(viewmodel.player.configurableSettings())
        }

        val title = pages.lastOrNull()?.first ?: open?.let { stringResource(it.title) } ?: stringResource(Res.string.room_card_title_in_room_prefs)
        PanelFrame(
            title = title,
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            actions = {
                if (open != null || pages.isNotEmpty()) {
                    GlyphButton(BackGlyph, name = stringResource(Res.string.action_back)) {
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
                        page != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { page.second() }
                        current == null -> SettingsCategoryList(list, columns = 2) { open = it }
                        else -> SettingsCategoryBody(current)
                    }
                }
            }
        }
    }
}
