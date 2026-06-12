package app.player.mpv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.ui.viewinterop.UIKitView
import app.player.PlayerImpl
import app.player.models.Chapter
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import app.player.models.Track
import app.preferences.PrefExtraConfig
import app.preferences.Preferences.MPV_DEBUG_MODE
import app.preferences.Preferences.MPV_EXPORT_CONF
import app.preferences.Preferences.MPV_HARDWARE_ACCELERATION
import app.preferences.Preferences.MPV_IMPORT_CONF
import app.preferences.Preferences.MPV_INTERPOLATION
import app.preferences.Preferences.MPV_PROFILE
import app.preferences.Preferences.MPV_VIDSYNC
import app.preferences.Preferences.SUBTITLE_SIZE
import app.preferences.settings.SettingCategory
import app.preferences.value
import app.room.RoomViewmodel
import app.utils.getMpvConfFilePath
import app.utils.loggy
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeMoviePlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.darwin.NSObjectProtocol
import platform.posix.O_RDONLY
import platform.posix.fstat
import platform.posix.open
import platform.posix.stat
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.uisetting_categ_mpv
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * iOS MPV player implementation using MPVKit via Swift bridge.
 *
 * Mirrors the Android [app.player.mpv.MpvImpl] but delegates all libmpv C API calls
 * to a [MpvKitPlayerBridge] instance provided by the Swift side.
 *
 * The bridge handles:
 * - mpv handle lifecycle (create/initialize/destroy)
 * - Metal rendering via MoltenVK
 * - Property observation (time-pos, duration, pause)
 * - All mpv commands and property access
 */
class MpvKitImpl(
    viewmodel: RoomViewmodel,
    val bridge: MpvKitPlayerBridge
) : PlayerImpl(viewmodel, MpvKitEngine) {

    override val supportsChapters: Boolean = true
    override val supportsPictureInPicture: Boolean = false
    override val supportsScreenshot: Boolean = true
    // We announce a loaded file from the "file-loaded" event below (once the real duration is
    // known), so the base parseMedia() must not announce it again on iOS. See PlayerImpl.
    override val announcesFileLoadViaEvent: Boolean = true
    // No manual position poll: the MPVKit bridge pushes a precise `time-pos` Double on every
    // frame (see onPropertyChange below), which already drives timeCurrentMillis — the only
    // consumer (the seekbar). Unlike AVPlayer/VLCKit there is no system transport-control actor
    // to catch out of band (supportsPictureInPicture=false), and sync reads currentPositionMs()
    // live, so a 500ms poll was pure duplication. If the observer ever stops feeding the seekbar
    // after a background/foreground vid=no/auto cycle, restore a poll here (e.g. 1.seconds).
    override val trackerJobInterval: Duration = Duration.ZERO

    /**
     * Observers for the app background/foreground transitions. mpv binds its video output and the
     * MoltenVK swapchain to the CAMetalLayer; iOS purges that GPU surface while the app is
     * backgrounded, leaving a dead swapchain that renders as a purple/garbage frame on return (the
     * reported bug; the "purple after reconnect" case is the same thing, since that reconnect
     * follows a background/foreground cycle). A dead swapchain cannot be repaired by re-rendering
     * into it — it must be recreated. We do that by toggling the video track off before
     * backgrounding ([UIApplicationDidEnterBackgroundNotification] -> `vid=no`, which tears the VO
     * + swapchain down cleanly) and back on when returning ([UIApplicationWillEnterForegroundNotification]
     * -> `vid=auto`, which rebuilds them and force-refreshes the current frame, repainting even
     * while paused). This is exactly the fix MPVKit's own iOS demo uses
     * (MPVMetalViewController.enterBackground / enterForeground).
     */
    private var didEnterBackgroundObserver: NSObjectProtocol? = null
    private var willEnterForegroundObserver: NSObjectProtocol? = null

    override fun initialize() {
        // Compose's UIKitView factory can re-fire on recomposition; calling
        // bridge.create() twice would leak the previous mpv handle and overwrite
        // observed-property registrations on the new one.
        if (isInitialized) return

        // Pass the user's saved MPV prefs as mpv's init-time options. mpv reads vo/hwdec/profile/
        // config-dir once, before initialize, so they must be set at creation (not after) to take
        // effect from the first frame, mirroring Android's MPVView.initOptions.
        bridge.create(
            configDir = getMpvConfFilePath()?.substringBeforeLast('/') ?: "",
            hwdec = if (MPV_HARDWARE_ACCELERATION.value()) "auto" else "no",
            // Always gpu-next on iOS: the plain "gpu" VO can't map VideoToolbox (videotoolbox-pl)
            // hardware frames under MoltenVK and hangs on the first frame, so MPV_GPU_NEXT is not
            // honored here.
            vo = "gpu-next",
            profile = MPV_PROFILE.value(),
            videoSync = MPV_VIDSYNC.value(),
            interpolation = if (MPV_INTERPOLATION.value()) "yes" else "no",
        )

        bridge.onPropertyChange = lambda@{ name, value ->
            when (name) {
                "time-pos" -> {
                    val seconds = value as? Double ?: return@lambda
                    playerManager.timeCurrentMillis.value = (seconds * 1000).roundToLong()
                }
                "duration" -> {
                    val seconds = value as? Double ?: return@lambda
                    playerManager.timeFullMillis.value = (seconds * 1000).roundToLong()
                }
                "pause" -> {
                    val paused = value as? Boolean ?: return@lambda
                    playerManager.isNowPlaying.value = !paused
                }
            }
        }

        bridge.onEvent = { eventName ->
            when (eventName) {
                "file-loaded" -> {
                    if (!viewmodel.isSoloMode) {
                        playerScopeIO.launch {
                            // Wait for duration to become available
                            while (playerManager.timeFullMillis.value <= 0L) {
                                delay(50)
                            }
                            playerManager.media.value?.fileDuration =
                                playerManager.timeFullMillis.value.toDouble() / 1000.0
                            announceFileLoaded()
                        }
                    }
                }
                "end-file" -> {
                    playerScopeMain.launch {
                        pause()
                        onPlaybackEnded()
                    }
                }
            }
        }

        configureAudioSession()
        registerLifecycleObservers()

        isInitialized = true
        startTrackingProgress()
    }

    /**
     * Register the background/foreground observers that recreate mpv's video surface across an app
     * background cycle. See [didEnterBackgroundObserver]. Both callbacks land on the main queue and
     * just flip the `vid` track via the (thread-safe) mpv C API.
     */
    private fun registerLifecycleObservers() {
        val center = NSNotificationCenter.defaultCenter
        val queue = NSOperationQueue.mainQueue

        didEnterBackgroundObserver = center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = queue
        ) { _ ->
            // Drop the video track BEFORE iOS purges the GPU surface, so the VO + swapchain are
            // torn down cleanly rather than left dangling on a dead CAMetalLayer drawable.
            if (isInitialized) bridge.setOptionString("vid", "no")
        }

        willEnterForegroundObserver = center.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = queue
        ) { _ ->
            // Re-select the video track: mpv rebuilds the VO + swapchain from scratch and
            // force-refreshes the current frame (repaints even while paused), replacing the purple
            // surface. No play() needed — playback state stays under the room's control.
            if (isInitialized) bridge.setOptionString("vid", "auto")
        }
    }

    private fun removeLifecycleObservers() {
        val center = NSNotificationCenter.defaultCenter
        didEnterBackgroundObserver?.let { center.removeObserver(it) }
        willEnterForegroundObserver?.let { center.removeObserver(it) }
        didEnterBackgroundObserver = null
        willEnterForegroundObserver = null
    }

    /**
     * Configure the shared AVAudioSession for video playback. MPVKit (unlike VLCKit) never
     * touches the audio session, so without this the app keeps iOS's default SoloAmbient session:
     * audio is silenced by the hardware mute switch and stops the instant the app backgrounds.
     * `.playback` + `.moviePlayback` mirrors [app.player.vlc.VlcKitImpl.configureAudioSession] and
     * keeps audio alive for PiP / lock-screen. Best-effort — a failure must not block playback.
     */
    private fun configureAudioSession() {
        try {
            val session = AVAudioSession.sharedInstance()
            // Positional args: K/N's Obj-C interop exposes overloaded `setCategory:*:` variants
            // sharing a base name, so named-parameter resolution can fail — positional keeps us on
            // the shortest matching overload (matches VlcKitImpl).
            session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeMoviePlayback, 0uL, null)
            session.setActive(true, null)
        } catch (e: Exception) {
            loggy("MPVKit: AVAudioSession configure failed: ${e.message}")
        }
    }

    override suspend fun destroy() {
        if (!isInitialized) return
        // Same destroy contract as the Android engines: flip the guard first so every
        // `isInitialized`-gated method (including the background/foreground vid toggles)
        // refuses to touch the bridge from here on, then cancel the supervisor so any
        // coroutine launched on the player scopes stops retaining the room graph.
        isInitialized = false
        playerSupervisorJob.cancel()
        // Drop the lifecycle observers first, so a background/foreground notification firing during
        // teardown can't poke the bridge we're about to destroy.
        removeLifecycleObservers()
        withContext(Dispatchers.Main) {
            bridge.onPropertyChange = null
            bridge.onEvent = null
            bridge.destroy()
        }
    }

    override suspend fun configurableSettings() = SettingCategory(
        title = Res.string.uisetting_categ_mpv,
        icon = Icons.Filled.SettingsInputComponent
    ) {
        // hwdec and profile are read once by mpv at init (see create()). Switching them live while
        // a MoltenVK swapchain and a VideoToolbox decoder are bound tears down and recreates the
        // video output, which on iOS wedges the decoder and kills playback. So they only persist
        // here and take effect the next time the player is created (next room join); tell the user.
        // gpu-next is intentionally NOT exposed: the plain "gpu" VO can't render VideoToolbox frames
        // under MoltenVK (it hangs), so create() always forces gpu-next.
        +MPV_HARDWARE_ACCELERATION.apply {
            config?.extraConfig = PrefExtraConfig.BooleanCallback {
                viewmodel.dispatchOSD { "Hardware decoding change applies after rejoining the room" }
            }
        }
        // video-sync and interpolation are render/timing options mpv applies live safely (no
        // video-output reinit), so push them straight through.
        +MPV_VIDSYNC.apply {
            config?.extraConfig = PrefExtraConfig.MultiChoice(
                entries = { vidsyncEntries.zip(vidsyncEntries).toMap() },
                onItemChosen = { videoSync -> bridge.setOptionString("video-sync", videoSync) }
            )
        }
        +MPV_INTERPOLATION.apply {
            config?.dependencyEnable = booleanMet@{
                val currentVidSyncMode = MPV_VIDSYNC.value()
                return@booleanMet currentVidSyncMode != "audio" && currentVidSyncMode != "desync"
            }
            config?.extraConfig = PrefExtraConfig.BooleanCallback { b ->
                bridge.setOptionString("interpolation", if (b) "yes" else "no")
            }
        }
        +MPV_PROFILE.apply {
            config?.extraConfig = PrefExtraConfig.MultiChoice(
                entries = { profileEntries.zip(profileEntries).toMap() },
                onItemChosen = { viewmodel.dispatchOSD { "Profile change applies after rejoining the room" } }
            )
        }
        +MPV_DEBUG_MODE.apply {
            config?.extraConfig = PrefExtraConfig.Slider(maxValue = 3, minValue = 0) { page ->
                bridge.setDebugOverlay(page)
            }
        }
        // mpv.conf import/export now works on iOS too: getMpvConfFilePath() returns a real path
        // and create() points mpv's config-dir at it (the new config applies on next playback).
        +MPV_IMPORT_CONF
        +MPV_EXPORT_CONF
    }

    @Composable
    override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) {
        DisposableEffect(Unit) {
            onDispose {
                // The MpvKitImpl owns the lifecycle exit (PlayerImpl.destroy()), but
                // when the composable leaves composition we still want to clear the
                // event/property callbacks so we stop reaching back into Compose state
                // that may have already been disposed. The bridge itself is torn down
                // by the next call to destroy().
                bridge.onPropertyChange = null
                bridge.onEvent = null
            }
        }

        UIKitView(
            modifier = modifier,
            factory = {
                val view = bridge.getPlayerView()
                // Background colour is set by MPVRenderView's init.

                initialize()
                onPlayerReady()

                return@UIKitView view
            }
        )
    }

    override suspend fun hasMedia(): Boolean {
        if (!isInitialized) return false
        return withContext(Dispatchers.Main.immediate) {
            bridge.getDuration() > 0
        }
    }

    override suspend fun isPlaying(): Boolean {
        if (!isInitialized) return false
        return withContext(Dispatchers.Main.immediate) {
            !bridge.isPaused()
        }
    }

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            viewmodel.media?.tracks?.clear()

            val count = bridge.getTrackCount()
            for (i in 0 until count) {
                val type = bridge.getTrackType(i) ?: continue
                if (type != "audio" && type != "sub") continue

                val mpvId = bridge.getTrackId(i)
                val lang = bridge.getTrackLang(i)
                val title = bridge.getTrackTitle(i)
                val selected = bridge.isTrackSelected(i)

                val trackName = when {
                    !title.isNullOrEmpty() && !lang.isNullOrEmpty() -> "$title [$lang]"
                    !title.isNullOrEmpty() -> "$title [UND]"
                    !lang.isNullOrEmpty() -> "Track [$lang]"
                    else -> "Track $mpvId [UND]"
                }

                viewmodel.media?.tracks?.add(
                    MpvKitTrack(
                        name = trackName,
                        type = if (type == "audio") TrackType.AUDIO else TrackType.SUBTITLE,
                        index = mpvId,
                        selected = selected
                    )
                )
            }
        }
    }

    override suspend fun selectTrack(track: Track?, type: TrackType) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            when (type) {
                TrackType.SUBTITLE -> {
                    bridge.setSubtitleTrack(track?.index ?: -1)
                    playerManager.currentTrackChoices.subtitleSelectionIndexMpv = track?.index ?: -1
                }
                TrackType.AUDIO -> {
                    bridge.setAudioTrack(track?.index ?: -1)
                    playerManager.currentTrackChoices.audioSelectionIndexMpv = track?.index ?: -1
                }
            }
        }
    }

    override suspend fun analyzeChapters(mediafile: MediaFile) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            mediafile.chapters.clear()
            val count = bridge.getChapterCount()
            for (i in 0 until count) {
                val title = bridge.getChapterTitle(i)
                val time = bridge.getChapterTime(i)
                mediafile.chapters.add(
                    Chapter(
                        index = i,
                        name = title ?: "Chapter $i",
                        timeOffsetMillis = (time * 1000).roundToLong()
                    )
                )
            }
        }
    }

    override suspend fun jumpToChapter(chapter: Chapter) {
        if (!isInitialized) return
        super.jumpToChapter(chapter)
        withContext(Dispatchers.Main.immediate) {
            bridge.setChapter(chapter.index)
        }
    }

    override suspend fun reapplyTrackChoices() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            val subIndex = playerManager.currentTrackChoices.subtitleSelectionIndexMpv
            val audioIndex = playerManager.currentTrackChoices.audioSelectionIndexMpv

            val tracks = playerManager.media.value?.tracks ?: return@withContext

            if (subIndex == -1) {
                selectTrack(null, TrackType.SUBTITLE)
            } else {
                tracks.firstOrNull { it.type == TrackType.SUBTITLE && it.index == subIndex }
                    ?.let { selectTrack(it, TrackType.SUBTITLE) }
            }

            if (audioIndex == -1) {
                selectTrack(null, TrackType.AUDIO)
            } else {
                tracks.firstOrNull { it.type == TrackType.AUDIO && it.index == audioIndex }
                    ?.let { selectTrack(it, TrackType.AUDIO) }
            }
        }
    }

    override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            // The subtitle is a separately-picked, security-scoped file; PlayerImpl only holds the
            // *video's* scope. Without claiming the subtitle's own scope here, mpv's `sub-add` open
            // fails with EPERM and the sideloaded sub silently never appears (the bug). Mirror
            // VlcKitImpl.loadExternalSubImpl: claim the scope and leave it held — mpv keeps the file
            // open to stream cues as playback advances, so releasing it early would break the sub
            // mid-playback.
            uri.nsUrl.startAccessingSecurityScopedResource()
            bridge.addSubtitleFile(uri.path)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun injectVideoFileImpl(location: MediaFileLocation.Local) {
        installMpvSubfontIfNeeded()
        val path = location.file.path
        // Open a blocking descriptor eagerly (while PlayerImpl's security scope is freshest) and
        // hand mpv the fd via `fdclose://`, instead of letting mpv do its own deferred O_NONBLOCK
        // path open on its demux thread.
        val fd = open(path, O_RDONLY)
        if (fd < 0) {
            loggy("MPVKit: eager open() failed for $path; letting mpv open the path")
            bridge.loadFile(path)
            return
        }

        // Decisive probe for the "jumps to EOF" failure: an iOS File Provider *dataless*
        // placeholder reports the file's full logical size via st_size but has almost nothing
        // actually on disk (st_blocks counts 512-byte blocks really present). When that is the
        // case, mpv reads the header then hits EOF on the body ("no clusters found") and parks at
        // end-of-file — no open trick can conjure bytes that were never downloaded. Log both
        // numbers so a not-downloaded placeholder is distinguishable from a small/corrupt file,
        // and tell the user instead of silently wedging at the last frame.
        memScoped {
            val st = alloc<stat>()
            if (fstat(fd, st.ptr) == 0) {
                val logical = st.st_size
                val onDisk = st.st_blocks * 512L
                loggy("MPVKit: '$path' logical=${logical}B onDisk≈${onDisk}B")
                if (logical > 0L && onDisk < logical / 2L) {
                    loggy("MPVKit: WARNING not fully downloaded (${onDisk}B of ${logical}B local) — mpv will EOF immediately")
                    viewmodel.dispatchOSD { "This file isn't fully downloaded to your device yet." }
                }
            }
        }

        bridge.loadFile("fdclose://$fd")
    }

    override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
        installMpvSubfontIfNeeded()
        bridge.loadURL(location.url)
    }

    override suspend fun pause() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            bridge.setPaused(true)
        }
    }

    override suspend fun play() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            bridge.setPaused(false)
        }
    }

    override suspend fun setSpeed(speed: Double) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            bridge.setSpeed(speed)
        }
    }

    override suspend fun isSeekable(): Boolean {
        if (!isInitialized) return false
        return withContext(Dispatchers.Main.immediate) {
            bridge.isSeekable()
        }
    }

    override fun seekTo(toPositionMs: Long) {
        if (!isInitialized) return
        super.seekTo(toPositionMs)
        bridge.seekTo(toPositionMs.toDouble() / 1000.0)
    }

    override fun currentPositionMs(): Long {
        if (!isInitialized) return 0L
        return (bridge.getTimePos() * 1000).roundToLong()
    }

    override suspend fun takeScreenshot(): Boolean {
        if (!isInitialized) return false
        return withContext(Dispatchers.Main.immediate) {
            bridge.takeScreenshot()
        }
    }

    override suspend fun switchAspectRatio(): String {
        if (!isInitialized) return "NO PLAYER FOUND"
        return withContext(Dispatchers.Main.immediate) {
            val currentAspect = bridge.getAspectOverride() ?: "-1.000000"
            val currentPanscan = bridge.getPanscan()

            val aspectRatios = listOf(
                "-1.000000" to "Original", "1.777778" to "16:9",
                "1.600000" to "16:10", "1.333333" to "4:3",
                "2.350000" to "2.35:1", "panscan" to "Pan/Scan"
            )

            var enablePanscan = false
            val nextAspect = if (currentPanscan == 1.0) {
                aspectRatios[0]
            } else if (currentAspect == "2.350000") {
                enablePanscan = true
                aspectRatios[5]
            } else {
                val idx = aspectRatios.indexOfFirst { it.first == currentAspect }
                aspectRatios.getOrElse(idx + 1) { aspectRatios[0] }
            }

            if (enablePanscan) {
                bridge.setAspectOverride("-1")
                bridge.setPanscan(1.0)
            } else {
                bridge.setAspectOverride(nextAspect.first)
                bridge.setPanscan(0.0)
            }

            nextAspect.second
        }
    }

    override suspend fun changeSubtitleSize(newSize: Int) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            val scale: Double = when {
                newSize == 16 -> 1.0
                newSize > 16 -> 1.0 + (newSize - 16) * 0.05
                else -> 1.0 - (16 - newSize) * (1.0 / 16)
            }
            bridge.setSubScale(scale)
        }
    }

    override fun getMaxVolume(): Int = bridge.getMaxVolume()
    override fun getCurrentVolume(): Int = bridge.getVolume()
    override fun changeCurrentVolume(v: Int) {
        bridge.setVolume(v.coerceIn(0, getMaxVolume()))
    }

    private companion object {
        // Mirrors MPVView.vidsyncEntries / profileEntries on Android (those live in androidMain
        // and aren't visible here). Keep in sync with the Android lists.
        val vidsyncEntries = listOf(
            "audio", "display-resample", "display-resample-vdrop", "display-resample-desync",
            "display-tempo", "display-vdrop", "display-adrop", "display-desync", "desync"
        )
        val profileEntries = listOf("fast", "high-quality", "gpu-hq", "low-latency", "sw-fast")
    }
}
