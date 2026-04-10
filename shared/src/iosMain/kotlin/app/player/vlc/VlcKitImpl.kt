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
import app.preferences.settings.SettingCategory
import app.preferences.value
import app.room.RoomViewmodel
import app.utils.loggy
import co.touchlab.kermit.Logger
import cocoapods.MobileVLCKit.VLCLibrary
import cocoapods.MobileVLCKit.VLCMedia
import cocoapods.MobileVLCKit.VLCMediaPlaybackSlaveTypeSubtitle
import cocoapods.MobileVLCKit.VLCMediaPlayer
import cocoapods.MobileVLCKit.VLCMediaPlayerDelegateProtocol
import cocoapods.MobileVLCKit.VLCMediaPlayerState
import cocoapods.MobileVLCKit.VLCTime
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSNotification
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
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

    /**
     * Cleans up VLC resources and stops playback.
     *
     * Properly disposes of the media player, media object, and VLC library.
     */
    override suspend fun destroy() {
        if (!isInitialized) return

        try {
            vlcPlayer?.stop()
            vlcPlayer?.finalize()
            vlcPlayer?.drawable = null
            vlcPlayer = null

            vlcMedia = null

            libvlc?.finalize()
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
                vlcView = UIView()
                vlcView!!.setBackgroundColor(UIColor.clearColor())

                val subSizeArg = "--freetype-fontsize=${SUBTITLE_SIZE.value() * 4}"
                libvlc = VLCLibrary(listOf(
                    "-vv",
                    subSizeArg,
                    "--network-caching=2000",
                    "--adaptive-logic=default",
                    "--http-reconnect",
                ))

                vlcPlayer = VLCMediaPlayer(libvlc!!)
                vlcPlayer!!.drawable = vlcView

                vlcPlayer

                initialize()

                onPlayerReady()

                return@UIKitView vlcView!!
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

        isInitialized = true

        //startTrackingProgress()
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
     * Populates the media file's track lists with VLC's detected tracks,
     * including track names and indices for selection.
     *
     * @param mediafile The media file to populate with track information
     */
    override suspend fun analyzeTracks(mediafile: MediaFile) {
        if (!isInitialized) return
        if (vlcPlayer == null) return

        withContext(Dispatchers.Main.immediate) {
            viewmodel.media?.tracks?.clear()

            val audioTracks = vlcPlayer!!.audioTrackIndexes.zip(vlcPlayer!!.audioTrackNames).toMap()
            audioTracks.forEach { (index, name) ->
                viewmodel.media?.tracks?.add(
                    VlcKitTrack(
                        name = name.toString(),
                        type = TrackType.AUDIO,
                        index = index as? Int ?: 0,
                        selected = vlcPlayer!!.currentAudioTrackIndex == index
                    )
                )
            }

            val subtitleTracks = vlcPlayer!!.videoSubTitlesIndexes.zip(vlcPlayer!!.videoSubTitlesNames).toMap()
            subtitleTracks.forEach { (index, name) ->
                viewmodel.media?.tracks?.add(
                    VlcKitTrack(
                        name = name.toString(),
                        type = TrackType.SUBTITLE,
                        index = index as? Int ?: 0,
                        selected = vlcPlayer!!.currentVideoSubTitleIndex == index
                    )
                )
            }
        }
    }

    /**
     * Selects a specific audio or subtitle track for playback.
     *
     * Pass null or negative index to disable that track type.
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
                        vlcPlayer?.setCurrentVideoSubTitleIndex(index)
                    } else {
                        vlcPlayer?.setCurrentVideoSubTitleIndex(-1)
                    }
                }

                TrackType.AUDIO -> {
                    val index = track?.index ?: -1
                    if (index >= 0) {
                        vlcPlayer?.setCurrentAudioTrackIndex(index)
                    } else {
                        vlcPlayer?.setCurrentAudioTrackIndex(-1)
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
                val descMap = desc as? Map<*, *>

                val timeOffset = (descMap?.get("VLCChapterDescriptionTimeOffset") as? NSNumber)?.longValue ?: 0L
                val name = descMap?.get("VLCChapterDescriptionName") as? String ?: ""

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
            vlcPlayer?.addPlaybackSlave(
                uri.nsUrl,
                VLCMediaPlaybackSlaveTypeSubtitle,
                true
            )
        }
    }

    override suspend fun injectVideoFileImpl(location: MediaFileLocation.Local) {
        val nsUrl = location.file.nsUrl
        nsUrl.startAccessingSecurityScopedResource()
        vlcMedia = VLCMedia(uRL = nsUrl)
        vlcMedia?.synchronousParse()
        vlcPlayer?.setMedia(vlcMedia)
    }

    override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
        val url = location.url
        vlcMedia = NSURL.URLWithString(url)?.let { VLCMedia(uRL = it) }

        val isAdaptiveStream = url.contains(".m3u8", ignoreCase = true)
                || url.contains(".mpd", ignoreCase = true)
                || url.contains("/manifest", ignoreCase = true)

        if (isAdaptiveStream) {
            // For HLS/DASH streams, skip synchronousParse which can block indefinitely
            // on stream manifests. VLC will parse during playback instead.
            vlcMedia?.addOption(":network-caching=3000")
            vlcMedia?.addOption(":clock-jitter=0")
            vlcMedia?.addOption(":clock-synchro=0")
        } else {
            vlcMedia?.synchronousParse()
        }
        vlcPlayer?.setMedia(vlcMedia)
    }

    override suspend fun parseMedia(media: MediaFile) {
        val lengthMs = vlcMedia?.length?.numberValue?.doubleValue?.toLong() ?: 0L
        if (lengthMs > 0) {
            playerManager.timeFullMillis.value = lengthMs
            media.fileDuration = lengthMs / 1000.0
        } else {
            // For streams (HLS/DASH), duration may not be known until playback starts.
            // Retry after a short delay to let VLC fetch the manifest.
            delay(1500)
            val retryMs = vlcMedia?.length?.numberValue?.doubleValue?.toLong() ?: 0L
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
            val currentAspectRatio = vlcPlayer?.videoAspectRatio?.toKString()

            val (width, height) = vlcPlayer?.videoSize?.useContents { width.toInt() to height.toInt() } ?: (0 to 0)

            // Available aspect ratio options
            val aspectRatios = mutableListOf(
                "$width:$height",
                "1:1", "4:3", "16:9", "16:10",
            )

            // Find the index of the current aspect ratio in the list
            val currentIndex = if (currentAspectRatio != null) {
                aspectRatios.indexOf(currentAspectRatio)
            } else {
                -1 // If current aspect ratio is null, set it to -1
            }

            val nextIndex = (currentIndex + 1) % aspectRatios.size
            val newAspectRatio = aspectRatios[nextIndex]

            // Convert the new aspect ratio to C string and set it
            memScoped {
                vlcPlayer?.videoAspectRatio = newAspectRatio.cstr.ptr
            }

            return@withContext newAspectRatio
        }
    }

    /**
     * Sadly, MobileVLCKit doesn't allow to change subtitle size at runtime
     */
    override suspend fun changeSubtitleSize(newSize: Int) {
        if (!isInitialized) return

        viewmodel.dispatchOSD {
            //TODO Localize
            "Subtitle size will take effect next time you open the room"
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
     */
    inner class VlcDelegate : NSObject(), VLCMediaPlayerDelegateProtocol {
        override fun mediaPlayerStateChanged(aNotification: NSNotification) {
            val isPlaying = vlcPlayer?.media != null && vlcPlayer?.isPlaying() == true
            playerManager.isNowPlaying.value = isPlaying
            playerManager.timeCurrentMillis.value = currentPositionMs()

            if (isPlaying) pausedSeekPosition = null

            if (vlcPlayer?.state == VLCMediaPlayerState.VLCMediaPlayerStateEnded) {
                onPlaybackEnded()
            }
        }

        override fun mediaPlayerTimeChanged(aNotification: NSNotification) {
            Logger.e("AUTOMATIC VLC UPDATOR")

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