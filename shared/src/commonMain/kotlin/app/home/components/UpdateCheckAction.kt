package app.home.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.i18n.strings
import app.theme.palette
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.ActionStatus
import app.uicomponents.controls.SecondaryAction

@Composable
internal fun UpdateCheckAction(
    result: UpdateCheck.Result?,
    isChecking: Boolean,
    onCheck: () -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    when (result) {
        UpdateCheck.Result.UpToDate -> ActionStatus(
            text = strings.aboutUpdateCurrent,
            // Text on the theme's surface, so it uses the readable green, not the badge green.
            color = palette.okText,
            modifier = Modifier.fillMaxWidth(),
        )
        is UpdateCheck.Result.Newer -> AccentAction(
            text = strings.aboutUpdateAvailable(result.version),
            onClick = { onOpenRelease(result.url) },
            modifier = Modifier.fillMaxWidth(),
        )
        else -> SecondaryAction(
            text = when {
                isChecking -> strings.aboutUpdateChecking
                result == UpdateCheck.Result.Unreachable -> strings.aboutUpdateFailed
                else -> strings.aboutUpdateButton
            },
            enabled = !isChecking,
            onClick = onCheck,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
