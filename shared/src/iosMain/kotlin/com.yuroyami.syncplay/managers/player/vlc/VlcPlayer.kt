package com.yuroyami.syncplay.managers.player.vlc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import cocoapods.MobileVLCKit.VLCLibrary
import cocoapods.MobileVLCKit.VLCMedia
import cocoapods.MobileVLCKit.VLCMediaPlaybackSlaveTypeSubtitle
import cocoapods.MobileVLCKit.VLCMediaPlayer
import cocoapods.MobileVLCKit.VLCMediaPlayerDelegateProtocol
import cocoapods.MobileVLCKit.VLCMediaPlayerState
import cocoapods.MobileVLCKit.VLCTime
import com.yuroyami.syncplay.managers.player.ApplePlayerEngine
import com.yuroyami.syncplay.managers.player.BasePlayer
import com.yuroyami.syncplay.managers.preferences.Preferences.SUBTITLE_SIZE
import com.yuroyami.syncplay.managers.preferences.value
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.MediaFileLocation
import com.yuroyami.syncplay.models.Track
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVPlayerLayer
import platform.Foundation.NSArray
import platform.Foundation.NSNotification
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * VLC media player implementation for iOS using MobileVLCKit.
 *
 * Provides comprehensive media playback functionality using VLC's powerful codec library.
 * This iOS version is more stable than its Android counterpart while maintaining extensive
 * format support.
 *
 * ## Architecture
 * Uses UIKitView to embed VLC's UIView into Compose UI
 *
 * @property viewmodel The parent RoomViewModel managing this player
 */
class VlcPlayer(viewmodel: RoomViewmodel) : BasePlayer(viewmodel, ApplePlayerEngine.VLC) {
    /**
     * VLC library instance providing codec and media parsing capabilities.
     */
    private var libvlc: VLCLibrary? = null

    /**
     * Main VLC media player instance handling playback.
     */
    var vlcPlayer: VLCMediaPlayer? = null

    /**
     * UIView that renders the video content.
     */
    private var vlcView: UIView? = null

    /**
     * Delegate for receiving VLC player state change notifications.
     */
    private var vlcDelegate = VlcDelegate()

    /**
     * Currently loaded VLC media object.
     */
    private var vlcMedia: VLCMedia? = null

    /**
     * AVPlayerLayer wrapper for Picture-in-Picture support.
     * VLC's UIView is added as a sublayer to enable PiP functionality.
     */
    var pipLayer: AVPlayerLayer? = null

    override val canChangeAspectRatio: Boolean
        get() = true

    override val supportsChapters: Boolean
        get() = true

    override val trackerJobInterval: Duration
        get() = 1.seconds

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

    /**
     * Returns player-specific configuration settings.
     *
     * TODO: Implement VLC-specific settings (deinterlacing, hardware acceleration, etc.)
     *
     * @return null (no custom settings currently implemented)
     */
    override suspend fun configurableSettings() = null

    /**
     * Renders the VLC video player view within Compose.
     *
     * Creates a UIView for video rendering, initializes the VLC library and player,
     * and sets up the Picture-in-Picture layer wrapper.
     *
     * @param modifier Compose modifier for layout and styling
     */
    @Composable
    override fun VideoPlayer(modifier: Modifier) {
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
                libvlc = VLCLibrary(listOf("-vv", subSizeArg))

                vlcPlayer = VLCMediaPlayer(libvlc!!)
                vlcPlayer!!.drawable = vlcView

                initialize()

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
     * Populates the media file's track lists with VLC's detected tracks,
     * including track names and indices for selection.
     *
     * @param mediafile The media file to populate with track information
     */
    override suspend fun analyzeTracks(mediafile: MediaFile) {
        if (!isInitialized) return
        if (vlcPlayer == null) return

        withContext(Dispatchers.Main.immediate) {
            viewmodel.media?.subtitleTracks?.clear()
            viewmodel.media?.audioTracks?.clear()

            val audioTracks = vlcPlayer!!.audioTrackIndexes.zip(vlcPlayer!!.audioTrackNames).toMap()
            audioTracks.forEach { (index, name) ->
                viewmodel.media?.audioTracks?.add(
                    object : Track {
                        override val name = name.toString()
                        override val index = index as? Int ?: 0
                        override val type = TRACKTYPE.AUDIO
                        override val selected = mutableStateOf(vlcPlayer!!.currentAudioTrackIndex == index)
                    }
                )
            }

            val subtitleTracks = vlcPlayer!!.videoSubTitlesIndexes.zip(vlcPlayer!!.videoSubTitlesNames).toMap()
            subtitleTracks.forEach { (index, name) ->
                viewmodel.media?.subtitleTracks?.add(
                    object : Track {
                        override val name = name.toString()
                        override val index = index as? Int ?: 0
                        override val type = TRACKTYPE.SUBTITLE
                        override val selected = mutableStateOf(vlcPlayer!!.currentAudioTrackIndex == index)
                    }
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
    override suspend fun selectTrack(track: Track?, type: TRACKTYPE) {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            when (type) {
                TRACKTYPE.SUBTITLE -> {
                    val index = track?.index ?: -1
                    if (index >= 0) {
                        vlcPlayer?.setCurrentVideoSubTitleIndex(index)
                    } else {
                        vlcPlayer?.setCurrentVideoSubTitleIndex(-1)
                    }
                }

                TRACKTYPE.AUDIO -> {
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
     * TODO: Implement chapter timestamp extraction (currently set to 0).
     *
     * @param mediafile The media file to populate with chapter information
     */
    override suspend fun analyzeChapters(mediafile: MediaFile) {
        if (!isInitialized) return

        withContext(Dispatchers.Main) {
            mediafile.chapters.clear()
            val chapters = (vlcPlayer?.chapterDescriptionsOfTitle(0) as? NSArray)?.toKList<String>()

            chapters?.forEachIndexed { index, name ->
                if (name.isNullOrBlank()) return@forEachIndexed

                mediafile.chapters.add(
                    Chapter(
                        index = index,
                        name = name,
                        timestamp = 0 // TODO: Implement chapter timestamps
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
            vlcPlayer?.setCurrentChapterIndex(chapter.index)
        }
    }

    /**
     * Skips to the next chapter in the media.
     */
    override suspend fun skipChapter() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            vlcPlayer?.nextChapter()
        }
    }

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
        vlcPlayer?.setMedia(vlcMedia)
    }

    override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
        vlcMedia = NSURL.URLWithString(location.url)?.let { VLCMedia(uRL = it) }
        vlcPlayer?.setMedia(vlcMedia)
    }

    override suspend fun parseMedia(media: MediaFile) {
        vlcMedia?.synchronousParse()

        vlcMedia?.length?.numberValue?.doubleValue?.toLong()?.let {
            val dur = if (it < 0) 0 else it
            playerManager.timeFullMillis.value = dur
            media.fileDuration = dur / 1000.0
        }
        super.parseMedia(media)
    }

    /**
     * Pauses playback on the main thread.
     */
    override suspend fun pause() {
        if (!isInitialized) return
        println("VLC PLAYER DELEGATE: ${vlcPlayer!!.delegate}")

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
            vlcPlayer?.play()
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

    /**
     * Seeks to a specific position in the media.
     *
     * @param toPositionMs The target position in milliseconds
     */
    override fun seekTo(toPositionMs: Long) {
        super.seekTo(toPositionMs)
        playerScopeMain.launch(Dispatchers.Main.immediate) {
            vlcPlayer?.setTime(toPositionMs.toVLCTime())
        }
    }

    /**
     * Gets the current playback position.
     *
     * @return Current position in milliseconds
     */
    override fun currentPositionMs(): Long {
        return vlcPlayer?.time?.value()?.longValue ?: 0L
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
            // Available aspect ratio options
            val aspectRatios = listOf(
                "1:1", "4:3", "16:9", "16:10", "2.21:1", "2.35:1"
                // Add more aspect ratios as needed
            )

            // Read the current aspect ratio
            val currentAspectRatio = vlcPlayer?.videoAspectRatio()?.toKString()

            // Find the index of the current aspect ratio in the list
            val currentIndex = if (currentAspectRatio != null) {
                aspectRatios.indexOf(currentAspectRatio)
            } else {
                -1 // If current aspect ratio is null, set it to -1
            }

            // Calculate the index of the next aspect ratio
            val nextIndex = (currentIndex + 1) % aspectRatios.size

            // Get the next aspect ratio
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

        viewmodel.osdManager.dispatchOSD {
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
     * Converts an NSArray to a Kotlin List for working with VLC track data.
     *
     * @param T The element type
     * @receiver NSArray to convert
     * @return List of elements from the array
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> NSArray.toKList(): List<T?> {
        return List(count.toInt()) { index ->
            objectAtIndex(index.toULong()) as? T
        }
    }


    /**
     * Delegate for receiving VLC media player state change notifications.
     *
     * Monitors playback state changes and notifies the server about play/pause events.
     * Also detects when playback ends to trigger playlist advancement.
     */
    inner class VlcDelegate : NSObject(), VLCMediaPlayerDelegateProtocol {
        /**
         * Called when the media player's state changes.
         *
         * Updates local playback state and sends state changes to the server
         * for synchronization. Handles end-of-playback for playlist advancement.
         *
         * @param aNotification Notification containing state change information
         */
        override fun mediaPlayerStateChanged(aNotification: NSNotification) {
            if (runBlocking { hasMedia() }) {
                val isPlaying = vlcPlayer?.state != VLCMediaPlayerState.VLCMediaPlayerStatePaused
                viewmodel.playerManager.isNowPlaying.value = isPlaying // Just to inform UI

                // Tell server about playback state change
                if (!viewmodel.isSoloMode) {
                    viewmodel.actionManager.sendPlayback(isPlaying)
                }

                if (vlcPlayer?.state == VLCMediaPlayerState.VLCMediaPlayerStateEnded) {
                    onPlaybackEnded()
                }
            }
        }
    }

    /********** Volume Control **********/

    /**
     * Maximum volume level (0-100 scale).
     */
    private val MAX_VOLUME = 100

    override fun getMaxVolume() = MAX_VOLUME

    override fun getCurrentVolume(): Int = (vlcPlayer?.pitch?.times(MAX_VOLUME))?.roundToInt() ?: 0

    override fun changeCurrentVolume(v: Int) {
        val clampedVolume = v.toFloat().coerceIn(0.0f, MAX_VOLUME.toFloat()) / MAX_VOLUME
        vlcPlayer?.setPitch(clampedVolume)
    }
}