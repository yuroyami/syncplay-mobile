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
import kotlinx.coroutines.flow.StateFlow

/**
 * Saves where the user left each file, and offers to continue from that position.
 *
 * The offer appears only in solo mode (watching alone). In a room (the group of people watching
 * together), the position of the room wins, and a resume prompt would fight the first sync.
 * Positions are still saved in a room, so the same file opened alone later continues from where
 * the room stopped.
 *
 * The store and the offer rules live next to [app.player.ResumePoint] and are tested there.
 */
class ResumeManager(private val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    /**
     * The saved position on offer, or null. The user answers with [continueFromOffer] or
     * [startOver].
     */
    val offer: StateFlow<ResumePoint?>
        field = MutableStateFlow(null)

    /** Called when a file finishes loading. Sets [offer] when a saved position is worth offering. */
    fun onMediaReady(fileName: String, durationMs: Long) {
        if (!viewmodel.isSoloMode) return
        if (!Preferences.RESUME_PLAYBACK.value()) return
        onIOThread {
            val point = resumePointFor(decodeResumePoints(Preferences.RESUME_POSITIONS.value()), fileName)
            // A file that is shorter than the saved position is a different file with the same
            // name. Do not offer to seek past its end. The file may also have changed meanwhile.
            if (point != null && (durationMs <= 0L || point.positionMs < durationMs) && viewmodel.media?.fileName == fileName) {
                offer.value = point
            }
        }
    }

    /** Seeks to the offered position, but only while the file that it belongs to is loaded. */
    fun continueFromOffer() {
        val point = offer.value ?: return
        offer.value = null
        if (viewmodel.media?.fileName != point.fileName) return
        viewmodel.dispatcher.seek(point.positionMs, recordUndo = false)
    }

    /** A new file replaces the loaded one, so an open offer no longer applies. */
    fun onMediaReplaced() {
        offer.value = null
    }

    fun startOver() {
        offer.value = null
    }

    /** Saves the position in the current file. Called on pause and when the user leaves the room. */
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
        offer.value = null
    }
}
