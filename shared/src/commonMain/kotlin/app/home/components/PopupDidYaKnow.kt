package app.home.components

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getStringArray
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.okay
import syncplaymobile.shared.generated.resources.tips
import syncplaymobile.shared.generated.resources.tips_did_ya_know
import syncplaymobile.shared.generated.resources.tips_dontshowmetips
import syncplaymobile.shared.generated.resources.tips_next

object PopupDidYaKnow {

    /** First-launch tips, one at a time, with a Next that advances instead of showing one forever. */
    @Composable
    fun DidYaKnowPopup(state: MutableState<Boolean>) {
        val viewmodel = LocalGlobalViewmodel.current
        val tips = remember { mutableStateListOf<String>() }
        var tipIndex by remember { mutableIntStateOf(0) }
        if (!state.value) return

        LaunchedEffect(Unit) {
            if (tips.isEmpty()) tips.addAll(getStringArray(Res.array.tips).map { it.replace("%1\$s", appName) }.shuffled())
        }

        Modal(
            open = true,
            onDismiss = { state.value = false },
            title = stringResource(Res.string.tips_did_ya_know),
            size = ModalSize.Ask,
            actions = {
                SecondaryAction(stringResource(Res.string.tips_dontshowmetips), onClick = {
                    viewmodel.viewModelScope.launch(Dispatchers.IO) { Preferences.NEVER_SHOW_TIPS.set(true) }
                    state.value = false
                })
                SecondaryAction(stringResource(Res.string.tips_next), onClick = { if (tips.isNotEmpty()) tipIndex = (tipIndex + 1) % tips.size })
                AccentAction(stringResource(Res.string.okay), onClick = { state.value = false })
            },
        ) {
            Text(tips.getOrNull(tipIndex) ?: "", style = Type.note, color = palette.ink)
        }
    }
}
