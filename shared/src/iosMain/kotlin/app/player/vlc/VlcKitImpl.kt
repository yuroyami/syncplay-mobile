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
    /** VLC library instance providing codec and media parsing capabilities. */
    private var libvlc: VLCLibrary? = null
    internal var vlcPlayer: VLCMediaPlayer? = null
    private var vlcView: UIView? = null
    private var vlcDelegate = VlcDelegate()
    internal var vlcMedia: VLCMedia? = null

    /**
     * VLCKit 4 drawable bridge — also implements [VLCPictureInPictureDrawableProtocol] so
     * VLCKit hands us a [VLCPictureInPictureWindowControllingProtocol] we can use to start/
     * stop PiP. Created in [VideoPlayer] alongside [vlcView]; cleared in [destroy].
     */
    internal var vlcDrawable: VlcDrawable? = null

    override val supportsVideoTrackSelection = true
    override val supportsChapters: Boolean = true

    /**
     * Native Picture-in-Picture, driven through [VlcDrawable]. See [enterPictureInPicture] /
     * [exitPictureInPicture].
     */
    override val supportsPictureInPicture: Boolean = true

    /** Read the native clock independently of VLCKit's fallible notification/interpolation cache. */
    override val trackerJobInterval: Duration
        get() = 250.milliseconds

    // Announce the loaded file from mediaPlayerLengthChanged once the real duration is known
    // (mirrors Android's LengthChanged handler), so the base parseMedia must NOT announce it early
    // with a possibly-zero duration. See PlayerImpl.announcesFileLoadViaEvent.
    override val announcesFileLoadViaEvent: Boolean = true

    /** The app's media identity, unlike VLCKit's replaceable Objective-C media wrapper. */
    private var announcedDurationMedia: MediaFile? = null

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

    /** Delayed system recovery must never overwrite a newer command or a different media item. */
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
     * Debounce job for the libvlc "Paused" state. VLCKit 4 emits transient Paused events
     * during non-user operations (vout rebuilds, some setTime/rate changes); honoring them
     * immediately broadcasts a phantom pause+unpause to the room. We only commit a Paused
     * after it survives a short delay, cancelled by an intervening Playing.
     */
    private var pauseDebounceJob: kotlinx.coroutines.Job? = null

    /**
     * True while opening media with ":start-paused" or briefly repainting a paused surface.
     * VLCKit
     * can momentarily report [VLCMediaPlayerStatePlaying] before settling on Paused during
     * that open; surfacing it as [PlayerManager.isNowPlaying] = true would make
     * [ProtocolManager]'s channel-health collector broadcast a spurious "unpaused" to the
     * room and desync everyone. While this flag is set, the delegate swallows that Playing
     * state. Cleared once the player settles into Paused/Stopped or any deliberate
     * play() arrives. A deliberate pause preserves suppression until its acknowledgment.
     * This state and the seek guard are confined to Main.
     */
    internal var primingFirstFrame = false

    /** Observer tokens for AVAudioSession notifications. Kept so we can unregister on destroy. */
    internal var interruptionObserver: NSObjectProtocol? = null
    internal var routeChangeObserver: NSObjectProtocol? = null

    /**
     * Observer for [UIApplicationDidBecomeActiveNotification]. After the app has been
     * backgrounded — including the screen being locked/unlocked while a video is open —
     * VLCKit 4's render layer attached to our drawable's containerView is left in a stale
     * state — coming back to foreground shows a blank (white/black) frame instead of
     * resuming video. Recovery is handled by [requestDrawableRecovery]. See its doc for
     * why a paused player additionally needs a forced frame.
     */
    internal var didBecomeActiveObserver: NSObjectProtocol? = null

    /**
     * Tears down the media player, media object, VLC library, observers and PiP controller.
     */
    override suspend fun destroy() {
        if (!isInitialized) return
        // Destroy contract shared with the Android engines (ExoImpl/MpvImpl): flip the
        // guard first so per-method `isInitialized` checks bail, then cancel the supervisor so
        // the 250ms position tracker stops. Without the cancel the tracker job outlives the
        // room, keeps polling a torn-down player, and retains the whole RoomViewmodel graph.
        isInitialized = false
        playerSupervisorJob.cancel()

        withContext(Dispatchers.Main.immediate) {
            supersedeRecovery()
            pauseDebounceJob?.cancel()
            resetSeekState()
            try {
                removeAudioSessionObservers()

                // Silence the PiP state-change handler BEFORE stopping PiP — otherwise
                // VLCKit may fire the "PiP stopped" callback synchronously during the stop,
                // and our recovery code in there would try to rebind a player that's
                // about to be torn down a few lines below.
                vlcDrawable?.onPipStateChanged = null
                // Stop PiP and break the controller/drawable retain cycle. stop alone does
                // not release the controller or clear its strong reference to our drawable.
                vlcDrawable?.dispose()
                vlcDrawable = null

                // Clear the delegate before stopping so the synthetic Stopped state
                // emitted by stop() doesn't get treated as natural playback end.
                vlcPlayer?.setDelegate(null)
                // Drawable's synchronous setter also drains earlier queued play/pause
                // calls. Stop afterward so an old queued play cannot restart the input.
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
     * Requests entry into Picture-in-Picture mode.
     *
     * VLCKit's PiP controller is only available *after* the framework has finished setting
     * up the AVSampleBufferDisplayLayer behind our drawable — this typically happens shortly
     * after the first video frame is rendered. If the user taps PiP before that, we silently
     * no-op (`pipController == null`). The state-change handler wired up in [VideoPlayer]
     * pushes the `isStarted` signal into [RoomUiStateManager.hasEnteredPipMode].
     */
    fun enterPictureInPicture() {
        val controller = vlcDrawable?.pipController ?: run {
            loggy("VLC PiP requested but controller not ready yet.")
            return
        }
        controller.startPictureInPicture()
    }

    /**
     * Exits Picture-in-Picture mode if currently active. No-op when the controller hasn't
     * been provisioned or when not in PiP. The state-change handler will flip
     * [RoomUiStateManager.hasEnteredPipMode] back to false on dismissal.
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
        val viewBindings = remember { mutableMapOf<UIView, Pair<VLCMediaPlayer, VlcDrawable>>() }

        UIKitView(
            modifier = modifier,
            factory = {
                // Black background, not clear: VLCKit's render view doesn't always cover the
                // full container (transient zero-size at first layout, or when the player has
                // no media yet), and "clear" would leak whatever is behind it (Compose surface
                // or prior layer state, device-dependent). Black matches AVPlayer's default.
                val view = UIView().also { it.setBackgroundColor(UIColor.blackColor) }
                vlcView = view

                // No --freetype-fontsize flag: VLCKit 4 exposes a runtime
                // [VLCMediaPlayer.currentSubTitleFontScale] (see [changeSubtitleSize]) that
                // multiplies on top of libvlc's internal default base size. Seeding a hardcoded
                // pixel size here would be multiplied by that scale, producing oversized subs.
                val baseArgs = listOf(
                    // No "-vv": libvlc verbose logging is frame-by-frame spam that shipped to every
                    // user in release. The user's custom flags can still re-add it if needed.
                    "--network-caching=2000",
                    "--adaptive-logic=default",
                    "--http-reconnect",
                )
                // Custom flags can override the playback defaults above; the app-owned
                // screensaver policy below is the final argument.
                // VLCKit 4 delivers event callbacks (state/length/track/media changed)
                // SYNCHRONOUSLY on libvlc worker threads by default. The "legacy" configuration
                // makes every callback arrive asynchronously on the main thread instead. Must be
                // set before any VLCMediaPlayer / VLCMedia is created, because each one snapshots
                // this shared config when it wires up its event handler. WARNING: this changes
                // every VLC event delivery path — verify on a real device before removing the
                // main-thread bounces in VlcDelegate.
                VLCLibrary.sharedEventsConfiguration = VLCEventsLegacyConfiguration()

                // The app's scene policy owns the idle timer. VLC's UIKit inhibitor clears
                // that same global flag on stop, which can let the next KitePlayer room sleep.
                // Zero prevents the inhibitor from being created. Keep this after custom flags.
                val lib = VLCLibrary(baseArgs + app.utils.vlcCustomFlags() + "--disable-screensaver=0")
                libvlc = lib

                val player = VLCMediaPlayer(lib)

                // The drawable is a VlcDrawable wrapper, NOT the raw UIView. VLCKit 4
                // detects the VLCPictureInPictureDrawable protocol on this object and
                // wires up its PiP machinery; the wrapper forwards addSubview/bounds
                // into the actual UIView.
                val drawable = VlcDrawable(view, this@VlcKitImpl).apply {
                    onPipStateChanged = { isStarted ->
                        viewmodel.uiState.hasEnteredPipMode.value = isStarted
                        // When PiP stops (user tapped the X on the floating overlay, or
                        // we called stopPictureInPicture programmatically), VLCKit doesn't
                        // reliably hand the render surface back to our drawable's
                        // containerView — the app shows an empty/white frame instead of
                        // resuming inline playback. Re-binding the drawable is the same
                        // recovery trick the DidBecomeActive observer uses for the
                        // backgrounding case; we apply it here because PiP dismissal
                        // doesn't always trigger DidBecomeActive (the user may have been
                        // in the app the whole time, with PiP just floating over it).
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
                // Reassert output configuration if it was detached. Actual renderer creation
                // belongs to libVLC; assigning a drawable does not promise a new render view.
                val drawable = vlcDrawable
                if (drawable != null && vlcPlayer?.drawable !== drawable) {
                    vlcPlayer?.drawable = drawable
                }
            },
            onRelease = { releasedView ->
                // Release the binding created for this exact view, even if a replacement
                // factory has already installed a newer player and drawable in the adapter.
                viewBindings.remove(releasedView)?.let { (player, drawable) ->
                    drawable.dispose()
                    // This exact view owns this player. Silence its queued delegate work
                    // before detaching/draining and stopping; a newer factory has its own
                    // player, and old callbacks must not be interpreted against that one.
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
     * Initializes the VLC player by setting up the delegate and starting progress tracking.
     */
    override fun initialize() {
        vlcPlayer!!.setDelegate(vlcDelegate)

        // Retain VLCKit's own update cadence for native consumers. Room progress uses the
        // independent native-clock poll, so a stalled wrapper timer cannot freeze the seekbar.
        vlcPlayer!!.timeChangeUpdateInterval = 0.25
        vlcPlayer!!.minimalTimePeriod = 250_000L  // microseconds

        configureAudioSession()
        registerAudioSessionObservers()

        isInitialized = true

        startTrackingProgress()
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
            // A stale/invalid chapter must not announce a room seek that VLC will ignore.
            super.jumpToChapter(chapter.copy(timeOffsetMillis = target))
            lastSeekRequestTargetMs = target
            seekGuard.seek(target, clockNowMs(), player.isPlaying() && !primingFirstFrame)
            playerManager.samplePosition(target)
            player.setCurrentChapterIndex(chapter.index)
        }
    }

    /** Hands the remembered audio and subtitle picks back to the engine after a reload. */
    override suspend fun reapplyTrackChoices() {
        if (!isInitialized) return
        reapplyIndexedTrackChoices()
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
            // libvlc reads the slave for the whole playback, so the scope stays open until the
            // next subtitle replaces it or the player is torn down.
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
        // Security scope is held centrally by PlayerImpl for the playback lifetime.
        val nsUrl = location.file.nsUrl
        vlcMedia = VLCMedia(uRL = nsUrl)
        // VLCKit 4 removed synchronousParse(); kick off an async local parse and
        // let parseMedia() poll length below.
        vlcMedia?.parseWithOptions(VLCMediaParseLocal.toInt())
        // Show the first frame as soon as the file is injected instead of a black surface.
        // VLCKit only spins up its video output (and decodes a frame) once playback starts,
        // and the load flow never auto-plays. ":start-paused" makes libvlc open the input,
        // decode + present frame 1, then immediately pause itself, so the poster frame is
        // visible while the room is still paused; the later sync-driven play()/seekTo()
        // resume from there.
        vlcMedia?.addOption(":start-paused")
        // setMedia is immediate, but earlier play/pause calls are queued. The public
        // drawable setter drains that queue before replacement, preventing an old play
        // from starting this input before its own prime play and thereby unpausing it.
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
            // HLS/DASH manifests can stall a parse indefinitely — let VLC parse during
            // playback instead and just feed the network-friendly options.
            vlcMedia?.addOption(":network-caching=3000")
            vlcMedia?.addOption(":clock-jitter=0")
            vlcMedia?.addOption(":clock-synchro=0")
        }
        // Open paused so the first frame paints immediately instead of a black surface
        // while the room is paused — see injectVideoFileImpl for the full rationale.
        vlcMedia?.addOption(":start-paused")
        // Order replacement after earlier queued playback commands; see the local-file path.
        vlcPlayer?.let { it.drawable = it.drawable }
        vlcPlayer?.setMedia(vlcMedia)
        primingFirstFrame = true
        vlcPlayer?.play()
    }

    override suspend fun parseMedia(media: MediaFile) {
        // Best-effort initial read. The authoritative duration arrives via mediaPlayerLengthChanged
        // (which also fires the room announce), so there's no retry/sentinel game here anymore —
        // mirrors how the Android VLC engine relies on its LengthChanged event.
        val lengthMs = vlcMedia?.length?.value()?.longValue ?: 0L
        super.parseMedia(media)
        // If the length was already final at parse time, LengthChanged may not fire again, so
        // announce now. The same path offers saved progress when watching alone.
        publishDuration(media, lengthMs)
    }

    private fun publishDuration(media: MediaFile, lengthMs: Long) {
        if (lengthMs <= 0L || viewmodel.media !== media) return
        val previousLengthMs = playerManager.timeFullMillis.value
        val firstAnnouncement = announcedDurationMedia !== media
        playerManager.timeFullMillis.value = lengthMs
        media.fileDuration = lengthMs / 1000.0
        // Resolver metadata may already contain the correct duration before native playback
        // opens. It must not suppress the first ready announcement. Later duration refinements
        // may update room metadata, but must not reopen a dismissed solo resume offer.
        if (firstAnnouncement || (!viewmodel.isSoloMode && previousLengthMs != lengthMs)) {
            announcedDurationMedia = media
            announceFileLoaded()
        }
    }

    /**
     * Pauses playback on the main thread.
     *
     * Both pause and play are queued on VLCKit's serial command queue. Pause is an
     * idempotent set-pause, not a toggle. Retain the media guards: this release's cookie-jar
     * patch dereferences the current media descriptor in native play before checking it.
     */
    override suspend fun pause() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            val player = vlcPlayer ?: return@withContext
            if (player.media == null) return@withContext
            val suppressTemporaryPlaying = primingFirstFrame
            supersedeRecovery()
            // A queued Playing from first-frame/repaint work may still arrive. Keep it
            // hidden until Paused acknowledges this command; clearing now could unpause the room.
            primingFirstFrame = suppressTemporaryPlaying
            player.pause()
        }
    }

    /**
     * Starts or resumes playback on the main thread. See [pause] for the [vlcPlayer.media]
     * NULL-guard rationale — same VLCKit 4 alpha crash applies.
     */
    override suspend fun play() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            val player = vlcPlayer ?: return@withContext
            if (player.media == null) return@withContext
            supersedeRecovery()
            // A real play ends the first-frame priming window, so a subsequent Playing
            // state is surfaced normally rather than swallowed. The post-seek shadow is left for
            // [currentPositionMs]'s convergence logic to clear — clearing it here would expose the
            // brief stale-clock window right after a seek-then-play as a phantom backward jump.
            primingFirstFrame = false
            player.play()
            // Repaint may already have started native playback while its Playing event was
            // suppressed. An idempotent play emits no second event, so reconcile that case now.
            if (player.isPlaying()) playerManager.isNowPlaying.value = true
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
     * Seeks to a specific position in the media.
     *
     * @param toPositionMs The target position in milliseconds
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

    /** Returns true while startup intent is waiting; waiting is not a fresh position sample. */
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
                // The input can accept the command now. This is intent, not proof of decoding;
                // the bounded guard covers the asynchronous clock transition after submission.
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

    /** Main-thread native sample. A missing sample must not be re-stamped as fresh progress. */
    private fun readPositionSample(): Long? {
        if (!isInitialized) return null
        val player = vlcPlayer ?: return null
        if (player.media == null) return null
        if (hasPendingSeek) return null
        val nativeMs = SyncplayVlcCurrentTimeMs(player)
        // No native input/terminal state: preserve the last good sample for EOF handling.
        // A pending target must not turn this missing clock into a fresh progress sample.
        val position = seekGuard.sample(nativeMs, clockNowMs(), player.isPlaying() && !primingFirstFrame)
        return position.takeIf { nativeMs >= 0L }
    }

    override fun currentPositionMs(): Long =
        readPositionSample() ?: playerManager.timeCurrentMillis.value

    override suspend fun updatePlaybackProgress() {
        if (!isInitialized) return
        val player = vlcPlayer ?: return
        if (player.media == null) return
        // Native submission belongs to the regular tracker, never a synchronous clock getter.
        if (submitPendingSeek(player)) return
        // Also sample non-seekable media: seek capability does not govern clock availability.
        readPositionSample()?.let(playerManager::samplePosition)
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

            // VLCKit 4 exposes videoAspectRatio as NSString — assignment is direct,
            // no more cstr.ptr round trip.
            vlcPlayer?.videoAspectRatio = newAspectRatio

            return@withContext if (nextIndex == 0) Localization.strings.roomAspectOriginal
            else Localization.strings.roomAspectRatioLabel(newAspectRatio)
        }
    }

    /**
     * VLCKit 4 exposes [VLCMediaPlayer.currentSubTitleFontScale] as a runtime-mutable
     * multiplier on libvlc's native default base font size. We anchor the slider's
     * default value ([SUBTITLE_SIZE]'s default of 16) to a 1.0× scale, so a fresh user
     * sees subs at libvlc's default size — sliding the preference up or down then
     * scales linearly relative to that baseline.
     *
     * `coerceAtLeast(0.1f)` keeps the minimum visible size from collapsing to zero if
     * the slider hits its minimum (2).
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
         * Mirrors [Preferences.SUBTITLE_SIZE]'s default. Used as the anchor for
         * [changeSubtitleSize]'s linear scale: at this value the libvlc default base
         * size is shown unscaled (1.0×).
         */
        const val SUBTITLE_SIZE_DEFAULT = 16

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
     * Delegate for VLCMediaPlayer events.
     *
     * State and length events use Legacy's asynchronous Main delivery, outside the native
     * callback lock stack. This is required: default synchronous delivery can re-enter libVLC
     * under its timer lock. Time notifications deliberately do not publish room progress;
     * [updatePlaybackProgress] reads the independent native clock on Main instead.
     */
    inner class VlcDelegate : NSObject(), VLCMediaPlayerDelegateProtocol {
        override fun mediaPlayerStateChanged(newState: VLCMediaPlayerState) {
            if (!isInitialized) return
            val player = vlcPlayer ?: return
            val media = vlcMedia ?: return
            if (!SyncplayVlcHasCurrentMedia(player, media)) return
            // Legacy queues only the state enum, without the originating media identity.
            // A queued old Paused must not clear a new start-paused load's priming and let
            // a queued old Playing unpause the room. Reconcile with the live native state;
            // the wrapper's state property has just been overwritten by this same event.
            when (newState) {
                VLCMediaPlayerState.VLCMediaPlayerStatePlaying,
                VLCMediaPlayerState.VLCMediaPlayerStatePaused,
                VLCMediaPlayerState.VLCMediaPlayerStateStopped,
                VLCMediaPlayerState.VLCMediaPlayerStateError -> {
                    if (!SyncplayVlcStateMatches(player, newState)) return
                }
                else -> Unit
            }
            // Native set-media precedes input opening. A replacement's old Stopped can
            // still match during that finite startup window; it is not this file's EOF.
            if (newState == VLCMediaPlayerState.VLCMediaPlayerStateStopped && isAwaitingNativeSeekInput) return
            // Only mirror Playing / Paused into [isNowPlaying]. The other VLCKit 4 states
            // (Opening, Buffering, Stopping, Stopped, Error) are intermediate transitions
            // during which the user-visible "is the video running" semantics are unchanged
            // from before the transition — flipping isNowPlaying false during Buffering,
            // for example, would flicker the room's play/pause button to "play" every
            // time playback hiccups for a network buffer dip and snap back to "pause" a
            // moment later. The Android libVLC engine handles this by listening only to
            // MediaPlayer.Event.Playing / Paused; we do the equivalent here.
            //
            // Display only, and separate from isNowPlaying on purpose: the room shows a waiting
            // indicator while VLCKit opens or refills, and still calls that state "playing".
            playerManager.isBuffering.value =
                newState == VLCMediaPlayerState.VLCMediaPlayerStateBuffering ||
                    newState == VLCMediaPlayerState.VLCMediaPlayerStateOpening

            when (newState) {
                VLCMediaPlayerState.VLCMediaPlayerStatePlaying -> {
                    // Swallow the transient Playing emitted while opening freshly injected
                    // media with ":start-paused" (see [primingFirstFrame]) — otherwise the
                    // channel-health collector would broadcast a bogus "unpaused" to the
                    // room. The Paused state that immediately follows clears the flag.
                    // A real (or transient) Playing cancels any pending debounced pause.
                    // The post-seek shadow is cleared by [currentPositionMs]'s convergence logic,
                    // not here, so a seek that doesn't change play-state still clears it.
                    pauseDebounceJob?.cancel()
                    if (!primingFirstFrame) {
                        playerManager.isNowPlaying.value = true
                    }
                }
                VLCMediaPlayerState.VLCMediaPlayerStatePaused -> {
                    primingFirstFrame = false
                    // Debounce: libvlc 4 briefly flips to Paused during non-user operations
                    // (vout rebuilds, some setTime/rate changes). Reporting it immediately makes
                    // the channel-health collector broadcast a phantom pause+unpause to the room.
                    // Only commit a Paused that is still paused a moment later. A deliberate
                    // command or media replacement invalidates this delayed observation too.
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
                    // Real end of playback (natural EOF or explicit stop) — reflect
                    // that in the button so it doesn't stay stuck showing "pause".
                    primingFirstFrame = false
                    // VLCKit 4 has no distinct "Ended" state: Stopped fires on a genuine
                    // end-of-file AND on error/teardown/manual stop. Only treat it as a
                    // natural EOF (which auto-advances the shared playlist for the WHOLE
                    // room) when we were actually near the end. Use the last tracked position/
                    // duration — on a Stopped transition vlcPlayer.time is being torn down and
                    // unreliable, so the last good seekbar value is the trustworthy reference.
                    val endPos = playerManager.timeCurrentMillis.value
                    val endDur = playerManager.timeFullMillis.value
                    val atEnd = endDur > 0L && endPos >= endDur - 1500L
                    // Anything but the end of the file is this client's own stop: local news,
                    // never a pause broadcast to the room.
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
                }
                else -> { /* Opening, Buffering, Stopping — leave isNowPlaying alone */ }
            }

            // Schedule the PiP refresh after this state update. Legacy event delivery already
            // keeps us off the native callback stack and its timer lock.
            playerScopeMain.launch { vlcDrawable?.pipController?.invalidatePlaybackState() }
        }

        /**
         * A duration notification prompts a fresh native read. Legacy queues only the number,
         * so its payload can belong to replaced media. Publish the current input's positive
         * duration and offer saved progress once per loaded file, including solo playback.
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

    /**
     * Maximum volume level (0-200 scale).
     */
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
