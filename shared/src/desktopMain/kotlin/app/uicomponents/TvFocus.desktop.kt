package app.uicomponents

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent

/**
 * A desktop has no soft keyboard, so nothing sits between the key and the field. The desktop app
 * never sets LocalIsTelevision, so this only runs under the render harness.
 */
@Composable
internal actual fun Modifier.onTvTextFieldNavigationKeyEvent(onKeyEvent: (KeyEvent) -> Boolean): Modifier =
    onPreviewKeyEvent(onKeyEvent)

@Composable
internal actual fun softKeyboardVisible(): Boolean = false
