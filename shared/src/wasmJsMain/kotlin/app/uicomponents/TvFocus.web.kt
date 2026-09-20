package app.uicomponents

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent

/** A browser is never a television here. */
@Composable
internal actual fun Modifier.onTvTextFieldNavigationKeyEvent(onKeyEvent: (KeyEvent) -> Boolean): Modifier = this

@Composable
internal actual fun softKeyboardVisible(): Boolean = false
