package app.player.exo

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import app.player.PlayerImpl
import app.player.models.Track
import app.player.models.TrackTrait

class ExoTrack(
    val trackGroup: TrackGroup,
    val format: Format,
    override val name: String,
    override val type: PlayerImpl.TrackType?,
    override val index: Int,
    override val selected: Boolean
): Track() {

    /**
     * Media3 carries the purpose in the format's role and selection flags, so nothing has to be
     * guessed from the track name.
     */
    override val language: String? get() = format.language

    override val trait: TrackTrait?
        get() = when {
            format.roleFlags and ACCESSIBILITY_ROLES != 0 -> TrackTrait.ACCESSIBILITY
            format.selectionFlags and C.SELECTION_FLAG_FORCED != 0 -> TrackTrait.FORCED
            else -> null
        }

    private companion object {
        /** Captions for the deaf and hard of hearing, plus described video and easy-to-read text. */
        const val ACCESSIBILITY_ROLES =
            C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND or
                C.ROLE_FLAG_DESCRIBES_VIDEO or
                C.ROLE_FLAG_TRANSCRIBES_DIALOG or
                C.ROLE_FLAG_EASY_TO_READ
    }
}
