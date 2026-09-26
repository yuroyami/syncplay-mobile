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
import app.i18n.Localization
import app.player.PlayerImpl
import app.player.models.Chapter
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
import java.util.Collections
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import app.uicomponents.glassEnabledNow

class ExoImpl(vm: RoomViewmodel) : PlayerImpl(vm, ExoEngine) {

    var exoplayer: ExoPlayer? = null
    private lateinit var exoView: PlayerView

    override val supportsVideoTrackSelection = true

    // The track selector applies the preferred languages (see the options), so the shared pass does not.
    override val appliesPreferredLanguagesItself: Boolean = true
    // Media3 reads the chapters of Matroska and MP4 files into the track formats.
    override val supportsChapters: Boolean = true

    override val trackerJobInterval: Duration = 500.milliseconds

    override fun initialize() {
        // A recreated view hosts the existing player; a second ExoPlayer would leak the first.
        if (isInitialized) {
            exoView.player = exoplayer
            return
        }
        val context = contextObtainer()


        val options = PlayerOptions.get()
        /* Clamp the buffer values so DefaultLoadControl's rules hold even with an invalid saved
         * combination: minBuffer <= maxBuffer, and both playback buffers <= minBuffer. All four
         * values are in milliseconds. */
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
                /* handleAudioFocus MUST stay false. When true, ExoPlayer pauses by itself on any
                 * audio focus loss (notifications, system sounds, doze, Bluetooth glitches) and
                 * fires onIsPlayingChanged(false). The isNowPlaying divergence collector in
                 * ProtocolManager.startChannelHealthMonitoring() then broadcasts `play=false` to
                 * the room, which shows a false "User X paused" minutes into a session. Any
                 * audio focus handling must pause locally only and never change
                 * viewmodel.playerManager.isNowPlaying. */
                false
            )
            .build()

        exoplayer?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT

        exoView.player = exoplayer

        exoplayer?.playWhenReady = false

        exoplayer?.addListener(object : Player.Listener {

            /* When loading stops, a stream may know a longer or a final duration. */
            override fun onIsLoadingChanged(isLoading: Boolean) {
                super.onIsLoadingChanged(isLoading)
                val player = exoplayer ?: return
                if (!isLoading) onEngineDurationChanged(knownDuration(player))
            }

            /* Buffering is display only: the room shows a waiting indicator and hears nothing.
             * The first READY of each file announces it. Later ones follow a seek or a refill. */
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                viewmodel.playerManager.isBuffering.value = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) exoplayer?.let { onEngineFileReady(knownDuration(it)) }
            }

            /* Tracks local pause/play transitions. */
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)

                val player = exoplayer ?: return
                if (player.mediaItemCount == 0) return
                when (player.playbackState) {
                    Player.STATE_BUFFERING -> return
                    Player.STATE_IDLE -> {
                        // The engine gave up (a stream error, a stop), but the room did not. Mark
                        // the pause as expected, so the collector does not broadcast it.
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

            /* Fires on any track change, such as a new external subtitle. List the tracks again. */
            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)

                playerScopeMain.launch lol@{
                    analyzeTracks(viewmodel.media ?: return@lol)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                loggy("Player error: ${error.errorCodeName} ${error.message ?: ""}")
                val reason = error.errorCodeName
                viewmodel.dispatchOSD(OSDCategory.WARNING) { Localization.strings.roomPlaybackError(reason) }
                viewmodel.dispatcher.broadcastMessage(isChat = false, isError = true) {
                    Localization.strings.roomPlaybackError(reason)
                }
                onEngineLoadFailed()
            }
        })

        isInitialized = true

        startTrackingProgress()
    }

    override suspend fun destroy() {
        if (!isInitialized) return
        // Reset the guard and cancel the 500 ms position tracker before releasing the player.
        // Exo polls a nullable instance, so it does not crash like mpv's global handle. But a live
        // tracker keeps polling a released player and keeps the captured RoomViewmodel in memory.
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
                // A TextureView when glass is on, so Haze (the blur library) can capture the
                // frames. A SurfaceView when glass is off, because it can use the hardware overlay
                // plane. The surface type is fixed at inflation, so read the setting once here.
                val layout = if (glassEnabledNow()) R.layout.exoview else R.layout.exoview_surface
                exoView = LayoutInflater.from(context).inflate(layout, null) as PlayerView
                initialize()
                onPlayerReady()
                return@AndroidView exoView
            }
        )
    }

    override suspend fun configurableSettings() = SettingCategory(
        key = "engine-exo",
        title = { it.uisettingCategExo },
        icon = Icons.Filled.SettingsInputComponent
    ) {
        +EXO_MAX_BUFFER; +EXO_MIN_BUFFER; +EXO_SEEK_BUFFER
    }

    override fun getEngineVolume(): Int = ((exoplayer?.volume ?: 1f) * 100).roundToInt().coerceIn(0, 100)
    override fun setEngineVolume(percent: Int) {
        exoplayer?.volume = percent.coerceIn(0, 100) / 100f
    }

    /* Amplification uses a LoudnessEnhancer on the player's audio session. 200 percent is +6 dB
     * (double the amplitude), where most material starts to clip. The effect is built on first
     * use and released with the player. */
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
                if (trackType == C.TRACK_TYPE_AUDIO || trackType == C.TRACK_TYPE_TEXT || trackType == C.TRACK_TYPE_VIDEO) {
                    for (i in (0 until trackGroup.length)) {
                        val format = trackGroup.getFormat(i)
                        val index = trackGroup.indexOf(format)

                        val exoTrack = ExoTrack(
                            trackGroup = trackGroup,
                            format = format,
                            name = format.label?.takeIf { it.isNotBlank() } ?: format.language ?: format.sampleMimeType?.substringAfter('/') ?: "",
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
        val exoType = type.getExoType()

        /* Clear only the override for the type being changed. */
        val cleared = builder.clearOverridesOfType(exoType)

        playerManager.currentTrackChoices.rememberLanguage(type, exoTrack)
        if (exoTrack == null) {
            /* Off means off. Removing the override alone lets the selector pick a track by
             * preferred language, so "no subtitles" would still show subtitles. */
            exoplayer?.trackSelector?.parameters = cleared.setTrackTypeDisabled(exoType, true).build()
            when (type) {
                TrackType.SUBTITLE -> playerManager.currentTrackChoices.subtitle = TrackChoice.Off
                TrackType.AUDIO -> playerManager.currentTrackChoices.audio = TrackChoice.Off
                TrackType.VIDEO -> playerManager.currentTrackChoices.video = TrackChoice.Off
            }
            return
        }

        val override = TrackSelectionOverride(exoTrack.trackGroup, exoTrack.index)
        when (type) {
            TrackType.SUBTITLE -> playerManager.currentTrackChoices.subtitle = TrackChoice.ByOverride(override)
            TrackType.AUDIO -> playerManager.currentTrackChoices.audio = TrackChoice.ByOverride(override)
            TrackType.VIDEO -> playerManager.currentTrackChoices.video = TrackChoice.ByOverride(override)
        }
        // Also undo the disable, or picking a track after Off would show nothing.
        exoplayer?.trackSelector?.parameters =
            cleared.setTrackTypeDisabled(exoType, false).addOverride(override).build()
    }

    override suspend fun analyzeChapters(mediafile: MediaFile) {
        if (!isInitialized) return
        val formats = withContext(Dispatchers.Main.immediate) {
            exoplayer?.currentTracks?.groups?.flatMap { group -> (0 until group.length).map(group::getTrackFormat) }
        } ?: return
        mediafile.chapters.clear()
        mediafile.chapters.addAll(chaptersOf(formats))
    }

    override suspend fun jumpToChapter(chapter: Chapter) {
        // The base class tells the room about the seek. This moves the local player.
        super.jumpToChapter(chapter)
        withContext(Dispatchers.Main.immediate) { seekTo(chapter.timeOffsetMillis) }
    }

    override suspend fun reapplyTrackChoices() {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            // No analyzeTracks() here, because this runs on every onResume. Adding the stored
            // overrides again restores the selection, since no engine is released across a pause
            // and resume. The media.tracks list is rebuilt when the tracks panel opens.
            if (viewmodel.media == null) return@withContext

            exoplayer?.apply {
                val builder = trackSelectionParameters.buildUpon()

                var newParams = builder.build()

                for (type in TrackType.entries) {
                    val exoType = type.getExoType()
                    val stored = playerManager.currentTrackChoices[type]
                    val override = (stored as? TrackChoice.ByOverride)?.override as? TrackSelectionOverride
                    newParams = when {
                        override != null -> newParams.buildUpon()
                            .setTrackTypeDisabled(exoType, false)
                            .addOverride(override)
                            .build()
                        // Restore the disable too, or Off would not hold across a resume.
                        stored == TrackChoice.Off -> newParams.buildUpon()
                            .clearOverridesOfType(exoType)
                            .setTrackTypeDisabled(exoType, true)
                            .build()
                        else -> newParams
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
        // playableUri gives a content:// uri for picker results and a file:// uri for the app's
        // own downloaded files. Neither needs FileProvider. If FileProvider is ever used here,
        // its authority is "${applicationId}.provider", not "${packageName}.fileprovider".
        val subUri = uri.playableUri

        externalSub = MediaItem.SubtitleConfiguration.Builder(subUri)
            .setUri(subUri)
            .setMimeType(extension.mimeType)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        externalSubMediaId = viewmodel.media?.location?.commonUri

        withContext(Dispatchers.Main.immediate) { attachExternalSub() }
    }

    /**
     * Exo can attach an external subtitle only by replacing the media item. It is the same file,
     * so the item comes back at the current position with the same play state, and none of the
     * steps of a new file run: no announcement, no resync, no reset of the tracks.
     */
    private fun attachExternalSub() {
        val player = exoplayer ?: return
        val current = player.currentMediaItem ?: return
        val mediaId = viewmodel.media?.location?.commonUri ?: return
        val playWhenReady = player.playWhenReady
        player.setMediaItem(current.buildUpon().withExternalSub(mediaId).build(), player.currentPosition)
        player.playWhenReady = playWhenReady
        player.prepare()
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
    /** The duration, or null while ExoPlayer does not know it. C.TIME_UNSET is not a duration. */
    private fun knownDuration(player: Player): Long? = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }

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

        resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIT] = Localization.strings.roomScalingFitScreen
        resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH] = Localization.strings.roomScalingFixedWidth
        resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT] = Localization.strings.roomScalingFixedHeight
        resolutions[AspectRatioFrameLayout.RESIZE_MODE_FILL] = Localization.strings.roomScalingFillScreen
        resolutions[AspectRatioFrameLayout.RESIZE_MODE_ZOOM] = Localization.strings.roomScalingZoom

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
            TrackType.VIDEO -> C.TRACK_TYPE_VIDEO
            TrackType.AUDIO -> C.TRACK_TYPE_AUDIO
            TrackType.SUBTITLE -> C.TRACK_TYPE_TEXT
        }
    }

    private fun Int.toCommonType(): TrackType {
        return when (this) {
            C.TRACK_TYPE_VIDEO -> TrackType.VIDEO
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