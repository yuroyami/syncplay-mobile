package app.player.mpv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import app.player.PlayerImpl
import app.player.models.Chapter
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import app.player.models.Track
import app.preferences.Preferences.SUBTITLE_SIZE
import app.preferences.value
import app.room.RoomViewmodel
import app.utils.loggy
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.UIKit.UIColor
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * iOS MPV player implementation using MPVKit via Swift bridge.
 *
 * Mirrors the Android [app.player.mpv.MpvImpl] but delegates all libmpv C API calls
 * to a [MpvKitPlayerBridge] instance provided by the Swift side.
 *
 * The bridge handles:
 * - mpv handle lifecycle (create/initialize/destroy)
 * - Metal rendering via MoltenVK
 * - Property observation (time-pos, duration, pause)
 * - All mpv commands and property access
 */
class MpvKitImpl(
    viewmodel: RoomViewmodel,
    val bridge: MpvKitPlayerBridge
) : PlayerImpl(viewmodel, MpvKitEngine) {

    override val supportsChapters: Boolean = true
    override val supportsPictureInPicture: Boolean = false
    override val trackerJobInterval: Duration = 500.milliseconds

    override fun initialize() {
        bridge.create()

        bridge.onPropertyChange = lambda@{ name, value ->
            when (name) {
                "time-pos" -> {
                    val seconds = value as? Double ?: return@lambda
                    playerManager.timeCurrentMillis.value = (seconds * 1000).roundToLong()
                }
                "duration" -> {
                    val seconds = value as? Double ?: return@lambda
                    playerManager.timeFullMillis.value = (seconds * 1000).roundToLong()
                }
                "pause" -> {
                    val paused = value as? Boolean ?: return@lambda
                    playerManager.isNowPlaying.value = !paused
                }
            }
        }

        bridge.onEvent = { eventName ->
            when (eventName) {
                "file-loaded" -> {
                    if (!viewmodel.isSoloMode) {
                        playerScopeIO.launch {
                            // Wait for duration to become available
                            while (playerManager.timeFullMillis.value <= 0L) {
                                delay(50)
                            }
                            playerManager.media.value?.fileDuration =
                                playerManager.timeFullMillis.value.toDouble() / 1000.0
                            announceFileLoaded()
                        }
                    }
                }
                "end-file" -> {
                    playerScopeMain.launch {
                        pause()
                        onPlaybackEnded()
                    }
                }
            }
        }

        isInitialized = true
        startTrackingProgress()
    }

    override suspend fun destroy() {
        if (!isInitialized) return
        withContext(Dispatchers.Main) {
            bridge.onPropertyChange = null
            bridge.onEvent = null
            bridge.destroy()
        }
    }

    override suspend fun configurableSettings() = null

    @Composable
    override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) {
        DisposableEffect(Unit) {
            onDispose {
                // Bridge cleanup handled by destroy()
            }
        }

        UIKitView(
            modifier = modifier,
            factory = {
                val view = bridge.getPlayerView()
                view.setBackgroundColor(UIColor.blackColor)

                initialize()
                onPlayerReady()

                return@UIKitView view
            }
        )
    }

    override suspend fun hasMedia(): Boolean {
        if (!isInitialized) return false
        return withContext(Dispatchers.Main.immediate) {
            bridge.getDuration() > 0
        }
    }

    override suspend fun isPlaying(): Boolean {
        if (!isInitialized) return false
        return withContext(Dispatchers.Main.immediate) {
            !bridge.isPaused()
        }
    }

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            viewmodel.media?.tracks?.clear()

            val count = bridge.getTrackCount()
            for (i in 0 until count) {
                val type = bridge.getTrackType(i) ?: continue
                if (type != "audio" && type != "sub") continue

                val mpvId = bridge.getTrackId(i)
                val lang = bridge.getTrackLang(i)
                val title = bridge.getTrackTitle(i)
                val selected = bridge.isTrackSelected(i)

                val trackName = when {
                    !title.isNullOrEmpty() && !lang.isNullOrEmpty() -> "$title [$lang]"
                    !title.isNullOrEmpty() -> "$title [UND]"
                    !lang.isNullOrEmpty() -> "Track [$lang]"
                    else -> "Track $mpvId [UND]"
                }

                viewmodel.media?.tracks?.add(
                    MpvKitTrack(
                        name = trackName,
                        type = if (type == "audio") TrackType.AUDIO else TrackType.SUBTITLE,
                        index = mpvId,
                        selected = selected
                    )
                )
            }
        }
    }

    override suspend fun selectTrack(track: Track?, type: TrackType) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            when (type) {
                TrackType.SUBTITLE -> {
                    bridge.setSubtitleTrack(track?.index ?: -1)
                    playerManager.currentTrackChoices.subtitleSelectionIndexMpv = track?.index ?: -1
                }
                TrackType.AUDIO -> {
                    bridge.setAudioTrack(track?.index ?: -1)
                    playerManager.currentTrackChoices.audioSelectionIndexMpv = track?.index ?: -1
                }
            }
        }
    }

    override suspend fun analyzeChapters(mediafile: MediaFile) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            mediafile.chapters.clear()
            val count = bridge.getChapterCount()
            for (i in 0 until count) {
                val title = bridge.getChapterTitle(i)
                val time = bridge.getChapterTime(i)
                mediafile.chapters.add(
                    Chapter(
                        index = i,
                        name = title ?: "Chapter $i",
                        timeOffsetMillis = (time * 1000).roundToLong()
                    )
                )
            }
        }
    }

    override suspend fun jumpToChapter(chapter: Chapter) {
        if (!isInitialized) return
        super.jumpToChapter(chapter)
        withContext(Dispatchers.Main.immediate) {
            bridge.setChapter(chapter.index)
        }
    }

    override suspend fun reapplyTrackChoices() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            val subIndex = playerManager.currentTrackChoices.subtitleSelectionIndexMpv
            val audioIndex = playerManager.currentTrackChoices.audioSelectionIndexMpv

            val tracks = playerManager.media.value?.tracks ?: return@withContext

            if (subIndex == -1) {
                selectTrack(null, TrackType.SUBTITLE)
            } else {
                tracks.firstOrNull { it.type == TrackType.SUBTITLE && it.index == subIndex }
                    ?.let { selectTrack(it, TrackType.SUBTITLE) }
            }

            if (audioIndex == -1) {
                selectTrack(null, TrackType.AUDIO)
            } else {
                tracks.firstOrNull { it.type == TrackType.AUDIO && it.index == audioIndex }
                    ?.let { selectTrack(it, TrackType.AUDIO) }
            }
        }
    }

    override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            bridge.addSubtitleFile(uri.path)
        }
    }

    override suspend fun injectVideoFileImpl(location: MediaFileLocation.Local) {
        val nsUrl = location.file.nsUrl
        nsUrl.startAccessingSecurityScopedResource()
        bridge.loadFile(location.file.path)
    }

    override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
        bridge.loadURL(location.url)
    }

    override suspend fun pause() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            bridge.setPaused(true)
        }
    }

    override suspend fun play() {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            bridge.setPaused(false)
        }
    }

    override suspend fun setSpeed(speed: Double) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            bridge.setSpeed(speed)
        }
    }

    override suspend fun isSeekable(): Boolean = isInitialized

    override fun seekTo(toPositionMs: Long) {
        if (!isInitialized) return
        super.seekTo(toPositionMs)
        bridge.seekTo(toPositionMs.toDouble() / 1000.0)
    }

    override fun currentPositionMs(): Long {
        if (!isInitialized) return 0L
        return (bridge.getTimePos() * 1000).roundToLong()
    }

    override suspend fun switchAspectRatio(): String {
        if (!isInitialized) return "NO PLAYER FOUND"
        return withContext(Dispatchers.Main.immediate) {
            val currentAspect = bridge.getAspectOverride() ?: "-1.000000"
            val currentPanscan = bridge.getPanscan()

            val aspectRatios = listOf(
                "-1.000000" to "Original", "1.777778" to "16:9",
                "1.600000" to "16:10", "1.333333" to "4:3",
                "2.350000" to "2.35:1", "panscan" to "Pan/Scan"
            )

            var enablePanscan = false
            val nextAspect = if (currentPanscan == 1.0) {
                aspectRatios[0]
            } else if (currentAspect == "2.350000") {
                enablePanscan = true
                aspectRatios[5]
            } else {
                val idx = aspectRatios.indexOfFirst { it.first == currentAspect }
                aspectRatios.getOrElse(idx + 1) { aspectRatios[0] }
            }

            if (enablePanscan) {
                bridge.setAspectOverride("-1")
                bridge.setPanscan(1.0)
            } else {
                bridge.setAspectOverride(nextAspect.first)
                bridge.setPanscan(0.0)
            }

            nextAspect.second
        }
    }

    override suspend fun changeSubtitleSize(newSize: Int) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            val scale: Double = when {
                newSize == 16 -> 1.0
                newSize > 16 -> 1.0 + (newSize - 16) * 0.05
                else -> 1.0 - (16 - newSize) * (1.0 / 16)
            }
            bridge.setSubScale(scale)
        }
    }

    override fun getMaxVolume(): Int = bridge.getMaxVolume()
    override fun getCurrentVolume(): Int = bridge.getVolume()
    override fun changeCurrentVolume(v: Int) {
        bridge.setVolume(v.coerceIn(0, getMaxVolume()))
    }
}
