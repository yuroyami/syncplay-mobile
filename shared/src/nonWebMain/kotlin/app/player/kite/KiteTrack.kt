package app.player.kite

import app.player.PlayerImpl
import app.player.models.Track
import app.player.models.TrackTrait
import io.github.yuroyami.kiteplayer.TrackId

/**
 * A KitePlayer track, carrying the engine's own [TrackId] instead of a bare position.
 *
 * KitePlayer identifies a track by its container stream index (or a negative value for an
 * externally added one). [Track.index] is an Int that the shared UI uses only for display and
 * ordering. With the real id beside it, selection never has to guess which list position maps to
 * which stream.
 */
class KiteTrack(
    override val name: String,
    override val type: PlayerImpl.TrackType?,
    override val index: Int,
    override val selected: Boolean,
    val trackId: TrackId,
    override val language: String? = null,
    override val trait: TrackTrait? = null,
    override val channelCount: Int? = null,
    override val codec: String? = null,
    override val videoDescription: String? = null,
) : Track()
