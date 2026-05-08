package app.player.vlc

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import app.player.PlayerImpl
import app.player.models.Chapter
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import app.player.models.Track
import app.preferences.Pref
import app.preferences.PrefExtraConfig
import app.preferences.Preferences.SUBTITLE_SIZE
import app.preferences.Preferences.VLC_CUSTOM_FLAGS
import app.preferences.settings.SettingCategory
import app.preferences.value
import app.room.RoomViewmodel
import app.utils.loggy
import cocoapods.VLCKit.VLCLibrary
import cocoapods.VLCKit.VLCMedia
import cocoapods.VLCKit.VLCMediaParseLocal
import cocoapods.VLCKit.VLCMediaPlaybackSlaveTypeSubtitle
import cocoapods.VLCKit.VLCMediaPlayer
import cocoapods.VLCKit.VLCMediaPlayerChapterDescription
import cocoapods.VLCKit.VLCMediaPlayerDelegateProtocol
import cocoapods.VLCKit.VLCMediaPlayerState
import cocoapods.VLCKit.VLCMediaPlayerTrack
import cocoapods.VLCKit.VLCMediaTrackTypeAudio
import cocoapods.VLCKit.VLCMediaTrackTypeText
import cocoapods.VLCKit.VLCTime
import cocoapods.VLCKit.audioTracks
import cocoapods.VLCKit.deselectAllAudioTracks
import cocoapods.VLCKit.deselectAllTextTracks
import cocoapods.VLCKit.selectTrackAtIndex
import cocoapods.VLCKit.textTracks
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionModeMoviePlayback
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.setActive
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.uisetting_audio_delay_summary
import syncplaymobile.shared.generated.resources.uisetting_audio_delay_title
import syncplaymobile.shared.generated.resources.uisetting_categ_vlc
import syncplaymobile.shared.generated.resources.uisetting_subtitle_delay_summary
import syncplaymobile.shared.generated.resources.uisetting_subtitle_delay_title
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class VlcKitImpl(viewmodel: RoomViewmodel): PlayerImpl(viewmodel, VlcKitEngine) {
    /** VLC library instance providing codec and media parsing capabilities. */
    private var libvlc: VLCLibrary? = null
    var vlcPlayer: VLCMediaPlayer? = null
    private var vlcView: UIView? = null
    private var vlcDelegate = VlcDelegate()
    private var vlcMedia: VLCMedia? = null

    override val supportsChapters: Boolean = true

    /* Sadly, iOS doesn't allow PiP for anything other than AVPlayer */
    override val supportsPictureInPicture: Boolean = false

    override val trackerJobInterval: Duration
        get() = 0.seconds

    private var pausedSeekPosition: Long? = null

    /** Observer tokens for AVAudioSession notifications. Kept so we can unregister on destroy. */
    private var interruptionObserver: NSObjectProtocol? = null
    private var routeChangeObserver: NSObjectProtocol? = null

    /**
     * Cleans up VLC resources and stops playback.
     *
     * Properly disposes of the media player, media object, and VLC library.
     */
    override suspend fun destroy() {
        if (!isInitialized) return

        try {
            removeAudioSessionObservers()

            // Clear the delegate before stopping so the synthetic Stopped state
            // emitted by stop() doesn't get treated as natural playback end.
            vlcPlayer?.setDelegate(null)
            vlcPlayer?.stop()
            vlcPlayer?.drawable = null
            vlcPlayer = null

            vlcMedia = null

            libvlc = null
            vlcView = null
        } catch (e: Exception) {
            loggy("Error disposing VLC: ${e.message}")
        }
    }

    override suspend fun configurableSettings() = SettingCategory(
        title = Res.string.uisetting_categ_vlc,
        icon = Icons.Filled.SettingsInputComponent
    ) {
        +Pref("vlc_subtitle_delay_ms", 0) {
            title = Res.string.uisetting_subtitle_delay_title
            summary = Res.string.uisetting_subtitle_delay_summary
            icon = Icons.Filled.ClosedCaptionOff
            extraConfig = PrefExtraConfig.Slider(minValue = -5000, maxValue = 5000) {
                vlcPlayer?.currentVideoSubTitleDelay = it * 1000L
            }
        }
        +Pref("vlc_audio_delay_ms", 0) {
            title = Res.string.uisetting_audio_delay_title
            summary = Res.string.uisetting_audio_delay_summary
            icon = Icons.Filled.SpatialAudio
            extraConfig = PrefExtraConfig.Slider(minValue = -5000, maxValue = 5000) {
                vlcPlayer?.currentAudioPlaybackDelay = it * 1000L
            }
        }
        // Custom LibVLC launch flags — only meaningful when the VLC engine is actually the
        // one constructing a VLCLibrary instance, so attached to the engine-specific category.
        +VLC_CUSTOM_FLAGS
    }

    /**
     * Renders the VLC video player view within Compose.
     *
     * Creates a UIView for video rendering, initializes the VLC library and player,
     * and sets up the Picture-in-Picture layer wrapper.
     *
     * @param modifier Compose modifier for layout and styling
     */
    @Composable
    override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) {
        DisposableEffect(Unit) {
            onDispose {
                if (vlcPlayer?.drawable === vlcView) {
                    vlcPlayer?.drawable = null
                }
            }
        }

        UIKitView(
            modifier = modifier,
            factory = {
                val view = UIView().also { it.setBackgroundColor(UIColor.clearColor()) }
                vlcView = view

                val subSizeArg = "--freetype-fontsize=${SUBTITLE_SIZE.value() * 4}"
                val baseArgs = listOf(
                    "-vv",
                    subSizeArg,
                    "--network-caching=2000",
                    "--adaptive-logic=default",
                    "--http-reconnect",
                )
                // User-supplied flags from Preferences.VLC_CUSTOM_FLAGS are appended last, so
                // they can override the defaults above (LibVLC honours the last occurrence).
                val lib = VLCLibrary(baseArgs + app.utils.vlcCustomFlags())
                libvlc = lib

                val player = VLCMediaPlayer(lib)
                player.drawable = view
                vlcPlayer = player

                initialize()
                onPlayerReady()

                view
            },
            update = { view ->
                // Ensure drawable is still set
                if (vlcPlayer?.drawable !== view) {
                    vlcPlayer?.drawable = view
                }
            }
        )
    }

    /**
     * Initializes the VLC player by setting up the delegate and starting progress tracking.
     */
    override fun initialize() {
        vlcPlayer!!.setDelegate(vlcDelegate)

        configureAudioSession()
        registerAudioSessionObservers()

        isInitialized = true

        //startTrackingProgress()
    }

    /**
     * Configures the shared AVAudioSession for video playback.
     *
     * Must use category `.playback` with mode `.moviePlayback` so audio continues in the
     * background (PiP, lock screen) and mixes correctly with the system. VLCKit does NOT
     * configure this on our behalf — without this call, audio may drop after the first
     * interruption or route change.
     */
    private fun configureAudioSession() {
        try {
            val session = AVAudioSession.sharedInstance()
            // Positional args: K/N's Obj-C interop exposes overloaded `setCategory:*:` /
            // `setActive:*:` variants that share a base name, so named-parameter resolution
            // can fail — positional keeps us on the shortest matching overload.
            session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeMoviePlayback, 0uL, null)
            session.setActive(true, null)
        } catch (e: Exception) {
            loggy("AVAudioSession configure failed: ${e.message}")
        }
    }

    /**
     * Registers NSNotificationCenter observers to recover audio after system interruptions
     * (Siri, incoming FaceTime, alarm, any other AVAudioSession grab) and route changes
     * (headphones plugged/unplugged, AirPods reconnect). On interruption-end we re-activate
     * the session and nudge VLC back into sync with a pause/play cycle; on route change we
     * only re-activate the session. This mirrors Apple's "AVAudioSession best practices"
     * sample and is the only reliable way to keep audio playing on VLCKit after Siri.
     */
    private fun registerAudioSessionObservers() {
        val center = NSNotificationCenter.defaultCenter
        val queue = NSOperationQueue.mainQueue

        interruptionObserver = center.addObserverForName(
            name = AVAudioSessionInterruptionNotification,
            `object` = null,
            queue = queue
        ) { note ->
            val info = note?.userInfo ?: return@addObserverForName
            val type = (info[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.unsignedLongValue
                ?: return@addObserverForName

            when (type) {
                AVAudioSessionInterruptionTypeBegan -> {
                    // Nothing to do: iOS already paused us. We'll recover on "ended".
                }
                AVAudioSessionInterruptionTypeEnded -> {
                    val options = (info[AVAudioSessionInterruptionOptionKey] as? NSNumber)
                        ?.unsignedLongValue ?: 0uL
                    val shouldResume = (options and AVAudioSessionInterruptionOptionShouldResume) != 0uL

                    try {
                        AVAudioSession.sharedInstance().setActive(true, error = null)
                    } catch (e: Exception) {
                        loggy("AVAudioSession re-activate failed: ${e.message}")
                    }

                    // VLCKit sometimes gets stuck with a silent audio pipeline even though its
                    // state says "playing". Toggling pause/play rewires the audio graph.
                    if (shouldResume) {
                        playerScopeMain.launch(Dispatchers.Main.immediate) {
                            val wasPlaying = vlcPlayer?.isPlaying() == true
                            vlcPlayer?.pause()
                            delay(50)
                            if (wasPlaying) vlcPlayer?.play()
                        }
                    }
                }
            }
        }

        routeChangeObserver = center.addObserverForName(
            name = AVAudioSessionRouteChangeNotification,
            `object` = null,
            queue = queue
        ) { _ ->
            try {
                AVAudioSession.sharedInstance().setActive(true, error = null)
            } catch (_: Exception) { }
        }
    }

    private fun removeAudioSessionObservers() {
        val center = NSNotificationCenter.defaultCenter
        interruptionObserver?.let { center.removeObserver(it) }
        routeChangeObserver?.let { center.removeObserver(it) }
        interruptionObserver = null
        routeChangeObserver = null
    }

    /**
     * Checks if any media is currently loaded.
     *
     * @return true if media is loaded, false otherwise
     */
    override suspend fun hasMedia(): Boolean {
        if (!isInitialized) return false
        return withContext(Dispatchers.Main.immediate) { vlcPlayer?.media != null }
    }

    /**
     * Checks if playback is currently active.
     *
     * @return true if playing, false if paused or stopped
     */
    override suspend fun isPlaying(): Boolean {
        if (!isInitialized) return false
        return withContext(Dispatchers.Main.immediate) { vlcPlayer?.isPlaying() == true }
    }

    /**
     * Analyzes and extracts available audio and subtitle tracks from the loaded media.
     *
     * Populates the media file's track lists with VLC's detected tracks. The `index`
     * we record is the position in [VLCMediaPlayer.audioTracks] / [VLCMediaPlayer.textTracks]
     * — that's what [VLCMediaPlayer.selectTrackAtIndex] expects in VLCKit 4.
     *
     * @param mediafile The media file to populate with track information
     */
    override suspend fun analyzeTracks(mediafile: MediaFile) {
        if (!isInitialized) return
        if (vlcPlayer == null) return

        withContext(Dispatchers.Main.immediate) {
            viewmodel.media?.tracks?.clear()

            vlcPlayer!!.audioTracks.forEachIndexed { i, raw ->
                val track = raw as? VLCMediaPlayerTrack ?: return@forEachIndexed
                viewmodel.media?.tracks?.add(
                    VlcKitTrack(
                        name = track.trackName,
                        type = TrackType.AUDIO,
                        index = i,
                        selected = track.isSelected()
                    )
                )
            }

            vlcPlayer!!.textTracks.forEachIndexed { i, raw ->
                val track = raw as? VLCMediaPlayerTrack ?: return@forEachIndexed
                viewmodel.media?.tracks?.add(
                    VlcKitTrack(
                        name = track.trackName,
                        type = TrackType.SUBTITLE,
                        index = i,
                        selected = track.isSelected()
                    )
                )
            }
        }
    }

    /**
     * Selects a specific audio or subtitle track for playback.
     *
     * Pass null or negative index to disable that track type. The index refers to
     * the position in [VLCMediaPlayer.audioTracks] / [VLCMediaPlayer.textTracks].
     *
     * @param track The track to select, or null to disable
     * @param type Whether this is an audio or subtitle track
     */
    override suspend fun selectTrack(track: Track?, type: TrackType) {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            when (type) {
                TrackType.SUBTITLE -> {
                    val index = track?.index ?: -1
                    if (index >= 0) {
                        vlcPlayer?.selectTrackAtIndex(index.toLong(), VLCMediaTrackTypeText)
                    } else {
                        vlcPlayer?.deselectAllTextTracks()
                    }
                }

                TrackType.AUDIO -> {
                    val index = track?.index ?: -1
                    if (index >= 0) {
                        vlcPlayer?.selectTrackAtIndex(index.toLong(), VLCMediaTrackTypeAudio)
                    } else {
                        vlcPlayer?.deselectAllAudioTracks()
                    }
                }
            }
        }
    }

    /**
     * Analyzes and extracts chapter information from the loaded media.
     *
     * @param mediafile The media file to populate with chapter information
     */
    override suspend fun analyzeChapters(mediafile: MediaFile) {
        if (!isInitialized || vlcPlayer == null) return

        withContext(Dispatchers.Main) {
            val chapterDescs = vlcPlayer!!.chapterDescriptionsOfTitle(vlcPlayer!!.currentTitleIndex)
            mediafile.chapters.clear()
            delay(500)
            chapterDescs.forEachIndexed { i, desc ->
                val chapter = desc as? VLCMediaPlayerChapterDescription ?: return@forEachIndexed

                val timeOffset = chapter.timeOffset.value()?.longValue ?: 0L
                val name = chapter.name ?: ""

                mediafile.chapters.add(
                    Chapter(
                        index = i,
                        name = name,
                        timeOffsetMillis = timeOffset
                    )
                )
            }
        }
    }

    /**
     * Jumps to a specific chapter in the media.
     *
     * @param chapter The chapter to jump to
     */
    override suspend fun jumpToChapter(chapter: Chapter) {
        if (!isInitialized) return
        super.jumpToChapter(chapter)
        withContext(Dispatchers.Main.immediate) {
            vlcPlayer?.setCurrentChapterIndex(chapter.index)
        }
    }

//    /**
//     * Skips to the next chapter in the media.
//     */
//    override suspend fun skipChapter() {
//        if (!isInitialized) return
//        withContext(Dispatchers.Main.immediate) {
//            vlcPlayer?.nextChapter()
//        }
//    }

    /**
     * Reapplies previously selected track choices.
     *
     * TODO: Implement track choice persistence for iOS VLC player.
     */
    override suspend fun reapplyTrackChoices() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {

        }
        //TODO Not implemented for iOS VLC player
    }

    /**
     * Loads an external subtitle file from a URI.
     *
     * Adds the subtitle as a playback slave with automatic selection.
     *
     * @param uri The file URI or URL of the subtitle
     * @param extension The subtitle file extension (unused by VLC)
     */
    override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            val nsUrl = uri.nsUrl
            nsUrl.startAccessingSecurityScopedResource()
            vlcPlayer?.addPlaybackSlave(
                nsUrl,
                VLCMediaPlaybackSlaveTypeSubtitle,
                true
            )
        }
    }

    override suspend fun injectVideoFileImpl(location: MediaFileLocation.Local) {
        val nsUrl = location.file.nsUrl
        nsUrl.startAccessingSecurityScopedResource()
        vlcMedia = VLCMedia(uRL = nsUrl)
        // VLCKit 4 removed synchronousParse(); kick off an async local parse and
        // let parseMedia() poll length below.
        vlcMedia?.parseWithOptions(VLCMediaParseLocal.toInt())
        vlcPlayer?.setMedia(vlcMedia)
    }

    override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
        val url = location.url
        vlcMedia = NSURL.URLWithString(url)?.let { VLCMedia(uRL = it) }

        val isAdaptiveStream = url.contains(".m3u8", ignoreCase = true)
                || url.contains(".mpd", ignoreCase = true)
                || url.contains("/manifest", ignoreCase = true)

        if (isAdaptiveStream) {
            // HLS/DASH manifests can stall a parse indefinitely — let VLC parse during
            // playback instead and just feed the network-friendly options.
            vlcMedia?.addOption(":network-caching=3000")
            vlcMedia?.addOption(":clock-jitter=0")
            vlcMedia?.addOption(":clock-synchro=0")
        }
        vlcPlayer?.setMedia(vlcMedia)
    }

    override suspend fun parseMedia(media: MediaFile) {
        val lengthMs = vlcMedia?.length?.value()?.longValue ?: 0L
        if (lengthMs > 0) {
            playerManager.timeFullMillis.value = lengthMs
            media.fileDuration = lengthMs / 1000.0
        } else {
            // Parsing is async in VLCKit 4 — and for streams (HLS/DASH) duration may
            // only be known once playback starts. Retry once after a short delay.
            delay(1500)
            val retryMs = vlcMedia?.length?.value()?.longValue ?: 0L
            val dur = if (retryMs > 0) retryMs else Long.MAX_VALUE
            playerManager.timeFullMillis.value = dur
            media.fileDuration = if (dur == Long.MAX_VALUE) 0.0 else dur / 1000.0
        }
        super.parseMedia(media)
    }

    /**
     * Pauses playback on the main thread.
     */
    override suspend fun pause() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            vlcPlayer?.pause()
        }
    }

    /**
     * Starts or resumes playback on the main thread.
     */
    override suspend fun play() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            pausedSeekPosition = null // VLC becomes reliable at updating time, again
            vlcPlayer?.play()
        }
    }

    override suspend fun setSpeed(speed: Double) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            vlcPlayer?.rate = speed.toFloat()
        }
    }

    /**
     * Checks if the current media supports seeking.
     *
     * @return true if seekable, false otherwise (e.g., live streams)
     */
    override suspend fun isSeekable(): Boolean {
        return isInitialized
    }

    /**
     * Seeks to a specific position in the media.
     *
     * @param toPositionMs The target position in milliseconds
     */
    override fun seekTo(toPositionMs: Long) {
        super.seekTo(toPositionMs)
        playerScopeMain.launch(Dispatchers.Main.immediate) {
            // If paused → VLC won't update time, so we manually declare current ms pos
            pausedSeekPosition = if (vlcPlayer?.isPlaying() != true) toPositionMs else null

            vlcPlayer?.setTime(toPositionMs.toVLCTime())
        }
    }

    /**
     * Gets the current playback position.
     *
     * @return Current position in milliseconds
     */
    override fun currentPositionMs(): Long {
        // If paused and we have a manual position → use it
        return (pausedSeekPosition ?: vlcPlayer?.time?.value()?.longValue ?: playerManager.timeCurrentMillis.value)
    }

    /**
     * Cycles through available aspect ratios.
     *
     * Supports multiple aspect ratios: 1:1, 4:3, 16:9, 16:10, 2.21:1, 2.35:1.
     * Cycles to the next ratio on each call.
     *
     * @return The name of the newly applied aspect ratio
     */
    override suspend fun switchAspectRatio(): String {
        if (!isInitialized) return "NO PLAYER FOUND"
        return withContext(Dispatchers.Main.immediate) {
            val currentAspectRatio = vlcPlayer?.videoAspectRatio

            val (width, height) = vlcPlayer?.videoSize?.useContents { width.toInt() to height.toInt() } ?: (0 to 0)

            val aspectRatios = mutableListOf(
                "$width:$height",
                "1:1", "4:3", "16:9", "16:10",
            )

            val currentIndex = if (currentAspectRatio != null) {
                aspectRatios.indexOf(currentAspectRatio)
            } else {
                -1
            }

            val nextIndex = (currentIndex + 1) % aspectRatios.size
            val newAspectRatio = aspectRatios[nextIndex]

            // VLCKit 4 exposes videoAspectRatio as NSString — assignment is direct,
            // no more cstr.ptr round trip.
            vlcPlayer?.videoAspectRatio = newAspectRatio

            return@withContext newAspectRatio
        }
    }

    /**
     * VLCKit 4 exposes [VLCMediaPlayer.currentSubTitleFontScale] as a runtime-mutable
     * property — the legacy --freetype-fontsize launch flag still seeds the baseline,
     * but we can now scale on top of it without a restart.
     *
     * The slider in [SUBTITLE_SIZE] runs roughly 1..30; a 1× font scale at the libvlc
     * default looks right around `newSize == 4` (which is the multiplier seeded into
     * --freetype-fontsize when the library starts), so we anchor to that.
     */
    override suspend fun changeSubtitleSize(newSize: Int) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            vlcPlayer?.currentSubTitleFontScale = (newSize / 4f).coerceAtLeast(0.1f)
        }
    }

    /********** VLC-Specific Helper Methods **********/

    /**
     * Converts a Long timestamp to VLCTime for use with VLCMediaPlayer.
     *
     * @receiver Timestamp in milliseconds
     * @return VLCTime object representing the timestamp
     */
    private fun Long.toVLCTime(): VLCTime {
        return VLCTime(number = NSNumber(long = this))
    }

    /**
     * Delegate for receiving VLC media player state change notifications.
     *
     * VLCKit 4 changes the [mediaPlayerStateChanged] selector from
     * `(NSNotification *)` to `(VLCMediaPlayerState)`, and folds the old `Ended`
     * state into `Stopped` — there's no longer a way to distinguish a natural
     * playback end from an explicit stop. We treat any Stopped transition as
     * "ended"; [destroy] clears the delegate before calling stop() so that
     * teardown doesn't fire a spurious onPlaybackEnded.
     */
    inner class VlcDelegate : NSObject(), VLCMediaPlayerDelegateProtocol {
        override fun mediaPlayerStateChanged(newState: VLCMediaPlayerState) {
            val isPlaying = vlcPlayer?.media != null && vlcPlayer?.isPlaying() == true
            playerManager.isNowPlaying.value = isPlaying
            playerManager.timeCurrentMillis.value = currentPositionMs()

            if (isPlaying) pausedSeekPosition = null

            if (newState == VLCMediaPlayerState.VLCMediaPlayerStateStopped) {
                onPlaybackEnded()
            }
        }

        override fun mediaPlayerTimeChanged(aNotification: NSNotification) {
            // Time ticking = definitely playing
            val isPlaying = vlcPlayer?.isPlaying() == true
            if (playerManager.isNowPlaying.value != isPlaying) {
                playerManager.isNowPlaying.value = isPlaying
            }

            playerManager.timeCurrentMillis.value = currentPositionMs()

            if (isPlaying && pausedSeekPosition != null) pausedSeekPosition = null
        }
    }

    /********** Volume Control **********/

    /**
     * Maximum volume level (0-200 scale).
     */
    private val MAX_VLC_VOLUME = 200

    override fun getMaxVolume() = 200
    override fun getCurrentVolume(): Int = vlcPlayer?.audio?.volume ?: 0
    override fun changeCurrentVolume(v: Int) {
        vlcPlayer?.audio?.volume = v.coerceIn(0, MAX_VLC_VOLUME)
    }
}