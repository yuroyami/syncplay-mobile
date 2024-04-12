package com.yuroyami.syncplay.player.exo

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Format
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
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import cafe.adriel.lyricist.Lyricist
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.yuroyami.syncplay.databinding.ExoviewBinding
import com.yuroyami.syncplay.lyricist.Stringies
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.Track
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.PlayerOptions
import com.yuroyami.syncplay.player.PlayerUtils.trackProgress
import com.yuroyami.syncplay.protocol.JsonSender
import com.yuroyami.syncplay.utils.RoomUtils.checkFileMismatches
import com.yuroyami.syncplay.utils.RoomUtils.sendPlayback
import com.yuroyami.syncplay.utils.collectInfoLocalAndroid
import com.yuroyami.syncplay.utils.getFileName
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.watchroom.dispatchOSD
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.lyricist
import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.Collections
import kotlin.math.abs
import kotlin.math.roundToInt

class ExoPlayer : BasePlayer() {
    override val engine = ENGINE.ANDROID_EXOPLAYER

    /*-- Exoplayer-related properties --*/
    var exoplayer: ExoPlayer? = null
    private var session: MediaSession? = null
    private var currentMedia: MediaItem? = null
    private lateinit var exoView: PlayerView

    override val canChangeAspectRatio: Boolean
        get() = true

    override val supportsChapters: Boolean
        get() = false

    override fun initialize() {
        val context = exoView.context

        playerScopeMain.launch {
            /** LoadControl (Buffering manager) and track selector (for track language preference) **/
            val options = PlayerOptions.getSuspendingly()
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    options.minBuffer,
                    options.maxBuffer,
                    options.playbackBuffer,
                    options.playbackBuffer + 500
                ).build()

            val trackSelector = DefaultTrackSelector(context.applicationContext)
            val params = trackSelector.buildUponParameters()
                .setPreferredAudioLanguage(options.audioPreference)
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

            exoplayer?.playWhenReady = false

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
                        viewmodel?.timeFull?.longValue = abs(duration.toLong())

                        if (isSoloMode) return
                        if (duration != viewmodel?.media?.fileDuration) {
                            playerScopeIO.launch launch2@{

                                //while (media?.fileSize == "") {}
                                viewmodel?.media?.fileDuration = duration
                                viewmodel!!.p.sendPacket(JsonSender.sendFile(viewmodel?.media ?: return@launch2))
                            }
                        }
                    }
                }

                /* This detects when the user pauses or plays */
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)

                    if (exoplayer != null && exoplayer?.mediaItemCount != 0) {
                        if (exoplayer!!.playbackState != ExoPlayer.STATE_BUFFERING) {
                            viewmodel?.isNowPlaying?.value = isPlaying //Just to inform UI

                            //Tell server about playback state change
                            if (!isSoloMode) {
                                sendPlayback(isPlaying)
                                viewmodel!!.p.paused = !isPlaying
                            }

                            if (exoplayer!!.playbackState == ExoPlayer.STATE_ENDED) {
                                onPlaybackEnded() //signaling end of playback
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
                    playerScopeMain.launch {
                        analyzeTracks(viewmodel?.media ?: return@launch)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    loggy("Player error: ${error.message ?: ""}", 301)
                }
            })

            playerScopeMain.trackProgress(intervalMillis = 500L)
        }
    }

    override fun destroy() {
        exoplayer?.stop()
        exoplayer?.release()
        exoplayer = null
    }

    @Composable
    override fun VideoPlayer(modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                exoView = ExoviewBinding.inflate(LayoutInflater.from(context)).exoview
                initialize()
                return@AndroidView exoView
            }
        )
    }

    override fun hasMedia(): Boolean {
        return exoplayer?.mediaItemCount != 0 && exoplayer != null
    }

    override fun isPlaying(): Boolean {
        return exoplayer?.playbackState == Player.STATE_READY && exoplayer?.playWhenReady == true
    }

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        viewmodel?.media?.audioTracks?.clear()
        viewmodel?.media?.subtitleTracks?.clear()
        val tracks = exoplayer?.currentTracks ?: return
        for (group in tracks.groups) {
            val trackGroup = group.mediaTrackGroup
            val trackType = group.type
            if (trackType == C.TRACK_TYPE_AUDIO || trackType == C.TRACK_TYPE_TEXT) {
                for (i in (0 until trackGroup.length)) {
                    val format = trackGroup.getFormat(i)
                    val index = trackGroup.indexOf(format)

                    /** Creating a custom Track instance for every track in a track group **/
                    val exoTrack = object: ExoTrack {
                        override val trackGroup = trackGroup
                        override val format = format
                        override val name = "${format.label} [${format.language?.uppercase() ?: "UND"}]"
                        override val type = trackType.toCommonType()
                        override val index = index
                        override val selected = mutableStateOf(group.isTrackSelected(index))
                    }

                    if (trackType == C.TRACK_TYPE_TEXT) {
                        viewmodel?.media?.subtitleTracks?.add(exoTrack)
                    } else {
                        viewmodel?.media?.audioTracks?.add(exoTrack)
                    }
                }
            }
        }
    }

    override fun selectTrack(track: Track?, type: TRACKTYPE) {
        val exoTrack = track as? ExoTrack

        val builder = exoplayer?.trackSelector?.parameters?.buildUpon() ?: return

        /* First, clearing our subtitle track selection (This helps troubleshoot many issues */
        exoplayer?.trackSelector?.parameters = builder.clearOverridesOfType(type.getExoType()).build()
        viewmodel?.currentTrackChoices?.lastSubtitleOverride = null

        /* Now, selecting our subtitle track should one be selected */
        if (exoTrack != null) {
            when (type) {
                TRACKTYPE.SUBTITLE -> {
                    viewmodel?.currentTrackChoices?.lastSubtitleOverride = TrackSelectionOverride(
                        exoTrack.trackGroup,
                        exoTrack.index
                    )
                }

                TRACKTYPE.AUDIO -> {
                    viewmodel?.currentTrackChoices?.lastSubtitleOverride = TrackSelectionOverride(
                        exoTrack.trackGroup,
                        exoTrack.index
                    )
                }
            }
            exoplayer?.trackSelector?.parameters = builder.addOverride(
                viewmodel?.currentTrackChoices?.lastSubtitleOverride as TrackSelectionOverride
            ).build()
        }
    }

    override suspend fun analyzeChapters(mediafile: MediaFile) {}
    override fun jumpToChapter(chapter: Chapter) {}
    override fun skipChapter() {}

    override fun reapplyTrackChoices() {
        /* We need to cast MediaController to ExoPlayer since they're roughly the same */
        playerScopeMain.launch {
            analyzeTracks(viewmodel?.media ?: return@launch)

            exoplayer?.apply {
                val builder = trackSelectionParameters.buildUpon()

                var newParams = builder.build()

                if (viewmodel?.currentTrackChoices?.lastAudioOverride != null) {
                    newParams = newParams.buildUpon().addOverride(
                        viewmodel?.currentTrackChoices?.lastAudioOverride as? TrackSelectionOverride ?: return@launch
                    ).build()
                }
                if (viewmodel?.currentTrackChoices?.lastSubtitleOverride != null) {
                    newParams = newParams.buildUpon().addOverride(
                        viewmodel?.currentTrackChoices?.lastSubtitleOverride as? TrackSelectionOverride ?: return@launch
                    ).build()
                }
                trackSelectionParameters = newParams
            }
        }
    }

    override fun loadExternalSub(uri: String) {
        if (hasMedia()) {
            val filename = getFileName(uri = uri).toString()
            val extension = filename.substring(filename.length - 4).lowercase()

            val mimeType =
                if (extension.contains("srt")) MimeTypes.APPLICATION_SUBRIP
                else if ((extension.contains("ass"))
                    || (extension.contains("ssa"))
                ) MimeTypes.TEXT_SSA
                else if (extension.contains("ttml")) MimeTypes.APPLICATION_TTML
                else if (extension.contains("vtt")) MimeTypes.TEXT_VTT else ""

            if (mimeType != "") {
                viewmodel?.media?.externalSub = MediaItem.SubtitleConfiguration.Builder(uri.toUri())
                    .setUri(uri.toUri())
                    .setMimeType(mimeType)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()

                injectVideo(uri)

                playerScopeMain.dispatchOSD(lyricist.strings.roomSelectedSub(filename))
            } else {
                playerScopeMain.dispatchOSD(lyricist.strings.roomSelectedSubError)
            }
        } else {
            playerScopeMain.dispatchOSD(lyricist.strings.roomSubErrorLoadVidFirst)
        }

    }

    override fun injectVideo(uri: String?, isUrl: Boolean) {
        /* Changing UI (hiding artwork, showing media controls) */
        viewmodel?.hasVideoG?.value = true

        playerScopeMain.launch {
            /* Creating a media file from the selected file */
            if (uri != null || viewmodel?.media == null) {
                viewmodel?.media = MediaFile()
                viewmodel?.media?.uri = uri

                /* Obtaining info from it (size and name) */
                if (isUrl) {
                    viewmodel?.media?.url = uri
                    viewmodel?.media?.let { collectInfoURL(it) }
                } else {
                    viewmodel?.media?.let { collectInfoLocal(it) }
                }

                /* Checking mismatches with others in room */
                checkFileMismatches()
            }

            /* Injecting the media into exoplayer */
            try {
                /* This is the builder responsible for building a MediaItem component for ExoPlayer **/
                val vid = MediaItem.Builder()
                    .setUri(viewmodel?.media?.uri)
                    .setMediaId(viewmodel?.media?.uri.toString())
                    .apply {
                        setSubtitleConfigurations(
                            Collections.singletonList(
                                viewmodel?.media?.externalSub
                                        as? MediaItem.SubtitleConfiguration ?: return@apply
                            )
                        )
                    }
                    .build()

                /* Injecting it into ExoPlayer and getting relevant info **/
                exoplayer?.setMediaItem(vid) /* This loads the media into ExoPlayer **/
                exoplayer?.prepare() /* This prepares it and makes the first frame visible */

                /* Goes back to the beginning for everyone */
                if (!isSoloMode) {
                    viewmodel!!.p.currentVideoPosition = 0.0
                }

                /* Updating play button */
                exoplayer?.duration?.let { viewmodel?.timeFull?.longValue = if (it < 0) 0 else it }
            } catch (e: IOException) {
                /* If, for some reason, the video didn't wanna load */
                e.printStackTrace()
                playerScopeMain.dispatchOSD("There was a problem loading this file.")
            }

            /* Finally, show a a toast to the user that the media file has been added */
            val lyricist = Lyricist("en", Stringies)
            playerScopeMain.dispatchOSD(lyricist.strings.roomSelectedVid("${viewmodel?.media?.fileName}"))
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
        super.seekTo(toPositionMs)
        playerScopeMain.launch {
            exoplayer?.seekTo((toPositionMs))
        }
    }


    override fun currentPositionMs(): Long {
        return exoplayer?.currentPosition ?: 0L
    }

    @SuppressLint("WrongConstant")
    override fun switchAspectRatio(): String {
        val resolutions = mutableMapOf<Int, String>()

        resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIT] = lyricist.strings.roomScalingFitScreen
        resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH] = lyricist.strings.roomScalingFixedWidth
        resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT] = lyricist.strings.roomScalingFixedHeight
        resolutions[AspectRatioFrameLayout.RESIZE_MODE_FILL] = lyricist.strings.roomScalingFillScreen
        resolutions[AspectRatioFrameLayout.RESIZE_MODE_ZOOM] = lyricist.strings.roomScalingZoom

        var nextRes = (exoView.resizeMode + 1)
        if (nextRes == 5) nextRes = 0
        exoView.resizeMode = nextRes

        return resolutions[nextRes]!!
    }

    override fun collectInfoLocal(mediafile: MediaFile) {
        collectInfoLocalAndroid(mediafile, exoView.context)
    }

    override fun changeSubtitleSize(newSize: Int) {
        playerScopeMain.launch {
            exoView.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, newSize.toFloat())
        }
    }

    /** EXO-EXCLUSIVE */
    private fun TRACKTYPE.getExoType(): Int {
        return when (this) {
            TRACKTYPE.AUDIO -> C.TRACK_TYPE_AUDIO
            TRACKTYPE.SUBTITLE -> C.TRACK_TYPE_TEXT
        }
    }

    private fun Int.toCommonType(): TRACKTYPE {
        return when (this) {
            C.TRACK_TYPE_AUDIO -> TRACKTYPE.AUDIO
            C.TRACK_TYPE_TEXT -> TRACKTYPE.SUBTITLE
            else -> TRACKTYPE.SUBTITLE
        }
    }

    fun retweakSubtitleAppearance(
        size: Float,
        captionStyle: CaptionStyleCompat = CaptionStyleCompat(
            Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, Color.BLACK, Typeface.DEFAULT_BOLD
        )
    ) {
        if (::exoView.isInitialized) {
            exoView.subtitleView?.setStyle(captionStyle)
            changeSubtitleSize(size.roundToInt())

        }
    }

    interface ExoTrack: Track {
        val trackGroup: TrackGroup
        val format: Format
    }
}