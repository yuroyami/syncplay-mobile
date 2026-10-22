package app.uicomponents

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether a screen reader (TalkBack, VoiceOver) is running. The root provides it and updates it
 * when the person turns one on or off, so composables never ask the platform themselves and a
 * test can set it.
 */
val LocalScreenReaderActive = compositionLocalOf { false }
