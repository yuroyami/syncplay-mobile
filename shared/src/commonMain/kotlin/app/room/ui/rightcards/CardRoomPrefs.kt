package app.room.ui.rightcards

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.LocalRoomViewmodel
import app.preferences.settings.LocalSettingsDensity
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
import syncplaymobile.shared.generated.resources.room_card_title_in_room_prefs

object CardRoomPrefs {

    /** In-room settings: the same console list as the global screen, inside the panel frame. */
    @Composable
    fun InRoomSettingsCard() {
        val viewmodel = LocalRoomViewmodel.current
        var categories: List<SettingCategory>? by remember { mutableStateOf(null) }
        var open by remember { mutableStateOf<SettingCategory?>(null) }

        LaunchedEffect(Unit) {
            categories = roomSettings(viewmodel.player.configurableSettings())
        }

        val title = open?.let { stringResource(it.title) } ?: stringResource(Res.string.room_card_title_in_room_prefs)
        PanelFrame(
            title = title,
            modifier = Modifier.fillMaxSize(),
            actions = {
                if (open != null) GlyphButton(BackGlyph, name = "Back", onClick = { open = null })
            },
        ) {
            val list = categories
            if (list == null) {
                ProgressBar(progress = null)
            } else {
                CompositionLocalProvider(LocalSettingsDensity provides SettingsDensity(showRowIcons = false)) {
                    val current = open
                    if (current == null) SettingsCategoryList(list) { open = it }
                    else SettingsCategoryBody(current)
                }
            }
        }
    }
}
