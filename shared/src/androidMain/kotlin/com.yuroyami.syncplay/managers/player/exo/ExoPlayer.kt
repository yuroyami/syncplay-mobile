package com.yuroyami.syncplay.managers.player.exo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.annotation.UiThread
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.C.STREAM_TYPE_MUSIC
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
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
import com.yuroyami.syncplay.databinding.ExoviewBinding
import com.yuroyami.syncplay.managers.player.AndroidPlayerEngine
import com.yuroyami.syncplay.managers.player.BasePlayer
import com.yuroyami.syncplay.managers.player.PlayerOptions
import com.yuroyami.syncplay.managers.preferences.Preferences.EXO_MAX_BUFFER
import com.yuroyami.syncplay.managers.preferences.Preferences.EXO_MIN_BUFFER
import com.yuroyami.syncplay.managers.preferences.Preferences.EXO_SEEK_BUFFER
import com.yuroyami.syncplay.managers.settings.SettingCategory
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.MediaFileLocation
import com.yuroyami.syncplay.models.Track
import com.yuroyami.syncplay.utils.contextObtainer
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.uri
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.toAndroidUri
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
import syncplaymobile.shared.generated.resources.uisetting_categ_exo
import java.util.Collections
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class ExoPlayer(viewmodel: RoomViewmodel) : BasePlayer(viewmodel, AndroidPlayerEngine.Exoplayer) {
    lateinit var audioManager: AudioManager

    //TODO TERMINATE THE SESSION ON PLAYER DESTROY!!!! KEEP THIS SINGLE-SESSION ONLY

    /*-- Exoplayer-related properties --*/
    var exoplayer: ExoPlayer? = null
    private var session: MediaSession? = null
    private var currentMedia: MediaItem? = null
    private lateinit var exoView: PlayerView

    override val supportsChapters: Boolean = false

    override val trackerJobInterval: Duration = 500.milliseconds

    override fun initialize() {
        playerScopeMain.launch(Dispatchers.Main.immediate) {
            val context = contextObtainer()

            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            /** LoadControl (Buffering manager) and track selector (for track language preference) **/
            val options = PlayerOptions.get()
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    options.minBuffer,
                    options.maxBuffer,
                    options.playbackBuffer,
                    options.playbackBuffer + 500
                ).build()

            val trackSelector = DefaultTrackSelector(context)
            val params = trackSelector.buildUponParameters()
                .setPreferredAudioLanguage(options.audioPreference)
                .setPreferredTextLanguage(options.ccPreference)
                .build()
            trackSelector.parameters = params

            /** Building ExoPlayer to use FFmpeg Audio Renderer and also enable fast-seeking */
            val ffmpegAvailable = FfmpegLibrary.isAvailable()

            loggy("FFMPEG IS AVAILABLE?: $ffmpegAvailable")

            exoplayer = ExoPlayer.Builder(context)
                .setLoadControl(loadControl) /* We use the custom LoadControl we initialized before */
                .setTrackSelector(trackSelector)
                .setRenderersFactory(
                    DefaultRenderersFactory(context).setExtensionRendererMode(
                        if (ffmpegAvailable) {
                            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON/* We force extensions, crash if no FFmpeg, but `ffmpegAvailable` ensures they are*/
                        } else {
                            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF/* We use platform renderer, crash if playing unsupported format which means ffmpeg is missing*/
                        }
                    ).setEnableDecoderFallback(true)
                )
                .setWakeMode(C.WAKE_MODE_NETWORK) /* Prevent the service from being killed during playback */
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true // Handle audio focus automatically
                )
                .build()

            exoplayer?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT /* Starter scaling */

            exoView.player = exoplayer

            exoplayer?.playWhenReady = false

            /** Creating our MediaSession */
            session = MediaSession
                .Builder(context, exoplayer!!)
                .setId("session_${UUID.randomUUID()}")
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
                            viewmodel.media?.fileDuration = durationMs / 1000.0

                            announceFileLoaded()
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

            isInitialized = true

            startTrackingProgress()
        }
    }

    override suspend fun destroy() {
        if (!isInitialized) return

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

    override suspend fun configurableSettings() = SettingCategory(
        title = Res.string.uisetting_categ_exo,
        icon = Icons.Filled.SettingsInputComponent
    ) {
        +EXO_MAX_BUFFER; +EXO_MIN_BUFFER; +EXO_SEEK_BUFFER
    }

    override fun getMaxVolume() = audioManager.getStreamMaxVolume(STREAM_TYPE_MUSIC)
    override fun getCurrentVolume() = audioManager.getStreamVolume(STREAM_TYPE_MUSIC)
    override fun changeCurrentVolume(v: Int) {
        if (!audioManager.isVolumeFixed) {
            audioManager.setStreamVolume(STREAM_TYPE_MUSIC, v, 0)
        }
    }

    override suspend fun hasMedia(): Boolean {
        if (!isInitialized) return false

        return withContext(Dispatchers.Main.immediate) { exoplayer?.mediaItemCount != 0 && exoplayer != null }
    }

    override suspend fun isPlaying(): Boolean {
        if (!isInitialized) return false

        return withContext(Dispatchers.Main.immediate) { exoplayer?.playbackState == Player.STATE_READY && exoplayer?.playWhenReady == true }
    }

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        if (!isInitialized) return

        viewmodel.media?.tracks?.clear()

        withContext(Dispatchers.Main) {
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
                            val exoTrack = ExoTrack(
                                trackGroup = trackGroup,
                                format = format,
                                name = "${format.label} [${format.language?.uppercase() ?: "UND"}]",
                                type = trackType.toCommonType(),
                                index = index,
                                selected = group.isTrackSelected(index)
                            )

                            viewmodel.media?.tracks?.add(exoTrack)
                        }
                    }
                }
            }
        }
    }

    override suspend fun selectTrack(track: Track?, type: TrackType) {
        if (!isInitialized) return

        val exoTrack = track as? ExoTrack

        val builder = exoplayer?.trackSelector?.parameters?.buildUpon() ?: return

        /* First, clearing our subtitle track selection (This helps troubleshoot many issues */
        exoplayer?.trackSelector?.parameters = builder.clearOverridesOfType(type.getExoType()).build()
        playerManager.currentTrackChoices.lastSubtitleOverride = null

        /* Now, selecting our subtitle track should one be selected */
        if (exoTrack != null) {
            when (type) {
                TrackType.SUBTITLE -> {
                    playerManager.currentTrackChoices.lastSubtitleOverride = TrackSelectionOverride(
                        exoTrack.trackGroup,
                        exoTrack.index
                    )
                }

                TrackType.AUDIO -> {
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

    override suspend fun reapplyTrackChoices() {
        if (!isInitialized) return

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

    var externalSub: MediaItem.SubtitleConfiguration? = null

    override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) {
        val authority = "${contextObtainer().packageName}.fileprovider"

        externalSub = MediaItem.SubtitleConfiguration.Builder(uri.toAndroidUri(authority))
            .setUri(uri.toAndroidUri(authority))
            .setMimeType(extension.mimeType)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        withContext(Dispatchers.Main.immediate) {
            //Exo requires that we reload the video
            reloadVideo()
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

    override suspend fun injectVideoFileImpl(location: MediaFileLocation.Local) {
        if (!isInitialized) return

        val vid = MediaItem.Builder()
            .setUri(location.file.uri)
            .setMediaId(location.file.uri.toString())
            .run {
                if (externalSub != null) setSubtitleConfigurations(Collections.singletonList(externalSub!!)) else this
            }
            .build()

        exoplayer?.setMediaItem(vid)
    }

    override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
        val vid = MediaItem.Builder()
            .setUri(location.url)
            .setMediaId(location.url)
            .run {
                if (externalSub != null) setSubtitleConfigurations(Collections.singletonList(externalSub!!)) else this
            }
            .build()

        exoplayer?.setMediaItem(vid)
    }

    override suspend fun parseMedia(media: MediaFile) {
        exoplayer?.prepare()
        exoplayer?.duration?.let { playerManager.timeFullMillis.value = if (it < 0) 0 else it }
        super.parseMedia(media)
    }

    override suspend fun reloadVideo() {
        val mediaLoc = viewmodel.media?.location ?: return
        when (mediaLoc) {
            is MediaFileLocation.Local -> injectVideoFile(mediaLoc.file)
            is MediaFileLocation.Remote -> injectVideoURL(mediaLoc.url)
        }
    }

    override suspend fun pause() {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            exoplayer?.pause()
        }
    }

    override suspend fun play() {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            exoplayer?.play()
        }
    }

    override suspend fun isSeekable(): Boolean {
        if (!isInitialized) return false

        return withContext(Dispatchers.Main.immediate) { exoplayer?.isCurrentMediaItemSeekable == true }
    }

    @UiThread
    override fun seekTo(toPositionMs: Long) {
        if (!isInitialized) return
        super.seekTo(toPositionMs)
        exoplayer?.seekTo((toPositionMs))
    }

    @UiThread
    override fun currentPositionMs(): Long {
        if (!isInitialized) return 0L

        return exoplayer?.currentPosition ?: 0L
    }

    @SuppressLint("WrongConstant")
    override suspend fun switchAspectRatio(): String {
        if (!isInitialized) return "NO PLAYER FOUND"
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

    override suspend fun changeSubtitleSize(newSize: Int) {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            exoView.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, newSize.toFloat())
        }
    }

    /** EXO-EXCLUSIVE */
    private fun TrackType.getExoType(): Int {
        return when (this) {
            TrackType.AUDIO -> C.TRACK_TYPE_AUDIO
            TrackType.SUBTITLE -> C.TRACK_TYPE_TEXT
        }
    }

    private fun Int.toCommonType(): TrackType {
        return when (this) {
            C.TRACK_TYPE_AUDIO -> TrackType.AUDIO
            C.TRACK_TYPE_TEXT -> TrackType.SUBTITLE
            else -> TrackType.SUBTITLE
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


}