package app.uicomponents

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent

@Composable
internal actual fun Modifier.onTvTextFieldNavigationKeyEvent(
    onKeyEvent: (KeyEvent) -> Boolean,
): Modifier = this
