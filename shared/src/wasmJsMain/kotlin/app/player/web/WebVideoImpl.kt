package app.player.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import app.player.PlayerEngine
import app.player.PlayerImpl
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import app.player.models.Track
import app.preferences.settings.SettingCategory
import app.room.RoomViewmodel
import app.utils.loggy
import io.github.vinceglb.filekit.PlatformFile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The `<video>` engine, with its plumbing in place and its element not yet attached.
 *
 * What is real here is the shape: the room, the sync algorithm and the transport all talk to a
 * `PlayerImpl`, and this is one, so the rest of the app compiles and runs against it. What is not
 * real yet is the element. Every method below records what it was asked to do and answers from
 * the recorded state, so nothing crashes and nothing lies about playing.
 *
 * **What finishing it takes.** One HTML `<video>` placed into the page underneath the Compose
 * canvas, through Compose Multiplatform's `HtmlElementView` (added in 1.9 as `WebElementView`,
 * renamed in 1.11), which needs the `ComposeViewport` entry point that :webApp already uses. Then
 * each method below maps to one property or call on that element:
 *
 *  - [play] / [pause] to `play()` / `pause()`
 *  - [currentPositionMs] to `currentTime`, and [seekTo] writes it
 *  - [setSpeed] to `playbackRate`, [setEngineVolume] to `volume`
 *  - [analyzeTracks] to `audioTracks` and `textTracks`
 *  - [injectVideoURLImpl] to `src`, and [injectVideoFileImpl] to a blob URL from the picked file
 *  - [loadExternalSubImpl] to a `<track>` child
 *
 * The room's position estimator wants a sample plus a timestamp rather than a poll, so the
 * finished version should listen for `timeupdate` and call `playerManager.samplePosition`, the
 * way VLCKit does, and drop [trackerJobInterval] to zero.
 */
internal class WebVideoImpl(
    viewmodel: RoomViewmodel,
    engine: PlayerEngine,
) : PlayerImpl(viewmodel, engine) {

    /** An HTML video has no chapter concept; a sidecar format would have to supply them. */
    override val supportsChapters: Boolean = false

    /** The browser owns fullscreen and picture-in-picture, and both need a user gesture. */
    override val supportsPictureInPicture: Boolean = false

    /** `playbackRate` accepts anything, so the room's drift slowdown applies unchanged. */
    override val supportsSpeedAdjustment: Boolean = true

    /** Until `timeupdate` is wired, the room polls, same as the engines that have no load event. */
    override val trackerJobInterval: Duration = 250.milliseconds

    /* The element's state, mirrored here so the contract answers consistently before it exists. */
    private var loadedUri: String? = null
    private var playing: Boolean = false
    private var positionMs: Long = 0L
    private var volumePercent: Int = 100

    override fun initialize() {
        isInitialized = true
    }

    override suspend fun destroy() {
        // The contract, in the order the build gate enforces: drop the guard so nothing in
        // flight touches the engine, stop the coroutines that would, then release the element.
        isInitialized = false
        playerSupervisorJob.cancel()
        loadedUri = null
        playing = false
    }

    /** Nothing engine-specific to offer yet; the browser exposes no decoder knobs. */
    override suspend fun configurableSettings(): SettingCategory? = null

    override suspend fun hasMedia(): Boolean = loadedUri != null

    override suspend fun isPlaying(): Boolean = playing

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        // The element's audioTracks and textTracks become Track entries here, once it exists.
        mediafile.tracks.clear()
    }

    override suspend fun selectTrack(track: Track?, type: TrackType) = Unit

    override suspend fun analyzeChapters(mediafile: MediaFile) = Unit

    override suspend fun reapplyTrackChoices() = Unit

    override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) {
        loggy("Web engine: external subtitles need a <track> child on the video element.")
    }

    override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
        loadedUri = location.url
        positionMs = 0L
        playing = false
    }

    override suspend fun injectVideoFileImpl(location: MediaFileLocation.Local) {
        // A picked file becomes a blob: URL, which is the element's src like any other.
        loadedUri = location.commonUri
        positionMs = 0L
        playing = false
    }

    override suspend fun play() {
        playing = true
    }

    override suspend fun pause() {
        playing = false
    }

    override suspend fun setSpeed(speed: Double) = Unit

    override suspend fun isSeekable(): Boolean = loadedUri != null

    override fun seekTo(toPositionMs: Long) {
        super.seekTo(toPositionMs)
        positionMs = toPositionMs.coerceAtLeast(0L)
    }

    override fun currentPositionMs(): Long = positionMs

    /** The element scales with CSS `object-fit`, so the room's aspect key has one honest answer. */
    override suspend fun switchAspectRatio(): String = "Fit"

    /** Subtitle size is a CSS rule on the element's cue pseudo-element, not an engine call. */
    override suspend fun changeSubtitleSize(newSize: Int) = Unit

    override fun getEngineVolume(): Int = volumePercent

    override fun setEngineVolume(percent: Int) {
        volumePercent = percent.coerceIn(0, 100)
    }

    /**
     * The video layer. Empty until the element is attached, and reporting itself ready anyway so
     * the room finishes starting up and the rest of the screen is usable.
     */
    @Composable
    override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) {
        LaunchedEffect(Unit) { onPlayerReady() }
        Box(modifier.fillMaxSize())
    }
}
