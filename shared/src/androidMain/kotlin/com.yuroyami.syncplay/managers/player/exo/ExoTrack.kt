package com.yuroyami.syncplay.managers.player.exo

import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import com.yuroyami.syncplay.managers.player.BasePlayer
import com.yuroyami.syncplay.models.Track

class ExoTrack(
    val trackGroup: TrackGroup,
    val format: Format,
    override val name: String,
    override val type: BasePlayer.TrackType?,
    override val index: Int,
    override val selected: Boolean
): Track()