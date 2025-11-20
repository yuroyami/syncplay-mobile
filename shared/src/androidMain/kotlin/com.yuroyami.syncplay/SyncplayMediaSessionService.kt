package com.yuroyami.syncplay

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class SyncplayMediaSessionService : MediaSessionService() {

    //TODO request FOREGROUND_SERVICE and NOTIFICATIONS permissions

    override fun onCreate() {
        super.onCreate()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return (application as SyncplayApp).mediaSession
    }

    override fun onDestroy() {
        val session = (application as SyncplayApp).mediaSession
        session.player.release()
        session.release()
        super.onDestroy()
    }
}