package app.room.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.theme.Radius
import app.theme.Space
import app.uicomponents.chromeSurface
import app.uicomponents.controls.GlyphButton
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_unlock
import kotlin.time.Duration.Companion.milliseconds

/** The locked room: a tap shows one unlock key on the chrome tier for a moment, nothing else. */
@Composable
fun RoomUnlockableLayout() {
    val viewmodel = LocalRoomViewmodel.current
    val ui = LocalRoomUiState.current
    val lockedMode by ui.tabLock.collectAsState()
    val isInPipMode by viewmodel.uiState.hasEnteredPipMode.collectAsState()
    if (!lockedMode) return

    var keyVisible by remember { mutableStateOf(true) }
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { keyVisible = !keyVisible },
    ) {
        if (keyVisible && !isInPipMode) {
            LaunchedEffect(null) {
                delay(2200.milliseconds)
                keyVisible = false
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = Space.gap, end = Space.gutter).size(Space.hero).chromeSurface(Radius.panelShape),
                contentAlignment = Alignment.Center,
            ) {
                GlyphButton(Icons.Filled.NoEncryption, name = stringResource(Res.string.room_unlock), size = Space.glyphLarge) {
                    ui.tabLock.value = false
                    viewmodel.uiState.showHud()
                }
            }
        }
    }
}
