package com.yuroyami.syncplay

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.yuroyami.syncplay.utils.loggy

class SyncplayMediaSessionService : MediaSessionService() {

    lateinit var player: Player
    private var mediaSession: MediaSession? = null

    //TODO request FOREGROUND_SERVICE and NOTIFICATIONS permissions

    override fun onCreate() {
        super.onCreate()

        /** Building ExoPlayer to use FFmpeg Audio Renderer and also enable fast-seeking */
        //TODO player = initialize player HERE

        /** Creating our MediaLibrarySession which is an advanced extension of a MediaSession */
        mediaSession = with(
            MediaSession.Builder()
        ) {
            // This allows the service to launch our app when a notification is clicked for example.
            setId(packageName)
            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                setSessionActivity(
                    PendingIntent.getActivity(
                        /* context= */ this@SyncplayMediaSessionService,
                        /* requestCode= */ 0,
                        sessionIntent,
                        FLAG_IMMUTABLE
                    )
                )
            }
            build()
        }

        /** Listening to some player events */
        player.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    error.printStackTrace()
                    loggy(error)
                }
            }
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    /** Content styling constants */
    companion object {
        private const val TAG = "SyncplayMediaSessionService"
    }
}