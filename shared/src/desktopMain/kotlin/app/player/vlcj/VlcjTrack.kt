package app.player.vlcj

import app.player.PlayerImpl
import app.player.models.Track

/** libVLC 3 track ids are ints; stored stringified so the shared
 *  TrackChoices.audioSelectionIdVlc/subtitleSelectionIdVlc (String) fields can be reused. */
class VlcjTrack(
    val id: String,
    override val name: String,
    override val type: PlayerImpl.TrackType?,
    override val index: Int,
    override val selected: Boolean
) : Track()
