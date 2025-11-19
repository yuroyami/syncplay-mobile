package com.yuroyami.syncplay.managers.player.mpv

import com.yuroyami.syncplay.managers.player.BasePlayer.TrackType
import com.yuroyami.syncplay.models.Track

class MpvTrack(
    override val name: String,
    override val type: TrackType?,
    override val index: Int,
    override val selected: Boolean
): Track()