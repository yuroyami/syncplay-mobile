package app.player.exo

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
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
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import app.R
import app.player.PlayerImpl
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import app.player.models.PlayerOptions
import app.player.models.Track
import app.player.models.TrackChoice
import app.preferences.Preferences.EXO_MAX_BUFFER
import app.preferences.Preferences.EXO_MIN_BUFFER
import app.preferences.Preferences.EXO_SEEK_BUFFER
import app.preferences.settings.SettingCategory
import app.room.OSDCategory
import app.room.RoomViewmodel
import app.utils.contextObtainer
import app.utils.loggy
import app.utils.playableUri
import app.utils.uri
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_playback_error
import syncplaymobile.shared.generated.resources.room_scaling_fill_screen
import syncplaymobile.shared.generated.resources.room_scaling_fit_screen
import syncplaymobile.shared.generated.resources.room_scaling_fixed_height
import syncplaymobile.shared.generated.resources.room_scaling_fixed_width
import syncplaymobile.shared.generated.resources.room_scaling_zoom
import syncplaymobile.shared.generated.resources.uisetting_categ_exo
import java.util.Collections
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import app.uicomponents.glassEnabledNow

class ExoImpl(vm: RoomViewmodel) : PlayerImpl(vm, ExoEngine) {

    /*-- Exoplayer-related properties --*/
    var exoplayer: ExoPlayer? = null
    private lateinit var exoView: PlayerView

    override val supportsChapters: Boolean = false

    override val trackerJobInterval: Duration = 500.milliseconds

    override fun initialize() {
        // A recreated view hosts the existing player; a second ExoPlayer would leak the first.
        if (isInitialized) {
            exoView.player = exoplayer
            return
        }
        val context = contextObtainer()


        val options = PlayerOptions.get()
        /* Clamp the buffer values so DefaultLoadControl's invariants always hold even with an
         * invalid saved pref combo: it requires minBuffer <= maxBuffer and
         * bufferForPlayback(AfterRebuffer) <= minBuffer. (min/max are ms; seek is already ms.) */
        val minBufferMs = options.minBuffer
        val maxBufferMs = maxOf(options.maxBuffer, minBufferMs)
        val bufferForPlaybackMs = minOf(options.playbackBuffer, minBufferMs)
        val bufferForPlaybackAfterRebufferMs = minOf(options.playbackBuffer + 500, minBufferMs)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs
            ).build()

        val trackSelector = DefaultTrackSelector(context)
        val params = trackSelector.buildUponParameters()
            .setPreferredAudioLanguage(options.audioPreference)
            .setPreferredTextLanguage(options.ccPreference)
            .build()
        trackSelector.parameters = params

        /* Enable the FFmpeg extension renderer (wider codec support) when its lib is present. */
        val ffmpegAvailable = FfmpegLibrary.isAvailable()

        loggy("FFMPEG IS AVAILABLE?: $ffmpegAvailable")

        exoplayer = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setRenderersFactory(
                DefaultRenderersFactory(context).setExtensionRendererMode(
                    if (ffmpegAvailable) {
                        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    } else {
                        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                    }
                ).setEnableDecoderFallback(true)
            )
            .setWakeMode(C.WAKE_MODE_NETWORK) /* Keep the CPU/network awake during playback. */
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* MUST stay false. When true, ExoPlayer silently pause()s on transient/permanent
                 * audio-focus loss (notifications, system sounds, OS doze, Bluetooth glitches),
                 * firing onIsPlayingChanged(false). The isNowPlaying divergence collector in
                 * ProtocolManager.startChannelHealthMonitoring() then broadcasts `play=false` to
                 * the room, producing a spurious "User X paused" minutes into a session. Any
                 * polite focus handling must pause LOCALLY only and never mutate
                 * viewmodel.playerManager.isNowPlaying. */
                false
            )
            .build()

        exoplayer?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT

        exoView.player = exoplayer

        exoplayer?.playWhenReady = false

        exoplayer?.addListener(object : Player.Listener {

            /* On load completion, announce the file's duration to the server. */
            override fun onIsLoadingChanged(isLoading: Boolean) {
                super.onIsLoadingChanged(isLoading)

                val player = exoplayer ?: return
                if (!isLoading) {
                    // C.TIME_UNSET means "not known yet"; its absolute value is not a duration.
                    val raw = player.duration
                    val durationMs = if (raw == C.TIME_UNSET || raw < 0) 0L else raw
                    viewmodel.playerManager.timeFullMillis.value = durationMs

                    if (viewmodel.isSoloMode) return
                    val durationSec = durationMs / 1000.0
                    if (abs(durationSec - (viewmodel.media?.fileDuration ?: -1.0)) > 0.001) {
                        viewmodel.media?.fileDuration = durationSec

                        announceFileLoaded()
                    }
                }
            }

            /* Display only: the room shows a waiting indicator, nothing is broadcast from here. */
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                viewmodel.playerManager.isBuffering.value = playbackState == Player.STATE_BUFFERING
            }

            /* Tracks local pause/play transitions. */
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)

                val player = exoplayer ?: return
                if (player.mediaItemCount == 0) return
                when (player.playbackState) {
                    Player.STATE_BUFFERING -> return
                    Player.STATE_IDLE -> {
                        // The engine gave up (a stream error, a stop); the room did not. Note
                        // the pause as expected so the collector treats it as local news only.
                        viewmodel.protocol.noteExpectedPlaybackState(paused = true)
                        viewmodel.playerManager.isNowPlaying.value = false
                        return
                    }
                }
                viewmodel.playerManager.isNowPlaying.value = isPlaying

                if (player.playbackState == Player.STATE_ENDED) {
                    onPlaybackEnded()
                }
            }

            /* Fires on any track change (e.g. loading a custom sub); re-analyze tracks. */
            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)

                playerScopeMain.launch lol@{
                    analyzeTracks(viewmodel.media ?: return@lol)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                loggy("Player error: ${error.errorCodeName} ${error.message ?: ""}")
                val reason = error.errorCodeName
                viewmodel.dispatchOSD(OSDCategory.WARNING) { getString(Res.string.room_playback_error, reason) }
                viewmodel.dispatcher.broadcastMessage(isChat = false, isError = true) {
                    getString(Res.string.room_playback_error, reason)
                }
            }
        })

        isInitialized = true

        startTrackingProgress()
    }

    override suspend fun destroy() {
        if (!isInitialized) return
        // Reset the guard and cancel the 500ms position-tracker job before releasing the player.
        // Exo polls a nullable instance (no hard crash like mpv's global handle), but leaving the
        // job alive keeps it polling a torn-down player and retains the captured RoomViewmodel graph.
        isInitialized = false
        playerSupervisorJob.cancel()

        withContext(Dispatchers.Main.immediate) {
            exoplayer?.stop()
            runCatching { loudness?.release() }
            loudness = null
            exoplayer?.release()
            exoplayer = null
        }
    }

    @SuppressLint("InflateParams")
    @Composable
    override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                // TextureView so Haze can capture the frames (glass over video); SurfaceView when
                // glass is off, which can take the hardware overlay plane instead. Surface type is
                // fixed at inflation, so this is read once here rather than observed.
                val layout = if (glassEnabledNow()) R.layout.exoview else R.layout.exoview_surface
                exoView = LayoutInflater.from(context).inflate(layout, null) as PlayerView
                initialize()
                onPlayerReady()
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

    override fun getEngineVolume(): Int = ((exoplayer?.volume ?: 1f) * 100).roundToInt().coerceIn(0, 100)
    override fun setEngineVolume(percent: Int) {
        exoplayer?.volume = percent.coerceIn(0, 100) / 100f
    }

    /* Amplification rides a LoudnessEnhancer on the player's audio session: 200 percent is +6 dB,
     * the point where a doubled amplitude starts to clip on most material. The effect is built on
     * first use and torn down with the player. */
    override val gainMax: Int = 200
    private var loudness: LoudnessEnhancer? = null
    private var gainPercent: Int = 100

    override fun getGain(): Int = gainPercent
    override fun setGain(percent: Int) {
        val target = percent.coerceIn(100, gainMax)
        gainPercent = target
        val session = exoplayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
        if (session == C.AUDIO_SESSION_ID_UNSET) return
        val effect = loudness ?: runCatching { LoudnessEnhancer(session) }.getOrNull()?.also { loudness = it } ?: return
        runCatching {
            effect.setTargetGain((target - 100) * 6)
            effect.enabled = target > 100
        }
    }

    override suspend fun hasMedia(): Boolean {
        if (!isInitialized) return false

        return withContext(Dispatchers.Main.immediate) { (exoplayer?.mediaItemCount ?: 0) != 0 }
    }

    override suspend fun isPlaying(): Boolean {
        if (!isInitialized) return false

        return withContext(Dispatchers.Main.immediate) { exoplayer?.playbackState == Player.STATE_READY && exoplayer?.playWhenReady == true }
    }

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        if (!isInitialized) return

        viewmodel.media?.tracks?.clear()

        withContext(Dispatchers.Main) {
            val tracks = exoplayer?.currentTracks ?: return@withContext
            for (group in tracks.groups) {
                val trackGroup = group.mediaTrackGroup
                val trackType = group.type
                if (trackType == C.TRACK_TYPE_AUDIO || trackType == C.TRACK_TYPE_TEXT) {
                    for (i in (0 until trackGroup.length)) {
                        val format = trackGroup.getFormat(i)
                        val index = trackGroup.indexOf(format)

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

    override suspend fun selectTrack(track: Track?, type: TrackType) {
        if (!isInitialized) return

        val exoTrack = track as? ExoTrack

        val builder = exoplayer?.trackSelector?.parameters?.buildUpon() ?: return

        /* Clear only the override for the type being changed. */
        exoplayer?.trackSelector?.parameters = builder.clearOverridesOfType(type.getExoType()).build()
        when (type) {
            TrackType.SUBTITLE -> playerManager.currentTrackChoices.subtitle = TrackChoice.Off
            TrackType.AUDIO -> playerManager.currentTrackChoices.audio = TrackChoice.Off
        }

        if (exoTrack != null) {
            val override = TrackSelectionOverride(
                exoTrack.trackGroup,
                exoTrack.index
            )
            when (type) {
                TrackType.SUBTITLE -> playerManager.currentTrackChoices.subtitle = TrackChoice.ByOverride(override)
                TrackType.AUDIO -> playerManager.currentTrackChoices.audio = TrackChoice.ByOverride(override)
            }
            exoplayer?.trackSelector?.parameters = builder.addOverride(override).build()
        }
    }

    override suspend fun analyzeChapters(mediafile: MediaFile) {}

    override suspend fun reapplyTrackChoices() {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            // No analyzeTracks() here (this runs on every onResume): re-adding the stored overrides
            // is all that's needed to restore the selection, since no engine is released across
            // pause/resume. The media.tracks UI list rebuilds on-demand when the track sheet opens.
            if (viewmodel.media == null) return@withContext

            exoplayer?.apply {
                val builder = trackSelectionParameters.buildUpon()

                var newParams = builder.build()

                for (type in TrackType.entries) {
                    val stored = playerManager.currentTrackChoices[type]
                    val override = (stored as? TrackChoice.ByOverride)?.override as? TrackSelectionOverride
                    if (override != null) {
                        newParams = newParams.buildUpon().addOverride(override).build()
                    } else if (stored == TrackChoice.Off) {
                        newParams = newParams.buildUpon().clearOverridesOfType(type.getExoType()).build()
                    }
                }
                trackSelectionParameters = newParams
            }
        }
    }

    var externalSub: MediaItem.SubtitleConfiguration? = null

    /** The media the external subtitle belongs to; a different file must not inherit it. */
    private var externalSubMediaId: String? = null

    override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) {
        // Was FileProvider with authority "${packageName}.fileprovider": that authority doesn't
        // exist (the manifest declares "${applicationId}.provider"), AND filesDir/logs (where
        // downloaded subs land) isn't in provider_paths.xml, so getUriForFile threw and the sub
        // silently never attached. playableUri yields the content:// uri for picker results and a
        // file:// uri for our own downloaded files, neither of which needs FileProvider.
        val subUri = uri.playableUri

        externalSub = MediaItem.SubtitleConfiguration.Builder(subUri)
            .setUri(subUri)
            .setMimeType(extension.mimeType)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        externalSubMediaId = viewmodel.media?.location?.commonUri

        withContext(Dispatchers.Main.immediate) {
            // Exo can only attach an external subtitle by reloading the media item.
            reloadVideo()
        }
    }

    /** The subtitle for [mediaId], or nothing when the file changed since it was chosen. */
    private fun MediaItem.Builder.withExternalSub(mediaId: String): MediaItem.Builder {
        val sub = externalSub
        if (sub == null) return this
        if (externalSubMediaId != mediaId) {
            externalSub = null
            externalSubMediaId = null
            return this
        }
        return setSubtitleConfigurations(Collections.singletonList(sub))
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
            .withExternalSub(location.commonUri)
            .build()

        exoplayer?.setMediaItem(vid)
    }

    override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
        val vid = MediaItem.Builder()
            .setUri(location.url)
            .setMediaId(location.url)
            .withExternalSub(location.commonUri)
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

    override suspend fun setSpeed(speed: Double) {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            exoplayer?.setPlaybackSpeed(speed.toFloat())
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

    @UiThread
    override fun bufferedPositionMs(): Long? {
        if (!isInitialized) return null
        return exoplayer?.bufferedPosition
    }

    @SuppressLint("WrongConstant")
    override suspend fun switchAspectRatio(): String {
        if (!isInitialized) return ""
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