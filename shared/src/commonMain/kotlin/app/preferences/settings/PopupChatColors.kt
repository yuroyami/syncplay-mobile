package app.preferences.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.preferences.Preferences
import app.preferences.settings.SettingRow
import app.uicomponents.SyncplayPopup
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.uisetting_categ_chat_colors

/**
 * One popup gathering every chat color preference (timestamp, self tag, friend tag, system,
 * user message, error). Opened from the single "Chat colors" entry inside the Chat category,
 * replacing the old dedicated settings category.
 */
@Composable
fun ChatColorsPopup(visibilityState: MutableState<Boolean>) {
    SyncplayPopup(
        dialogOpen = visibilityState.value,
        widthPercent = 0.85f,
        onDismiss = { visibilityState.value = false }
    ) {
        Text(
            text = stringResource(Res.string.uisetting_categ_chat_colors),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        ) {
            Preferences.COLOR_TIMESTAMP.SettingRow()
            Preferences.COLOR_SELFTAG.SettingRow()
            Preferences.COLOR_FRIENDTAG.SettingRow()
            Preferences.COLOR_SYSTEMMSG.SettingRow()
            Preferences.COLOR_USERMSG.SettingRow()
            Preferences.COLOR_ERRORMSG.SettingRow()
        }
    }
}
