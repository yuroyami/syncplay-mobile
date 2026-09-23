package app.uicomponents

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent

/**
 * A desktop has no soft keyboard, so nothing sits between the key and the text field. The desktop
 * app never sets LocalIsTelevision, so this code runs only in the headless render tests
 * (desktopTest).
 */
@Composable
internal actual fun Modifier.onTvTextFieldNavigationKeyEvent(onKeyEvent: (KeyEvent) -> Boolean): Modifier =
    onPreviewKeyEvent(onKeyEvent)

@Composable
internal actual fun softKeyboardVisible(): Boolean = false
