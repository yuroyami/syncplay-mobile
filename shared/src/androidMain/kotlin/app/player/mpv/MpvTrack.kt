package app.player.mpv

import app.player.PlayerImpl
import app.player.models.Track

class MpvTrack(
    override val name: String,
    override val type: PlayerImpl.TrackType?,
    override val index: Int,
    override val language: String? = null,
    override val channelCount: Int? = null,
    override val channelLayout: String? = null,
    override val codec: String? = null,
    override val videoDescription: String? = null,
    override val selected: Boolean
): Track()