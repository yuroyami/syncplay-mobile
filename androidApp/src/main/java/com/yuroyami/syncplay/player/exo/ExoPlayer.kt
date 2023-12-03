package com.yuroyami.syncplay.player.exo

import android.annotation.SuppressLint
import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.yuroyami.syncplay.databinding.ExoviewBinding
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.Track
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.ENGINE
import com.yuroyami.syncplay.player.PlayerOptions
import com.yuroyami.syncplay.player.PlayerUtils.trackProgress
import com.yuroyami.syncplay.protocol.JsonSender
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.RoomUtils.sendPlayback
import com.yuroyami.syncplay.utils.getFileName
import com.yuroyami.syncplay.watchroom.currentTrackChoices
import com.yuroyami.syncplay.watchroom.hasVideoG
import com.yuroyami.syncplay.watchroom.isNowPlaying
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.media
import com.yuroyami.syncplay.watchroom.p
import com.yuroyami.syncplay.watchroom.timeFull
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Collections
import kotlin.math.abs
import com.yuroyami.syncplay.shared.R as XR

class ExoPlayer : BasePlayer() {

    val engine = ENGINE.ANDROID_EXOPLAYER

    /*-- Exoplayer-related properties --*/
    var exoplayer: ExoPlayer? = null
    private var session: MediaSession? = null
    private var currentMedia: MediaItem? = null
    private lateinit var exoView: PlayerView

    override fun initialize() {
        val context = exoView.context

        playerScopeMain.launch {

            /** LoadControl (Buffering manager) and track selector (for track language preference) **/
            val options = PlayerOptions.get()
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    options.minBuffer,
                    options.maxBuffer,
                    options.playbackBuffer,
                    options.playbackBuffer + 500
                ).build()

            val trackSelector = DefaultTrackSelector(context.applicationContext)
            val params = trackSelector.buildUponParameters().setPreferredAudioLanguage(options.audioPreference)
                .setPreferredTextLanguage(options.ccPreference)
                .build()
            trackSelector.parameters = params

            /** Building ExoPlayer to use FFmpeg Audio Renderer and also enable fast-seeking */
            exoplayer = ExoPlayer.Builder(context.applicationContext)
                .setLoadControl(loadControl) /* We use the custom LoadControl we initialized before */
                .setTrackSelector(trackSelector)
                .setRenderersFactory(
                    DefaultRenderersFactory(context).setExtensionRendererMode(
                        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER /* We prefer extensions, such as FFmpeg */
                    )
                )
                .setWakeMode(C.WAKE_MODE_NETWORK) /* Prevent the service from being killed during playback */
                .build()

            exoplayer?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT /* Starter scaling */

            exoView.player = exoplayer

            /** Creating our MediaSession */
            session = MediaSession
                .Builder(context, exoplayer!!)
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
                })
                .build()


            exoplayer?.addListener(object : Player.Listener {

                /* This detects when the player loads a file/URL, so we tell the server */
                override fun onIsLoadingChanged(isLoading: Boolean) {
                    super.onIsLoadingChanged(isLoading)

                    if (!isLoading && exoplayer != null) {
                        /* Updating our timeFull */
                        val duration = exoplayer!!.duration.div(1000.0)
                        timeFull.longValue = abs(duration.toLong())

                        if (isSoloMode) return
                        if (duration != media?.fileDuration) {
                            playerScopeIO.launch launch2@{

                                //while (media?.fileSize == "") {}
                                media?.fileDuration = duration
                                p.sendPacket(JsonSender.sendFile(media ?: return@launch2))
                            }
                        }
                    }
                }

                /* This detects when the user pauses or plays */
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)

                    if (exoplayer != null && exoplayer?.mediaItemCount != 0) {
                        if (exoplayer!!.playbackState != ExoPlayer.STATE_BUFFERING) {
                            isNowPlaying.value = isPlaying //Just to inform UI

                            //Tell server about playback state change
                            if (!isSoloMode) {
                                sendPlayback(isPlaying)
                                p.paused = !isPlaying
                            }
                        }
                    }
                }

                /* This detects when a media track change has happened (such as loading a custom sub) */
                override fun onTracksChanged(tracks: Tracks) {
                    super.onTracksChanged(tracks)

                    /* Updating our HUD's timeFull here again, just in case. */
                    //val duration = exoplayer.duration / 1000.0
                    //timeFull.longValue = kotlin.math.abs(duration.toLong())

                    /* Repopulate audio and subtitle track lists with the new analysis of tracks **/
                    analyzeTracks(media ?: return)
                }

                override fun onPlayerError(error: PlaybackException) {
                    loggy("Player error: ${error.message ?: ""}")
                }
            })


            trackProgress()
        }
    }

    @Composable
    override fun VideoPlayer(modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                exoView = ExoviewBinding.inflate(LayoutInflater.from(context)).exoview
                initialize()
                return@AndroidView exoView
            },
            update = {})
    }

    override fun hasMedia(): Boolean {
        return exoplayer?.mediaItemCount != 0 && exoplayer != null
    }

    override fun isPlaying(): Boolean {
        return exoplayer?.playbackState == Player.STATE_READY && exoplayer?.playWhenReady == true
    }

    override fun analyzeTracks(mediafile: MediaFile) {
        media?.audioTracks?.clear()
        media?.subtitleTracks?.clear()
        val tracks = exoplayer?.currentTracks ?: return
        for (group in tracks.groups) {
            val trackGroup = group.mediaTrackGroup
            val trackType = group.type
            if (trackType == C.TRACK_TYPE_AUDIO || trackType == C.TRACK_TYPE_TEXT) {
                for (i in (0 until trackGroup.length)) {
                    val format = trackGroup.getFormat(i)
                    val index = trackGroup.indexOf(format)

                    /** Creating a custom Track instance for every track in a track group **/
                    val exoTrack = Track(
                        trackGroup_EXO_ONLY = trackGroup,
                        trackType = trackType,
                        index = index,
                        format_EXO_ONLY = format,
                        name = "${format.label} [${format.language?.uppercase() ?: "UND"}]"
                    ).apply {
                        this.selected.value = group.isTrackSelected(index)
                    }

                    exoTrack.selected.value = group.isTrackSelected(index)

                    if (trackType == C.TRACK_TYPE_TEXT) {
                        media?.subtitleTracks?.add(exoTrack)
                    } else {
                        media?.audioTracks?.add(exoTrack)
                    }
                }
            }
        }
    }

    override fun selectTrack(type: Int, index: Int) {
        val builder = exoplayer?.trackSelector?.parameters?.buildUpon() ?: return

        /* First, clearing our subtitle track selection (This helps troubleshoot many issues */
        exoplayer?.trackSelector?.parameters = builder.clearOverridesOfType(type).build()
        currentTrackChoices.lastSubtitleOverride = null

        /* Now, selecting our subtitle track should one be selected */
        if (index >= 0) {
            when (type) {
                C.TRACK_TYPE_TEXT -> {
                    currentTrackChoices.lastSubtitleOverride = TrackSelectionOverride(
                        media!!.subtitleTracks[index].trackGroup_EXO_ONLY as TrackGroup,
                        media!!.subtitleTracks[index].index
                    )
                }

                C.TRACK_TYPE_AUDIO -> {
                    currentTrackChoices.lastSubtitleOverride = TrackSelectionOverride(
                        media!!.audioTracks[index].trackGroup_EXO_ONLY as TrackGroup,
                        media!!.audioTracks[index].index
                    )
                }
            }
            exoplayer?.trackSelector?.parameters = builder.addOverride(
                currentTrackChoices.lastSubtitleOverride as TrackSelectionOverride
            ).build()
        }
    }

    override fun reapplyTrackChoices() {
        /* We need to cast MediaController to ExoPlayer since they're roughly the same */
        analyzeTracks(media ?: return)

        exoplayer?.apply {
            val builder = trackSelectionParameters.buildUpon()

            var newParams = builder.build()

            if (currentTrackChoices.lastAudioOverride != null) {
                newParams = newParams.buildUpon().addOverride(
                    currentTrackChoices.lastAudioOverride as? TrackSelectionOverride ?: return
                ).build()
            }
            if (currentTrackChoices.lastSubtitleOverride != null) {
                newParams = newParams.buildUpon().addOverride(
                    currentTrackChoices.lastSubtitleOverride as? TrackSelectionOverride ?: return
                ).build()
            }
            trackSelectionParameters = newParams
        }
    }

    override fun loadExternalSub(uri: String) {
        if (hasMedia()) {
            val filename = getFileName(uri = uri, exoView.context).toString()
            val extension = filename.substring(filename.length - 4)

            val mimeType =
                if (extension.contains("srt")) MimeTypes.APPLICATION_SUBRIP
                else if ((extension.contains("ass"))
                    || (extension.contains("ssa"))
                ) MimeTypes.TEXT_SSA
                else if (extension.contains("ttml")) MimeTypes.APPLICATION_TTML
                else if (extension.contains("vtt")) MimeTypes.TEXT_VTT else ""

            if (mimeType != "") {
                media?.externalSub = MediaItem.SubtitleConfiguration.Builder(uri.toUri())
                    .setUri(uri.toUri())
                    .setMimeType(mimeType)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()

                injectVideo(uri)

                //toasty(string(R.string.room_selected_sub, filename))
            } else {
                //toasty(getString(R.string.room_selected_sub_error))
            }
        } else {
            //toasty(getString(R.string.room_sub_error_load_vid_first))
        }
    }

    override fun injectVideo(uri: String?, isUrl: Boolean) {
        /* Changing UI (hiding artwork, showing media controls) */
        hasVideoG.value = true

        playerScopeMain.launch {
            /* Creating a media file from the selected file */
            if (uri != null || media == null) {
                media = MediaFile()
                media?.uri = uri

                /* Obtaining info from it (size and name) */
                if (isUrl) {
                    media?.url = uri
                    //TODO: media?.collectInfoURL()
                } else {
                    //TODO: media?.collectInfo(applicationContext)
                }

                /* Checking mismatches with others in room */
                //checkFileMismatches(p) TODO
            }

            /* Injecting the media into exoplayer */
            try {
                /* This is the builder responsible for building a MediaItem component for ExoPlayer **/
                val vid = MediaItem.Builder()
                    .setUri(media?.uri)
                    .setMediaId(media?.uri.toString())
                    .apply {
                        setSubtitleConfigurations(Collections.singletonList(media?.externalSub as? MediaItem.SubtitleConfiguration ?: return@apply))
                    }
                    .build()

                /* Injecting it into ExoPlayer and getting relevant info **/
                exoplayer?.setMediaItem(vid) /* This loads the media into ExoPlayer **/
                exoplayer?.prepare() /* This prepares it and makes the first frame visible */

                /* Goes back to the beginning for everyone */
                if (!isSoloMode) {
                    p.currentVideoPosition = 0.0
                }

                /* Updating play button */
                exoplayer?.duration?.let { timeFull.longValue = if (it < 0) 0 else it }

                /* Seeing if we have to start over TODO **/
                //if (startFromPosition != (-3.0).toLong()) myExoPlayer?.seekTo(startFromPosition)
            } catch (e: IOException) {
                /* If, for some reason, the video didn't wanna load */
                e.printStackTrace()
                //toasty("There was a problem loading this file.")
            }

            /* Finally, show a a toast to the user that the media file has been added */
            //toasty(string(R.string.room_selected_vid, "${media?.fileName}"))
        }
    }

    override fun pause() {
        playerScopeMain.launch {
            exoplayer?.pause()
        }
    }

    override fun play() {
        playerScopeMain.launch {
            exoplayer?.play()
        }
    }

    override fun isSeekable(): Boolean {
        return exoplayer?.isCurrentMediaItemSeekable == true
    }

    override fun seekTo(toPositionMs: Long) {
        playerScopeMain.launch {
            exoplayer?.seekTo((toPositionMs))
        }
    }

    override fun currentPositionMs(): Long {
        return exoplayer?.currentPosition ?: 0L
    }

    @SuppressLint("WrongConstant")
    override fun switchAspectRatio(): String {
        with(exoView.context.applicationContext) {
            val resolutions = mutableMapOf<Int, String>()
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIT] = getString(XR.string.room_scaling_fit_screen)
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH] = getString(XR.string.room_scaling_fixed_width)
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT] = getString(XR.string.room_scaling_fixed_height)
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FILL] = getString(XR.string.room_scaling_fill_screen)
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_ZOOM] = getString(XR.string.room_scaling_zoom)

            var nextRes = (exoView.resizeMode + 1)
            if (nextRes == 5) nextRes = 0
            exoView.resizeMode = nextRes

            return resolutions[nextRes]!!
        }
    }


    /** EXO-EXCLUSIVE */
}