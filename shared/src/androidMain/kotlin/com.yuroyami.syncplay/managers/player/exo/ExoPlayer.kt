package com.yuroyami.syncplay.managers.player.exo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.C.STREAM_TYPE_MUSIC
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
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.yuroyami.syncplay.SyncplayViewmodel
import com.yuroyami.syncplay.databinding.ExoviewBinding
import com.yuroyami.syncplay.managers.player.AndroidPlayerEngine
import com.yuroyami.syncplay.managers.player.BasePlayer
import com.yuroyami.syncplay.managers.player.PlayerOptions
import com.yuroyami.syncplay.managers.protocol.PacketCreator
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.Track
import com.yuroyami.syncplay.utils.collectInfoLocalAndroid
import com.yuroyami.syncplay.utils.loggy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_scaling_fill_screen
import syncplaymobile.shared.generated.resources.room_scaling_fit_screen
import syncplaymobile.shared.generated.resources.room_scaling_fixed_height
import syncplaymobile.shared.generated.resources.room_scaling_fixed_width
import syncplaymobile.shared.generated.resources.room_scaling_zoom
import java.util.Collections
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class ExoPlayer(viewmodel: SyncplayViewmodel) : BasePlayer(viewmodel, AndroidPlayerEngine.Exoplayer) {
    lateinit var audioManager: AudioManager

    /*-- Exoplayer-related properties --*/
    var exoplayer: ExoPlayer? = null
    private var session: MediaSession? = null
    private var currentMedia: MediaItem? = null
    private lateinit var exoView: PlayerView

    override val canChangeAspectRatio: Boolean
        get() = true

    override val supportsChapters: Boolean
        get() = false

    override val trackerJobInterval: Duration = 500.milliseconds

    override fun initialize() {
        playerScopeMain.launch(Dispatchers.Main.immediate) {
            val context = exoView.context

            audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true // Handle audio focus automatically
                ).setRenderersFactory(
                    DefaultRenderersFactory(context)
                        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)  // Change to ON
                )
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
                        val durationMs = exoplayer!!.duration
                        playerManager.timeFullMillis.value = abs(durationMs)

                        if (viewmodel.isSoloMode) return
                        if (durationMs / 1000.0 != viewmodel.media?.fileDuration) {
                            playerScopeIO.launch {
                                viewmodel.media?.fileDuration = durationMs / 1000.0
                                viewmodel.networkManager.send<PacketCreator.File> {
                                    media = viewmodel.media
                                }.await()
                            }
                        }
                    }
                }

                /* This detects when the user pauses or plays */
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)

                    if (exoplayer != null && exoplayer?.mediaItemCount != 0) {
                        if (exoplayer!!.playbackState != ExoPlayer.STATE_BUFFERING) {
                            playerManager.isNowPlaying.value = isPlaying //Just to inform UI

                            //Tell server about playback state change
                            if (!viewmodel.isSoloMode) {
                                viewmodel.actionManager.sendPlayback(isPlaying)
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
                    playerScopeMain.launch lol@{
                        analyzeTracks(viewmodel.media ?: return@lol)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    loggy("Player error: ${error.message ?: ""}")
                }
            })

            startTrackingProgress()
        }
    }

    override suspend fun destroy() {
        withContext(Dispatchers.Main.immediate) {
            exoplayer?.stop()
            exoplayer?.release()
            exoplayer = null
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
            }
        )
    }

    override suspend fun configurableSettings() = getExtraSettings()

    override fun getMaxVolume() = audioManager.getStreamMaxVolume(STREAM_TYPE_MUSIC)
    override fun getCurrentVolume() = audioManager.getStreamVolume(STREAM_TYPE_MUSIC)
    override fun changeCurrentVolume(v: Int) {
        if (!audioManager.isVolumeFixed) {
            audioManager.setStreamVolume(STREAM_TYPE_MUSIC, v, 0)
        }
    }

    override suspend fun hasMedia(): Boolean {
        return withContext(Dispatchers.Main.immediate) { exoplayer?.mediaItemCount != 0 && exoplayer != null }
    }

    override suspend fun isPlaying(): Boolean {
        return withContext(Dispatchers.Main.immediate) { exoplayer?.playbackState == Player.STATE_READY && exoplayer?.playWhenReady == true }
    }

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        viewmodel.media?.audioTracks?.clear()
        viewmodel.media?.subtitleTracks?.clear()
        playerScopeMain.launch {
            withContext(Dispatchers.Main) {
                val tracks = exoplayer?.currentTracks ?: return@withContext
                for (group in tracks.groups) {
                    val trackGroup = group.mediaTrackGroup
                    val trackType = group.type
                    if (trackType == C.TRACK_TYPE_AUDIO || trackType == C.TRACK_TYPE_TEXT) {
                        for (i in (0 until trackGroup.length)) {
                            val format = trackGroup.getFormat(i)
                            val index = trackGroup.indexOf(format)

                            /** Creating a custom Track instance for every track in a track group **/
                            val exoTrack = object : ExoTrack {
                                override val trackGroup = trackGroup
                                override val format = format
                                override val name = "${format.label} [${format.language?.uppercase() ?: "UND"}]"
                                override val type = trackType.toCommonType()
                                override val index = index
                                override val selected = mutableStateOf(group.isTrackSelected(index))
                            }

                            if (trackType == C.TRACK_TYPE_TEXT) {
                                viewmodel.media?.subtitleTracks?.add(exoTrack)
                            } else {
                                viewmodel.media?.audioTracks?.add(exoTrack)
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun selectTrack(track: Track?, type: TRACKTYPE) {
        val exoTrack = track as? ExoTrack

        val builder = exoplayer?.trackSelector?.parameters?.buildUpon() ?: return

        /* First, clearing our subtitle track selection (This helps troubleshoot many issues */
        exoplayer?.trackSelector?.parameters = builder.clearOverridesOfType(type.getExoType()).build()
        playerManager.currentTrackChoices.lastSubtitleOverride = null

        /* Now, selecting our subtitle track should one be selected */
        if (exoTrack != null) {
            when (type) {
                TRACKTYPE.SUBTITLE -> {
                    playerManager.currentTrackChoices.lastSubtitleOverride = TrackSelectionOverride(
                        exoTrack.trackGroup,
                        exoTrack.index
                    )
                }

                TRACKTYPE.AUDIO -> {
                    playerManager.currentTrackChoices.lastSubtitleOverride = TrackSelectionOverride(
                        exoTrack.trackGroup,
                        exoTrack.index
                    )
                }
            }
            exoplayer?.trackSelector?.parameters = builder.addOverride(
                playerManager.currentTrackChoices.lastSubtitleOverride as TrackSelectionOverride
            ).build()
        }
    }

    override suspend fun analyzeChapters(mediafile: MediaFile) {}
    override suspend fun jumpToChapter(chapter: Chapter) {}
    override suspend fun skipChapter() {}

    override suspend fun reapplyTrackChoices() {
        /* We need to cast MediaController to ExoPlayer since they're roughly the same */
        withContext(Dispatchers.Main.immediate) {
            analyzeTracks(viewmodel.media ?: return@withContext)

            exoplayer?.apply {
                val builder = trackSelectionParameters.buildUpon()

                var newParams = builder.build()

                if (playerManager.currentTrackChoices.lastAudioOverride != null) {
                    newParams = newParams.buildUpon().addOverride(
                        playerManager.currentTrackChoices.lastAudioOverride as? TrackSelectionOverride ?: return@withContext
                    ).build()
                }
                if (playerManager.currentTrackChoices.lastSubtitleOverride != null) {
                    newParams = newParams.buildUpon().addOverride(
                        playerManager.currentTrackChoices.lastSubtitleOverride as? TrackSelectionOverride ?: return@withContext
                    ).build()
                }
                trackSelectionParameters = newParams
            }
        }
    }

    override suspend fun loadExternalSubImpl(uri: String, extension: String) {
        viewmodel.media?.externalSub = MediaItem.SubtitleConfiguration.Builder(uri.toUri())
            .setUri(uri.toUri())
            .setMimeType(extension.mimeType)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        withContext(Dispatchers.Main.immediate) {
            //Exo requires that we reload the video
            injectVideo(uri)
        }
    }

    private val String.mimeType: String
        get() = when {
            contains("srt") -> MimeTypes.APPLICATION_SUBRIP
            contains("ass") || contains("ssa") -> MimeTypes.TEXT_SSA
            contains("ttml") -> MimeTypes.APPLICATION_TTML
            contains("vtt") -> MimeTypes.TEXT_VTT
            else -> ""
        }

    override suspend fun injectVideoImpl(media: MediaFile, isUrl: Boolean) {
        /* This is the builder responsible for building a MediaItem component for ExoPlayer **/
        val vid = MediaItem.Builder()
            .setUri(media.uri)
            .setMediaId(media.uri.toString())
            .apply {
                setSubtitleConfigurations(
                    Collections.singletonList(
                        media.externalSub as? MediaItem.SubtitleConfiguration ?: return@apply
                    )
                )
            }
            .build()

        /* Injecting it into ExoPlayer and getting relevant info **/
        exoplayer?.setMediaItem(vid) /* This loads the media into ExoPlayer **/
        exoplayer?.prepare() /* This prepares it and makes the first frame visible */

        /* Updating play button */
        exoplayer?.duration?.let { playerManager.timeFullMillis.value = if (it < 0) 0 else it }
    }

    override suspend fun pause() {
        withContext(Dispatchers.Main.immediate) {
            exoplayer?.pause()
        }
    }

    override suspend fun play() {
        withContext(Dispatchers.Main.immediate) {
            exoplayer?.play()
        }
    }

    override suspend fun isSeekable(): Boolean {
        return withContext(Dispatchers.Main.immediate) { exoplayer?.isCurrentMediaItemSeekable == true }
    }

    override suspend fun seekTo(toPositionMs: Long) {
        super.seekTo(toPositionMs)
        withContext(Dispatchers.Main.immediate) {
            exoplayer?.seekTo((toPositionMs))
        }
    }


    override suspend fun currentPositionMs(): Long {
        return withContext(Dispatchers.Main.immediate) { exoplayer?.currentPosition ?: 0L }
    }

    @SuppressLint("WrongConstant")
    override suspend fun switchAspectRatio(): String {
        val resolutions = mutableMapOf<Int, String>()

        resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIT] = getString(Res.string.room_scaling_fit_screen)
        resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH] = getString(Res.string.room_scaling_fixed_width)
        resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT] = getString(Res.string.room_scaling_fixed_height)
        resolutions[AspectRatioFrameLayout.RESIZE_MODE_FILL] = getString(Res.string.room_scaling_fill_screen)
        resolutions[AspectRatioFrameLayout.RESIZE_MODE_ZOOM] = getString(Res.string.room_scaling_zoom)

        var nextRes = (exoView.resizeMode + 1)
        if (nextRes == 5) nextRes = 0
        exoView.resizeMode = nextRes

        return resolutions[nextRes]!!
    }

    override suspend fun collectInfoLocal(mediafile: MediaFile) {
        collectInfoLocalAndroid(mediafile, exoView.context)
    }

    override suspend fun changeSubtitleSize(newSize: Int) {
        withContext(Dispatchers.Main.immediate) {
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

    suspend fun retweakSubtitleAppearance(
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

    interface ExoTrack : Track {
        val trackGroup: TrackGroup
        val format: Format
    }
}