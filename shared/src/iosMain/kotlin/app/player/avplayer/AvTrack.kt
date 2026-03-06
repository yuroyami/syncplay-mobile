package app.player.avplayer

import app.player.PlayerImpl
import app.player.models.Track
import platform.AVFoundation.AVMediaSelectionGroup
import platform.AVFoundation.AVMediaSelectionOption

/**
 * Extended Track interface for AVPlayer tracks.
 *
 * Includes references to AVFoundation's media selection option and group
 * required for track switching operations.
 */
class AvTrack(
    val sOption: AVMediaSelectionOption, /* The AVFoundation media selection option representing this track */
    val sGroup: AVMediaSelectionGroup, /* The media selection group this track belongs to */
    override val name: String,
    override val type: PlayerImpl.TrackType?,
    override val index: Int,
    override val selected: Boolean
): Track()