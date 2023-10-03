package com.yuroyami.syncplay.utils.player.mpv

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import com.yuroyami.syncplay.databinding.MpvviewBinding
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.Track
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.ENGINE
import com.yuroyami.syncplay.player.PlayerUtils
import com.yuroyami.syncplay.protocol.JsonSender
import com.yuroyami.syncplay.utils.CommonUtils.loggy
import com.yuroyami.syncplay.utils.RoomUtils.sendPlayback
import com.yuroyami.syncplay.utils.getFileName
import com.yuroyami.syncplay.watchroom.currentTrackChoices
import com.yuroyami.syncplay.watchroom.hasVideoG
import com.yuroyami.syncplay.watchroom.isNowPlaying
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.media
import com.yuroyami.syncplay.watchroom.p
import com.yuroyami.syncplay.watchroom.player
import com.yuroyami.syncplay.watchroom.timeFull
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class MpvPlayer : BasePlayer {

    val engine = ENGINE.ANDROID_MPV

    var mpvPos = 0L
    lateinit var observer: MPVLib.EventObserver
    var ismpvInit = false
    lateinit var mpvView: MPVView
    lateinit var ctx: Context

    val mpvMainScope = CoroutineScope(Dispatchers.Main)
    val mpvBGScope = CoroutineScope(Dispatchers.IO)

    override fun initialize() {
        ctx = mpvView.context.applicationContext

        copyAssets(ctx)

        //TODO: LoadControl
        //TODO: AudioLock
        //TODO: TrackSelection applying default language
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

    override fun hasMedia(): Boolean {
        return MPVLib.getPropertyInt("playlist-count") > 0
    }

    override fun isPlaying(): Boolean {
        return if (!ismpvInit) false
        else mpvView.paused == false
    }

    override fun analyzeTracks(mediafile: MediaFile) {
        media?.subtitleTracks?.clear()
        media?.audioTracks?.clear()
        val count = MPVLib.getPropertyInt("track-list/count")!!
        // Note that because events are async, properties might disappear at any moment
        // so use ?: continue instead of !!
        for (i in 0 until count) {
            val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
            if (type != "audio" && type != "sub") continue;
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
                    media?.audioTracks?.add(
                        Track(
                            name = trackName,
                            index = mpvId,
                            trackType = C.TRACK_TYPE_AUDIO,
                        ).apply {
                            this.selected.value = selected
                        }
                    )
                }
                "sub" -> {
                    media?.subtitleTracks?.add(
                        Track(
                            name = trackName,
                            index = mpvId,
                            trackType = C.TRACK_TYPE_TEXT,
                        ).apply {
                            this.selected.value = selected
                        }
                    )
                }
            }
        }
    }


    override fun selectTrack(type: Int, index: Int) {
        when (type) {
            C.TRACK_TYPE_TEXT -> {
                if (index >= 0) {
                    MPVLib.setPropertyInt("sid", index)
                } else if (index == -1) {
                    MPVLib.setPropertyString("sid", "no")
                }

                currentTrackChoices.subtitleSelectionIndexMpv = index
            }

            C.TRACK_TYPE_AUDIO -> {
                if (index >= 0) {
                    MPVLib.setPropertyInt("aid", index)
                } else if (index == -1) {
                    MPVLib.setPropertyString("aid", "no")
                }

                currentTrackChoices.audioSelectionIndexMpv = index
            }
        }
    }

    override fun reapplyTrackChoices() {
        val subIndex = currentTrackChoices.subtitleSelectionIndexMpv
        val audioIndex = currentTrackChoices.audioSelectionIndexMpv

        with(player ?: return) {
            if (subIndex != null) selectTrack(C.TRACK_TYPE_TEXT, subIndex)
            if (audioIndex != null) selectTrack(C.TRACK_TYPE_AUDIO, audioIndex)
        }
    }

    override fun loadExternalSub(uri: String) {
        if (hasMedia()) {
            val filename = getFileName(uri = uri, ctx).toString()
            val extension = filename.substring(filename.length - 4)

            val mimeType =
                if (extension.contains("srt")) MimeTypes.APPLICATION_SUBRIP
                else if ((extension.contains("ass"))
                    || (extension.contains("ssa"))
                ) MimeTypes.TEXT_SSA
                else if (extension.contains("ttml")) MimeTypes.APPLICATION_TTML
                else if (extension.contains("vtt")) MimeTypes.TEXT_VTT else ""

            if (mimeType != "") {
                ctx.resolveUri(uri.toUri())?.let {
                    MPVLib.command(arrayOf("sub-add", it, "cached"))
                }
                //toasty(string(R.string.room_selected_sub, filename))
            } else {
                //toasty(getString(R.string.room_selected_sub_error))
            }
        } else {
            //toasty(getString(R.string.room_sub_error_load_vid_first))
        }
    }

    override fun injectVideo(uri: String?, isUrl: Boolean) {
        /* Changing UI (hiding artwork, showing media controls) */
        hasVideoG.value = true
        val ctx = mpvView.context

        mpvMainScope.launch {
            /* Creating a media file from the selected file */
            if (uri != null || media == null) {
                media = MediaFile()
                media?.uri = uri

                /* Obtaining info from it (size and name) */
                if (isUrl) {
                    media?.url = uri.toString()
                    //TODO: media?.collectInfoURL()
                } else {
                    //TODO: media?.collectInfo(applicationContext)
                }

                /* Checking mismatches with others in room */
                //checkFileMismatches(p) TODO
            }
            /* Injecting the media into exoplayer */
            try {

                delay(500)
                uri?.let {
                    if (!isUrl) {
                        ctx.resolveUri(it.toUri())?.let { it2 ->
                            loggy("Final path $it2")
                            if (!ismpvInit) {
                                mpvView.initialize(ctx.filesDir.path)
                                ismpvInit = true
                            }
                            mpvObserverAttach()
                            mpvView.playFile(it2)
                            mpvView.surfaceCreated(mpvView.holder)
                        }
                    } else {
                        if (!ismpvInit) {
                            mpvView.initialize(ctx.filesDir.path)
                            ismpvInit = true
                        }
                        mpvObserverAttach()
                        mpvView.playFile(uri.toString())
                        mpvView.surfaceCreated(mpvView.holder)
                    }
                }

                /* Goes back to the beginning for everyone */
                if (!isSoloMode()) {
                    p.currentVideoPosition = 0.0
                }

                /* Seeing if we have to start over TODO **/
                //if (startFromPosition != (-3.0).toLong()) myExoPlayer?.seekTo(startFromPosition)
            } catch (e: IOException) {
                /* If, for some reason, the video didn't wanna load */
                e.printStackTrace()
                //TODO: toasty("There was a problem loading this file.")
            }

            /* Finally, show a a toast to the user that the media file has been added */
            //TODO: toasty(string(R.string.room_selected_vid, "${media?.fileName}"))
        }
    }

    override fun pause() {
        mpvView.cyclePause() //FIXME: Don't cycle pause, rather pause directly
    }

    override fun play() {
        mpvView.cyclePause() //FIXME: Don't cycle pause, rather play directly
    }

    override fun isSeekable(): Boolean {
        return true
    }

    override fun seekTo(toPositionMs: Long) {
        mpvView.timePos = toPositionMs.toInt() / 1000 //TODO: Check if it works
    }

    override fun currentPositionMs(): Long {
        return mpvPos
    }

    override fun switchAspectRatio(): String {
        return "" //TODO
    }


    /** MPV EXCLUSIVE */
    private fun mpvObserverAttach() {
        if (::observer.isInitialized) {
            mpvView.removeObserver(observer)
        }
        observer = object : MPVLib.EventObserver {
            override fun eventProperty(property: String) {
            }

            override fun eventProperty(property: String, value: Long) {
                when (property) {
                    "time-pos" -> mpvPos = value * 1000
                    "duration" -> timeFull.longValue = value
                    //"file-size" -> value
                }
            }

            override fun eventProperty(property: String, value: Boolean) {
                when (property) {
                    "pause" -> {
                        isNowPlaying.value = !value //Just to inform UI

                        //Tell server about playback state change
                        if (!isSoloMode()) {
                            sendPlayback(!value)
                            p.paused = value
                        }
                    }
                }
            }

            override fun eventProperty(property: String, value: String) {
            }

            override fun event(eventId: Int) {
                when (eventId) {
                    MPVLib.mpvEventId.MPV_EVENT_START_FILE -> {
                        hasVideoG.value = true

                        if (isSoloMode()) return
                        mpvBGScope.launch {
                            while (true) {
                                if (timeFull.longValue.toDouble() > 0) {
                                    media?.fileDuration = timeFull.longValue.toDouble()
                                    p.sendPacket(JsonSender.sendFile(media ?: return@launch))
                                    break
                                }
                            }
                        }
                    }
                }
            }
        }
        mpvView.addObserver(observer)

        PlayerUtils.trackProgress()
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
        return "fdclose://${fd}"
    }


    fun findRealPath(fd: Int): String? {
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
}