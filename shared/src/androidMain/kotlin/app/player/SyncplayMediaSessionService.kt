package app.player

import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.utils.loggy

/**
 * Puts the room on the lock screen and answers the headset button.
 *
 * This used to be a bare foreground notification: it kept the process alive, showed a line of
 * text, and offered no controls at all, which is most of what issue 125 is about. It is a real
 * MediaSessionService now, so the system draws the transport, media buttons work, and Android
 * knows something is playing.
 *
 * The session is created by the room and handed here, because the player behind it is whichever
 * engine the room built. [RoomMediaSessionHolder] is the handover.
 */
@UnstableApi
class SyncplayMediaSessionService : MediaSessionService() {

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        RoomMediaSessionHolder.session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away should end playback, not leave a notification behind with no room.
        val session = RoomMediaSessionHolder.session
        if (session == null || !session.player.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        loggy("Media session service destroyed")
        super.onDestroy()
    }
}

/**
 * Where the room leaves its session for the service to pick up.
 *
 * A process-wide holder rather than a binder because the service and the room live in the same
 * process and the alternative is a lot of ceremony for one reference. Cleared on teardown, so a
 * dead room can never be driven from a stale notification.
 */
@UnstableApi
object RoomMediaSessionHolder {
    @Volatile
    var session: MediaSession? = null
        private set

    fun install(session: MediaSession) {
        clear()
        this.session = session
    }

    fun clear() {
        val current = session ?: return
        (current.player as? RoomMediaSessionPlayer)?.stopWatching()
        current.release()
        session = null
    }
}
