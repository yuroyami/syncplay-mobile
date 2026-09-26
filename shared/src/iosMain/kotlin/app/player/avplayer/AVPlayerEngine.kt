package app.player.avplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import app.i18n.Localization
import app.player.PlayerEngine
import app.player.PlayerImpl
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import platform.AVFoundation.tracks
import platform.AVFoundation.mediaType
import platform.AVFoundation.AVPlayerItemTrack
import platform.AVFoundation.AVMediaTypeVideo
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
import platform.AVFoundation.AVAudioTimePitchAlgorithmTimeDomain
import platform.AVFoundation.audioTimePitchAlgorithm
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
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
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
import platform.AVFoundation.rate
import platform.AVFoundation.seekToTime
import platform.AVFoundation.seekableTimeRanges
import platform.AVFoundation.currentMediaSelection
import platform.AVFoundation.selectMediaOption
import platform.AVFoundation.selectedMediaOptionInMediaSelectionGroup
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
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.addObserver
import platform.Foundation.removeObserver
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.foundation.NSKeyValueObservingProtocol
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.swift
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The AVPlayer engine: Apple's native media player from AVFoundation. An engine is one of the
 * video players the app can drive.
 *
 * AVPlayer is stable, uses little battery and has native Picture-in-Picture. It only plays
 * formats that Apple supports (mainly MP4 and HLS/m3u8), and it cannot load external subtitles.
 * The narrow format support is why it is not the default engine.
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

        /** The only render surface: a sublayer of the container, and the PiP controller's source. */
        var avPlayerLayer: AVPlayerLayer? = null

        /** The speed to use. [play] applies it, so a speed change never starts a paused player. */
        private var desiredRate: Float = 1f

        private var avContainer: UIView? = null

        private var avMedia: AVPlayerItem? = null

        val observer = AVPlayerObserver()

        override val trackerJobInterval: Duration
            get() = 250.milliseconds

        /**
         * Whether [observer] is registered on [avPlayer]. Each `inject*Impl` creates a new
         * `AVPlayer`, so on every media switch the observer must leave the old instance and
         * join the new one.
         */
        private var observerAttached = false

        /**
         * Starts progress tracking. The KVO (key-value observing) `timeControlStatus` observer is
         * not attached here, because `avPlayer` is still null at this point. [injectVideoFileImpl]
         * and [injectVideoURLImpl] create the player, then call [attachTimeControlObserver].
         */
        override fun initialize() {
            startTrackingProgress()
        }

        /**
         * Registers [observer] on the current [avPlayer] for `timeControlStatus` KVO events.
         * [observerAttached] prevents a double registration.
         *
         * This matters on iPad, where play and pause can come from system controls
         * (Picture-in-Picture, Control Center, lock screen, an external keyboard's space bar,
         * AirPlay). The progress tracker does not update [app.player.PlayerManager.isNowPlaying],
         * so without the observer the play button shows a stale state and the other users in the
         * room (the group of people watching together) never hear about the pause.
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
         * Removes [observer] from the current [avPlayer] if it is attached. Call this before
         * [avPlayer] gets a new instance, or the old player leaks its observer registration.
         */
        private fun detachTimeControlObserver() {
            if (!observerAttached) return
            try { avPlayer?.removeObserver(observer, "timeControlStatus") } catch (_: Throwable) { }
            observerAttached = false
        }

        /** The end-of-item registration for the current item, kept so it can be removed. */
        private var endOfItemObserver: Any? = null

        /**
         * Tells the base class when the item finishes.
         *
         * AVPlayer has no state callback that says "ended": KVO on `timeControlStatus` reports a
         * finished item as just paused. Without this notification, `onPlaybackEnded` never runs
         * on this engine. The shared playlist (the file list that everyone in a room follows)
         * then stops at the end of every entry, while the other engines move the room on.
         */
        private fun attachEndOfItemObserver() {
            detachEndOfItemObserver()
            val item = avMedia ?: return
            endOfItemObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                name = AVPlayerItemDidPlayToEndTimeNotification,
                `object` = item,
                queue = NSOperationQueue.mainQueue,
            ) { _ ->
                playerManager.isNowPlaying.value = false
                onPlaybackEnded()
            }
        }

        private fun detachEndOfItemObserver() {
            endOfItemObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
            endOfItemObserver = null
        }


        /** KVO observer for changes to the AVPlayer `timeControlStatus`. */
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
                        val status = avPlayer?.timeControlStatus
                        val isPlaying = status == AVPlayerTimeControlStatusPlaying

                        // The third status, WaitingToPlayAtSpecifiedRate, is a stalled buffer or
                        // a start that waits for data. It only drives the buffering indicator.
                        val waiting = status == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
                        viewmodel.playerManager.isBuffering.value = waiting

                        // A failed player or item pauses by itself. That pause stays local and is
                        // not sent to the room.
                        val failure = avPlayer?.error ?: avMedia?.error
                        if (!isPlaying && failure != null) {
                            viewmodel.protocol.noteExpectedPlaybackState(paused = true)
                            if (failure !== reportedFailure) {
                                reportedFailure = failure
                                val reason = failure.localizedDescription
                                viewmodel.dispatchOSD(OSDCategory.WARNING) { Localization.strings.roomPlaybackError(reason) }
                                viewmodel.dispatcher.broadcastMessage(isChat = false, isError = true) {
                                    Localization.strings.roomPlaybackError(reason)
                                }
                                onEngineLoadFailed()
                            }
                        }

                        // A stall is not a pause, and the room must never hear it as one. So while
                        // AVPlayer waits, the play state stays as it was, as on the other engines.
                        // Counting the wait as playing would send a false unpause to a paused room.
                        if (!waiting || failure != null) viewmodel.playerManager.isNowPlaying.value = isPlaying
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
            /* Before the guard, on purpose. The injection path registers this observer, not
             * initialize(). An engine that never became initialized would otherwise leave
             * NSNotificationCenter holding the block, and through it the whole room, for the life
             * of the process. */
            detachEndOfItemObserver()
            if (!isInitialized) return
            // Destroy contract shared with the Android engines: drop the guard first, then
            // cancel the supervisor. The 250 ms position tracker then stops polling the player
            // that is about to be cleared, and stops holding the RoomViewmodel after room exit.
            isInitialized = false
            playerSupervisorJob.cancel()

            // The AVPlayer is not a notification observer. The only NSNotificationCenter
            // registration this engine holds is the end-of-item one, removed above.
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
                        // Add the AVPlayerLayer once, as the container's sublayer. videoGravity
                        // resizes this layer, and the PiP controller is built on it.
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
         * Fills [mediafile] with the video tracks of the current item and the audio and subtitle
         * tracks of its media selection groups. Then it selects the tracks whose language matches
         * the audio and subtitle preferences, unless the user already chose a track.
         */
        override suspend fun analyzeTracks(mediafile: MediaFile) {
            if (avPlayer == null || avMedia == null || !isInitialized) return

            viewmodel.media?.tracks?.clear()

            avPlayer?.currentItem?.tracks?.filterIsInstance<AVPlayerItemTrack>()
                ?.filter { it.assetTrack?.mediaType == AVMediaTypeVideo }
                ?.forEachIndexed { i, itemTrack ->
                    mediafile.tracks.add(AvVideoTrack(i, itemTrack.enabled))
                }

            // Audio and subtitle tracks come from the media selection groups.
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
                                name = option.displayName,
                                index = i,
                                type = if (option.mediaType == AVMediaTypeAudio) TrackType.AUDIO else TrackType.SUBTITLE,
                                // Compare with the current selection, not the group's default
                                // option, or the check mark never moves off the first track.
                                selected = avPlayer?.currentItem?.currentMediaSelection
                                    ?.selectedMediaOptionInMediaSelectionGroup(group) == option
                            )
                        )
                    }
                }
            }
        }

        /**
         * Selects a video, audio or subtitle track. A video track is picked by enabling only its
         * player item track. Audio and subtitle tracks go through AVFoundation's media selection
         * API, where a null [track] turns the [type] off if its group allows an empty selection.
         */
        override suspend fun selectTrack(track: Track?, type: TrackType) {
            if (!isInitialized) return

            playerManager.currentTrackChoices.remember(type, track)
            if (type == TrackType.VIDEO) {
                avPlayer?.currentItem?.tracks?.filterIsInstance<AVPlayerItemTrack>()
                    ?.filter { it.assetTrack?.mediaType == AVMediaTypeVideo }
                    ?.forEachIndexed { i, itemTrack -> itemTrack.enabled = i == track?.index }
                return
            }
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
                        TrackType.VIDEO -> Unit
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

        /** Gives the remembered track choices back to the engine after a reload. */
        override suspend fun reapplyTrackChoices() {
            if (!isInitialized) return
            reapplyIndexedTrackChoices()
        }

        /**
         * AVPlayer cannot load external subtitles. The base class turns this failure into the
         * subtitle error notice.
         */
        override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) {
            throw UnsupportedOperationException("AVPlayer does not support external subtitles")
        }

        override suspend fun injectVideoFileImpl(location: MediaFileLocation.Local) {
            // Detach the observer from the previous AVPlayer (if any) before the reference
            // changes, or the old instance leaks its KVO registration.
            detachTimeControlObserver()
            // PlayerImpl holds the file's security scope for the whole playback.
            val nsUrl = location.file.nsUrl
            val asset = AVAsset.assetWithURL(nsUrl)
            avMedia = AVPlayerItem(asset).also(::allowAnyRate)
            avPlayer = AVPlayer.playerWithPlayerItem(avMedia)
            attachTimeControlObserver()
            attachEndOfItemObserver()
        }

        override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
            detachTimeControlObserver()
            val nsUrl = NSURL.URLWithString(location.url)
                ?: throw IllegalArgumentException("Not a URL AVPlayer can open: ${location.url}")
            avMedia = AVPlayerItem(uRL = nsUrl).also(::allowAnyRate)
            avPlayer = AVPlayer.playerWithPlayerItem(avMedia)
            attachTimeControlObserver()
            attachEndOfItemObserver()
        }

        /**
         * Lets the item play at any rate. iOS picks a low-quality pitch algorithm by default, and
         * that one snaps the rate to a few fixed values, so the room's speed corrections (0.95,
         * and the half-percent nudge) would not take effect.
         */
        private fun allowAnyRate(item: AVPlayerItem) {
            item.audioTimePitchAlgorithm = AVAudioTimePitchAlgorithmTimeDomain
        }

        /* No onClosing override. The readiness wait in parseMedia checks isClosing on every turn,
         * and that check is what ends the wait on exit. The wait can last ten seconds while it
         * holds the media transaction mutex, and teardown waits for the same mutex. Without the
         * check, leaving a room during a slow load blocks the exit and queues the next room behind
         * the teardown. Cancelling the supervisor in onClosing would kill the engine's scopes while
         * isInitialized is still true, which is the reverse of the destroy contract. */

        // parseMedia reports the file itself, after the item is ready, so the base does not.
        override val announcesFileLoadViaEvent: Boolean = true

        override suspend fun parseMedia(media: MediaFile) {
            hookPlayerAgain()

            withTimeoutOrNull(10.seconds) {
                while (avMedia?.status != AVPlayerItemStatusReadyToPlay) {
                    if (!isInitialized || isClosing) return@withTimeoutOrNull
                    delay(250)
                }
            }

            // The item is ready, or the wait ended. A live stream has no duration, and still
            // opens. An item that failed is not announced: its error is already on screen.
            val item = avMedia
            if (item?.status == AVPlayerItemStatusFailed) {
                onEngineLoadFailed()
            } else if (item != null) {
                onEngineFileReady(item.asset.duration.toMillis().coerceAtLeast(0L))
            }

            super.parseMedia(media)
        }

        override suspend fun pause() {
            if (!isInitialized) return
            avPlayer?.pause()
        }

        override suspend fun play() {
            if (!isInitialized) return
            // A positive rate is how AVPlayer plays, so this also applies the desired speed.
            avPlayer?.rate = desiredRate
        }

        override suspend fun setSpeed(speed: Double) {
            if (!isInitialized) return
            desiredRate = speed.toFloat()
            // A non-zero rate means playing, so while paused the new speed waits for play().
            if ((avPlayer?.rate ?: 0f) > 0f) avPlayer?.rate = desiredRate
        }

        /** False when the current item has no seekable time ranges, as with a live stream. */
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

        /**
         * Cycles the video gravity through Resize (stretch), ResizeAspect (letterbox) and
         * ResizeAspectFill (crop). Returns the localized name of the new mode.
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
                0 -> Localization.strings.roomAspectStretch
                1 -> Localization.strings.roomAspectFit
                else -> Localization.strings.roomAspectFill
            }
        }

        /** Not implemented for AVPlayer. */
        override suspend fun changeSubtitleSize(newSize: Int) {
        }

        override val supportsVideoTrackSelection = true
        override val supportsChapters = false

        override suspend fun analyzeChapters(mediafile: MediaFile) = Unit

        /** 0 for an indefinite time, such as a live stream's duration, which has no seconds. */
        private fun CValue<CMTime>.toMillis(): Long {
            val seconds = CMTimeGetSeconds(this)
            return if (seconds.isFinite()) (seconds * 1000.0).roundToLong() else 0L
        }


        /** AVPlayer's volume runs from 0.0 to 1.0, so this engine has no gain above 100%. */
        override fun getEngineVolume(): Int = (avPlayer?.volume?.times(100))?.roundToInt() ?: 0
        override fun setEngineVolume(percent: Int) {
            avPlayer?.setVolume(percent.coerceIn(0, 100) / 100f)
        }
    }
}