package app.player.vlc

import app.player.PlayerImpl
import app.player.models.Track

class VlcTrack(
    val id: String,
    override val name: String,
    override val type: PlayerImpl.TrackType?,
    override val index: Int,
    override val selected: Boolean
    ): Track()