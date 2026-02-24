package com.yuroyami.syncplay.managers.player.vlc

import com.yuroyami.syncplay.models.Track

class VlcKitTrack(
    override val name: String,
    override val type: TrackType?,
    override val index: Int,
    override val selected: Boolean
): Track()