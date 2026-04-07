package app.player.mpv

import app.player.PlayerImpl
import app.player.models.Track

class MpvKitTrack(
    override val name: String,
    override val type: PlayerImpl.TrackType?,
    override val index: Int,
    override val selected: Boolean
) : Track()
