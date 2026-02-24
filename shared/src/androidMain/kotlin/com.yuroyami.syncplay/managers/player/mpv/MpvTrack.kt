package com.yuroyami.syncplay.managers.player.mpv

import com.yuroyami.syncplay.managers.player.PlayerImpl
import com.yuroyami.syncplay.models.Track

class MpvTrack(
    override val name: String,
    override val type: PlayerImpl.TrackType?,
    override val index: Int,
    override val selected: Boolean
): Track()