package app.room.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import app.i18n.Localization
import app.i18n.strings
import app.room.OSDCategory
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.theme.Radius
import app.theme.Space
import app.uicomponents.LocalIsTelevision
import app.uicomponents.tvOverscan
import app.uicomponents.chromeSurface
import app.uicomponents.controls.GlyphButton
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * The locked room screen. A tap shows one unlock key for a moment, and nothing else. A room is
 * the group of people watching together.
 *
 * The key hides by itself. Without a hint, a user who looked away could not tell the locked room
 * from a frozen app. So locking shows a notice that says what to do, and the whole surface tells
 * a screen reader what it is.
 */
@Composable
fun RoomUnlockableLayout() {
    val viewmodel = LocalRoomViewmodel.current
    val ui = LocalRoomUiState.current
    val lockedMode by ui.tabLock.collectAsState()
    val isInPipMode by viewmodel.uiState.hasEnteredPipMode.collectAsState()
    if (!lockedMode) return

    var keyVisible by remember { mutableStateOf(true) }

    // The hint shows once, when the lock turns on.
    LaunchedEffect(lockedMode) {
        if (lockedMode) viewmodel.dispatchOSD(OSDCategory.SAME_ROOM) { Localization.strings.roomLockedHint }
    }

    /* A remote cannot tap the screen to wake the key, so this code sets the focus: on the key
     * while it shows, and on the surface once it hides. On the surface, the press key brings the
     * key back. The lock takes the whole screen, so nothing else competes for that focus. */
    val remoteOrKeyboard = LocalIsTelevision.current || LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val surfaceFocus = remember { FocusRequester() }
    val keyFocus = remember { FocusRequester() }
    var keyFocused by remember { mutableStateOf(false) }
    LaunchedEffect(keyVisible, isInPipMode, remoteOrKeyboard) {
        if (!remoteOrKeyboard || isInPipMode) return@LaunchedEffect
        val target = if (keyVisible) keyFocus else surfaceFocus
        // The key appears one frame later, so the request repeats up to 8 times, 50 ms apart,
        // until it succeeds.
        repeat(8) {
            if (runCatching { target.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            delay(50.milliseconds)
        }
    }

    val surfaceLabel = strings.roomLockedSurface
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(surfaceFocus)
            .clickable(interactionSource = null, indication = null) { keyVisible = !keyVisible }
            .semantics {
                contentDescription = surfaceLabel
                onClick(label = surfaceLabel) { keyVisible = !keyVisible; true }
            },
    ) {
        if (keyVisible && !isInPipMode) {
            /* The key hides by itself after 2.2 seconds, unless it has focus. A finger taps the
             * key directly, so 2.2 seconds is enough. A remote must press the key that holds
             * focus, and without this exception the key could hide before the press arrives. */
            LaunchedEffect(keyFocused) {
                if (keyFocused) return@LaunchedEffect
                delay(2200.milliseconds)
                keyVisible = false
            }
            Box(
                Modifier.align(Alignment.TopEnd)
                    // A television can cut off the outer edge of the picture (overscan), so the key
                    // keeps clear of that edge.
                    .then(if (LocalIsTelevision.current) Modifier.windowInsetsPadding(tvOverscan()) else Modifier)
                    .padding(top = Space.gap, end = Space.gutter).size(Space.hero).chromeSurface(Radius.panelShape),
                contentAlignment = Alignment.Center,
            ) {
                GlyphButton(
                    Icons.Filled.NoEncryption,
                    name = strings.roomUnlock,
                    modifier = Modifier.onFocusChanged { keyFocused = it.isFocused },
                    size = Space.glyphLarge,
                    focusRequester = keyFocus,
                ) {
                    ui.tabLock.value = false
                    viewmodel.uiState.showHud()
                }
            }
        }
    }
}
