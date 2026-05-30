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
import platform.UIKit.UIApplicationDidBecomeActiveNotification
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
    private var vlcDrawable: VlcDrawable? = null

    override val supportsChapters: Boolean = true

    /**
     * VLCKit 4 added native Picture-in-Picture for iOS (see NEWS, "added support for Picture-
     * in-Picture playback on iOS and macOS"). We drive it through [VlcDrawable] — see
     * [enterPictureInPicture] / [exitPictureInPicture].
     */
    override val supportsPictureInPicture: Boolean = true

    /**
     * VLCKit 4 rewrote its event management: delegate callbacks now come from libvlc
     * worker threads (the timer thread holds `player->timer.lock` for `mediaPlayerTimeChanged`),
     * and `mediaPlayerTimeChanged` fires only once per second by default
     * (`timeChangeUpdateInterval` = 1.0s). We can't safely read `vlcPlayer.time` from inside
     * the callback (libvlc's `vlc_player_Lock` asserts `!vlc_mutex_held(timer.lock)`), and
     * even if we could, 1 Hz updates make the seekbar look stuck.
     *
     * So we run our own 250 ms tracker job on the main thread (just like all the other
     * engines) and ignore VLCKit's time-change notifications for position reads. This
     * mirrors what MobileVLCKit 3 was doing implicitly via its NSNotificationCenter-backed
     * 4 Hz delegate.
     */
    override val trackerJobInterval: Duration
        get() = 250.milliseconds

    private var pausedSeekPosition: Long? = null

    /**
     * Debounce job for the libvlc "Paused" state. VLCKit 4 emits transient Paused events
     * during non-user operations (vout rebuilds, some setTime/rate changes); honoring them
     * immediately broadcasts a phantom pause+unpause to the room. We only commit a Paused
     * after it survives a short delay, cancelled by an intervening Playing.
     */
    private var pauseDebounceJob: kotlinx.coroutines.Job? = null

    /**
     * True only during the brief window where we open freshly injected media with
     * ":start-paused" purely to paint its first frame (see [injectVideoFileImpl]). VLCKit
     * can momentarily report [VLCMediaPlayerStatePlaying] before settling on Paused during
     * that open; surfacing it as [PlayerManager.isNowPlaying] = true would make
     * [ProtocolManager]'s channel-health collector broadcast a spurious "unpaused" to the
     * room and desync everyone. While this flag is set, the delegate swallows that Playing
     * state. Cleared once the player settles into Paused/Stopped or any deliberate
     * play()/pause() arrives. Plain var (not atomic) to match [pausedSeekPosition]'s
     * existing main-thread-write / libvlc-thread-read pattern.
     */
    private var primingFirstFrame = false

    /** Observer tokens for AVAudioSession notifications. Kept so we can unregister on destroy. */
    private var interruptionObserver: NSObjectProtocol? = null
    private var routeChangeObserver: NSObjectProtocol? = null

    /**
     * Observer for [UIApplicationDidBecomeActiveNotification]. After the app has been
     * backgrounded — including the screen being locked/unlocked while a video is open —
     * VLCKit 4's render layer attached to our drawable's containerView is left in a stale
     * state — coming back to foreground shows a blank (white/black) frame instead of
     * resuming video. Recovery is handled by [rebindDrawableAndRepaint]. See its doc for
     * why a paused player additionally needs a forced frame.
     */
    private var didBecomeActiveObserver: NSObjectProtocol? = null

    /**
     * Cleans up VLC resources and stops playback.
     *
     * Properly disposes of the media player, media object, and VLC library.
     */
    override suspend fun destroy() {
        if (!isInitialized) return

        try {
            removeAudioSessionObservers()

            // Silence the PiP state-change handler BEFORE stopping PiP — otherwise
            // VLCKit may fire the "PiP stopped" callback synchronously during the stop,
            // and our recovery code in there would try to rebind a player that's
            // about to be torn down a few lines below.
            vlcDrawable?.onPipStateChanged = null
            // Make sure we don't leave a floating PiP window after teardown — this also
            // releases VLCKit's internal AVPictureInPictureController so the next room can
            // claim the system PiP slot cleanly.
            vlcDrawable?.pipController?.stopPictureInPicture()
            vlcDrawable = null

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
                // Black background, not clear: VLCKit's render view doesn't always
                // cover the full container (transient zero-size at first layout, or
                // when the player has no media yet), and "clear" would leak whatever
                // happens to be behind. Different devices showed different leak
                // colors — iPhone 13 = white (Compose surface), iPhone XS = black
                // (prior layer state) — neither of which we want. Black matches the
                // standard "movie theater" aesthetic and matches what AVPlayer does
                // by default.
                val view = UIView().also { it.setBackgroundColor(UIColor.blackColor) }
                vlcView = view

                // No --freetype-fontsize flag: VLCKit 4 exposes a runtime
                // [VLCMediaPlayer.currentSubTitleFontScale] (see [changeSubtitleSize]) that
                // multiplies on top of libvlc's internal default base size, which already
                // looks right out of the box. Setting --freetype-fontsize here would seed
                // a hardcoded pixel size that the runtime scale then multiplies on top of,
                // producing comically oversized subs. Under MobileVLCKit 3.x we had to
                // bake the size in at launch because no runtime scale property existed;
                // VLCKit 4 makes that workaround obsolete.
                val baseArgs = listOf(
                    "-vv",
                    "--network-caching=2000",
                    "--adaptive-logic=default",
                    "--http-reconnect",
                )
                // User-supplied flags from Preferences.VLC_CUSTOM_FLAGS are appended last, so
                // they can override the defaults above (LibVLC honours the last occurrence).
                val lib = VLCLibrary(baseArgs + app.utils.vlcCustomFlags())
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
                            playerScopeMain.launch { rebindDrawableAndRepaint() }
                        }
                    }
                }
                vlcDrawable = drawable
                player.drawable = drawable

                vlcPlayer = player

                initialize()
                onPlayerReady()

                view
            },
            update = { view ->
                // Re-bind the drawable if Compose recreates the view but keeps the player
                // alive. Setting the drawable to our wrapper re-attaches the underlying
                // UIView via VlcDrawable.addSubview and re-arms PiP.
                val drawable = vlcDrawable
                if (drawable != null && vlcPlayer?.drawable !== drawable) {
                    vlcPlayer?.drawable = drawable
                }
            }
        )
    }

    /**
     * Initializes the VLC player by setting up the delegate and starting progress tracking.
     */
    override fun initialize() {
        vlcPlayer!!.setDelegate(vlcDelegate)

        // Tighten VLCKit 4's notification cadence so the system PiP overlay's scrubber
        // ticks visibly (defaults: timeChangeUpdateInterval = 1.0s, minimalTimePeriod =
        // 500 ms). Our own time tracker (see [trackerJobInterval]) drives the in-room
        // seekbar; this just makes the system overlay match.
        vlcPlayer!!.timeChangeUpdateInterval = 0.25
        vlcPlayer!!.minimalTimePeriod = 250_000L  // microseconds

        configureAudioSession()
        registerAudioSessionObservers()

        isInitialized = true

        // Drive [PlayerManager.timeCurrentMillis] from our own 250 ms main-thread job —
        // we can't read vlcPlayer.time from inside [VlcDelegate.mediaPlayerTimeChanged]
        // because VLCKit 4 fires that callback with libvlc's timer.lock held. See the
        // doc on [trackerJobInterval] for the full story.
        startTrackingProgress()
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

        // After backgrounding (app switch OR a screen lock/unlock with the player open),
        // VLCKit 4's render layer hosted on our drawable's containerView is sometimes left
        // disconnected — the app comes back showing a blank frame instead of resuming
        // video. [rebindDrawableAndRepaint] drops the stale render UIView and forces a
        // fresh one (plus a frame when paused). We listen on DidBecomeActive (rather than
        // WillEnterForeground) so the layout pass has settled and the containerView's
        // bounds are valid by the time we re-bind.
        didBecomeActiveObserver = center.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = queue
        ) { _ ->
            playerScopeMain.launch { rebindDrawableAndRepaint() }
        }
    }

    /**
     * Re-attaches the render surface to [vlcDrawable] and, when paused, decodes a single
     * frame so the freshly-attached render view actually shows the current frame instead
     * of staying blank/black. Shared by the foreground-recovery (DidBecomeActive) and
     * PiP-stop recovery paths. Must run on the main thread (callers wrap in
     * `playerScopeMain.launch`).
     *
     * **Why the extra frame when paused:** setting `drawable` makes VLCKit hand us a brand
     * new (empty) render UIView via [VlcDrawable.addSubview]. VLC only paints into it when
     * it produces a frame. While playing, the next decoded frame fills it within ~1 frame,
     * so the rebind alone is enough. While PAUSED — the usual state when someone locks
     * their phone mid-session — no new frame is ever produced, so the view would stay
     * black until the user seeks/plays. [VLCMediaPlayer.gotoNextFrame] decodes and presents
     * exactly one frame (and leaves the player paused), repainting the recovered surface.
     * The ~1-frame advance (~20-40 ms) is far below the protocol's 1 s seek threshold, so
     * the room can't mistake it for a user seek.
     */
    private suspend fun rebindDrawableAndRepaint() {
        val player = vlcPlayer ?: return
        val drawable = vlcDrawable ?: return
        // Skip if there's no media — nothing to render anyway, and rebinding an empty
        // player can confuse VLCKit's PiP setup.
        if (player.media == null) return
        player.drawable = null
        player.drawable = drawable
        if (!player.isPlaying()) {
            // iOS often tears down libvlc's video output (vout) along with the GPU surface
            // while backgrounded. A single gotoNextFrame() can NOT rebuild a destroyed vout;
            // only a real play() transition stands the decoder->vout pipeline back up. So
            // briefly play to force vout re-creation, wait for playback to repaint, then
            // restore the paused frame. primingFirstFrame swallows the transient Playing so
            // the room never sees a phantom "unpaused" broadcast.
            primingFirstFrame = true
            player.play()
            // Give libvlc time to rebuild the vout via the real play() transition (a single
            // gotoNextFrame can't recreate a vout iOS destroyed while backgrounded). ~400 ms
            // of actual playback reliably stands the decoder->vout pipeline back up; the tiny
            // advance is far under the 1 s seek threshold so the room can't see it as a seek.
            delay(400)
            val p = vlcPlayer
            if (p == null) {
                primingFirstFrame = false
                return
            }
            p.pause()
            if (p.media != null && !p.isPlaying()) p.gotoNextFrame()
        }
    }

    private fun removeAudioSessionObservers() {
        val center = NSNotificationCenter.defaultCenter
        interruptionObserver?.let { center.removeObserver(it) }
        routeChangeObserver?.let { center.removeObserver(it) }
        didBecomeActiveObserver?.let { center.removeObserver(it) }
        interruptionObserver = null
        routeChangeObserver = null
        didBecomeActiveObserver = null
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
        // Security scope is held centrally by PlayerImpl for the playback lifetime.
        val nsUrl = location.file.nsUrl
        vlcMedia = VLCMedia(uRL = nsUrl)
        // VLCKit 4 removed synchronousParse(); kick off an async local parse and
        // let parseMedia() poll length below.
        vlcMedia?.parseWithOptions(VLCMediaParseLocal.toInt())
        // Show the first frame as soon as the file is injected instead of a black surface.
        // VLCKit only spins up its video output (and decodes a frame) once playback starts,
        // and our load flow never auto-plays — so without this a freshly injected file shows
        // nothing until the room/user unpauses. ":start-paused" makes libvlc open the input,
        // decode + present frame 1, then immediately pause itself, so the poster frame is
        // visible while the room is still paused; the later sync-driven play()/seekTo()
        // resume from there. (Verified ":start-paused" is a real option in this VLCKit build,
        // so play() lands paused-on-frame-1 rather than actually starting playback.)
        vlcMedia?.addOption(":start-paused")
        vlcPlayer?.setMedia(vlcMedia)
        primingFirstFrame = true
        vlcPlayer?.play()
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
        // Open paused so the first frame paints immediately instead of a black surface
        // while the room is paused — see injectVideoFileImpl for the full rationale.
        vlcMedia?.addOption(":start-paused")
        vlcPlayer?.setMedia(vlcMedia)
        primingFirstFrame = true
        vlcPlayer?.play()
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
     *
     * The [vlcPlayer.media] check below is defense-in-depth against a VLCKit 4 alpha
     * crash: `[VLCMediaPlayer pause]` and `[VLCMediaPlayer play]` dispatch onto a
     * private libdispatch queue (see VLCMediaPlayer.m), and the queued block calls
     * `libvlc_media_player_pause/play(_p_mi)`. When no media has been associated with
     * the player, libvlc dereferences `_p_mi` (which is NULL or freed) at offset 0x58
     * and segfaults. The protocol-layer gates in RoomEventDispatcher.controlPlayback
     * and RoomCallback.onSomeonePaused/Played are the primary defense; this guard
     * catches any future code path that bypasses those (e.g. a service callback or
     * a system media-control intent that talks directly to the engine).
     */
    override suspend fun pause() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            val player = vlcPlayer ?: return@withContext
            if (player.media == null) return@withContext
            // A deliberate pause ends any first-frame priming window.
            primingFirstFrame = false
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
            // A real play ends the first-frame priming window, so a subsequent Playing
            // state is surfaced normally rather than swallowed.
            primingFirstFrame = false
            pausedSeekPosition = null // VLC becomes reliable at updating time, again
            player.play()
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
            val player = vlcPlayer ?: return@launch
            // Defense-in-depth NULL-media guard — same rationale as in [pause]/[play].
            // setTime on a media-less VLCMediaPlayer enters the same libdispatch path
            // and dereferences the NULL libvlc handle.
            if (player.media == null) return@launch
            // Always shadow the player's clock with our requested position until VLC's
            // time-changed callback fires (which means the player has caught up). Without
            // this, VLCKit 4 has a window between setTime() and the next time-update where
            // vlcPlayer.time can return 0 or a stale value — the protocol's seek detector
            // sees this as a sudden 4-second drop and broadcasts a phantom "user jumped
            // from X to 0" to the room. The shadow gets cleared in mediaPlayerTimeChanged.
            pausedSeekPosition = toPositionMs
            player.setTime(toPositionMs.toVLCTime())
        }
    }

    /**
     * Gets the current playback position.
     *
     * @return Current position in milliseconds
     */
    override fun currentPositionMs(): Long {
        // If paused and we have a manual position → use it
        // A live VLCKit clock of 0/null means "not known yet" (a buffer dip, or the moment
        // right after media injection), NOT a real jump to the start. Returning 0 there made
        // the sync layer broadcast a phantom "seeked to 0" to the room. Treat a non-positive
        // sample as unknown and fall back to the last good tracked time.
        pausedSeekPosition?.let { return it }
        val live = vlcPlayer?.time?.value()?.longValue ?: 0L
        return if (live > 0L) live else playerManager.timeCurrentMillis.value
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
     * **Critical threading rule (VLCKit 4):** these callbacks are dispatched from libvlc
     * worker threads — `mediaPlayerTimeChanged` in particular fires from the timer thread
     * with `player->timer.lock` held. Calling ANY method on `vlcPlayer` / `vlcMedia` (or
     * any object that re-enters libvlc, including the PiP controller's
     * `invalidatePlaybackState`) from inside these callbacks trips libvlc's lock-ordering
     * assertion:
     *
     *     Assertion failed: (!vlc_mutex_held(&player->timer.lock)),
     *     function vlc_player_Lock, file player.c, line 992.
     *
     * The assertion is the symptom; the underlying issue is that `vlc_player_Lock` and
     * `timer.lock` are not allowed to be held by the same thread at once. So we keep these
     * bodies VLC-free: derive what we can from the callback parameters, and `launch` any
     * VLC calls onto our own coroutine scope so they execute on the main thread *after*
     * the libvlc lock has been released.
     *
     * This was a non-issue under MobileVLCKit 3 because callbacks went through
     * NSNotificationCenter (a runloop hop, no libvlc locks held). VLCKit 4 rewrote the
     * event dispatcher; see VLCKit/NEWS, "rewritten event management".
     */
    inner class VlcDelegate : NSObject(), VLCMediaPlayerDelegateProtocol {
        override fun mediaPlayerStateChanged(newState: VLCMediaPlayerState) {
            // Only mirror Playing / Paused into [isNowPlaying]. The other VLCKit 4 states
            // (Opening, Buffering, Stopping, Stopped, Error) are intermediate transitions
            // during which the user-visible "is the video running" semantics are unchanged
            // from before the transition — flipping isNowPlaying false during Buffering,
            // for example, would flicker the room's play/pause button to "play" every
            // time playback hiccups for a network buffer dip and snap back to "pause" a
            // moment later. The Android libVLC engine handles this by listening only to
            // MediaPlayer.Event.Playing / Paused; we do the equivalent here.
            //
            // We deliberately don't call vlcPlayer.isPlaying() — even though it would be
            // more accurate — because the delegate runs on libvlc's timer thread and that
            // method takes vlc_player_Lock, which asserts when timer.lock is held.
            when (newState) {
                VLCMediaPlayerState.VLCMediaPlayerStatePlaying -> {
                    // Swallow the transient Playing emitted while opening freshly injected
                    // media with ":start-paused" (see [primingFirstFrame]) — otherwise the
                    // channel-health collector would broadcast a bogus "unpaused" to the
                    // room. The Paused state that immediately follows clears the flag.
                    // A real (or transient) Playing cancels any pending debounced pause.
                    pauseDebounceJob?.cancel()
                    if (!primingFirstFrame) {
                        playerManager.isNowPlaying.value = true
                        pausedSeekPosition = null
                    }
                }
                VLCMediaPlayerState.VLCMediaPlayerStatePaused -> {
                    primingFirstFrame = false
                    // Debounce: libvlc 4 briefly flips to Paused during non-user operations
                    // (vout rebuilds, some setTime/rate changes). Reporting it immediately
                    // made the channel-health collector broadcast a phantom pause+unpause to
                    // the room (the "1Hz spasm"). Only commit a Paused that is still paused a
                    // moment later (an intervening Playing cancels this). Reading isPlaying()
                    // is safe here because it runs on the main thread after this callback (and
                    // libvlc's timer.lock) has returned, unlike a call inside the delegate.
                    pauseDebounceJob?.cancel()
                    pauseDebounceJob = playerScopeMain.launch(Dispatchers.Main.immediate) {
                        delay(250)
                        if (vlcPlayer?.isPlaying() != true) {
                            playerManager.isNowPlaying.value = false
                        }
                    }
                }
                VLCMediaPlayerState.VLCMediaPlayerStateStopped -> {
                    // Real end of playback (natural EOF or explicit stop) — reflect
                    // that in the button so it doesn't stay stuck showing "pause".
                    primingFirstFrame = false
                    playerManager.isNowPlaying.value = false
                    // VLCKit 4 has no distinct "Ended" state: Stopped fires on a genuine
                    // end-of-file AND on error/teardown/manual stop. Only treat it as a
                    // natural EOF (which auto-advances the shared playlist for the WHOLE
                    // room) when we were actually near the end. We can't read vlcPlayer.time
                    // here (libvlc holds timer.lock during this callback), so use the last
                    // tracked position/duration.
                    val endPos = playerManager.timeCurrentMillis.value
                    val endDur = playerManager.timeFullMillis.value
                    if (endDur > 0L && endPos >= endDur - 1500L) {
                        onPlaybackEnded()
                    }
                }
                else -> { /* Opening, Buffering, Stopping, Error — leave isNowPlaying alone */ }
            }

            // Bounce off our coroutine scope to invalidate the PiP overlay so libvlc has
            // a chance to release timer.lock before invalidatePlaybackState() re-enters
            // it via mediaTime / mediaLength getters in [VlcDrawable].
            playerScopeMain.launch { vlcDrawable?.pipController?.invalidatePlaybackState() }
        }

        override fun mediaPlayerTimeChanged(aNotification: NSNotification) {
            // We can't read vlcPlayer.time here (it would assert in libvlc — see the
            // class doc for VlcDelegate), but the mere fact that VLCKit fired this
            // notification tells us the player's clock is ticking and has caught up
            // with whatever setTime() we last issued. We deliberately do NOT drop the
            // post-seek shadow here anymore: this notification often arrives from a tick
            // queued BEFORE the seek landed, so clearing the shadow now made
            // currentPositionMs briefly report the pre-seek position (a phantom backward
            // jump for the room). The shadow is instead cleared by the main-thread tracker
            // once the live clock actually converges on the target (see startTrackingProgress).
        }

        /**
         * Notify the PiP overlay when media duration becomes known (or changes mid-stream
         * for HLS/DASH live windows). The system uses [VlcDrawable.mediaLength] for its
         * scrubbing UI. Like the state-change handler, we bounce off the coroutine scope
         * to avoid the timer-lock assertion.
         */
        override fun mediaPlayerLengthChanged(length: Long) {
            playerScopeMain.launch { vlcDrawable?.pipController?.invalidatePlaybackState() }
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