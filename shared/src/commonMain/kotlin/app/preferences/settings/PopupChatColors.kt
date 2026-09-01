package app.preferences.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import app.preferences.Preferences
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.uisetting_categ_chat_colors

/** The six chat colour rows behind one entry in the Chat category. */
@Composable
fun ChatColorsPopup(visibilityState: MutableState<Boolean>) {
    Modal(
        open = visibilityState.value,
        onDismiss = { visibilityState.value = false },
        title = stringResource(Res.string.uisetting_categ_chat_colors),
        size = ModalSize.Panel,
        inset = false,
    ) {
        Preferences.COLOR_TIMESTAMP.SettingRow()
        Preferences.COLOR_SELFTAG.SettingRow()
        Preferences.COLOR_FRIENDTAG.SettingRow()
        Preferences.COLOR_SYSTEMMSG.SettingRow()
        Preferences.COLOR_USERMSG.SettingRow()
        Preferences.COLOR_ERRORMSG.SettingRow()
    }
}
