package app.room.ui.misc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.datastore.preferences.core.edit
import app.i18n.strings
import app.preferences.PrefExtraConfig
import app.preferences.Preferences.AUDIO_VISUALIZATION
import app.preferences.Preferences.AUDIO_VIZ_NOTICE_SEEN
import app.preferences.Preferences.REDUCE_MOTION
import app.preferences.datastore
import app.preferences.prefKey
import app.preferences.set
import app.preferences.value
import app.preferences.watchPref
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Text
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.utils.reducedMotion
import kotlinx.coroutines.launch

/**
 * Whether the audio visualizer runs calm: the app's Reduce motion switch or the platform setting is
 * on. A calm visualizer does not flash, does not throw its camera about, and changes drawings with
 * a fade.
 */
@Composable
fun rememberVisualizerCalm(): Boolean {
    val reduceMotion by REDUCE_MOTION.watchPref()
    // The platform setting is read again only when the switch changes, as for the rest of the app.
    return remember(reduceMotion) { reduceMotion || reducedMotion() }
}

/** True until the flashing light notice has been accepted once. */
fun visualizerNoticeNeeded(): Boolean = !AUDIO_VIZ_NOTICE_SEEN.value()

/**
 * The one-time notice that the audio visualizer can flash. "Turn on" keeps the answer, turns the
 * visualizer on and then runs [onTurnOn]. "Cancel", Back and a tap outside run [onCancel].
 */
@Composable
fun VisualizerNotice(open: MutableState<Boolean>, onTurnOn: () -> Unit = {}, onCancel: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val cancel = {
        open.value = false
        onCancel()
    }
    Modal(
        open = open.value,
        onDismiss = cancel,
        title = strings.roomVisualizerNoticeTitle,
        size = ModalSize.Ask,
        actions = {
            SecondaryAction(strings.cancel, onClick = cancel)
            AccentAction(strings.roomVisualizerNoticeTurnOn, onClick = {
                scope.launch {
                    // One write, so the visualizer is never on without the answer kept. The notice
                    // closes after it: its caller may leave the composition, and this scope with it.
                    datastore.edit { preferences ->
                        preferences[AUDIO_VIZ_NOTICE_SEEN.prefKey()] = true
                        preferences[AUDIO_VISUALIZATION.prefKey()] = true
                    }
                    open.value = false
                    onTurnOn()
                }
            })
        },
    ) {
        Text(strings.roomVisualizerNoticeText, style = Type.note, color = palette.inkDim)
    }
}

/**
 * Shows [visualizer] once the flashing light notice has been accepted. A visualizer that was on
 * before the notice existed shows the notice first, and "Cancel" turns it off.
 */
@Composable
fun AfterVisualizerNotice(visualizer: @Composable () -> Unit) {
    val seen by AUDIO_VIZ_NOTICE_SEEN.watchPref()
    if (seen) {
        visualizer()
    } else {
        val open = remember { mutableStateOf(true) }
        val scope = rememberCoroutineScope()
        VisualizerNotice(open, onCancel = { scope.launch { AUDIO_VISUALIZATION.set(false) } })
    }
}

/** The audio visualizer's settings row: it asks about flashing light before it first turns on. */
val VisualizerNoticeControl: PrefExtraConfig = PrefExtraConfig.AskBeforeOn(
    asks = ::visualizerNoticeNeeded,
    ask = { open -> VisualizerNotice(open) },
)
