package com.yuroyami.syncplay.player.vlc
//
//import androidx.annotation.MainThread
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.interop.UIKitView
//import cafe.adriel.lyricist.Lyricist
//import cocoapods.MobileVLCKit.VLCLibrary
//import cocoapods.MobileVLCKit.VLCMedia
//import cocoapods.MobileVLCKit.VLCMediaPlaybackSlaveTypeSubtitle
//import cocoapods.MobileVLCKit.VLCMediaPlayer
//import cocoapods.MobileVLCKit.VLCMediaPlayerDelegateProtocol
//import cocoapods.MobileVLCKit.VLCMediaPlayerState
//import cocoapods.MobileVLCKit.VLCTime
//import cocoapods.MobileVLCKit.audioTrackIndexes
//import cocoapods.MobileVLCKit.audioTrackNames
//import cocoapods.MobileVLCKit.currentAudioTrackIndex
//import cocoapods.MobileVLCKit.currentVideoSubTitleIndex
//import cocoapods.MobileVLCKit.setCurrentAudioTrackIndex
//import cocoapods.MobileVLCKit.setCurrentVideoSubTitleIndex
//import cocoapods.MobileVLCKit.videoSubTitlesIndexes
//import cocoapods.MobileVLCKit.videoSubTitlesNames
//import com.yuroyami.syncplay.lyricist.Stringies
//import com.yuroyami.syncplay.models.Chapter
//import com.yuroyami.syncplay.models.MediaFile
//import com.yuroyami.syncplay.models.Track
//import com.yuroyami.syncplay.player.BasePlayer
//import com.yuroyami.syncplay.player.PlayerUtils.trackProgress
//import com.yuroyami.syncplay.protocol.JsonSender
//import com.yuroyami.syncplay.utils.RoomUtils
//import com.yuroyami.syncplay.utils.RoomUtils.checkFileMismatches
//import com.yuroyami.syncplay.utils.collectInfoLocaliOS
//import com.yuroyami.syncplay.utils.getFileName
//import com.yuroyami.syncplay.utils.loggy
//import com.yuroyami.syncplay.watchroom.dispatchOSD
//import com.yuroyami.syncplay.watchroom.isSoloMode
//import com.yuroyami.syncplay.watchroom.lyricist
//import com.yuroyami.syncplay.watchroom.viewmodel
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.runBlocking
//import kotlinx.coroutines.withContext
//import platform.AVFoundation.AVLayerVideoGravityResizeAspect
//import platform.AVFoundation.AVPlayerLayer
//import platform.Foundation.NSArray
//import platform.Foundation.NSNotificationCenter
//import platform.Foundation.NSNumber
//import platform.Foundation.NSSelectorFromString
//import platform.Foundation.NSURL
//import platform.UIKit.UIColor
//import platform.UIKit.UIView
//import platform.darwin.NSObject
//import platform.posix.int64_t
//import kotlin.math.abs
//
//class VlcPlayer : BasePlayer() {
//    override val engine = Engine.IOS_VLC
//
//    private var libvlc: VLCLibrary? = null
//    var vlcPlayer: VLCMediaPlayer? = null
//    private var vlcView: UIView? = null
//
//    private var vlcMedia: VLCMedia? = null
//
//    var pipLayer: AVPlayerLayer? = null
//
//    override val canChangeAspectRatio: Boolean
//        get() = true
//
//    override val supportsChapters: Boolean
//        get() = false //todo: fix chapters
//
//    override fun destroy() {
//        playerScopeMain.launch {
//            vlcPlayer?.stop()
//            vlcPlayer?.finalize()
//            vlcPlayer = null
//            vlcMedia = null
//            libvlc?.finalize()
//            libvlc = null
//        }
//    }
//
//    @Composable
//    override fun VideoPlayer(modifier: Modifier) {
//        UIKitView(
//            modifier = modifier,
//            factory = {
//                vlcView = UIView()
//                vlcView!!.setBackgroundColor(UIColor.clearColor())
//                libvlc = VLCLibrary(listOf("-vv"))
//
//                vlcPlayer = VLCMediaPlayer(libvlc!!)
//                vlcPlayer!!.drawable = vlcView
//
//                /* Layer for PIP */
//                pipLayer = AVPlayerLayer.playerLayerWithPlayer(null)
//                pipLayer?.frame = vlcView!!.bounds
//                pipLayer?.videoGravity = AVLayerVideoGravityResizeAspect
//                pipLayer?.addSublayer(vlcView!!.layer)
//
//                return@UIKitView vlcView!!.also {
//                    initialize()
//                }
//            },
//            interactive = false,
//            update = {}
//        )
//    }
//
//    override fun initialize() {
//        vlcPlayer!!.delegate = object : NSObject(), VLCMediaPlayerDelegateProtocol {
//            init {
//                NSNotificationCenter.defaultCenter.addObserver(
//                    this,
//                    NSSelectorFromString("mediaPlayerStateChanged:"),
//                    "VLCMediaPlayerStateChanged",
//                    this
//                )
//            }
//
//            override fun mediaPlayerStateChanged(newState: VLCMediaPlayerState) {
//                if (hasMedia()) {
//                    val isPlaying = newState == VLCMediaPlayerState.VLCMediaPlayerStatePlaying
//                    viewmodel?.isNowPlaying?.value = isPlaying // Just to inform UI
//
//                    // Tell server about playback state change
//                    if (!isSoloMode) {
//                        RoomUtils.sendPlayback(isPlaying)
//                        viewmodel!!.p.paused = !isPlaying
//                    }
//                }
//
//            }
//
//            override fun mediaPlayerLengthChanged(length: int64_t) {
//                if (length <= 0) return
//
//                viewmodel?.timeFull?.longValue = abs(length)
//
//                if (!isSoloMode) {
//                    if (length.toDouble() != viewmodel?.media?.fileDuration) {
//                        playerScopeIO.launch launch2@{
//                            viewmodel?.media?.fileDuration = length.toDouble()
//                            viewmodel!!.p.sendPacket(JsonSender.sendFile(viewmodel?.media ?: return@launch2))
//                        }
//                    }
//                }
//            }
//        }
//
//
//
//        playerScopeMain.trackProgress(intervalMillis = 1000L)
//    }
//
//    override fun hasMedia(): Boolean {
//        return runBlocking(Dispatchers.Main) { vlcPlayer?.media != null }
//    }
//
//    @MainThread
//    override fun isPlaying(): Boolean {
//        return vlcPlayer?.isPlaying() == true
//    }
//
//    override suspend fun analyzeTracks(mediafile: MediaFile) {
//        if (vlcPlayer == null) return
//
//        withContext(Dispatchers.Main) {
//            viewmodel?.media?.subtitleTracks?.clear()
//            viewmodel?.media?.audioTracks?.clear()
//
//            val audioTracks = vlcPlayer!!.audioTrackIndexes.zip(vlcPlayer!!.audioTrackNames).toMap()
//            audioTracks.forEach { (index, name) ->
//                viewmodel?.media?.audioTracks?.add(
//                    object : Track {
//                        override val name = name.toString()
//                        override val index = index as? Int ?: 0
//                        override val type = TRACKTYPE.AUDIO
//                        override val selected = mutableStateOf(vlcPlayer!!.currentAudioTrackIndex == index)
//                    }
//                )
//            }
//
//            val subtitleTracks = vlcPlayer!!.videoSubTitlesIndexes.zip(vlcPlayer!!.videoSubTitlesNames).toMap()
//            subtitleTracks.forEach { (index, name) ->
//                viewmodel?.media?.subtitleTracks?.add(
//                    object : Track {
//                        override val name = name.toString()
//                        override val index = index as? Int ?: 0
//                        override val type = TRACKTYPE.SUBTITLE
//                        override val selected = mutableStateOf(vlcPlayer!!.currentVideoSubTitleIndex == index)
//                    }
//                )
//            }
//        }
//    }
//
//    override fun selectTrack(track: Track?, type: TRACKTYPE) {
//        playerScopeMain.launch {
//            when (type) {
//                TRACKTYPE.SUBTITLE -> {
//                    val index = track?.index ?: -1
//                    if (index >= 0) {
//                        vlcPlayer?.setCurrentVideoSubTitleIndex(index)
//                    } else {
//                        vlcPlayer?.setCurrentVideoSubTitleIndex(-1)
//                    }
//                }
//
//                TRACKTYPE.AUDIO -> {
//                    val index = track?.index ?: -1
//                    if (index >= 0) {
//                        vlcPlayer?.setCurrentAudioTrackIndex(index)
//                    } else {
//                        vlcPlayer?.setCurrentAudioTrackIndex(-1)
//                    }
//                }
//            }
//        }
//    }
//
//    override suspend fun analyzeChapters(mediafile: MediaFile) {
//        playerScopeMain.launch {
//            mediafile.chapters.clear()
//            val chapters = (vlcPlayer?.chapterDescriptionsOfTitle(0) as? NSArray)?.toKList<String>()
//
//            chapters?.forEachIndexed { index, name ->
//                if (name.isNullOrBlank()) return@forEachIndexed
//
//                mediafile.chapters.add(
//                    Chapter(
//                        index = index,
//                        name = name,
//                        timestamp = 0 // TODO: Implement chapter timestamps
//                    )
//                )
//            }
//        }
//    }
//
//    override fun jumpToChapter(chapter: Chapter) {
//        playerScopeMain.launch {
//            vlcPlayer?.setCurrentChapterIndex(chapter.index)
//        }
//    }
//
//    override fun skipChapter() {
//        playerScopeMain.launch {
//            vlcPlayer?.nextChapter()
//        }
//    }
//
//    override fun reapplyTrackChoices() {
//        //TODO Not implemented for iOS VLC player
//    }
//
//    override fun loadExternalSub(uri: String) {
//        if (hasMedia()) {
//            val filename = getFileName(uri = uri).toString()
//            val extension = filename.substring(filename.lastIndexOf('.') + 1).lowercase()
//
//            val mimeTypeValid = listOf("srt", "ass", "ssa", "ttml", "vtt").contains(extension)
//
//            if (mimeTypeValid) {
//                playerScopeMain.launch {
//                    vlcPlayer?.addPlaybackSlave(
//                        NSURL.URLWithString(uri)!!,
//                        VLCMediaPlaybackSlaveTypeSubtitle,
//                        true
//                    )
//                }
//
//                playerScopeMain.dispatchOSD(lyricist.strings.roomSelectedSub(filename))
//            } else {
//                playerScopeMain.dispatchOSD(lyricist.strings.roomSelectedSubError)
//            }
//        } else {
//            playerScopeMain.dispatchOSD(lyricist.strings.roomSubErrorLoadVidFirst)
//        }
//    }
//
//    override fun injectVideo(uri: String?, isUrl: Boolean) {
//        /* Changing UI (hiding artwork, showing media controls) */
//        viewmodel?.hasVideoG?.value = true
//
//        playerScopeMain.launch {
//            /* Creating a media file from the selected file */
//            if (uri != null || viewmodel?.media == null) {
//                viewmodel?.media = MediaFile()
//                viewmodel?.media?.uri = uri
//
//                /* Obtaining info from it (size and name) */
//                if (isUrl) {
//                    viewmodel?.media?.url = uri.toString()
//                    viewmodel?.media?.let { collectInfoURL(it) }
//                } else {
//                    viewmodel?.media?.let { collectInfoLocal(it) }
//                }
//
//                /* Checking mismatches with others in room */
//                checkFileMismatches()
//            }
//            /* Injecting the media into VLC player */
//            try {
//                delay(500)
//                loggy("Attempting to insert media: $uri")
//
//                uri?.let {
//                    vlcMedia = if (isUrl) {
//                        val nsurl = NSURL.URLWithString(it) ?: throw Exception()
//                        val vlcmedia = VLCMedia(uRL = nsurl)
//                        vlcPlayer?.setMedia(vlcmedia)
//                        vlcmedia
//                    } else {
//                        val vlcmedia = VLCMedia(path = it)
//                        vlcPlayer?.setMedia(vlcmedia)
//                        vlcmedia
//                    }
//
//                    seekTo(0) //VLC retains video position for some reason
//                }
//
//                /* Goes back to the beginning for everyone */
//                if (!isSoloMode) {
//                    viewmodel!!.p.currentVideoPosition = 0.0
//                }
//            } catch (e: Exception) {
//                /* If, for some reason, the video didn't want to load */
//                e.printStackTrace()
//                playerScopeMain.dispatchOSD("There was a problem loading this file.")
//            }
//
//            /* Finally, show a toast to the user that the media file has been added */
//            val lyricist = Lyricist("en", Stringies)
//            playerScopeMain.dispatchOSD(lyricist.strings.roomSelectedVid("${viewmodel?.media?.fileName}"))
//        }
//    }
//
//    override fun pause() {
//        playerScopeMain.launch {
//            vlcPlayer?.pause()
//        }
//    }
//
//    override fun play() {
//        playerScopeMain.launch {
//            vlcPlayer?.play()
//        }
//    }
//
//    @MainThread
//    override fun isSeekable(): Boolean {
//        return vlcPlayer?.isSeekable() == true
//    }
//
//    override fun seekTo(toPositionMs: Long) {
//        super.seekTo(toPositionMs)
//        playerScopeMain.launch {
//            vlcPlayer?.setTime(toPositionMs.toVLCTime())
//        }
//    }
//
//    @MainThread
//    override fun currentPositionMs(): Long {
//        return vlcPlayer?.time?.value()?.longValue ?: 0L
//    }
//
//    override fun switchAspectRatio(): String {
//        return runBlocking(Dispatchers.Main) {
//            // Available aspect ratio options
//            val aspectRatios = listOf(
//                "1:1", "4:3", "16:9", "16:10", "2.21:1", "2.35:1"
//                // Add more aspect ratios as needed
//            )
//
//            // Read the current aspect ratio
//            val currentAspectRatio = vlcPlayer?.videoAspectRatio()
//
//            // Find the index of the current aspect ratio in the list
//            val currentIndex = if (currentAspectRatio != null) {
//                aspectRatios.indexOf(currentAspectRatio)
//            } else {
//                -1 // If current aspect ratio is null, set it to -1
//            }
//
//            // Calculate the index of the next aspect ratio
//            val nextIndex = (currentIndex + 1) % aspectRatios.size
//
//            // Get the next aspect ratio
//            val newAspectRatio = aspectRatios[nextIndex]
//
//            // Convert the new aspect ratio to C string and set it
//            vlcPlayer?.videoAspectRatio = newAspectRatio
//
//            newAspectRatio
//        }
//    }
//
//    override fun collectInfoLocal(mediafile: MediaFile) {
//        collectInfoLocaliOS(mediafile)
//    }
//
//    override fun changeSubtitleSize(newSize: Int) {
//        // TODO: Implement subtitle size changing for iOS VLC player
//        print("VLC Font Scaling: $newSize")
//        playerScopeMain.launch {
//            vlcPlayer?.setCurrentSubTitleFontScale(newSize.toFloat())
//        }
//    }
//
//    /** VLC EXCLUSIVE */
//    /* Some ObjC-interop/VLC-interop helper methods */
//    /** Converts a long to a VLCTime, to use with VLCMediaPlayer */
//    private fun Long.toVLCTime(): VLCTime {
//        return VLCTime(number = NSNumber(long = this))
//    }
//
//    /** Converts an NSArray to a valid Kotlin list, to use with VLC tracks */
//    @Suppress("UNCHECKED_CAST")
//    private fun <T> NSArray.toKList(): List<T?> {
//        return List(count.toInt()) { index ->
//            objectAtIndex(index.toULong()) as? T
//        }
//    }
//}