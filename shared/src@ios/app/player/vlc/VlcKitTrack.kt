package app.player.vlc

import app.player.PlayerImpl
import app.player.models.Track

class VlcKitTrack(
    override val name: String,
    override val type: PlayerImpl.TrackType?,
    override val index: Int,
    override val language: String? = null,
    override val channelCount: Int? = null,
    override val codec: String? = null,
    override val selected: Boolean
): Track()