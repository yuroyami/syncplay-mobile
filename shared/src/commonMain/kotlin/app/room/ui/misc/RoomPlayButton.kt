package app.room.ui.misc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.LocalRoomViewmodel
import app.player.Playback
import app.room.LocalRoomInitialFocus
import app.theme.Radius
import app.theme.Space
import app.theme.palette
import app.uicomponents.controls.PauseGlyph
import app.uicomponents.controls.PlayGlyph
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.pressFeedback
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_pause
import syncplaymobile.shared.generated.resources.room_play

/**
 * The room's one gradient moment: a 60dp square key filled with the brand field. It acts on the
 * state it shows, so the key can never show pause and send play.
 */
@Composable
fun RoomPlayButton(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val hasVideo by viewmodel.hasVideo.collectAsState()
    val playing by viewmodel.playerManager.isNowPlaying.collectAsState()
    if (!hasVideo) return

    val p = palette
    val source = remember { MutableInteractionSource() }
    val name = stringResource(if (playing) Res.string.room_pause else Res.string.room_play)
    val initialFocus = LocalRoomInitialFocus.current

    Box(
        modifier = modifier
            .size(Space.hero)
            .then(if (initialFocus != null) Modifier.focusRequester(initialFocus) else Modifier)
            .clip(Radius.panelShape)
            .background(Brush.horizontalGradient(p.brandField))
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                viewmodel.dispatcher.controlPlayback(if (playing) Playback.PAUSE else Playback.PLAY, true)
            }
            .hoverable(source)
            .semantics { contentDescription = name }
            .controlStates(source, Radius.panelShape)
            .pressFeedback(source),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playing) PauseGlyph else PlayGlyph,
            contentDescription = null,
            tint = p.ground,
            modifier = Modifier.size(28.dp),
        )
    }
}
