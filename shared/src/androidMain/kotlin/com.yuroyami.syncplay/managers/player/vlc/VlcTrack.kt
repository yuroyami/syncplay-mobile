package com.yuroyami.syncplay.managers.player.vlc

import com.yuroyami.syncplay.managers.player.PlayerImpl

class VlcTrack(
    val id: String,
    override val name: String,
    override val type: PlayerImpl.TrackType?,
    override val index: Int,
    override val selected: Boolean
    ): com.yuroyami.syncplay.models.Track()