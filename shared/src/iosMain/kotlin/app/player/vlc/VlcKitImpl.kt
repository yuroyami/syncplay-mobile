package app.player.vlc

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import app.i18n.Localization
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
import app.protocol.ProtocolManager
import app.room.OSDCategory
import app.room.RoomViewmodel
import app.utils.loggy
import cocoapods.VLCKit.SyncplayVlcCurrentTimeMs
import cocoapods.VLCKit.SyncplayVlcCurrentLengthMs
import cocoapods.VLCKit.SyncplayVlcHasCurrentMedia
import cocoapods.VLCKit.SyncplayVlcInputState
import cocoapods.VLCKit.SyncplayVlcNativeLengthMs
import cocoapods.VLCKit.SyncplayVlcReadInputState
import cocoapods.VLCKit.SyncplayVlcStateMatches
import cocoapods.VLCKit.VLCEventsLegacyConfiguration
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
import cocoapods.VLCKit.VLCMediaTrackTypeVideo
import cocoapods.VLCKit.videoTracks
import cocoapods.VLCKit.deselectAllVideoTracks
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSNumber
import platform.Foundation.NSOrderedSame
import platform.Foundation.NSURL
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class VlcKitImpl(viewmodel: RoomViewmodel): PlayerImpl(viewmodel, VlcKitEngine) {
    /** The VLC library instance, which provides the codecs and media parsing. */
    private var libvlc: VLCLibrary? = null
    internal var vlcPlayer: VLCMediaPlayer? = null
    private var vlcView: UIView? = null
    private var vlcDelegate = VlcDelegate()
    internal var vlcMedia: VLCMedia? = null

    /**
     * The VLCKit 4 drawable. It also implements [VLCPictureInPictureDrawableProtocol], so VLCKit
     * passes it a [VLCPictureInPictureWindowControllingProtocol] that starts and stops PiP.
     * Created in [VideoPlayer] with [vlcView]. Cleared in [destroy], or when its view is released.
     */
    internal var vlcDrawable: VlcDrawable? = null

    override val supportsVideoTrackSelection = true
    override val supportsChapters: Boolean = true

    /**
     * Native Picture-in-Picture, driven through [VlcDrawable]. See [enterPictureInPicture] and
     * [exitPictureInPicture].
     */
    override val supportsPictureInPicture: Boolean = true

    /**
     * The tracker polls the native clock at this rate. VLCKit's time notifications and its
     * interpolated `time` cache can be wrong, so they are not used.
     */
    override val trackerJobInterval: Duration
        get() = 250.milliseconds

    // This engine reports the loaded file itself: from the first positive length (in
    // mediaPlayerLengthChanged, or in parseMedia when the length is already final), or when the
    // opened input first pauses or plays, which covers a live stream with no length. So the base
    // parseMedia must not announce it early. See PlayerImpl.announcesFileLoadViaEvent.
    override val announcesFileLoadViaEvent: Boolean = true

    private val seekGuard = VlcSeekGuard()
    private val seekRequests = VlcSeekRequests(ProtocolManager.AWAITING_ROOM_RESYNC_TIMEOUT_SECONDS * 1_000L)
    private val seekStartup = VlcSeekStartup(ProtocolManager.AWAITING_ROOM_RESYNC_TIMEOUT_SECONDS * 1_000L)
    internal val hasPendingSeek: Boolean get() = seekRequests.hasPending
    internal val isAwaitingNativeSeekInput: Boolean get() = seekStartup.isOpen(clockNowMs())
    internal var seekRevision = 0L
        private set
    internal var lastSeekRequestTargetMs: Long? = null
        private set

    private fun beginSeekAttempt() {
        seekRevision++
        lastSeekRequestTargetMs = null
        seekRequests.reset()
        seekGuard.reset()
    }

    internal fun resetSeekState(startingNewMedia: Boolean = false) {
        beginSeekAttempt()
        seekStartup.reset()
        if (startingNewMedia) seekStartup.begin(clockNowMs())
    }
    private val clockOrigin = TimeSource.Monotonic.markNow()
    private fun clockNowMs(): Long = clockOrigin.elapsedNow().inWholeMilliseconds

    /**
     * The running system recovery job (a repaint or an audio recovery). A delayed recovery must
     * never override a newer command or act on a different media item.
     */
    internal var recoveryJob: Job? = null
    internal var commandRevision = 0L
        private set

    internal fun supersedeRecovery() {
        commandRevision++
        if (recoveryJob != null) primingFirstFrame = false
        recoveryJob?.cancel()
        recoveryJob = null
    }

    /**
     * Debounce job for the libvlc Paused state. VLCKit 4 sends short Paused events during
     * operations that the user did not start (video output rebuilds, some setTime and rate
     * changes). Acting on them at once sends a false pause and unpause to the room (the group of
     * people watching together). So a Paused counts only if it lasts a short delay, and a Playing
     * in between cancels it.
     */
    private var pauseDebounceJob: kotlinx.coroutines.Job? = null

    /**
     * True while media opens with ":start-paused", or while a paused surface is briefly
     * repainted. During that open, VLCKit can report [VLCMediaPlayerStatePlaying] for a moment
     * before it settles on Paused. Setting [PlayerManager.isNowPlaying] to true then would make
     * [ProtocolManager]'s channel-health collector send a false "unpaused" to the room and put
     * everyone out of sync. While this flag is set, the delegate ignores that Playing state.
     * The flag clears when the player settles in Paused, Stopped or Error, or when a deliberate
     * play() arrives. A deliberate pause keeps it set until the Paused event confirms the pause.
     * Only the main thread touches this flag and the seek guard.
     */
    internal var primingFirstFrame = false

    /** Observer tokens for AVAudioSession notifications, kept so [destroy] can remove them. */
    internal var interruptionObserver: NSObjectProtocol? = null
    internal var routeChangeObserver: NSObjectProtocol? = null

    /**
     * Observer for [UIApplicationDidBecomeActiveNotification]. After the app was in the
     * background (this includes locking and unlocking the screen with a video open), VLCKit 4's
     * render layer in the drawable's containerView can be stale. Back in the foreground it then
     * shows a blank (white or black) frame instead of the video. [requestDrawableRecovery] does
     * the recovery, including the forced frame that a paused player needs.
     */
    internal var didBecomeActiveObserver: NSObjectProtocol? = null

    /**
     * Tears down the media player, media object, VLC library, observers and PiP controller.
     */
    override suspend fun destroy() {
        if (!isInitialized) return
        // Destroy contract shared with the Android engines (ExoImpl, MpvImpl): flip the guard
        // first so the per-method `isInitialized` checks return early, then cancel the
        // supervisor so the 250 ms position tracker stops. Without the cancel, the tracker job
        // outlives the room, keeps polling a torn-down player and holds the whole RoomViewmodel.
        isInitialized = false
        playerSupervisorJob.cancel()

        withContext(Dispatchers.Main.immediate) {
            supersedeRecovery()
            pauseDebounceJob?.cancel()
            resetSeekState()
            try {
                removeAudioSessionObservers()

                // Clear the PiP state-change handler before stopping PiP. VLCKit can call the
                // "PiP stopped" handler synchronously during the stop, and its recovery code
                // would then rebind a player that is torn down a few lines below.
                vlcDrawable?.onPipStateChanged = null
                // Stop PiP and break the controller and drawable retain cycle. Stopping alone
                // does not release the controller or clear its strong reference to the drawable.
                vlcDrawable?.dispose()
                vlcDrawable = null

                // Clear the delegate before stopping, so the synthetic Stopped state from
                // stop() is not taken as a natural end of playback.
                vlcPlayer?.setDelegate(null)
                // The drawable setter is synchronous and also drains earlier queued play and
                // pause calls. Stop after it, so an old queued play cannot restart the input.
                vlcPlayer?.drawable = null
                vlcPlayer?.stop()
                vlcPlayer = null

                vlcMedia = null
                releaseSubtitleScope()

                libvlc = null
                vlcView = null
            } catch (e: Exception) {
                loggy("Error disposing VLC: ${e.message}")
            }
        }
    }

    /**
     * Asks VLCKit to enter Picture-in-Picture.
     *
     * VLCKit's PiP controller exists only after VLCKit has set up the AVSampleBufferDisplayLayer
     * behind the drawable, usually shortly after the first video frame. A PiP request before that
     * only logs, because `pipController` is still null. The state-change handler set in
     * [VideoPlayer] passes `isStarted` to [RoomUiStateManager.hasEnteredPipMode].
     */
    fun enterPictureInPicture() {
        val controller = vlcDrawable?.pipController ?: run {
            loggy("VLC PiP requested but controller not ready yet.")
            return
        }
        controller.startPictureInPicture()
    }

    /**
     * Leaves Picture-in-Picture if it is active. Does nothing when the controller does not exist
     * yet or PiP is not active. The state-change handler sets
     * [RoomUiStateManager.hasEnteredPipMode] back to false when PiP closes.
     */
    fun exitPictureInPicture() {
        vlcDrawable?.pipController?.stopPictureInPicture()
    }

    override suspend fun configurableSettings() = SettingCategory(
        key = "engine-vlc",
        title = { it.uisettingCategVlc },
        icon = Icons.Filled.SettingsInputComponent
    ) {
        +Pref("vlc_subtitle_delay_ms", 0) {
            title = { it.uisettingSubtitleDelayTitle }
            summary = { it.uisettingSubtitleDelaySummary }
            icon = Icons.Filled.ClosedCaptionOff
            extraConfig = PrefExtraConfig.Slider(minValue = -5000, maxValue = 5000) {
                vlcPlayer?.currentVideoSubTitleDelay = it * 1000L
            }
        }
        +Pref("vlc_audio_delay_ms", 0) {
            title = { it.uisettingAudioDelayTitle }
            summary = { it.uisettingAudioDelaySummary }
            icon = Icons.Filled.SpatialAudio
            extraConfig = PrefExtraConfig.Slider(minValue = -5000, maxValue = 5000) {
                vlcPlayer?.currentAudioPlaybackDelay = it * 1000L
            }
        }
        // Custom libVLC launch flags. They only matter when this engine creates the
        // VLCLibrary, so they live in the engine's own settings category.
        +VLC_CUSTOM_FLAGS
    }

    /**
     * Renders the VLC video view in Compose. The factory creates the UIView, the VLC library and
     * player, and the [VlcDrawable] that carries PiP.
     */
    @Composable
    override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) {
        val viewBindings = remember { mutableMapOf<UIView, Pair<VLCMediaPlayer, VlcDrawable>>() }

        UIKitView(
            modifier = modifier,
            factory = {
                // Black background, not clear. VLCKit's render view does not always cover the
                // whole container (zero size for a moment at first layout, or no media yet),
                // and "clear" would show whatever is behind it (the Compose surface or an older
                // layer state, depending on the device). Black matches AVPlayer's default.
                val view = UIView().also { it.setBackgroundColor(UIColor.blackColor) }
                vlcView = view

                // No --freetype-fontsize flag. VLCKit 4 has a runtime
                // [VLCMediaPlayer.currentSubTitleFontScale] (see [changeSubtitleSize]) that
                // multiplies libvlc's own default base size. A fixed pixel size here would be
                // multiplied by that scale too, which makes the subtitles too large.
                val baseArgs = listOf(
                    // No "-vv": libvlc's verbose logging writes lines for every frame, which is
                    // too much for release builds. The user's custom flags can add it back.
                    "--network-caching=2000",
                    "--adaptive-logic=default",
                    "--http-reconnect",
                )
                // By default, VLCKit 4 delivers event callbacks (state, length, track and media
                // changes) synchronously on libvlc worker threads. The "legacy" configuration
                // delivers every callback asynchronously on the main thread instead. Set it
                // before any VLCMediaPlayer or VLCMedia exists, because each one copies this
                // shared config when it sets up its event handler. Warning: this changes every
                // VLC event delivery path. Test on a real device before you remove the
                // main-thread hops in VlcDelegate.
                VLCLibrary.sharedEventsConfiguration = VLCEventsLegacyConfiguration()

                // Custom flags can override the playback defaults above, but the app's
                // screensaver policy stays the last argument. The app's scene policy owns the
                // idle timer. VLC's UIKit inhibitor clears that same global flag on stop, which
                // can let the screen sleep in the next KitePlayer room. Zero stops VLC from
                // creating the inhibitor.
                val lib = VLCLibrary(baseArgs + app.utils.vlcCustomFlags() + "--disable-screensaver=0")
                libvlc = lib

                val player = VLCMediaPlayer(lib)

                // The drawable is a VlcDrawable wrapper, not the raw UIView. VLCKit 4 detects
                // the VLCPictureInPictureDrawable protocol on this object and sets up PiP. The
                // wrapper forwards addSubview and bounds to the real UIView.
                val drawable = VlcDrawable(view, this@VlcKitImpl).apply {
                    onPipStateChanged = { isStarted ->
                        viewmodel.uiState.hasEnteredPipMode.value = isStarted
                        // When PiP stops (the user closes the PiP window, or the app calls
                        // stopPictureInPicture), VLCKit does not always give the render surface
                        // back to the drawable's containerView. The app then shows an empty
                        // white frame instead of the video. Rebinding the drawable is the same
                        // recovery that the DidBecomeActive observer uses after the background.
                        // It runs here too, because closing PiP does not always send
                        // DidBecomeActive (the user may have stayed in the app, with PiP on top).
                        if (!isStarted) {
                            playerScopeMain.launch { requestDrawableRecovery() }
                        }
                    }
                }
                viewBindings[view] = player to drawable
                vlcDrawable = drawable
                player.drawable = drawable

                vlcPlayer = player

                initialize()
                onPlayerReady()

                view
            },
            update = {
                // Set the drawable again if it was detached. libVLC decides when to create the
                // renderer, so setting a drawable does not promise a new render view.
                val drawable = vlcDrawable
                if (drawable != null && vlcPlayer?.drawable !== drawable) {
                    vlcPlayer?.drawable = drawable
                }
            },
            onRelease = { releasedView ->
                // Release the binding made for this exact view, even if a newer factory call
                // has already installed a newer player and drawable in this engine.
                viewBindings.remove(releasedView)?.let { (player, drawable) ->
                    drawable.dispose()
                    // This view owns this player. Clear its delegate before the drawable detach
                    // (which drains queued commands) and the stop. A newer factory call has its
                    // own player, and old callbacks must not act on that one.
                    player.setDelegate(null)
                    player.drawable = null
                    player.stop()
                    if (vlcDrawable === drawable) {
                        supersedeRecovery()
                        vlcDrawable = null
                    }
                    if (vlcView === releasedView) vlcView = null
                }
            }
        )
    }

    /**
     * Sets up the player's delegate, time update rate and audio session, then starts progress
     * tracking.
     */
    override fun initialize() {
        vlcPlayer!!.setDelegate(vlcDelegate)

        // Keep VLCKit's own time update rate for its own users. Room progress uses the separate
        // native clock poll, so a stalled VLCKit timer cannot freeze the seekbar.
        vlcPlayer!!.timeChangeUpdateInterval = 0.25
        vlcPlayer!!.minimalTimePeriod = 250_000L  // microseconds

        configureAudioSession()
        registerAudioSessionObservers()

        isInitialized = true

        startTrackingProgress()
     }

    override suspend fun hasMedia(): Boolean {
        if (!isInitialized) return false
        return withContext(Dispatchers.Main.immediate) { vlcPlayer?.media != null }
    }

    override suspend fun isPlaying(): Boolean {
        if (!isInitialized) return false
        return withContext(Dispatchers.Main.immediate) { vlcPlayer?.isPlaying() == true }
    }

    /**
     * Reads the audio, subtitle and video tracks of the loaded media into [mediafile]. Each
     * track's `index` is its position in [VLCMediaPlayer.audioTracks],
     * [VLCMediaPlayer.textTracks] or [VLCMediaPlayer.videoTracks], which is what
     * [VLCMediaPlayer.selectTrackAtIndex] expects in VLCKit 4.
     */
    override suspend fun analyzeTracks(mediafile: MediaFile) {
        if (!isInitialized) return
        if (vlcPlayer == null) return

        withContext(Dispatchers.Main.immediate) {
            viewmodel.media?.tracks?.clear()

            listOf(
                TrackType.AUDIO to vlcPlayer!!.audioTracks,
                TrackType.SUBTITLE to vlcPlayer!!.textTracks,
                TrackType.VIDEO to vlcPlayer!!.videoTracks,
            ).forEach { (type, tracks) ->
                tracks.forEachIndexed { i, raw ->
                    val track = raw as? VLCMediaPlayerTrack ?: return@forEachIndexed
                    mediafile.tracks.add(VlcKitTrack(
                        name = track.trackName,
                        type = type,
                        index = i,
                        language = track.language,
                        channelCount = track.audio?.channelsNumber?.toInt()?.takeIf { it > 0 },
                        codec = track.codecName(),
                        selected = track.isSelected(),
                    ))
                }
            }
        }
    }

    /**
     * Selects a video, audio or subtitle track. A null [track] turns that track type off, and so
     * does a negative index for audio and subtitles. The index is the track's position in the
     * matching [VLCMediaPlayer] track list.
     */
    override suspend fun selectTrack(track: Track?, type: TrackType) {
        if (!isInitialized) return

        playerManager.currentTrackChoices.remember(type, track)
        withContext(Dispatchers.Main.immediate) {
            when (type) {
                TrackType.VIDEO -> {
                    if (track != null) vlcPlayer?.selectTrackAtIndex(track.index.toLong(), VLCMediaTrackTypeVideo)
                    else vlcPlayer?.deselectAllVideoTracks()
                }
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

    /** Reads the chapters of the current title into [mediafile]. */
    override suspend fun analyzeChapters(mediafile: MediaFile) {
        if (!isInitialized || vlcPlayer == null) return

        withContext(Dispatchers.Main) {
            val chapterDescs = vlcPlayer!!.chapterDescriptionsOfTitle(vlcPlayer!!.currentTitleIndex)
            mediafile.chapters.clear()
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

    /** Jumps to [chapter], with VLC's own chapter offset as the room's seek target. */
    override suspend fun jumpToChapter(chapter: Chapter) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            beginSeekAttempt()
            val player = vlcPlayer ?: return@withContext
            if (player.media == null || !player.isSeekable()) return@withContext
            val descriptions = player.chapterDescriptionsOfTitle(player.currentTitleIndex)
            val description = descriptions.getOrNull(chapter.index) as? VLCMediaPlayerChapterDescription
                ?: return@withContext
            val nativeOffset = description.timeOffset.value()?.longValue ?: return@withContext
            if (nativeOffset < 0L) return@withContext
            val target = normalizeVlcSeekTarget(nativeOffset, SyncplayVlcNativeLengthMs(player))
            // Announce only after the checks above. A stale or invalid chapter must not announce
            // a room seek that VLC ignores.
            super.jumpToChapter(chapter.copy(timeOffsetMillis = target))
            lastSeekRequestTargetMs = target
            seekGuard.seek(target, clockNowMs(), player.isPlaying() && !primingFirstFrame)
            playerManager.samplePosition(target)
            player.setCurrentChapterIndex(chapter.index)
        }
    }

    /** Gives the remembered track choices back to the engine after a reload. */
    override suspend fun reapplyTrackChoices() {
        if (!isInitialized) return
        reapplyIndexedTrackChoices()
    }

    /**
     * Adds an external subtitle file as a playback slave (an extra input that libVLC plays with
     * the media) and selects it.
     *
     * @param uri The subtitle file.
     * @param extension The subtitle file extension (VLC does not use it).
     */
    override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            val nsUrl = uri.nsUrl
            // libvlc reads the slave for the whole playback, so its security scope stays open
            // until the next subtitle replaces it or the player is torn down.
            releaseSubtitleScope()
            if (nsUrl.startAccessingSecurityScopedResource()) subtitleScopedUrl = nsUrl
            vlcPlayer?.addPlaybackSlave(
                nsUrl,
                VLCMediaPlaybackSlaveTypeSubtitle,
                true
            )
        }
    }

    /** The external subtitle whose security scope is currently held open. */
    private var subtitleScopedUrl: NSURL? = null

    private fun releaseSubtitleScope() {
        subtitleScopedUrl?.stopAccessingSecurityScopedResource()
        subtitleScopedUrl = null
    }

    override suspend fun injectVideoFileImpl(location: MediaFileLocation.Local) {
        supersedeRecovery()
        pauseDebounceJob?.cancel()
        resetSeekState(startingNewMedia = true)
        // PlayerImpl holds the file's security scope for the whole playback.
        val nsUrl = location.file.nsUrl
        vlcMedia = VLCMedia(uRL = nsUrl)
        // VLCKit 4 has no synchronousParse(), so start an asynchronous local parse. parseMedia()
        // reads the length once, and mediaPlayerLengthChanged delivers the final value.
        vlcMedia?.parseWithOptions(VLCMediaParseLocal.toInt())
        // Show the first frame as soon as the file is injected, not a black surface. VLCKit
        // only starts its video output (and decodes a frame) once playback starts, and the load
        // flow never plays on its own. ":start-paused" makes libvlc open the input, decode and
        // show the first frame, then pause itself. The first frame is then visible while the
        // room is still paused, and the later play() and seekTo() from sync resume from there.
        vlcMedia?.addOption(":start-paused")
        // setMedia runs at once, but earlier play and pause calls are queued. The public
        // drawable setter drains that queue before the media changes. Otherwise an old play
        // could start this input before its own priming play, and unpause it.
        vlcPlayer?.let { it.drawable = it.drawable }
        vlcPlayer?.setMedia(vlcMedia)
        primingFirstFrame = true
        vlcPlayer?.play()
    }

    override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
        val nativeUrl = requireNotNull(NSURL.URLWithString(location.url)) { "Invalid video URL" }
        supersedeRecovery()
        pauseDebounceJob?.cancel()
        resetSeekState(startingNewMedia = true)
        val url = location.url
        vlcMedia = VLCMedia(uRL = nativeUrl)

        val isAdaptiveStream = url.contains(".m3u8", ignoreCase = true)
                || url.contains(".mpd", ignoreCase = true)
                || url.contains("/manifest", ignoreCase = true)

        if (isAdaptiveStream) {
            // URLs get no explicit parse: HLS and DASH manifests can stall a parse for a long
            // time, so VLC parses during playback. Adaptive streams also get these network options.
            vlcMedia?.addOption(":network-caching=3000")
            vlcMedia?.addOption(":clock-jitter=0")
            vlcMedia?.addOption(":clock-synchro=0")
        }
        // Open paused, so the first frame shows at once instead of a black surface while the
        // room is paused. See injectVideoFileImpl for the full reason.
        vlcMedia?.addOption(":start-paused")
        // Replace the media only after earlier queued playback commands. See the local-file path.
        vlcPlayer?.let { it.drawable = it.drawable }
        vlcPlayer?.setMedia(vlcMedia)
        primingFirstFrame = true
        vlcPlayer?.play()
    }

    override suspend fun parseMedia(media: MediaFile) {
        // A best-effort first read. The final duration arrives through mediaPlayerLengthChanged,
        // which also announces the file to the room, so this read needs no retry.
        val lengthMs = vlcMedia?.length?.value()?.longValue ?: 0L
        super.parseMedia(media)
        // If the length was already final at parse time, LengthChanged may not fire again, so
        // announce now. The same path offers saved progress when watching alone.
        publishDuration(media, lengthMs)
    }

    /** The first positive length announces the file. A later one only updates the room's copy. */
    private fun publishDuration(media: MediaFile, lengthMs: Long) {
        if (lengthMs <= 0L || viewmodel.media !== media) return
        onEngineFileReady(lengthMs)
    }

    /** The input opened: announce it once, with its length when VLCKit knows one. */
    private fun announceOpenedInput(player: VLCMediaPlayer) {
        if (viewmodel.media == null) return
        onEngineFileReady(SyncplayVlcCurrentLengthMs(player))
    }

    /**
     * Pauses playback on the main thread.
     *
     * VLCKit queues both pause and play on its serial command queue. Pause sets the paused state
     * and does not toggle it, so a repeated pause is harmless. Keep the null-media guards: in the
     * VLCKit release in use, the cookie-jar patch dereferences the current media descriptor in
     * native play before it checks it.
     */
    override suspend fun pause() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            val player = vlcPlayer ?: return@withContext
            if (player.media == null) return@withContext
            val suppressTemporaryPlaying = primingFirstFrame
            supersedeRecovery()
            // A queued Playing from the first-frame or repaint work can still arrive. Keep it
            // hidden until Paused confirms this command, because clearing the flag now could
            // unpause the room.
            primingFirstFrame = suppressTemporaryPlaying
            player.pause()
        }
    }

    /**
     * Starts or resumes playback on the main thread. See [pause] for why the null-media guard
     * stays.
     */
    override suspend fun play() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            val player = vlcPlayer ?: return@withContext
            if (player.media == null) return@withContext
            supersedeRecovery()
            // A real play ends the first-frame priming window, so the next Playing state shows
            // normally instead of being ignored. The seek guard is not reset here: it releases
            // itself in [readPositionSample] once the clock settles or its time limit passes.
            // Resetting it here would show the short stale-clock window after a seek-then-play
            // as a false backward jump.
            primingFirstFrame = false
            player.play()
            // The repaint may already have started native playback while its Playing event was
            // hidden. A play on a playing player sends no second event, so correct that case now.
            if (player.isPlaying()) playerManager.isNowPlaying.value = true
        }
    }

    override suspend fun setSpeed(speed: Double) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            vlcPlayer?.rate = speed.toFloat()
        }
    }

    /** False for media that cannot seek, such as live streams. */
    override suspend fun isSeekable(): Boolean {
        if (!isInitialized) return false
        return withContext(Dispatchers.Main.immediate) { vlcPlayer?.isSeekable() == true }
    }

    override suspend fun prepareSeekTarget(targetMs: Long): Long? = withContext(Dispatchers.Main.immediate) {
        if (!isInitialized) return@withContext null
        val player = vlcPlayer ?: return@withContext null
        if (player.media == null) return@withContext null
        val (readiness, nativeLength) = nativeSeekReadiness(player)
        if (readiness == VlcSeekReadiness.UNAVAILABLE) null
        else normalizeVlcSeekTarget(targetMs, nativeLength)
    }

    /**
     * Records a seek to [toPositionMs] and submits it once the native input can take it. While
     * the input is still opening, the progress tracker submits it later.
     */
    override fun seekTo(toPositionMs: Long) {
        if (!isInitialized) return
        playerScopeMain.launch(Dispatchers.Main.immediate) {
            beginSeekAttempt()
            val player = vlcPlayer ?: return@launch
            if (player.media == null) return@launch
            seekRequests.request(toPositionMs, clockNowMs())
            submitPendingSeek(player)
        }
    }

    /**
     * Submits a pending seek when the native input is ready. Returns true while the seek still
     * waits for startup. The caller must not treat that wait as a fresh position sample.
     */
    private fun submitPendingSeek(player: VLCMediaPlayer): Boolean {
        if (!hasPendingSeek && !isAwaitingNativeSeekInput) return false
        val (readiness, nativeLength) = nativeSeekReadiness(player)
        when (val decision = seekRequests.poll(readiness, nativeLength, clockNowMs())) {
            is VlcSeekDecision.Wait -> {
                lastSeekRequestTargetMs = decision.targetMs
                return true
            }
            is VlcSeekDecision.Submit -> {
                val target = decision.targetMs
                lastSeekRequestTargetMs = target
                // The input can take the command now. Submitting it does not prove the new
                // position is decoded, so the seek guard covers the asynchronous clock change
                // after the submission, for a limited time.
                super.seekTo(target)
                seekGuard.seek(target, clockNowMs(), player.isPlaying() && !primingFirstFrame)
                player.setTime(target.toVLCTime())
            }
            VlcSeekDecision.Rejected -> {
                lastSeekRequestTargetMs = null
                loggy("VLC seek discarded: input unavailable or startup wait expired.")
            }
            VlcSeekDecision.None -> Unit
        }
        return false
    }

    private fun nativeSeekReadiness(player: VLCMediaPlayer): Pair<VlcSeekReadiness, Long> {
        val state = SyncplayVlcReadInputState(player)
        val inputState = when (state) {
            SyncplayVlcInputState.SyncplayVlcInputStateReady -> VlcSeekInputState.SEEKABLE
            SyncplayVlcInputState.SyncplayVlcInputStateOpening -> VlcSeekInputState.OPENING
            SyncplayVlcInputState.SyncplayVlcInputStateUnseekable -> VlcSeekInputState.UNSEEKABLE
            SyncplayVlcInputState.SyncplayVlcInputStateFailed -> VlcSeekInputState.FAILED
            else -> VlcSeekInputState.INACTIVE
        }
        val nativeLength = SyncplayVlcNativeLengthMs(player)
        val readiness = seekStartup.readiness(inputState, nativeLength, SyncplayVlcCurrentTimeMs(player), clockNowMs())
        return readiness to nativeLength
    }

    /**
     * Reads the native position through the seek guard, on the main thread. Returns null when
     * there is no sample, and a missing sample must not be stored again as fresh progress.
     */
    private fun readPositionSample(): Long? {
        if (!isInitialized) return null
        val player = vlcPlayer ?: return null
        if (player.media == null) return null
        if (hasPendingSeek) return null
        val nativeMs = SyncplayVlcCurrentTimeMs(player)
        // A negative native time means no input or a terminal state. Return null then, so the
        // last good sample stays for end-of-file handling. A pending seek target must not turn
        // this missing clock into a fresh progress sample.
        val position = seekGuard.sample(nativeMs, clockNowMs(), player.isPlaying() && !primingFirstFrame)
        return position.takeIf { nativeMs >= 0L }
    }

    override fun currentPositionMs(): Long =
        readPositionSample() ?: playerManager.timeCurrentMillis.value

    override suspend fun updatePlaybackProgress() {
        if (!isInitialized) return
        val player = vlcPlayer ?: return
        if (player.media == null) return
        // Submit pending seeks from this tracker, never from a synchronous clock getter.
        if (submitPendingSeek(player)) return
        // Sample non-seekable media too. Seeking and a working clock are separate things.
        readPositionSample()?.let(playerManager::samplePosition)
    }

    /**
     * Cycles through the aspect ratios: the video's own size, 1:1, 4:3, 16:9 and 16:10. Returns
     * the localized name of the new ratio.
     */
    override suspend fun switchAspectRatio(): String {
        if (!isInitialized) return ""
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

            // VLCKit 4 exposes videoAspectRatio as an NSString, so a plain assignment works.
            vlcPlayer?.videoAspectRatio = newAspectRatio

            return@withContext if (nextIndex == 0) Localization.strings.roomAspectOriginal
            else Localization.strings.roomAspectRatioLabel(newAspectRatio)
        }
    }

    /**
     * Sets the subtitle size through [VLCMediaPlayer.currentSubTitleFontScale], a runtime
     * multiplier on libvlc's own default font size. The preference default ([SUBTITLE_SIZE], 16)
     * maps to a scale of 1.0, so a new user sees libvlc's default size. Moving the slider scales
     * the size linearly from there.
     *
     * `coerceAtLeast(0.1f)` keeps the scale above zero. The slider minimum (2) already gives
     * 0.125, so this floor only catches values below the slider range.
     */
    override suspend fun changeSubtitleSize(newSize: Int) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            vlcPlayer?.currentSubTitleFontScale = (newSize / SUBTITLE_SIZE_DEFAULT.toFloat())
                .coerceAtLeast(0.1f)
        }
    }

    private companion object {
        /**
         * Copies the default of [Preferences.SUBTITLE_SIZE]. It anchors the linear scale in
         * [changeSubtitleSize]: at this value, libvlc's default base size shows unscaled (1.0×).
         */
        const val SUBTITLE_SIZE_DEFAULT = 16

    }

    /********** VLC-Specific Helper Methods **********/

    /** Wraps this millisecond timestamp in a [VLCTime]. */
    private fun Long.toVLCTime(): VLCTime {
        return VLCTime(number = NSNumber(long = this))
    }

    /**
     * Receives VLCMediaPlayer events.
     *
     * State and length events arrive through the legacy configuration: asynchronously on the main
     * thread, outside the native callback lock stack. This is required, because the default
     * synchronous delivery can re-enter libVLC while it holds its timer lock. Time notifications
     * do not publish room progress on purpose; [updatePlaybackProgress] reads the native clock on
     * the main thread instead.
     */
    inner class VlcDelegate : NSObject(), VLCMediaPlayerDelegateProtocol {
        override fun mediaPlayerStateChanged(newState: VLCMediaPlayerState) {
            if (!isInitialized) return
            val player = vlcPlayer ?: return
            val media = vlcMedia ?: return
            if (!SyncplayVlcHasCurrentMedia(player, media)) return
            // The legacy configuration queues only the state value, not the media it came from.
            // A queued Paused from old media must not clear the priming of a new start-paused
            // load, which would let a queued old Playing unpause the room. So compare with the
            // live native state: VLCKit's own `state` property was just overwritten by this event.
            when (newState) {
                VLCMediaPlayerState.VLCMediaPlayerStatePlaying,
                VLCMediaPlayerState.VLCMediaPlayerStatePaused,
                VLCMediaPlayerState.VLCMediaPlayerStateStopped,
                VLCMediaPlayerState.VLCMediaPlayerStateError -> {
                    if (!SyncplayVlcStateMatches(player, newState)) return
                }
                else -> Unit
            }
            // The native set-media happens before the input opens. During that short startup
            // window, a Stopped left from the replaced media can still match, and it is not this
            // file's end.
            if (newState == VLCMediaPlayerState.VLCMediaPlayerStateStopped && isAwaitingNativeSeekInput) return
            // isBuffering is display only, and kept apart from isNowPlaying on purpose: the room
            // shows a waiting indicator while VLCKit opens or refills, and still counts that
            // state as playing.
            playerManager.isBuffering.value =
                newState == VLCMediaPlayerState.VLCMediaPlayerStateBuffering ||
                    newState == VLCMediaPlayerState.VLCMediaPlayerStateOpening

            // isNowPlaying follows Playing and Paused, and turns false on Stopped and Error.
            // Opening, Buffering and Stopping leave it alone. Setting it to false during
            // Buffering would flip the room's play and pause button to "play" at every short
            // network stall, and back to "pause" a moment later.
            when (newState) {
                VLCMediaPlayerState.VLCMediaPlayerStatePlaying -> {
                    // Ignore the short Playing sent while new media opens with ":start-paused"
                    // (see [primingFirstFrame]). Otherwise the channel-health collector would
                    // send a false "unpaused" to the room. The Paused that follows clears the
                    // flag. Any Playing, real or short, cancels a pending debounced pause. The
                    // seek guard releases itself in [readPositionSample], not here, so a seek
                    // that does not change the play state still releases it.
                    pauseDebounceJob?.cancel()
                    if (!primingFirstFrame) {
                        playerManager.isNowPlaying.value = true
                        announceOpenedInput(player)
                    }
                }
                VLCMediaPlayerState.VLCMediaPlayerStatePaused -> {
                    primingFirstFrame = false
                    announceOpenedInput(player)
                    // Debounce (see [pauseDebounceJob]): count a Paused only if the player is
                    // still paused a moment later. A deliberate command or a media change also
                    // cancels this delayed check.
                    pauseDebounceJob?.cancel()
                    val revision = commandRevision
                    val player = vlcPlayer
                    val media = player?.media
                    pauseDebounceJob = playerScopeMain.launch(Dispatchers.Main.immediate) {
                        delay(250)
                        if (isInitialized && commandRevision == revision && vlcPlayer === player &&
                            media != null && player?.media?.compare(media) == NSOrderedSame &&
                            SyncplayVlcStateMatches(player, VLCMediaPlayerState.VLCMediaPlayerStatePaused)) {
                            playerManager.isNowPlaying.value = false
                        }
                    }
                }
                VLCMediaPlayerState.VLCMediaPlayerStateStopped -> {
                    supersedeRecovery()
                    resetSeekState()
                    pauseDebounceJob?.cancel()
                    // Playback really stopped (end of file or an explicit stop), so the button
                    // must not stay on "pause".
                    primingFirstFrame = false
                    // VLCKit 4 has no separate "Ended" state: Stopped fires at a real end of file,
                    // and also on an error, a teardown or a manual stop. Treat it as the natural
                    // end only when playback was near the end, because the natural end moves the
                    // shared playlist (the file list that everyone in a room follows) on for the
                    // whole room. Use the last tracked position and duration: during a Stopped
                    // transition vlcPlayer.time is being torn down, so the last good seekbar
                    // value is the reliable one.
                    val endPos = playerManager.timeCurrentMillis.value
                    val endDur = playerManager.timeFullMillis.value
                    val atEnd = endDur > 0L && endPos >= endDur - 1500L
                    // Any stop that is not the end of the file is this client's own stop. It
                    // stays local and is never sent to the room as a pause.
                    if (!atEnd) viewmodel.protocol.noteExpectedPlaybackState(paused = true)
                    playerManager.isNowPlaying.value = false
                    if (atEnd) onPlaybackEnded()
                }
                VLCMediaPlayerState.VLCMediaPlayerStateError -> {
                    supersedeRecovery()
                    resetSeekState()
                    pauseDebounceJob?.cancel()
                    primingFirstFrame = false
                    viewmodel.protocol.noteExpectedPlaybackState(paused = true)
                    playerManager.isNowPlaying.value = false
                    val reason = vlcPlayer?.media?.url?.lastPathComponent ?: ""
                    viewmodel.dispatchOSD(OSDCategory.WARNING) { Localization.strings.roomPlaybackError(reason) }
                    viewmodel.dispatcher.broadcastMessage(isChat = false, isError = true) {
                        Localization.strings.roomPlaybackError(reason)
                    }
                    onEngineLoadFailed()
                }
                else -> { /* Opening, Buffering, Stopping: leave isNowPlaying alone */ }
            }

            // Schedule the PiP refresh after this state update. The legacy event delivery
            // already keeps this code off the native callback stack and its timer lock.
            playerScopeMain.launch { vlcDrawable?.pipController?.invalidatePlaybackState() }
        }

        /**
         * Reads the native duration again when VLCKit reports a length change. The legacy
         * configuration queues only the number, so the payload can belong to media that was
         * already replaced. Publishes the current input's duration when it is positive, and
         * announces the file once per loaded file (which offers saved progress in solo playback).
         */
        override fun mediaPlayerLengthChanged(length: Long) {
            if (!isInitialized) return
            val player = vlcPlayer ?: return
            val media = vlcMedia ?: return
            if (!SyncplayVlcHasCurrentMedia(player, media)) return
            val nativeLength = SyncplayVlcCurrentLengthMs(player)
            viewmodel.media?.let { publishDuration(it, nativeLength) }
            playerScopeMain.launch { vlcDrawable?.pipController?.invalidatePlaybackState() }
        }
    }

    /********** Volume Control **********/

    /** The top of libVLC's volume scale, which runs from 0 to 200. */
    private val MAX_VLC_VOLUME = 200

    /* libVLC's volume runs 0 to 200: the first hundred is output, the second is amplification. */
    override fun getEngineVolume(): Int = (vlcPlayer?.audio?.volume ?: 0).coerceIn(0, 100)
    override fun setEngineVolume(percent: Int) {
        vlcPlayer?.audio?.volume = percent.coerceIn(0, 100)
    }

    override val gainMax: Int = MAX_VLC_VOLUME
    override fun getGain(): Int = (vlcPlayer?.audio?.volume ?: 100).coerceIn(100, MAX_VLC_VOLUME)
    override fun setGain(percent: Int) {
        vlcPlayer?.audio?.volume = percent.coerceIn(100, MAX_VLC_VOLUME)
    }
}
