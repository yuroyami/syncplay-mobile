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
import app.i18n.strings
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

/**
 * The gestures panel: the gesture switches, and the two values that the swipe gestures change.
 *
 * A swipe is of no use to someone with a screen reader or a keyboard. So volume and brightness
 * also sit here, as ordinary slider rows.
 */
object CardGestures {

    @Composable
    fun GesturesPanel(shape: Shape) {
        val ui = LocalRoomUiState.current
        val viewmodel = LocalRoomViewmodel.current
        PanelFrame(
            title = strings.roomGesturesPanelTitle,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            centerTitle = true,
            actions = { GlyphButton(CloseGlyph, name = strings.actionClose) { ui.toggleGestures(false) } },
        ) {
            DOUBLETAP_SEEK.SettingRow()
            SWIPE_GESTURES.SettingRow()

            val volumeControl = viewmodel.player.volume
            val ladder = volumeControl.ladder
            var volume by remember { mutableFloatStateOf(volumeControl.current().toFloat() / ladder.max) }
            TrackRow(
                label = strings.roomVolumeLabel,
                value = volume,
                describe = { "${(it * ladder.max).roundToInt()}" },
            ) { next ->
                volume = next
                volumeControl.set((next * ladder.max).roundToInt())
            }

            if (platformCallback.supportsBrightness) {
                var brightness by remember { mutableFloatStateOf(platformCallback.getCurrentBrightness() / platformCallback.getMaxBrightness()) }
                TrackRow(
                    label = strings.roomBrightnessLabel,
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
