package app.player.avplayer

import app.player.PlayerImpl
import app.player.models.Track
import platform.AVFoundation.AVMediaSelectionGroup
import platform.AVFoundation.AVMediaSelectionOption

/**
 * [Track] carrying the AVFoundation media selection option and its group, both required to
 * switch tracks via the media selection API.
 */
class AvTrack(
    val sOption: AVMediaSelectionOption,
    val sGroup: AVMediaSelectionGroup,
    override val name: String,
    override val type: PlayerImpl.TrackType?,
    override val index: Int,
    override val selected: Boolean
): Track()