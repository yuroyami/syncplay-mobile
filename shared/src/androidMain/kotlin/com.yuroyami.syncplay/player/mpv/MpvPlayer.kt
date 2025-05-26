@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
package com.yuroyami.syncplay.player.mpv

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.C.STREAM_TYPE_MUSIC
import com.yuroyami.syncplay.databinding.MpvviewBinding
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.Track
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.protocol.sending.Packet
import com.yuroyami.syncplay.screens.room.dispatchOSD
import com.yuroyami.syncplay.settings.ExtraSettingBundle
import com.yuroyami.syncplay.utils.collectInfoLocalAndroid
import com.yuroyami.syncplay.utils.getFileName
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.timeStamper
import com.yuroyami.syncplay.viewmodel.SyncplayViewmodel
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_selected_sub
import syncplaymobile.shared.generated.resources.room_selected_sub_error
import syncplaymobile.shared.generated.resources.room_selected_vid
import syncplaymobile.shared.generated.resources.room_sub_error_load_vid_first
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToLong

class MpvPlayer(viewmodel: SyncplayViewmodel) : BasePlayer(viewmodel) {
    lateinit var audioManager: AudioManager

    override val engine = ENGINE.ANDROID_MPV

    var mpvPos = 0L
    private lateinit var observer: MPVLib.EventObserver
    private var ismpvInit = false
    private lateinit var mpvView: MPVView
    private lateinit var ctx: Context

    override val canChangeAspectRatio: Boolean
        get() = true

    override val supportsChapters: Boolean
        get() = true

    override fun initialize() {
        ctx = mpvView.context.applicationContext
        audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        copyAssets(ctx)
    }

    override fun destroy() {
        if (!ismpvInit) return
        mpvView.destroy()
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

    override fun configurableSettings(): ExtraSettingBundle = getExtraSettings()

    override fun hasMedia(): Boolean {
        val c = MPVLib.getPropertyInt("playlist-count" as java.lang.String)
        return c != null && c > 0
    }

    override fun isPlaying(): Boolean {
        return if (!ismpvInit) false
        else !mpvView.paused
    }

    override suspend fun analyzeTracks(mediafile: MediaFile) {
        viewmodel.media?.subtitleTracks?.clear()
        viewmodel.media?.audioTracks?.clear()
        val count = MPVLib.getPropertyInt("track-list/count" as java.lang.String)!!
        // Note that because events are async, properties might disappear at any moment
        // so use ?: continue instead of !!
        for (i in 0 until count.toInt()) {
            val type = MPVLib.getPropertyString("track-list/$i/type" as java.lang.String)?.toString() ?: continue
            if (type != "audio" && type != "sub") continue
            val mpvId = MPVLib.getPropertyInt("track-list/$i/id" as java.lang.String) ?: continue
            val lang = MPVLib.getPropertyString("track-list/$i/lang" as java.lang.String)
            val title = MPVLib.getPropertyString("track-list/$i/title" as java.lang.String)
            val selected = MPVLib.getPropertyBoolean("track-list/$i/selected" as java.lang.String)?.booleanValue() ?: false

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
                    viewmodel.media?.audioTracks?.add(
                        object : Track {
                            override val name = trackName
                            override val type = TRACKTYPE.AUDIO
                            override val index = mpvId.toInt()
                            override val selected = mutableStateOf(selected)
                        }
                    )
                }

                "sub" -> {
                    viewmodel.media?.subtitleTracks?.add(
                        object : Track {
                            override val name = trackName
                            override val type = TRACKTYPE.SUBTITLE
                            override val index = mpvId.toInt()
                            override val selected = mutableStateOf(selected)
                        }
                    )
                }
            }
        }
    }

    override fun selectTrack(track: Track?, type: TRACKTYPE) {
        when (type) {
            TRACKTYPE.SUBTITLE -> {
                if (track != null) {
                    MPVLib.setPropertyInt("sid" as java.lang.String, track.index as Integer)
                } else {
                    MPVLib.setPropertyString("sid" as java.lang.String, "no" as java.lang.String)
                }

                viewmodel.currentTrackChoices.subtitleSelectionIndexMpv = track?.index ?: -1
            }

            TRACKTYPE.AUDIO -> {
                if (track != null) {
                    MPVLib.setPropertyInt("aid" as java.lang.String, track.index as Integer)
                } else {
                    MPVLib.setPropertyString("aid" as java.lang.String, "no" as java.lang.String)
                }

                viewmodel.currentTrackChoices.audioSelectionIndexMpv = track?.index ?: -1
            }
        }
    }

    override suspend fun analyzeChapters(mediafile: MediaFile) {
        if (!ismpvInit) return
        val chapters = mpvView.loadChapters()
        if (chapters.isEmpty()) return
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

    override fun jumpToChapter(chapter: Chapter) {
        if (!ismpvInit) return
        MPVLib.setPropertyInt("chapter" as java.lang.String, chapter.index as Integer)
    }

    override fun skipChapter() {
        if (!ismpvInit) return

        MPVLib.command(arrayOf("add" as java.lang.String, "chapter" as java.lang.String, "1" as java.lang.String))
    }

    override fun reapplyTrackChoices() {
        val subIndex = viewmodel.currentTrackChoices.subtitleSelectionIndexMpv
        val audioIndex = viewmodel.currentTrackChoices.audioSelectionIndexMpv

        val ccMap = viewmodel.media?.subtitleTracks
        val audioMap = viewmodel.media?.subtitleTracks

        val ccGet = ccMap?.firstOrNull { it.index == subIndex }
        val audioGet = audioMap?.firstOrNull { it.index == audioIndex }

        with(viewmodel.player ?: return) {
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

    override fun loadExternalSub(uri: String) {
        if (hasMedia()) {
            val filename = getFileName(uri = uri).toString()
            val extension = filename.substring(filename.length - 4).lowercase()

            val mimeTypeValid = (extension.contains("srt")
                    || extension.contains("ass")
                    || extension.contains("ssa")
                    || extension.contains("ttml")
                    || extension.contains("vtt"))

            if (mimeTypeValid) {
                ctx.resolveUri(uri.toUri())?.let {
                    MPVLib.command(arrayOf("sub-add" as java.lang.String, it as java.lang.String, "cached" as java.lang.String))
                }
                playerScopeMain.dispatchOSD {
                    getString(Res.string.room_selected_sub, filename)
                }
            } else {
                playerScopeMain.dispatchOSD {
                    getString(Res.string.room_selected_sub_error)
                }
            }
        } else {
            playerScopeMain.dispatchOSD {
                getString(Res.string.room_sub_error_load_vid_first)
            }
        }
    }

    override fun injectVideo(uri: String?, isUrl: Boolean) {
        /* Changing UI (hiding artwork, showing media controls) */
        viewmodel.hasVideoG.value = true
        val ctx = mpvView.context ?: return

        playerScopeMain.launch {
            /* Creating a media file from the selected file */
            if (uri != null || viewmodel.media == null) {
                viewmodel.media = MediaFile()
                viewmodel.media?.uri = uri

                /* Obtaining info from it (size and name) */
                if (isUrl) {
                    viewmodel.media?.url = uri.toString()
                    viewmodel.media?.let { collectInfoURL(it) }
                } else {
                    viewmodel.media?.let { collectInfoLocal(it) }
                }
            }
            /* Injecting the media into exoplayer */
            try {

                delay(500)
                uri?.let {
                    if (!isUrl) {
                        ctx.resolveUri(it.toUri())?.let { it2 ->
                            loggy("Final path $it2", 301)
                            if (ismpvInit) {
                                MPVLib.destroy()
                            }
                            mpvView.initialize(ctx.filesDir.path, ctx.cacheDir.path)
                            ismpvInit = true
                            mpvObserverAttach()
                            mpvView.playFile(it2)
                            mpvView.surfaceCreated(mpvView.holder)
                        }
                    } else {
                        if (ismpvInit) {
                            MPVLib.destroy()
                        }
                        mpvView.initialize(ctx.filesDir.path, ctx.cacheDir.path)
                        ismpvInit = true
                        mpvObserverAttach()
                        mpvView.playFile(uri)
                        mpvView.surfaceCreated(mpvView.holder)
                    }
                }

                /* Goes back to the beginning for everyone */
                if (!viewmodel.isSoloMode) {
                    viewmodel.p.currentVideoPosition = 0.0
                }
            } catch (e: IOException) {
                /* If, for some reason, the video didn't wanna load */
                e.printStackTrace()
                playerScopeMain.dispatchOSD("There was a problem loading this file.")
            }

            /* Finally, show a a toast to the user that the media file has been added */
            playerScopeMain.dispatchOSD {
                getString(Res.string.room_selected_vid, "${viewmodel.media?.fileName}")
            }
        }
    }

    override fun pause() {
        if (!ismpvInit) return

        playerScopeIO.launch {
            mpvView.paused = true
        }
    }

    override fun play() {
        if (!ismpvInit) return

        playerScopeIO.launch {
            mpvView.paused = false
        }
    }

    override fun isSeekable(): Boolean {
        return true
    }

    override fun seekTo(toPositionMs: Long) {
        if (!ismpvInit) return
        super.seekTo(toPositionMs)

        playerScopeIO.launch {
            mpvView.timePos = toPositionMs.toInt() / 1000
        }
    }

    override fun currentPositionMs(): Long {
        return mpvPos
    }

    override suspend fun switchAspectRatio(): String {
        val currentAspect = MPVLib.getPropertyString("video-aspect-override" as java.lang.String)?.toString()
        val currentPanscan = MPVLib.getPropertyDouble("panscan" as java.lang.String)?.toDouble()

        loggy("currentAspect: $currentAspect and currentPanscan: $currentPanscan", 0)

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
            MPVLib.setPropertyString("video-aspect-override" as java.lang.String, "-1" as java.lang.String)
            MPVLib.setPropertyDouble("panscan" as java.lang.String, 1.0 as java.lang.Double)
        } else {
            MPVLib.setPropertyString("video-aspect-override" as java.lang.String, nextAspect.first as java.lang.String)
            MPVLib.setPropertyDouble("panscan" as java.lang.String, 0.0 as java.lang.Double)
        }

        return nextAspect.second
    }

    override fun collectInfoLocal(mediafile: MediaFile) {
        collectInfoLocalAndroid(mediafile, ctx)
    }

    override fun changeSubtitleSize(newSize: Int) {

        val s: Double = when {
            newSize == 16 -> 1.0
            newSize > 16 -> {
                1.0 + (newSize - 16) * 0.05
            }

            else -> {
                1.0 - (16 - newSize) * (1.0 / 16)
            }
        }

        MPVLib.setPropertyDouble("sub-scale" as java.lang.String, s as java.lang.Double)
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
                    "duration" -> viewmodel.timeFull.longValue = value
                    //"file-size" -> value
                }
            }

            override fun eventProperty(property: String, value: Boolean) {
                when (property) {
                    "pause" -> {
                        viewmodel.isNowPlaying.value = !value //Just to inform UI

                        //Tell server about playback state change
                        if (!viewmodel.isSoloMode) {
                            viewmodel.sendPlayback(!value)
                            viewmodel.p.paused = value
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
                    MPVLib.mpvEventId.MPV_EVENT_START_FILE -> {
                        viewmodel.hasVideoG.value = true

                        if (viewmodel.isSoloMode) return
                        playerScopeIO.launch {
                            while (true) {
                                if (viewmodel.timeFull.longValue.toDouble() > 0) {
                                    viewmodel.media?.fileDuration = viewmodel.timeFull.longValue?.toDouble()!!
                                    viewmodel.p.send<Packet.File> {
                                        media = viewmodel.media
                                    }.await()
                                    break
                                }
                                delay(10)
                            }
                        }
                    }

                    MPVLib.mpvEventId.MPV_EVENT_END_FILE -> {
                        pause()
                        onPlaybackEnded()
                    }
                }
            }
        }
        mpvView.addObserver(observer)

        viewmodel.trackProgress(intervalMillis = 500L)
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
                    loggy("Skipping copy of asset file (exists same size): $filename", 302)
                    continue
                }
                out = FileOutputStream(outFile)
                ins.copyTo(out)
                loggy("Copied asset file: $filename", 303)
            } catch (e: IOException) {
                e.printStackTrace()
                loggy("Failed to copy asset file: $filename", 304)
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
        if (!ismpvInit) return
        MPVLib.setOptionString("hwdec" as java.lang.String, if (b) "auto" as java.lang.String else "no" as java.lang.String)
    }

    fun toggleGpuNext(b: Boolean) {
        if (!ismpvInit) return
        MPVLib.setOptionString("vo" as java.lang.String, if (b) "gpu-next" as java.lang.String else "gpu" as java.lang.String)
    }

    fun toggleInterpolation(b: Boolean) {
        if (!ismpvInit) return
        MPVLib.setOptionString("interpolation" as java.lang.String, if (b) "yes" as java.lang.String else "no" as java.lang.String)
    }

    fun toggleDebugMode(i: Int) {
        if (!ismpvInit) return
        MPVLib.command(arrayOf("script-binding" as java.lang.String, "stats/display-page-$i" as java.lang.String))
    }

    fun setProfileMode(p: String) {
        if (!ismpvInit) return
        MPVLib.setOptionString("profile" as java.lang.String, p as java.lang.String)
    }

    fun setVidSyncMode(m: String) {
        if (!ismpvInit) return
        MPVLib.setOptionString("video-sync" as java.lang.String, m as java.lang.String)
    }

    override fun getMaxVolume() = audioManager.getStreamMaxVolume(STREAM_TYPE_MUSIC)
    override fun getCurrentVolume() = audioManager.getStreamVolume(STREAM_TYPE_MUSIC)
    override fun changeCurrentVolume(v: Int) {
        if (!audioManager.isVolumeFixed) {
            audioManager.setStreamVolume(STREAM_TYPE_MUSIC, v, 0)
        }
    }
}