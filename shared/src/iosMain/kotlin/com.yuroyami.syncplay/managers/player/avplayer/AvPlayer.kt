package com.yuroyami.syncplay.managers.player.avplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.yuroyami.syncplay.managers.player.ApplePlayerEngine
import com.yuroyami.syncplay.managers.player.BasePlayer
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.Track
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFoundation.AVLayerVideoGravityResize
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaCharacteristic
import platform.AVFoundation.AVMediaSelectionGroup
import platform.AVFoundation.AVMediaSelectionOption
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeText
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.asset
import platform.AVFoundation.availableMediaCharacteristicsWithMediaSelectionOptions
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.mediaSelectionGroupForMediaCharacteristic
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.seekToTime
import platform.AVFoundation.seekableTimeRanges
import platform.AVFoundation.selectMediaOption
import platform.AVFoundation.setVolume
import platform.AVFoundation.timeControlStatus
import platform.AVFoundation.volume
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRect
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSKeyValueObservingOptionNew
import platform.Foundation.NSURL.Companion.URLWithString
import platform.Foundation.NSURL.Companion.fileURLWithPath
import platform.Foundation.addObserver
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView
import platform.darwin.NSObject
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * AVPlayer implementation for iOS using Apple's native AVFoundation framework.
 *
 * Provides stable, efficient media playback deeply integrated with iOS system features.
 * While more limited in codec support than VLC, AVPlayer excels in stability, battery
 * efficiency, and native iOS integration.
 *
 * ## Limitations
 * - **Extremely** Limited codec support (primarily MP4, HLS, m3u8)
 * - No external subtitle loading
 * - Chapter navigation not available
 *
 * ## Architecture
 * Uses AVPlayerViewController embedded in UIKitView for Compose integration.
 * The player layer is exposed for Picture-in-Picture functionality.
 *
 * @property viewmodel The parent RoomViewModel managing this player
 */
class AvPlayer(viewmodel: RoomViewmodel) : BasePlayer(viewmodel, ApplePlayerEngine.AVPlayer) {

    /**
     * Core AVPlayer instance handling media playback.
     */
    var avPlayer: AVPlayer? = null

    /**
     * View controller providing the player interface.
     */
    private lateinit var avView: AVPlayerViewController

    /**
     * Player layer for rendering video and enabling Picture-in-Picture.
     */
    var avPlayerLayer: AVPlayerLayer? = null

    /**
     * Container UIView hosting the player view controller.
     */
    private var avContainer: UIView? = null

    /**
     * Currently loaded media item.
     */
    private var avMedia: AVPlayerItem? = null

    override val trackerJobInterval: Duration
        get() = 250.milliseconds

    override val canChangeAspectRatio: Boolean
        get() = true

    /**
     * Initializes the AVPlayer view controller by disabling default playback controls
     * and starting progress tracking.
     */
    override fun initialize() {
        //avView.view.setBackgroundColor(UIColor.clearColor())
        //avPlayerLayer!!.setBackgroundColor(UIColor.clearColor().CGColor)
        avView.showsPlaybackControls = false

        startTrackingProgress()
    }

    /**
     * Hooks the AVPlayer instance to the view controller and layer.
     *
     * Sets up Key-Value Observing (KVO) for monitoring playback state changes.
     * Called after creating a new AVPlayer instance.
     */
    private fun hookPlayerAgain() {
        avView.player = avPlayer!!
        avPlayerLayer?.player = avPlayer

        avPlayer?.addObserver(
            observer = object: NSObject() {
                //todo
            },
            forKeyPath = "timeControlStatus",
            options = NSKeyValueObservingOptionNew,
            context = null
        )

        val status = avPlayer?.timeControlStatus()
    }

    /**
     * Cleans up AVPlayer resources and stops playback.
     */
    override suspend fun destroy() {
        if (!isInitialized) return

        avPlayer?.pause()
        avPlayer?.finalize()
        avPlayer = null
        avContainer = null
    }

    /**
     * Returns player-specific configuration settings.
     *
     * AVPlayer has no user-configurable settings currently.
     *
     * @return null (no custom settings)
     */
    override suspend fun configurableSettings() = null

    /**
     * Renders the AVPlayer video view within Compose.
     *
     * Creates the player layer and view controller, then wraps it in a UIKitView
     * for Compose integration. Handles view resizing with Core Animation transactions.
     *
     * @param modifier Compose modifier for layout and styling
     */
    @OptIn(ExperimentalForeignApi::class)
    @Composable
    override fun VideoPlayer(modifier: Modifier) {
        UIKitView(
            modifier = modifier,
            factory = remember {
                factorylambda@{
                    avPlayerLayer = AVPlayerLayer()
                    avView = AVPlayerViewController()
                    initialize()
                    avContainer = UIView()
                    avContainer!!.addSubview(avView.view)
                    return@factorylambda avContainer!!
                }
            },
            onResize = { view: UIView, rect: CValue<CGRect> ->
                CATransaction.begin()
                CATransaction.setValue(true, kCATransactionDisableActions)
                view.layer.setFrame(rect)
                avPlayerLayer?.setFrame(rect)
                avView.view.layer.frame = rect
                CATransaction.commit()
            },
            update = { }
        )
    }

    /**
     * Checks if any media is currently loaded.
     *
     * @return true if media is loaded, false otherwise
     */
    override suspend fun hasMedia(): Boolean {
        if (!isInitialized) return false
        return avPlayer?.currentItem != null
    }

    /**
     * Checks if playback is currently active.
     *
     * @return true if playing (rate > 0 and no error), false otherwise
     */
    override suspend fun isPlaying(): Boolean {
        if (!isInitialized) return false
        return (avPlayer?.rate() ?: 0f) > 0f && avPlayer?.error == null
    }

    /**
     * Analyzes and extracts available audio and subtitle tracks using media selection groups.
     *
     * AVFoundation uses media selection groups for track management. This method
     * extracts tracks from available characteristics and populates the media file's
     * track lists with displayable names and language tags.
     *
     * @param mediafile The media file to populate with track information
     */
    override suspend fun analyzeTracks(mediafile: MediaFile) {
        // Check if the AVPlayer is initialized
        if (avPlayer == null || avMedia == null) return

        viewmodel.media?.subtitleTracks?.clear()
        viewmodel.media?.audioTracks?.clear()

        //Groups
        val asset = avPlayer?.currentItem?.asset ?: return
        val characteristics = asset.availableMediaCharacteristicsWithMediaSelectionOptions.map { it as AVMediaCharacteristic }
        characteristics.forEach {
            loggy("Characteristic: $it")

            val group = asset.mediaSelectionGroupForMediaCharacteristic(it)
            group?.options?.map { op -> op as AVMediaSelectionOption }?.forEachIndexed { i, option ->
                loggy("Option: $option")

                val isSelected = group.defaultOption == option

                if (option.mediaType == AVMediaTypeText || option.mediaType == AVMediaTypeAudio) {
                    mediafile.audioTracks.add(
                        object : AvTrack {
                            override val sOption = option
                            override val sGroup: AVMediaSelectionGroup = group
                            override val name = option.displayName + " [${option.extendedLanguageTag}]"
                            override val index = i
                            override val type = if (option.mediaType == AVMediaTypeAudio) TRACKTYPE.AUDIO else TRACKTYPE.SUBTITLE
                            override val selected = mutableStateOf(isSelected)
                        }
                    )
                }
            }
        }
    }

    /**
     * Selects a specific audio or subtitle track for playback.
     *
     * Uses AVFoundation's media selection API to change tracks. Pass null to disable
     * a track type (if the selection group allows empty selection).
     *
     * @param track The track to select (must be AvTrack), or null to disable
     * @param type Whether this is an audio or subtitle track
     */
    override suspend fun selectTrack(track: Track?, type: TRACKTYPE) {
        if (!isInitialized) return

        val avtrack = track as? AvTrack

        if (avtrack != null) {
            avPlayer?.currentItem?.selectMediaOption(avtrack.sOption, avtrack.sGroup)
        } else {
            val asset = avPlayer?.currentItem?.asset ?: return
            val characteristics = asset.availableMediaCharacteristicsWithMediaSelectionOptions.map { it as AVMediaCharacteristic }
            var groupInQuestion: AVMediaSelectionGroup? = null
            characteristics.forEach { characteristic ->
                val group = asset.mediaSelectionGroupForMediaCharacteristic(characteristic)

                when (type) {
                    TRACKTYPE.AUDIO -> {
                        val isAudio = group?.options?.any { (it as? AVMediaSelectionOption)?.mediaType == AVMediaTypeAudio }
                        if (isAudio == true) {
                            groupInQuestion = group
                        }
                    }
                    TRACKTYPE.SUBTITLE -> {
                        val isSubtitle = group?.options?.any { (it as? AVMediaSelectionOption)?.mediaType == AVMediaTypeText }
                        if (isSubtitle == true) {
                            groupInQuestion = group
                        }
                    }
                }
            }

            if (groupInQuestion?.allowsEmptySelection == true) {
                avPlayer?.currentItem?.selectMediaOption(null, groupInQuestion!!)
            }
        }
    }

    /**
     * Reapplies previously selected track choices.
     *
     * TODO: Implement track choice persistence for AVPlayer.
     */
    override suspend fun reapplyTrackChoices() {
        if (!isInitialized) return
        //TODO("Not yet implemented")
    }

    /**
     * Attempts to load an external subtitle file.
     *
     * AVPlayer does not support external subtitle loading, so this displays
     * an error message to the user.
     *
     * @param uri The subtitle file URI (unused)
     * @param extension The subtitle file extension (unused)
     */
    override suspend fun loadExternalSubImpl(uri: String, extension: String) {
        if (!isInitialized) return

        viewmodel.osdManager.dispatchOSD {
            "Player does not support external subtitles."
            //TODO LOCALIZE
        }
    }

    /**
     * Loads and prepares a media file for playback.
     *
     * Creates an AVPlayerItem from either a URL or local file path, waits for the
     * media to become ready (with 10-second timeout), extracts duration, and declares
     * it to the server.
     *
     * @param media The media file to load
     * @param isUrl Whether the URI is a remote URL or local file path
     * @throws Exception if media fails to load within the timeout period
     */
    override suspend fun injectVideoImpl(media: MediaFile, isUrl: Boolean) {
        if (!isInitialized) return

        delay(500)

        media.uri?.let { it ->
            val nsUrl = when (isUrl) {
                true -> URLWithString(it)
                false -> fileURLWithPath(it)
            } ?: throw Exception()

            avMedia = AVPlayerItem(uRL = nsUrl)
            avPlayer = AVPlayer.playerWithPlayerItem(avMedia)

            hookPlayerAgain()

            val isTimeout = withTimeoutOrNull(10.seconds) {
                while (avMedia?.status != AVPlayerItemStatusReadyToPlay) {
                    delay(250)
                }

                //File is loaded, get duration and declare file
                avMedia!!.asset.duration.toMillis().let { dur ->
                    playerManager.timeFullMillis.value = if (dur < 0) 0 else dur

                    playerManager.media.value?.fileDuration = playerManager.timeFullMillis.value / 1000.0
                    announceFileLoaded()
                }
            }

            if (isTimeout == null) throw Exception("Media not loaded by AVPlayer")
        }
    }

    /**
     * Pauses playback.
     */
    override suspend fun pause() {
        if (!isInitialized) return
        avPlayer?.pause()
    }

    /**
     * Starts or resumes playback.
     */
    override suspend fun play() {
        if (!isInitialized) return
        avPlayer?.play()
    }

    /**
     * Checks if the current media supports seeking.
     *
     * @return true if seekable time ranges exist, false for live streams
     */
    override suspend fun isSeekable(): Boolean {
        if (!isInitialized) return false
        return avPlayer?.currentItem?.seekableTimeRanges?.isNotEmpty() == true
    }

    /**
     * Seeks to a specific position in the media.
     *
     * @param toPositionMs The target position in milliseconds
     */
    @OptIn(ExperimentalForeignApi::class)
    override fun seekTo(toPositionMs: Long) {
        if (!isInitialized) return

        super.seekTo(toPositionMs)
        avPlayer?.seekToTime(CMTimeMake(toPositionMs, 1000))
    }

    /**
     * Gets the current playback position.
     *
     * @return Current position in milliseconds
     */
    @OptIn(ExperimentalForeignApi::class)
    override fun currentPositionMs(): Long {
        if (!isInitialized) return 0L

        return avPlayer?.currentTime()?.toMillis() ?: 0L
    }

    /**
     * Cycles through available video scaling modes.
     *
     * Supports three modes:
     * - **Resize**: Stretches to fill (may distort)
     * - **ResizeAspect**: Fits while preserving aspect ratio (letterboxing)
     * - **ResizeAspectFill**: Fills while preserving aspect ratio (cropping)
     *
     * @return The name of the newly applied scaling mode
     */
    override suspend fun switchAspectRatio(): String {
        if (!isInitialized) return "NO PLAYER FOUND"

        val scales = listOf(
            AVLayerVideoGravityResize,
            AVLayerVideoGravityResizeAspect,
            AVLayerVideoGravityResizeAspectFill
        )
        val current = avPlayerLayer?.videoGravity
        val currentIndex = if (current != null) scales.indexOf(current) else -1
        val nextIndex = (currentIndex + 1) % scales.size
        val nextScale = scales[nextIndex]
        avPlayerLayer?.videoGravity = nextScale
        return nextScale!!
    }

    /**
     * Changes the subtitle font size.
     *
     * TODO: Implement subtitle size control for AVPlayer.
     *
     * @param newSize The new subtitle size
     */
    override suspend fun changeSubtitleSize(newSize: Int) {
        //TODO
    }

    /********** Chapter Navigation (Not Supported) **********/

    /**
     * AVPlayer does not support chapter navigation currently.
     */
    override val supportsChapters = false

    override suspend fun analyzeChapters(mediafile: MediaFile) = Unit
    override suspend fun jumpToChapter(chapter: Chapter) = Unit
    override suspend fun skipChapter() = Unit


    /**
     * Converts CMTime to milliseconds.
     *
     * @receiver CMTime value from AVFoundation
     * @return Time in milliseconds
     */
    private fun CValue<CMTime>.toMillis(): Long {
        return CMTimeGetSeconds(this).times(1000.0).roundToLong()
    }

    /**
     * Extended Track interface for AVPlayer tracks.
     *
     * Includes references to AVFoundation's media selection option and group
     * required for track switching operations.
     */
    interface AvTrack : Track {
        /** The AVFoundation media selection option representing this track */
        val sOption: AVMediaSelectionOption
        /** The media selection group this track belongs to */
        val sGroup: AVMediaSelectionGroup
    }

    /********** Volume Control **********/

    /**
     * Maximum volume level (0-100 scale).
     */
    private val MAX_VOLUME = 100

    override fun getMaxVolume() = MAX_VOLUME

    override fun getCurrentVolume(): Int = (avPlayer?.volume?.times(MAX_VOLUME))?.roundToInt() ?: 0

    override fun changeCurrentVolume(v: Int) {
        val clampedVolume = v.toFloat().coerceIn(0.0f, MAX_VOLUME.toFloat()) / MAX_VOLUME
        avPlayer?.setVolume(clampedVolume)
    }
}