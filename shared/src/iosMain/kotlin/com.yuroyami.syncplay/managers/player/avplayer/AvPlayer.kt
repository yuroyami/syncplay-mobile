package com.yuroyami.syncplay.managers.player.avplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.yuroyami.syncplay.managers.player.ApplePlayerEngine
import com.yuroyami.syncplay.managers.player.BasePlayer
import com.yuroyami.syncplay.managers.settings.ExtraSettingBundle
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

class AvPlayer(viewmodel: RoomViewmodel) : BasePlayer(viewmodel, ApplePlayerEngine.AVPlayer) {

    /*-- Exoplayer-related properties --*/
    var avPlayer: AVPlayer? = null
    private lateinit var avView: AVPlayerViewController
    var avPlayerLayer: AVPlayerLayer? = null
    private var avContainer: UIView? = null

    private var avMedia: AVPlayerItem? = null

    override val trackerJobInterval: Duration
        get() = 250.milliseconds

    override val canChangeAspectRatio: Boolean
        get() = true

    override fun initialize() {
        //avView.view.setBackgroundColor(UIColor.clearColor())
        //avPlayerLayer!!.setBackgroundColor(UIColor.clearColor().CGColor)
        avView.showsPlaybackControls = false

        startTrackingProgress()
    }

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

    override suspend fun destroy() {
        if (!isInitialized) return

        avPlayer?.pause()
        avPlayer?.finalize()
        avPlayer = null
        avContainer = null
    }

    override suspend fun configurableSettings(): ExtraSettingBundle? = null

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

    override suspend fun hasMedia(): Boolean {
        if (!isInitialized) return false
        return avPlayer?.currentItem != null
    }

    override suspend fun isPlaying(): Boolean {
        if (!isInitialized) return false
        return (avPlayer?.rate() ?: 0f) > 0f && avPlayer?.error == null
    }

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
//
//
//        // Extract audio tracks information
//        val audioTracks = avMedia!!.tracks.map { it as AVAssetTrack }.filter { it.mediaType == AVMediaTypeAudio }
//        audioTracks.forEachIndexed { i, track ->
//            mediafile.audioTracks.add(
//                object : Track {
//                    override val name = track.languageCode ?: "Track ${i + 1}"
//                    override val index = i
//                    override val type = TRACKTYPE.AUDIO
//                    override val selected = mutableStateOf(track.selected)
//                }
//            )
//        }
//
//        val subtitleTracks = avMedia!!.tracks.map { it as AVAssetTrack }.filter { it.mediaType == AVMediaTypeText }
//        subtitleTracks.forEachIndexed { i, track ->
//            mediafile.subtitleTracks.add(
//                object : Track {
//                    override val name = track.languageCode ?: "Subtitle ${i + 1}"
//                    override val index = i
//                    override val type = TRACKTYPE.SUBTITLE
//                    override val selected = mutableStateOf(track.selected)
//                }
//            )
//        }
    }

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

    override suspend fun reapplyTrackChoices() {
        if (!isInitialized) return
        //TODO("Not yet implemented")
    }

    override suspend fun loadExternalSubImpl(uri: String, extension: String) {
        if (!isInitialized) return

        viewmodel.osdManager.dispatchOSD {
            "Player does not support external subtitles."
            //TODO LOCALIZE
        }
    }

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
                    declareFile()
                }
            }

            if (isTimeout == null) throw Exception("Media not loaded by AVPlayer")
        }
    }

    override suspend fun pause() {
        if (!isInitialized) return
        avPlayer?.pause()
    }

    override suspend fun play() {
        if (!isInitialized) return
        avPlayer?.play()
    }

    override suspend fun isSeekable(): Boolean {
        if (!isInitialized) return false
        return avPlayer?.currentItem?.seekableTimeRanges?.isNotEmpty() == true
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun seekTo(toPositionMs: Long) {
        if (!isInitialized) return

        super.seekTo(toPositionMs)
        avPlayer?.seekToTime(CMTimeMake(toPositionMs, 1000))
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun currentPositionMs(): Long {
        if (!isInitialized) return 0L

        return avPlayer?.currentTime()?.toMillis() ?: 0L
    }

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

    override suspend fun changeSubtitleSize(newSize: Int) {
        //TODO
    }

    //TODO
    override val supportsChapters = false
    override suspend fun analyzeChapters(mediafile: MediaFile) = Unit
    override suspend fun jumpToChapter(chapter: Chapter) = Unit
    override suspend fun skipChapter() = Unit


    private fun CValue<CMTime>.toMillis(): Long {
        return CMTimeGetSeconds(this).times(1000.0).roundToLong()
    }

    interface AvTrack : Track {
        val sOption: AVMediaSelectionOption
        val sGroup: AVMediaSelectionGroup
    }

    private val MAX_VOLUME = 100
    override fun getMaxVolume() = MAX_VOLUME
    override fun getCurrentVolume(): Int = (avPlayer?.volume?.times(MAX_VOLUME))?.roundToInt() ?: 0
    override fun changeCurrentVolume(v: Int) {
        val clampedVolume = v.toFloat().coerceIn(0.0f, MAX_VOLUME.toFloat()) / MAX_VOLUME
        avPlayer?.setVolume(clampedVolume)
    }
}
