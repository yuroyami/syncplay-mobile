@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.yuroyami.syncplay.managers.player.mpv

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.LayoutInflater
import androidx.annotation.UiThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.C.STREAM_TYPE_MUSIC
import com.yuroyami.syncplay.databinding.MpvviewBinding
import com.yuroyami.syncplay.managers.player.AndroidPlayerEngine
import com.yuroyami.syncplay.managers.player.BasePlayer
import com.yuroyami.syncplay.managers.protocol.creator.PacketCreator
import com.yuroyami.syncplay.managers.settings.ExtraSettingBundle
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.Track
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.timeStamper
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class MpvPlayer(viewmodel: RoomViewmodel) : BasePlayer(viewmodel, AndroidPlayerEngine.Mpv) {
    lateinit var audioManager: AudioManager

    var mpvPos = 0L
    private lateinit var observer: MPVLib.EventObserver
    private lateinit var mpvView: MPVView
    private lateinit var ctx: Context

    override val canChangeAspectRatio: Boolean
        get() = true

    override val supportsChapters: Boolean
        get() = true

    override val trackerJobInterval: Duration = 500.milliseconds

    override fun initialize() {
        ctx = mpvView.context.applicationContext
        audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        copyAssets(ctx)

        isInitialized = true
    }

    override suspend fun destroy() {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            mpvView.destroy()
        }
    }

    @Composable
    override fun VideoPlayer(modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                mpvView = MpvviewBinding.inflate(LayoutInflater.from(context)).mpvview
                initialize()
                return@AndroidView mpvView
            },
            update = {})
    }

    override suspend fun configurableSettings(): ExtraSettingBundle = getExtraSettings()

    override suspend fun hasMedia(): Boolean {
        if (!isInitialized) return false

        return withContext(Dispatchers.Main.immediate) {
            val c = MPVLib.getPropertyInt("playlist-count")
            c != null && c > 0
        }
    }

    override suspend fun isPlaying(): Boolean {
        return withContext(Dispatchers.Main.immediate) {
            if (!isInitialized) false else !mpvView.paused
        }
    }

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            playerManager.media.value?.subtitleTracks?.clear()
            playerManager.media.value?.audioTracks?.clear()
            val count = MPVLib.getPropertyInt("track-list/count")!!
            // Note that because events are async, properties might disappear at any moment
            // so use ?: continue instead of !!
            for (i in 0 until count) {
                val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
                if (type != "audio" && type != "sub") continue
                val mpvId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
                val lang = MPVLib.getPropertyString("track-list/$i/lang")
                val title = MPVLib.getPropertyString("track-list/$i/title")
                val selected = MPVLib.getPropertyBoolean("track-list/$i/selected") ?: false

                /** Speculating the track name based on whatever info there is on it */
                val trackName = if (!lang.isNullOrEmpty() && !title.isNullOrEmpty())
                    "$title [$lang]"
                else if (!lang.isNullOrEmpty() && title.isNullOrEmpty()) {
                    "$title [UND]"
                } else if (!title.isNullOrEmpty() && lang.isNullOrEmpty())
                    "Track [$lang]"
                else "Track $mpvId [UND]"

                Log.e("trck", "Found track $mpvId: $type, $title [$lang], $selected")
                when (type) {
                    "audio" -> {
                        playerManager.media.value?.audioTracks?.add(
                            object : Track {
                                override val name = trackName
                                override val type = TRACKTYPE.AUDIO
                                override val index = mpvId
                                override val selected = mutableStateOf(selected)
                            }
                        )
                    }

                    "sub" -> {
                        playerManager.media.value?.subtitleTracks?.add(
                            object : Track {
                                override val name = trackName
                                override val type = TRACKTYPE.SUBTITLE
                                override val index = mpvId
                                override val selected = mutableStateOf(selected)
                            }
                        )
                    }
                }
            }
        }
    }

    override suspend fun selectTrack(track: Track?, type: TRACKTYPE) {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            when (type) {
                TRACKTYPE.SUBTITLE -> {
                    if (track != null) {
                        MPVLib.setPropertyInt("sid", track.index)
                    } else {
                        MPVLib.setPropertyString("sid", "no")
                    }

                    playerManager.currentTrackChoices.subtitleSelectionIndexMpv = track?.index ?: -1
                }

                TRACKTYPE.AUDIO -> {
                    if (track != null) {
                        MPVLib.setPropertyInt("aid", track.index)
                    } else {
                        MPVLib.setPropertyString("aid", "no")
                    }

                    playerManager.currentTrackChoices.audioSelectionIndexMpv = track?.index ?: -1
                }
            }
        }
    }

    override suspend fun analyzeChapters(mediafile: MediaFile) {
        withContext(Dispatchers.Main.immediate) {
            if (!isInitialized) return@withContext
            val chapters = mpvView.loadChapters()
            if (chapters.isEmpty()) return@withContext
            mediafile.chapters.clear()
            mediafile.chapters.addAll(chapters.map {
                val timestamp = " (${timeStamper(it.time.roundToLong())})"
                Chapter(
                    it.index,
                    (it.title ?: "Chapter ${it.index}") + timestamp,
                    (it.time * 1000).roundToLong()
                )
            })
        }
    }

    override suspend fun jumpToChapter(chapter: Chapter) {
        if (!isInitialized) return
        withContext(Dispatchers.Main.immediate) {
            MPVLib.setPropertyInt("chapter", chapter.index)
        }
    }

    override suspend fun skipChapter() {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            MPVLib.command(arrayOf("add", "chapter", "1"))
        }
    }

    override suspend fun reapplyTrackChoices() {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            val subIndex = playerManager.currentTrackChoices.subtitleSelectionIndexMpv
            val audioIndex = playerManager.currentTrackChoices.audioSelectionIndexMpv

            val ccMap = playerManager.media.value?.subtitleTracks
            val audioMap = playerManager.media.value?.subtitleTracks

            val ccGet = ccMap?.firstOrNull { it.index == subIndex }
            val audioGet = audioMap?.firstOrNull { it.index == audioIndex }

            with(playerManager.player) {
                if (subIndex == -1) {
                    selectTrack(null, TRACKTYPE.SUBTITLE)
                } else if (ccGet != null) {
                    selectTrack(ccGet, TRACKTYPE.SUBTITLE)
                }

                if (audioIndex == -1) {
                    selectTrack(null, TRACKTYPE.AUDIO)
                } else if (audioGet != null) {
                    selectTrack(audioGet, TRACKTYPE.AUDIO)
                }
            }
        }
    }

    override suspend fun loadExternalSubImpl(uri: String, extension: String) {
        withContext(Dispatchers.IO) {
            ctx.resolveUri(uri.toUri())?.let { subUri ->
                withContext(Dispatchers.Main) {
                    MPVLib.command(
                        arrayOf(
                            "sub-add",
                            subUri,
                            "cached"
                        )
                    )
                }
            }
        }
    }

    override suspend fun injectVideoImpl(media: MediaFile, isUrl: Boolean) {
        withContext(Dispatchers.Main.immediate) {
            delay(500)
            media.uri?.let { uri ->
                if (!isUrl) {
                    ctx.resolveUri(uri.toUri())?.let { it2 ->
                        loggy("Final path $it2")
                        if (isInitialized) {
                            MPVLib.destroy()
                        }
                        mpvView.initialize(ctx.filesDir.path, ctx.cacheDir.path)
                        isInitialized = true
                        mpvObserverAttach()
                        mpvView.playFile(it2)
                        mpvView.surfaceCreated(mpvView.holder)
                    }
                } else {
                    if (isInitialized) {
                        MPVLib.destroy()
                    }
                    mpvView.initialize(ctx.filesDir.path, ctx.cacheDir.path)
                    isInitialized = true
                    mpvObserverAttach()
                    mpvView.playFile(uri)
                    mpvView.surfaceCreated(mpvView.holder)
                }
            }
        }
    }

    override suspend fun pause() {
        if (!isInitialized) return

        mpvView.paused = true
    }

    override suspend fun play() {
        if (!isInitialized) return

        mpvView.paused = false
    }

    override suspend fun isSeekable(): Boolean {
        return true
    }

    @UiThread
    override fun seekTo(toPositionMs: Long) {
        if (!isInitialized) return
        super.seekTo(toPositionMs)

        mpvView.timePos = toPositionMs.toInt() / 1000
    }

    override fun currentPositionMs(): Long {
        return mpvPos
    }

    override suspend fun switchAspectRatio(): String {
        if (!isInitialized) return "NO PLAYER FOUND"

        return withContext(Dispatchers.Main.immediate) {
            val currentAspect = MPVLib.getPropertyString("video-aspect-override")
            val currentPanscan = MPVLib.getPropertyDouble("panscan")

            loggy("currentAspect: $currentAspect and currentPanscan: $currentPanscan")

            val aspectRatios = listOf(
                "-1.000000" to "Original",
                "1.777778" to "16:9",
                "1.600000" to "16:10",
                "1.333333" to "4:3",
                "2.350000" to "2.35:1",
                "panscan" to "Pan/Scan"
            )

            var enablePanscan = false
            val nextAspect = if (currentPanscan == 1.0) {
                aspectRatios[0]
            } else if (currentAspect == "2.350000") {
                enablePanscan = true
                aspectRatios[5]
            } else {
                aspectRatios[aspectRatios.indexOfFirst { it.first == currentAspect } + 1]
            }

            if (enablePanscan) {
                MPVLib.setPropertyString("video-aspect-override", "-1")
                MPVLib.setPropertyDouble("panscan", 1.0)
            } else {
                MPVLib.setPropertyString("video-aspect-override", nextAspect.first)
                MPVLib.setPropertyDouble("panscan", 0.0)
            }

            return@withContext nextAspect.second
        }
    }

    override suspend fun changeSubtitleSize(newSize: Int) {
        if (!isInitialized) return

        withContext(Dispatchers.Main.immediate) {
            val s: Double = when {
                newSize == 16 -> 1.0
                newSize > 16 -> {
                    1.0 + (newSize - 16) * 0.05
                }

                else -> {
                    1.0 - (16 - newSize) * (1.0 / 16)
                }
            }

            MPVLib.setPropertyDouble("sub-scale", s)
        }
    }

    /** MPV EXCLUSIVE */
    private fun mpvObserverAttach() {
        removeObserver()

        observer = object : MPVLib.EventObserver {
            override fun eventProperty(property: String) {
            }

            override fun eventProperty(property: String, value: Long) {
                when (property) {
                    "time-pos" -> mpvPos = value * 1000
                    "duration" -> playerManager.timeFullMillis.value = value * 1000
                    //"file-size" -> value
                }
            }

            override fun eventProperty(property: String, value: Boolean) {
                when (property) {
                    "pause" -> {
                        playerManager.isNowPlaying.value = !value //Just to inform UI

                        //Tell server about playback state change
                        if (!viewmodel.isSoloMode) {
                            viewmodel.actionManager.sendPlayback(!value)
                        }
                    }
                }
            }

            override fun eventProperty(property: String, value: String) {
            }

            override fun eventProperty(property: String, value: Double) {

            }

            override fun event(eventId: Int) {
                when (eventId) {
                    MPVLib.MpvEvent.MPV_EVENT_START_FILE -> {
                        if (viewmodel.isSoloMode) return
                        playerScopeIO.launch {
                            while (true) {
                                if (playerManager.timeFullMillis.value.toDouble() > 0) {
                                    playerManager.media.value?.fileDuration = playerManager.timeFullMillis.value / 1000.0

                                    viewmodel.networkManager.sendAsync<PacketCreator.File> {
                                        media = playerManager.media.value
                                    }
                                    break
                                }
                                delay(10)
                            }
                        }
                    }

                    MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                        playerScopeMain.launch {
                            pause()
                            onPlaybackEnded()
                        }
                    }
                }
            }
        }

        if (!isInitialized) return

        mpvView.addObserver(observer)

        startTrackingProgress()
    }

    //TODO
    private fun copyAssets(context: Context) {
        val assetManager = context.assets
        val files = arrayOf("subfont.ttf", "cacert.pem")
        val configDir = context.filesDir.path
        for (filename in files) {
            var ins: InputStream? = null
            var out: OutputStream? = null
            try {
                ins = assetManager.open(filename, AssetManager.ACCESS_STREAMING)
                val outFile = File("$configDir/$filename")
                // Note that .available() officially returns an *estimated* number of bytes available
                // this is only true for generic streams, asset streams return the full file size
                if (outFile.length() == ins.available().toLong()) {
                    loggy("Skipping copy of asset file (exists same size): $filename")
                    continue
                }
                out = FileOutputStream(outFile)
                ins.copyTo(out)
                loggy("Copied asset file: $filename")
            } catch (e: IOException) {
                e.printStackTrace()
                loggy("Failed to copy asset file: $filename")
            } finally {
                ins?.close()
                out?.close()
            }
        }
    }

    private fun Context.resolveUri(data: Uri): String? {
        val filepath = when (data.scheme) {
            "file" -> data.path
            "content" -> openContentFd(this, data)
            "http", "https", "rtmp", "rtmps", "rtp", "rtsp", "mms", "mmst", "mmsh", "tcp", "udp"
                -> data.toString()

            else -> null
        }

        if (filepath == null)
            Log.e("mpv", "unknown scheme: ${data.scheme}")
        return filepath
    }


    private fun openContentFd(context: Context, uri: Uri): String? {
        val resolver = context.applicationContext.contentResolver
        Log.e("mpv", "Resolving content URI: $uri")
        val fd = try {
            val desc = resolver.openFileDescriptor(uri, "r")
            desc!!.detachFd()
        } catch (e: Exception) {
            Log.e("mpv", "Failed to open content fd: $e")
            return null
        }
        // See if we skip the indirection and read the real file directly
        val path = findRealPath(fd)
        if (path != null) {
            Log.e("mpv", "Found real file path: $path")
            ParcelFileDescriptor.adoptFd(fd).close() // we don't need that anymore
            return path
        }
        // Else, pass the fd to mpv
        return "fd://${fd}"
    }

    private fun findRealPath(fd: Int): String? {
        var ins: InputStream? = null
        try {
            val path = File("/proc/self/fd/${fd}").canonicalPath
            if (!path.startsWith("/proc") && File(path).canRead()) {
                // Double check that we can read it
                ins = FileInputStream(path)
                ins.read()
                return path
            }
        } catch (_: Exception) {
        } finally {
            ins?.close()
        }
        return null
    }

    fun removeObserver() {
        if (::observer.isInitialized) {
            mpvView.removeObserver(observer)
        }
    }

    fun toggleHardwareAcceleration(b: Boolean) {
        if (!isInitialized) return
        MPVLib.setOptionString("hwdec", if (b) "auto" else "no")
    }

    fun toggleGpuNext(b: Boolean) {
        if (!isInitialized) return
        MPVLib.setOptionString("vo", if (b) "gpu-next" else "gpu")
    }

    fun toggleInterpolation(b: Boolean) {
        if (!isInitialized) return
        MPVLib.setOptionString("interpolation", if (b) "yes" else "no")
    }

    fun toggleDebugMode(i: Int) {
        if (!isInitialized) return
        MPVLib.command(arrayOf("script-binding", "stats/display-page-$i"))
    }

    fun setProfileMode(p: String) {
        if (!isInitialized) return
        MPVLib.setOptionString("profile", p)
    }

    fun setVidSyncMode(m: String) {
        if (!isInitialized) return
        MPVLib.setOptionString("video-sync", m)
    }

    override fun getMaxVolume() = audioManager.getStreamMaxVolume(STREAM_TYPE_MUSIC)
    override fun getCurrentVolume() = audioManager.getStreamVolume(STREAM_TYPE_MUSIC)
    override fun changeCurrentVolume(v: Int) {
        if (!audioManager.isVolumeFixed) {
            audioManager.setStreamVolume(STREAM_TYPE_MUSIC, v, 0)
        }
    }
}