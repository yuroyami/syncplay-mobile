package app.player.avplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import app.player.PlayerEngine
import app.player.PlayerImpl
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import app.player.PlayerImpl.TrackType
import app.player.models.Track
import app.room.OSDCategory
import app.room.RoomViewmodel
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.DrawableResource
import platform.AVFoundation.AVAsset
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
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
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
import platform.CoreGraphics.CGRect
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSError
import platform.Foundation.NSKeyValueObservingOptionNew
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.Foundation.addObserver
import platform.Foundation.removeObserver
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.foundation.NSKeyValueObservingProtocol
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_aspect_fill
import syncplaymobile.shared.generated.resources.room_aspect_fit
import syncplaymobile.shared.generated.resources.room_aspect_stretch
import syncplaymobile.shared.generated.resources.room_playback_error
import syncplaymobile.shared.generated.resources.swift
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * AVPlayer engine - Apple's native media player (AVFoundation).
 *
 * Stable and battery-efficient with native Picture-in-Picture, but limited to Apple-supported
 * formats (mainly MP4/HLS/m3u8) and supports no external subtitles. Marked experimental and
 * not the default because of the narrow format support.
 */
object AVPlayerEngine: PlayerEngine {
    override val isAvailable: Boolean = true
    override val isDefault: Boolean = false
    override val isSystem: Boolean = true
    override val name: String = "AVPlayer"
    override val img: DrawableResource = Res.drawable.swift

    override fun createImpl(viewmodel: RoomViewmodel) = AVPlayerImpl(viewmodel)

    class AVPlayerImpl(viewmodel: RoomViewmodel): PlayerImpl(viewmodel, this@AVPlayerEngine) {
        var avPlayer: AVPlayer? = null

        /** The one render surface: a sublayer of the container, and the PiP controller's source. */
        var avPlayerLayer: AVPlayerLayer? = null

        /** The rate playback should run at; applied on play() so a paused player is never started by a speed change. */
        private var desiredRate: Float = 1f

        private var avContainer: UIView? = null

        private var avMedia: AVPlayerItem? = null

        val observer = AVPlayerObserver()

        override val trackerJobInterval: Duration
            get() = 250.milliseconds

        /**
         * Whether [observer] is currently registered on [avPlayer]. Each `inject*Impl` creates
         * a fresh `AVPlayer`, so the observer must be detached from the old instance and
         * re-attached to the new one on every media switch.
         */
        private var observerAttached = false

        /**
         * Starts progress tracking. The KVO `timeControlStatus` observer is not attached here:
         * `avPlayer` is still null at this point (created lazily in [injectVideoFileImpl] /
         * [injectVideoURLImpl]). Attachment happens in [attachTimeControlObserver] after each
         * player instance is created.
         */
        override fun initialize() {
            startTrackingProgress()
        }

        /**
         * Registers [observer] on the current [avPlayer] for `timeControlStatus` KVO events,
         * guarded against double-registration by [observerAttached].
         *
         * Essential on iPad where pause/play can come from system controls (Picture-in-Picture,
         * Control Center, lock screen, external keyboard spacebar, AirPlay). Without it,
         * [app.player.PlayerManager.isNowPlaying] only updates on the polling tracker tick,
         * leaving the play button stale and delaying pause-state propagation to peers.
         */
        private fun attachTimeControlObserver() {
            if (observerAttached) return
            avPlayer?.addObserver(
                observer = observer,
                forKeyPath = "timeControlStatus",
                options = NSKeyValueObservingOptionNew,
                context = null
            )
            observerAttached = (avPlayer != null)
        }

        /**
         * Removes the [AVPlayerObserver] from the current [avPlayer] if previously attached.
         * Must be called BEFORE reassigning [avPlayer] to a new instance (otherwise the old
         * player leaks its observer registration).
         */
        private fun detachTimeControlObserver() {
            if (!observerAttached) return
            try { avPlayer?.removeObserver(observer, "timeControlStatus") } catch (_: Throwable) { }
            observerAttached = false
        }


        /**
         * KVO Observer for AVPlayer timeControlStatus changes
         */
        inner class AVPlayerObserver : NSObject(), NSKeyValueObservingProtocol {

            @OptIn(ExperimentalForeignApi::class)
            override fun observeValueForKeyPath(
                keyPath: String?,
                ofObject: Any?,
                change: Map<Any?, *>?,
                context: CPointer<*>?
            ) {
                when (keyPath) {
                    "timeControlStatus" -> {
                        // Only the genuine Playing state counts as playing. The third status,
                        // WaitingToPlayAtSpecifiedRate (buffering/stalled), must not: while the
                        // room is paused AVPlayer can briefly enter it (e.g. a programmatic rate
                        // change), and treating that as playing would broadcast a phantom unpause.
                        val isPlaying = avPlayer?.timeControlStatus == AVPlayerTimeControlStatusPlaying

                        // The same third status, told to the room as a waiting indicator only.
                        viewmodel.playerManager.isBuffering.value =
                            avPlayer?.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate

                        // A failed player or item pauses on its own; that is local news only.
                        val failure = avPlayer?.error ?: avMedia?.error
                        if (!isPlaying && failure != null) {
                            viewmodel.protocol.noteExpectedPlaybackState(paused = true)
                            if (failure !== reportedFailure) {
                                reportedFailure = failure
                                val reason = failure.localizedDescription
                                viewmodel.dispatchOSD(OSDCategory.WARNING) { getString(Res.string.room_playback_error, reason) }
                                viewmodel.dispatcher.broadcastMessage(isChat = false, isError = true) {
                                    getString(Res.string.room_playback_error, reason)
                                }
                            }
                        }

                        viewmodel.playerManager.isNowPlaying.value = isPlaying
                    }
                }
            }
        }

        /** The last failure told to the user, so one error is not announced on every KVO tick. */
        private var reportedFailure: NSError? = null

        /** Re-attaches the current [avPlayer] to the render layer after a media switch. */
        private fun hookPlayerAgain() {
            avPlayerLayer?.player = avPlayer
        }

        override suspend fun destroy() {
            if (!isInitialized) return
            // Destroy contract shared with the Android engines: drop the guard first, then
            // cancel the supervisor so the 250ms position tracker stops polling the
            // about-to-be-nulled player and stops retaining the RoomViewmodel after room exit.
            isInitialized = false
            playerSupervisorJob.cancel()

            avPlayer?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }

            detachTimeControlObserver()
            avPlayer?.pause()
            avPlayerLayer?.player = null
            avPlayerLayer?.removeFromSuperlayer()
            avPlayerLayer = null
            avMedia = null
            avPlayer = null
            avContainer = null
        }

        /** AVPlayer exposes no user-configurable settings. */
        override suspend fun configurableSettings() = null

        /**
         * Renders the AVPlayer video surface inside a UIKitView. Resizes are applied inside a
         * Core Animation transaction with implicit actions disabled to avoid frame animations.
         */
        @OptIn(ExperimentalForeignApi::class)
        @Composable
        override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) {
            UIKitView(
                modifier = modifier,
                factory = remember {
                    factorylambda@{
                        // The layer is the picture: added once as the container's sublayer, it is
                        // what videoGravity resizes and what the PiP controller is built around.
                        val container = UIView().also { it.setBackgroundColor(UIColor.blackColor) }
                        val layer = AVPlayerLayer().also { it.videoGravity = AVLayerVideoGravityResizeAspect }
                        container.layer.addSublayer(layer)
                        avPlayerLayer = layer
                        avContainer = container
                        initialize()
                        isInitialized = true
                        onPlayerReady()
                        return@factorylambda container
                    }
                },
                onResize = { view: UIView, rect: CValue<CGRect> ->
                    CATransaction.begin()
                    CATransaction.setValue(true, kCATransactionDisableActions)
                    view.layer.setFrame(rect)
                    avPlayerLayer?.setFrame(view.layer.bounds)
                    CATransaction.commit()
                },
                update = { }
            )
        }

        override suspend fun hasMedia(): Boolean {
            if (!isInitialized) return false
            return avPlayer?.currentItem != null
        }

        /** Playing means rate > 0 with no current error. */
        override suspend fun isPlaying(): Boolean {
            if (!isInitialized) return false
            return (avPlayer?.rate() ?: 0f) > 0f && avPlayer?.error == null
        }

        /**
         * Populates [mediafile] with audio and subtitle tracks from AVFoundation media selection
         * groups, then auto-selects tracks whose language tag matches the audio/cc preferences.
         */
        override suspend fun analyzeTracks(mediafile: MediaFile) {
            if (avPlayer == null || avMedia == null || !isInitialized) return

            viewmodel.media?.tracks?.clear()

            //Groups
            val asset = avPlayer?.currentItem?.asset ?: return
            val characteristics = asset.availableMediaCharacteristicsWithMediaSelectionOptions.map { it as AVMediaCharacteristic }
            characteristics.forEach {
                val group = asset.mediaSelectionGroupForMediaCharacteristic(it)
                group?.options?.map { op -> op as AVMediaSelectionOption }?.forEachIndexed { i, option ->

                    if (option.mediaType == AVMediaTypeText || option.mediaType == AVMediaTypeAudio) {
                        mediafile.tracks.add(
                            AvTrack(
                                sOption = option,
                                sGroup = group,
                                name = option.displayName + " [${option.extendedLanguageTag}]",
                                index = i,
                                type = if (option.mediaType == AVMediaTypeAudio) TrackType.AUDIO else TrackType.SUBTITLE,
                                selected = group.defaultOption == option
                            )
                        )
                    }
                }
            }

            applyPreferredLanguages(mediafile)
        }

        /**
         * Selects an audio or subtitle track via AVFoundation's media selection API. A null
         * [track] disables the given [type], but only if its selection group allows empty selection.
         */
        override suspend fun selectTrack(track: Track?, type: TrackType) {
            if (!isInitialized) return

            playerManager.currentTrackChoices.remember(type, track)
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
                        TrackType.AUDIO -> {
                            val isAudio = group?.options?.any { (it as? AVMediaSelectionOption)?.mediaType == AVMediaTypeAudio }
                            if (isAudio == true) {
                                groupInQuestion = group
                            }
                        }

                        TrackType.SUBTITLE -> {
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

        /** Hands the remembered audio and subtitle picks back to the engine after a reload. */
        override suspend fun reapplyTrackChoices() {
            if (!isInitialized) return
            reapplyIndexedTrackChoices()
        }

        /** AVPlayer cannot load external subtitles; the base class turns the failure into the subtitle error notice. */
        override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) {
            throw UnsupportedOperationException("AVPlayer does not support external subtitles")
        }

        override suspend fun injectVideoFileImpl(location: MediaFileLocation.Local) {
            // Detach observer from the previous AVPlayer (if any) BEFORE replacing the
            // reference, otherwise the old instance leaks its KVO registration.
            detachTimeControlObserver()
            // Security scope is held centrally by PlayerImpl for the playback lifetime.
            val nsUrl = location.file.nsUrl
            val asset = AVAsset.assetWithURL(nsUrl)
            avMedia = AVPlayerItem(asset)
            avPlayer = AVPlayer.playerWithPlayerItem(avMedia)
            attachTimeControlObserver()
        }

        override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
            detachTimeControlObserver()
            val nsUrl = NSURL.URLWithString(location.url) ?: throw Exception()
            avMedia = AVPlayerItem(uRL = nsUrl)
            avPlayer = AVPlayer.playerWithPlayerItem(avMedia)
            attachTimeControlObserver()
        }

        override suspend fun parseMedia(media: MediaFile) {
            hookPlayerAgain()

            withTimeoutOrNull(10.seconds) {
                while (avMedia?.status != AVPlayerItemStatusReadyToPlay) {
                    delay(250)
                }
            }

            //File is loaded, get duration and declare file
            avMedia!!.asset.duration.toMillis().let { dur ->
                val actualDur = if (dur < 0) 0 else dur
                playerManager.timeFullMillis.value = actualDur
                playerManager.media.value?.fileDuration = actualDur / 1000.0
            }

            super.parseMedia(media)
        }

        override suspend fun pause() {
            if (!isInitialized) return
            avPlayer?.pause()
        }

        override suspend fun play() {
            if (!isInitialized) return
            // Setting a positive rate is how AVPlayer plays, so the desired speed rides along.
            avPlayer?.rate = desiredRate
        }

        override suspend fun setSpeed(speed: Double) {
            if (!isInitialized) return
            desiredRate = speed.toFloat()
            // On AVPlayer a non-zero rate IS play: a speed change while paused must stay pending.
            if ((avPlayer?.rate ?: 0f) > 0f) avPlayer?.rate = desiredRate
        }

        /** False for live streams (no seekable time ranges). */
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

        /** Current playback position in milliseconds. */
        @OptIn(ExperimentalForeignApi::class)
        override fun currentPositionMs(): Long {
            if (!isInitialized) return 0L

            return avPlayer?.currentTime()?.toMillis() ?: 0L
        }

        /**
         * Cycles the video gravity through Resize (stretch), ResizeAspect (letterbox), and
         * ResizeAspectFill (crop), returning the name of the newly applied mode.
         */
        override suspend fun switchAspectRatio(): String {
            if (!isInitialized) return ""

            val scales = listOf(
                AVLayerVideoGravityResize,
                AVLayerVideoGravityResizeAspect,
                AVLayerVideoGravityResizeAspectFill
            )
            val current = avPlayerLayer?.videoGravity
            val currentIndex = if (current != null) scales.indexOf(current) else -1
            val nextIndex = (currentIndex + 1) % scales.size
            avPlayerLayer?.videoGravity = scales[nextIndex]
            return when (nextIndex) {
                0 -> getString(Res.string.room_aspect_stretch)
                1 -> getString(Res.string.room_aspect_fit)
                else -> getString(Res.string.room_aspect_fill)
            }
        }

        /** Not implemented: AVPlayer has no subtitle size control. */
        override suspend fun changeSubtitleSize(newSize: Int) {
            //TODO
        }

        override val supportsChapters = false

        override suspend fun analyzeChapters(mediafile: MediaFile) = Unit

        /** Converts an AVFoundation CMTime to milliseconds. */
        private fun CValue<CMTime>.toMillis(): Long {
            return CMTimeGetSeconds(this).times(1000.0).roundToLong()
        }


        /** AVPlayer's volume is 0.0 to 1.0 and stops there: no gain rung. */
        override fun getEngineVolume(): Int = (avPlayer?.volume?.times(100))?.roundToInt() ?: 0
        override fun setEngineVolume(percent: Int) {
            avPlayer?.setVolume(percent.coerceIn(0, 100) / 100f)
        }
    }
}