package app.room.ui.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.theme.Radius
import app.theme.Space
import app.theme.palette
import app.room.ui.misc.JumpKeyFill

/**
 * The tile behind a room key over video. It has the shape, the size and the spacing of the add
 * key's tile, and the dark see-through fill and thin rim of the jump keys. The key stays readable on
 * a bright picture, and only the purple add key draws the eye.
 */
@Composable
fun Modifier.videoKeyTile(): Modifier =
    padding(Space.gapTight).clip(Radius.panelShape).background(JumpKeyFill).border(Space.hair, palette.rule, Radius.panelShape)
