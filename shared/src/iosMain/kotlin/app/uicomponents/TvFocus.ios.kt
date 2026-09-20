package app.uicomponents

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent

/** No television build for iOS; tvOS would be a target of its own. */
@Composable
internal actual fun Modifier.onTvTextFieldNavigationKeyEvent(onKeyEvent: (KeyEvent) -> Boolean): Modifier = this

@Composable
internal actual fun softKeyboardVisible(): Boolean = false
