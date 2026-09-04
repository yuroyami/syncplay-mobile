package app.room.ui.rightcards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.preferences.Preferences.DOUBLETAP_SEEK
import app.preferences.Preferences.SWIPE_GESTURES
import app.preferences.settings.SettingRow
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.CloseGlyph
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.ScrubTrack
import app.uicomponents.controls.Text
import app.uicomponents.frames.PanelFrame
import app.utils.platformCallback
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.action_close
import syncplaymobile.shared.generated.resources.room_brightness_label
import syncplaymobile.shared.generated.resources.room_gestures_panel_title
import syncplaymobile.shared.generated.resources.room_volume_label

/**
 * The gesture switches, plus the two things the gestures change.
 *
 * Volume and brightness were reachable by swipe alone, which is nothing at all to someone using a
 * screen reader or a keyboard. The same two values sit here as ordinary tracks.
 */
object CardGestures {

    @Composable
    fun GesturesPanel(shape: Shape) {
        val ui = LocalRoomUiState.current
        val viewmodel = LocalRoomViewmodel.current
        PanelFrame(
            title = stringResource(Res.string.room_gestures_panel_title),
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            centerTitle = true,
            actions = { GlyphButton(CloseGlyph, name = stringResource(Res.string.action_close)) { ui.toggleGestures(false) } },
        ) {
            DOUBLETAP_SEEK.SettingRow()
            SWIPE_GESTURES.SettingRow()

            val volumeControl = viewmodel.player.volume
            val ladder = volumeControl.ladder
            var volume by remember { mutableFloatStateOf(volumeControl.current().toFloat() / ladder.max) }
            TrackRow(
                label = stringResource(Res.string.room_volume_label),
                value = volume,
                describe = { "${(it * ladder.max).roundToInt()}" },
            ) { next ->
                volume = next
                volumeControl.set((next * ladder.max).roundToInt())
            }

            if (platformCallback.supportsBrightness) {
                var brightness by remember { mutableFloatStateOf(platformCallback.getCurrentBrightness() / platformCallback.getMaxBrightness()) }
                TrackRow(
                    label = stringResource(Res.string.room_brightness_label),
                    value = brightness,
                    describe = { "${(it * 100).roundToInt()}" },
                ) { next ->
                    brightness = next
                    platformCallback.changeCurrentBrightness(next * platformCallback.getMaxBrightness())
                }
            }
        }
    }

    @Composable
    private fun TrackRow(
        label: String,
        value: Float,
        describe: (Float) -> String,
        onValue: (Float) -> Unit,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.gapTight)) {
            Text(label, style = Type.label, color = palette.inkDim)
            ScrubTrack(value = value, onValueChange = onValue, name = label, describe = describe)
        }
    }
}
