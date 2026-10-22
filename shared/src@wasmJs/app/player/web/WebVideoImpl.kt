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
 * The `<video>` engine, with its plumbing in place and no element attached.
 *
 * The shape is real: the room, the sync algorithm and the transport all talk to a `PlayerImpl`,
 * and this is one, so the rest of the app compiles and runs against it. The element is not
 * there. Every method below records what it was asked to do and answers from the recorded state,
 * so nothing crashes. No video plays.
 *
 * **Attaching the element.** One HTML `<video>` goes into the page under the Compose canvas,
 * through Compose Multiplatform's `HtmlElementView` (named `WebElementView` before 1.11). That
 * needs the `ComposeViewport` entry point, which :webApp already uses. Each method below then
 * maps to one property or call on the element:
 *
 *  - [play] / [pause] to `play()` / `pause()`
 *  - [currentPositionMs] to `currentTime`, and [seekTo] writes it
 *  - [setSpeed] to `playbackRate`, [setEngineVolume] to `volume`
 *  - [analyzeTracks] to `audioTracks` and `textTracks`
 *  - [injectVideoURLImpl] to `src`, and [injectVideoFileImpl] to a blob URL from the picked file
 *  - [loadExternalSubImpl] to a `<track>` child
 *
 * The room's position estimator prefers a sample plus a timestamp over a poll. So with a real
 * element, listen for `timeupdate`, call `playerManager.samplePosition` (as VLCKit does), and set
 * [trackerJobInterval] to zero.
 */
internal class WebVideoImpl(
    viewmodel: RoomViewmodel,
    engine: PlayerEngine,
) : PlayerImpl(viewmodel, engine) {

    /** An HTML video has no chapter concept; a sidecar format would have to supply them. */
    override val supportsChapters: Boolean = false

    /** The browser owns fullscreen and picture-in-picture, and both need a user gesture. */
    override val supportsPictureInPicture: Boolean = false

    /** `playbackRate` takes any normal rate, so the room's drift slowdown applies unchanged. */
    override val supportsSpeedAdjustment: Boolean = true

    /** No `timeupdate` listener, so the room polls the position, as for engines without events. */
    override val trackerJobInterval: Duration = 250.milliseconds

    /* The recorded element state, so the contract answers consistently while no element exists. */
    private var loadedUri: String? = null
    private var playing: Boolean = false
    private var positionMs: Long = 0L
    private var volumePercent: Int = 100

    override fun initialize() {
        isInitialized = true
    }

    override suspend fun destroy() {
        // The destroy contract, in the order that the checkDestroyContract build gate enforces:
        // drop the guard so nothing in flight touches the engine, stop the coroutines that
        // would, then release the element.
        isInitialized = false
        playerSupervisorJob.cancel()
        loadedUri = null
        playing = false
    }

    /** No engine settings: the browser exposes no decoder options. */
    override suspend fun configurableSettings(): SettingCategory? = null

    override suspend fun hasMedia(): Boolean = loadedUri != null

    override suspend fun isPlaying(): Boolean = playing

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        // With a real element, its audioTracks and textTracks become Track entries here.
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
        // With a real element, a picked file becomes a blob: URL for the element's src. Without
        // one, only its commonUri is recorded.
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

    /** The element scales with CSS `object-fit`, so the room's aspect key always answers "Fit". */
    override suspend fun switchAspectRatio(): String = "Fit"

    /** Subtitle size is a CSS rule on the element's cue pseudo-element, not an engine call. */
    override suspend fun changeSubtitleSize(newSize: Int) = Unit

    override fun getEngineVolume(): Int = volumePercent

    override fun setEngineVolume(percent: Int) {
        volumePercent = percent.coerceIn(0, 100)
    }

    /**
     * The video layer. It is empty while no element is attached, but it reports itself ready, so
     * the room finishes starting up and the rest of the screen is usable.
     */
    @Composable
    override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) {
        LaunchedEffect(Unit) { onPlayerReady() }
        Box(modifier.fillMaxSize())
    }
}
