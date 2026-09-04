package app.room

import app.AbstractManager
import app.player.ResumePoint
import app.player.decodeResumePoints
import app.player.encodeResumePoints
import app.player.resumePointFor
import app.player.withResumePoint
import app.preferences.Preferences
import app.preferences.set
import app.preferences.value
import app.utils.SyncClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembering where a file was left, and offering it back.
 *
 * Only ever offered when watching alone. In a room the room's position is the right answer, and
 * a prompt that fights the first sync would be worse than nothing. Positions are still recorded
 * in a room, so opening the same file alone later picks up where the room got to.
 *
 * The store and the policy are in [app.player.ResumePoint] and are tested there.
 */
class ResumeManager(private val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    private val _offer = MutableStateFlow<ResumePoint?>(null)

    /** A file the viewer has seen before, waiting on continue or start over. */
    val offer = _offer.asStateFlow()

    /** Called when a file finishes loading. Raises the question, or does not. */
    fun onMediaReady(fileName: String, durationMs: Long) {
        if (!viewmodel.isSoloMode) return
        if (!Preferences.RESUME_PLAYBACK.value()) return
        onIOThread {
            val point = resumePointFor(decodeResumePoints(Preferences.RESUME_POSITIONS.value()), fileName)
            // A file whose length we now know to be shorter than the remembered position is a
            // different file with the same name. Do not offer to seek past its end.
            if (point != null && (durationMs <= 0L || point.positionMs < durationMs)) {
                _offer.value = point
            }
        }
    }

    fun continueFromOffer() {
        val point = _offer.value ?: return
        _offer.value = null
        viewmodel.dispatcher.seek(point.positionMs, recordUndo = false)
    }

    fun startOver() {
        _offer.value = null
    }

    /** Writes down where we are. Called on pause and on the way out of the room. */
    fun record() {
        if (!Preferences.RESUME_PLAYBACK.value()) return
        val media = viewmodel.media ?: return
        val fileName = media.fileName.ifBlank { return }
        val position = viewmodel.playerManager.estimatedPositionMs()
        val duration = viewmodel.playerManager.timeFullMillis.value
        onIOThread {
            val updated = withResumePoint(
                decodeResumePoints(Preferences.RESUME_POSITIONS.value()),
                ResumePoint(fileName, position, duration, SyncClock.nowMillis()),
            )
            Preferences.RESUME_POSITIONS.set(encodeResumePoints(updated))
        }
    }

    override fun invalidate() {
        _offer.value = null
    }
}
