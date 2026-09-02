package app.room.ui.rightcards

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import app.LocalRoomUiState
import app.preferences.Preferences.DOUBLETAP_SEEK
import app.preferences.Preferences.SWIPE_GESTURES
import app.preferences.settings.SettingRow
import app.uicomponents.controls.CloseGlyph
import app.uicomponents.controls.GlyphButton
import app.uicomponents.frames.PanelFrame
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.action_close
import syncplaymobile.shared.generated.resources.room_gestures_panel_title

/** The two gesture switches as a side panel, so they can be flipped mid-playback without a dialog. */
object CardGestures {

    @Composable
    fun GesturesPanel(shape: Shape) {
        val ui = LocalRoomUiState.current
        PanelFrame(
            title = stringResource(Res.string.room_gestures_panel_title),
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            centerTitle = true,
            actions = { GlyphButton(CloseGlyph, name = stringResource(Res.string.action_close)) { ui.toggleGestures(false) } },
        ) {
            DOUBLETAP_SEEK.SettingRow()
            SWIPE_GESTURES.SettingRow()
        }
    }
}
