package com.yuroyami.syncplay.managers.player.vlc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
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
import com.yuroyami.syncplay.managers.settings.ExtraSettingBundle
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.Track
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
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

class VlcPlayer(viewmodel: RoomViewmodel) : BasePlayer(viewmodel, ApplePlayerEngine.VLC) {
    private var libvlc: VLCLibrary? = null
    var vlcPlayer: VLCMediaPlayer? = null
    private var vlcView: UIView? = null
    private var vlcDelegate = VlcDelegate()
    private var vlcMedia: VLCMedia? = null

    var pipLayer: AVPlayerLayer? = null

    override val canChangeAspectRatio: Boolean
        get() = true

    override val supportsChapters: Boolean
        get() = false //todo: fix chapters

    override val trackerJobInterval: Duration
        get() = 1.seconds

    override suspend fun destroy() {
        if (!isInitialized) return

        vlcPlayer?.stop()
        vlcPlayer?.finalize()
        vlcPlayer = null
        vlcMedia = null
        libvlc?.finalize()
        libvlc = null
    }

    //TODO
    override suspend fun configurableSettings(): ExtraSettingBundle? = null

    @Composable
    override fun VideoPlayer(modifier: Modifier) {
        UIKitView(
            modifier = modifier,
            factory = {
                vlcView = UIView()
                vlcView!!.setBackgroundColor(UIColor.clearColor())
                libvlc = VLCLibrary(listOf("-vv"))
                vlcPlayer = VLCMediaPlayer(libvlc!!)
                vlcPlayer!!.drawable = vlcView

                /* Layer for PIP */
                pipLayer = AVPlayerLayer.playerLayerWithPlayer(null)
                pipLayer?.frame = vlcView!!.bounds
                pipLayer?.videoGravity = AVLayerVideoGravityResizeAspect
                pipLayer?.addSublayer(vlcView!!.layer)

                initialize()

                return@UIKitView vlcView!!
            },
            update = {},
            properties = UIKitInteropProperties(interactionMode = null),
        )
    }

    override fun initialize() {
        vlcPlayer!!.setDelegate(vlcDelegate)

        isInitialized = true

        startTrackingProgress()
    }

    override suspend fun hasMedia(): Boolean {
        if (!isInitialized) return false

        return vlcPlayer?.media != null
    }

    override suspend fun isPlaying(): Boolean {
        if (!isInitialized) return false

        return vlcPlayer?.isPlaying() == true
    }

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        if (!isInitialized) return
        if (vlcPlayer == null) return

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

    override suspend fun selectTrack(track: Track?, type: TRACKTYPE) {
        if (!isInitialized) return

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

    override suspend fun analyzeChapters(mediafile: MediaFile) {
        if (!isInitialized) return

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

    override suspend fun jumpToChapter(chapter: Chapter) {
        if (!isInitialized) return

        vlcPlayer?.setCurrentChapterIndex(chapter.index)
    }

    override suspend fun skipChapter() {
        if (!isInitialized) return

        vlcPlayer?.nextChapter()
    }

    override suspend fun reapplyTrackChoices() {
        if (!isInitialized) return

        //TODO Not implemented for iOS VLC player
    }

    override suspend fun loadExternalSubImpl(uri: String, extension: String) {
        if (!isInitialized) return

        vlcPlayer?.addPlaybackSlave(
            NSURL.URLWithString(uri)!!,
            VLCMediaPlaybackSlaveTypeSubtitle,
            true
        )
    }

    override suspend fun injectVideoImpl(media: MediaFile, isUrl: Boolean) {
        if (!isInitialized) return

        delay(500)

        media.uri?.let {
            vlcMedia = if (isUrl) {
                VLCMedia(uRL = NSURL.URLWithString(it) ?: throw Exception())
            } else {
                VLCMedia(path = it)
            }
            vlcPlayer?.setMedia(vlcMedia)
        }

        vlcMedia?.synchronousParse()

        seekTo(0) //VLC retains video position for some reason

        vlcMedia?.length?.numberValue?.doubleValue?.toLong()?.let {
            playerManager.timeFullMillis.value = if (it < 0) 0 else it

            playerManager.media.value?.fileDuration = playerManager.timeFullMillis.value / 1000.0
            declareFile()
        }
    }

    override suspend fun pause() {
        if (!isInitialized) return
        println("VLC PLAYER DELEGATE: ${vlcPlayer!!.delegate}")

        playerScopeMain.launch {
            vlcPlayer?.pause()
        }
    }

    override suspend fun play() {
        if (!isInitialized) return

        println("VLC PLAYER DELEGATE: ${vlcPlayer!!.delegate}")

        playerScopeMain.launch {
            vlcPlayer?.play()
        }
    }

    override suspend fun isSeekable(): Boolean {
        if (!isInitialized) return false

        return vlcPlayer?.isSeekable() == true
    }

    override fun seekTo(toPositionMs: Long) {
        super.seekTo(toPositionMs)
        vlcPlayer?.setTime(toPositionMs.toVLCTime())
    }

    override fun currentPositionMs(): Long {
        return vlcPlayer?.time?.value()?.longValue ?: 0L
    }

    override suspend fun switchAspectRatio(): String {
        if (!isInitialized) return "NO PLAYER FOUND"

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

        return newAspectRatio
    }

    override suspend fun changeSubtitleSize(newSize: Int) {
        if (!isInitialized) return

        // TODO: Implement subtitle size changing for iOS VLC player
    }

    /** VLC EXCLUSIVE */
    /* Some ObjC-interop/VLC-interop helper methods */
    /** Converts a long to a VLCTime, to use with VLCMediaPlayer */
    private fun Long.toVLCTime(): VLCTime {
        return VLCTime(number = NSNumber(long = this))
    }

    /** Converts an NSArray to a valid Kotlin list, to use with VLC tracks */
    @Suppress("UNCHECKED_CAST")
    private fun <T> NSArray.toKList(): List<T?> {
        return List(count.toInt()) { index ->
            objectAtIndex(index.toULong()) as? T
        }
    }


    /* Delegate */
    inner class VlcDelegate : NSObject(), VLCMediaPlayerDelegateProtocol {
        override fun mediaPlayerStateChanged(aNotification: NSNotification) {
            playerScopeMain.launch {
                if (hasMedia()) {
                    val isPlaying = vlcPlayer?.state != VLCMediaPlayerState.VLCMediaPlayerStatePaused
                    viewmodel.playerManager.isNowPlaying.value = isPlaying // Just to inform UI

                    // Tell server about playback state change
                    if (!viewmodel.isSoloMode) {
                        viewmodel.actionManager.sendPlayback(isPlaying)

                        if (vlcPlayer?.state == VLCMediaPlayerState.VLCMediaPlayerStateEnded) {
                            onPlaybackEnded()
                        }
                    }
                }
            }
        }
    }

    private val MAX_VOLUME = 100
    override fun getMaxVolume() = MAX_VOLUME
    override fun getCurrentVolume(): Int = (vlcPlayer?.pitch?.times(MAX_VOLUME))?.roundToInt() ?: 0
    override fun changeCurrentVolume(v: Int) {
        val clampedVolume = v.toFloat().coerceIn(0.0f, MAX_VOLUME.toFloat()) / MAX_VOLUME
        vlcPlayer?.setPitch(clampedVolume)
    }
}