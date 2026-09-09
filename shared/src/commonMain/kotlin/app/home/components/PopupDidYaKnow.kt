package app.home.components

import app.i18n.strings
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import app.LocalGlobalViewmodel
import app.preferences.Preferences
import app.preferences.set
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.utils.appName
import app.utils.ioDispatcher
import kotlinx.coroutines.launch
import syncplaymobile.shared.generated.resources.okay

object PopupDidYaKnow {

    /** First-launch tips, one at a time, with a Next that advances instead of showing one forever. */
    @Composable
    fun DidYaKnowPopup(state: MutableState<Boolean>) {
        val viewmodel = LocalGlobalViewmodel.current
        val tips = remember { mutableStateListOf<String>() }
        var tipIndex by remember { mutableIntStateOf(0) }
        if (!state.value) return

        val allTips = strings.tips
        LaunchedEffect(allTips) {
            if (tips.isEmpty()) tips.addAll(allTips.map { it.replace("%1\$s", appName) }.shuffled())
        }

        Modal(
            open = true,
            onDismiss = { state.value = false },
            title = strings.tipsDidYaKnow,
            size = ModalSize.Ask,
            actions = {
                SecondaryAction(strings.tipsDontshowmetips, onClick = {
                    viewmodel.viewModelScope.launch(ioDispatcher) { Preferences.NEVER_SHOW_TIPS.set(true) }
                    state.value = false
                })
                SecondaryAction(strings.tipsNext, onClick = { if (tips.isNotEmpty()) tipIndex = (tipIndex + 1) % tips.size })
                AccentAction(strings.okay, onClick = { state.value = false })
            },
        ) {
            Text(tips.getOrNull(tipIndex) ?: "", style = Type.note, color = palette.ink)
        }
    }
}
