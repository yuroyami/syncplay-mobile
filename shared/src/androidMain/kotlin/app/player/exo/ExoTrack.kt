package app.player.exo

import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import app.player.PlayerImpl
import app.player.models.Track

class ExoTrack(
    val trackGroup: TrackGroup,
    val format: Format,
    override val name: String,
    override val type: PlayerImpl.TrackType?,
    override val index: Int,
    override val selected: Boolean
): Track()