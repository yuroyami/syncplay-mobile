package app.player

import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.utils.loggy

/**
 * Puts the room on the lock screen and tells Android that something is playing (issue #125).
 *
 * The session offers no controls (see [RoomMediaSessionPlayer]), so the lock screen, the
 * notification and a headset button show the room and never change it.
 *
 * The room creates the session and hands it over through [RoomMediaSessionHolder], because the
 * player behind the session is whichever engine the room built.
 */
@UnstableApi
class SyncplayMediaSessionService : MediaSessionService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Joining starts this service directly, without a MediaController binding. That path
        // never calls onGetSession, so register the room before Media3 watches for playback.
        val session = RoomMediaSessionHolder.session
        sessions.filter { it !== session }.forEach(::removeSession)
        if (session != null) addSession(session)

        // Keep Media3's media-button and notification start-intent handling.
        super.onStartCommand(intent, flags, startId)
        if (session == null) stopSelf(startId)

        // The room and its player cannot be restored after process death.
        return START_NOT_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        RoomMediaSessionHolder.session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // After a swipe from recents, stop when there is no room or nothing plays, so no
        // notification stays behind without a room.
        val session = RoomMediaSessionHolder.session
        if (session == null || !session.player.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Detach this service's controllers; the room still owns the session and the engine.
        sessions.forEach(::removeSession)
        loggy("Media session service destroyed")
        super.onDestroy()
    }
}

/**
 * Where the room leaves its session for the service to pick up.
 *
 * A process-wide holder, not a binder: the service and the room live in the same process, and a
 * binder is a lot of extra code for one reference. The room clears it on teardown, so a stale
 * notification can never drive a dead room.
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
