package app.player.mpvjvm

import app.player.PlayerImpl
import app.player.models.Track

/** mpv track ids are ints; [index] carries the mpv track id, mirroring the Android MpvTrack
 *  so the shared TrackChoices.audioSelectionIndexMpv/subtitleSelectionIndexMpv fields work. */
class MpvJvmTrack(
    override val name: String,
    override val type: PlayerImpl.TrackType?,
    override val index: Int,
    override val selected: Boolean
) : Track()
