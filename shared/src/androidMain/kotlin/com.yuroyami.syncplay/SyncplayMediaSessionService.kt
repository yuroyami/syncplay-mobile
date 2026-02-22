package com.yuroyami.syncplay

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.yuroyami.syncplay.utils.loggy

class SyncplayMediaSessionService : MediaSessionService() {

    //TODO request FOREGROUND_SERVICE and NOTIFICATIONS permissions

    override fun onCreate() {
        super.onCreate()

        loggy("The Media Service is CREATED !")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return (application as SyncplayApp).mediaSession.also {
            loggy("The Media Service is GOTTEEEEEEEEEEN !")
        }
    }

    override fun onDestroy() {
        val session = (application as SyncplayApp).mediaSession
        session.player.release()
        session.release()
        super.onDestroy()
    }
}