package app.playback

import android.os.Bundle
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionResult.RESULT_SUCCESS
import app.playback.PlaybackProperties.audioPreference
import app.playback.PlaybackProperties.ccPreference
import app.playback.PlaybackProperties.maxBuffer
import app.playback.PlaybackProperties.minBuffer
import app.playback.PlaybackProperties.playbackBuffer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaSessionService() {

    lateinit var player: Player
    private var session: MediaSession? = null

    private var currentMedia: MediaItem? = null

    override fun onCreate() {
        super.onCreate()

        /** LoadControl (Buffering manager) and track selector (for track language preference) **/
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBuffer, maxBuffer, playbackBuffer, playbackBuffer + 500
            ).build()

        val trackSelector = DefaultTrackSelector(applicationContext)
        val params = trackSelector.buildUponParameters().setPreferredAudioLanguage(audioPreference)
            .setPreferredTextLanguage(ccPreference)
            .build()
        trackSelector.parameters = params

        /** Building ExoPlayer to use FFmpeg Audio Renderer and also enable fast-seeking */
        player = ExoPlayer.Builder(applicationContext)
            .setLoadControl(loadControl) /* We use the custom LoadControl we initialized before */
            .setTrackSelector(trackSelector)
            .setRenderersFactory(
                DefaultRenderersFactory(this).setExtensionRendererMode(
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER /* We prefer extensions, such as FFmpeg */
                )
            )
            .setWakeMode(C.WAKE_MODE_NETWORK) /* Prevent the service from being killed during playback */
            .build()
        (player as ExoPlayer).videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT /* Starter scaling */

        /** Listening to some player events */
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("PLAYER", error.message ?: "")
            }
        })

        /** Creating our MediaSession */
        session = MediaSession
            .Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> {
                    currentMedia = if (mediaItems.isEmpty()) {
                        null
                    } else {
                        mediaItems[0]
                    }

                    val newMediaItems = mediaItems.map {
                        it.buildUpon().setUri(it.mediaId).build()
                    }.toMutableList()
                    return Futures.immediateFuture(newMediaItems)
                }

                override fun onCustomCommand(
                    session: MediaSession, controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand, args: Bundle,
                ): ListenableFuture<SessionResult> {
                    /** When the controller (like the app) closes fully, we need to disconnect */
                    if (customCommand == CUSTOM_COM_END_SERVICE) {
                        session.release()
                        player.release()
                        this@PlaybackService.stopSelf()

                        return Futures.immediateFuture(SessionResult(RESULT_SUCCESS))
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return session
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
            session = null
        }
        super.onDestroy()
    }
}