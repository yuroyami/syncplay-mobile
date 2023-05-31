package app.player.highlevel

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.R
import app.activities.WatchActivity
import app.playback.PlaybackProperties
import app.player.highlevel.HighLevelPlayer.ENGINE.EXOPLAYER
import app.player.highlevel.HighLevelPlayer.ENGINE.MPV
import app.player.highlevel.HighLevelPlayer.ENGINE.VLC
import app.player.mpv.MPVView
import app.protocol.JsonSender
import app.utils.MiscUtils.string
import app.utils.MiscUtils.toasty
import app.utils.RoomUtils.sendPlayback
import app.wrappers.ExoTrack
import app.wrappers.MediaFile
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVLib.mpvFormat.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import kotlin.math.abs


/** This is a class that wraps different players and interfaces different actions
 * to do different things based on the current player.
 *
 * Currently available players are:
 * 1- ExoPlayer (by Google) which is known to be the most stable and versatile
 * 2- MPV (most powerful and plays most formats), mildly stable
 *
 */


class HighLevelPlayer private constructor() {

    enum class ENGINE {
        EXOPLAYER,
        MPV,
        VLC //Not yet used
    }

    lateinit var engine: ENGINE

    var playerSet: Boolean = false

    /*-- Exoplayer-related properties --*/
    //var exoplayer: MediaController? = null
    lateinit var exoplayer: Player
    private var session: MediaSession? = null
    private var currentMedia: MediaItem? = null

    /* Player Views */
    lateinit var exoView: PlayerView
    lateinit var mpvView: MPVView


    companion object {
        fun create(context: WatchActivity, player: String): HighLevelPlayer {
            val p = HighLevelPlayer()

            when (player) {
                "exo" -> {
                    p.engine = EXOPLAYER

                    p.exoView = context.binding.exoview

                    /** LoadControl (Buffering manager) and track selector (for track language preference) **/
                    val loadControl = DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            PlaybackProperties.minBuffer,
                            PlaybackProperties.maxBuffer,
                            PlaybackProperties.playbackBuffer,
                            PlaybackProperties.playbackBuffer + 500
                        ).build()

                    val trackSelector = DefaultTrackSelector(context.applicationContext)
                    val params = trackSelector.buildUponParameters().setPreferredAudioLanguage(PlaybackProperties.audioPreference)
                        .setPreferredTextLanguage(PlaybackProperties.ccPreference)
                        .build()
                    trackSelector.parameters = params

                    /** Building ExoPlayer to use FFmpeg Audio Renderer and also enable fast-seeking */
                    p.exoplayer = ExoPlayer.Builder(context.applicationContext)
                        .setLoadControl(loadControl) /* We use the custom LoadControl we initialized before */
                        .setTrackSelector(trackSelector)
                        .setRenderersFactory(
                            DefaultRenderersFactory(context).setExtensionRendererMode(
                                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER /* We prefer extensions, such as FFmpeg */
                            )
                        )
                        .setWakeMode(C.WAKE_MODE_NETWORK) /* Prevent the service from being killed during playback */
                        .build()

                    (p.exoplayer as ExoPlayer).videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT /* Starter scaling */

                    p.exoView.player = p.exoplayer

                    /** Creating our MediaSession */
                    p.session = MediaSession
                        .Builder(context, p.exoplayer)
                        .setCallback(object : MediaSession.Callback {
                            override fun onAddMediaItems(
                                mediaSession: MediaSession,
                                controller: MediaSession.ControllerInfo,
                                mediaItems: MutableList<MediaItem>,
                            ): ListenableFuture<MutableList<MediaItem>> {
                                p.currentMedia = if (mediaItems.isEmpty()) {
                                    null
                                } else {
                                    mediaItems[0]
                                }

                                val newMediaItems = mediaItems.map {
                                    it.buildUpon().setUri(it.mediaId).build()
                                }.toMutableList()
                                return Futures.immediateFuture(newMediaItems)
                            }
                        })
                        .build()


                    with(p) {
                        context.exoObserverAttach()
                    }
                }

                "mpv" -> {
                    p.engine = MPV
                    p.mpvView = context.binding.mpvview

                    p.copyAssets(context)

                    //TODO: LoadControl
                    //TODO: AudioLock
                    //TODO: TrackSelection applying default language
                }

                "vlc" -> {
                    p.engine = VLC

                }

                else -> {}
            }


            return p
        }
    }


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
                    Log.v("mpv", "Skipping copy of asset file (exists same size): $filename")
                    continue
                }
                out = FileOutputStream(outFile)
                ins.copyTo(out)
                Log.w("mpv", "Copied asset file: $filename")
            } catch (e: IOException) {
                Log.e("mpv", "Failed to copy asset file: $filename", e)
            } finally {
                ins?.close()
                out?.close()
            }
        }
    }

    /** Wrapping seek behavior */
    fun seekTo(toPositionMs: Long) {
        when (engine) {
            EXOPLAYER -> exoplayer.seekTo((toPositionMs))
            MPV -> mpvView.timePos = toPositionMs.toInt() / 1000 //TODO: Check if it works
            else -> {}
        }
    }

    /** Wrapping pause behavior */
    fun pause() {
        when (engine) {
            EXOPLAYER -> exoplayer.pause()
            MPV -> {
                mpvView.cyclePause()
            }
            else -> {}
        }
    }

    /** Wrapping play behavior */
    fun play() {
        when (engine) {
            EXOPLAYER -> exoplayer.play()
            MPV -> {
                mpvView.cyclePause()
            }
            else -> {}
        }
    }

    /** Returns whether the current media is seekable, it's always true for mpv */
    fun isSeekable(): Boolean {
        return when (engine) {
            EXOPLAYER -> exoplayer.isCurrentMediaItemSeekable
            MPV -> true
            else -> false
        }
    }

    /** Returns the current media playback position */
    fun getPositionMs(): Long {
        return when (engine) {
            EXOPLAYER -> exoplayer.currentPosition
            MPV -> mpvPos
            else -> 0L
        }
    }

    @SuppressLint("WrongConstant")
    fun switchAspectRatio(context: Context): String {
        return when (engine) {
            EXOPLAYER -> {
                with(context) {
                    val resolutions = mutableMapOf<Int, String>()
                    resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIT] = getString(R.string.room_scaling_fit_screen)
                    resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH] = getString(R.string.room_scaling_fixed_width)
                    resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT] = getString(R.string.room_scaling_fixed_height)
                    resolutions[AspectRatioFrameLayout.RESIZE_MODE_FILL] = getString(R.string.room_scaling_fill_screen)
                    resolutions[AspectRatioFrameLayout.RESIZE_MODE_ZOOM] = getString(R.string.room_scaling_zoom)

                    var nextRes = (exoView.resizeMode + 1)
                    if (nextRes == 5) nextRes = 0
                    exoView.resizeMode = nextRes

                    return resolutions[nextRes]!!
                }
            }

            MPV -> "" //TODO
            else -> ""
        }
    }

    /** Returns whether the current player has media. Will return false if the player has no loaded media */
    fun hasAnyMedia(): Boolean {
        return when (engine) {
            EXOPLAYER -> exoplayer.mediaItemCount != 0
            MPV -> false //TODO
            else -> false
        }
    }

    /** @return whether the current player is in a "play" state. When this value is false, it means it's not playing
     * which can mean it's either paused or idle. */
    fun isInPlayState(): Boolean {
        return when (engine) {
            EXOPLAYER -> exoplayer.playbackState == Player.STATE_READY && exoplayer.playWhenReady
            MPV -> mpvView.paused == false
            else -> false
        }
    }


    /** Aggregates the media file's tracks with the necessary track information
     * to be used for track selection, adapts to whatever player that is used */
    fun analyzeTracks(media: MediaFile) {
        when (engine) {
            EXOPLAYER -> analyzeTracksExo(media)
            MPV -> false //TODO
            else -> {}
        }
    }

    /** In ExoPlayer, a MediaItem consists of TrackGroups
     * Each TrackGroup can contain one track (format) or more.
     * Basically, ExoPlayer gathers similar tracks (same language, different bitrate, for example)
     * into one track group. There can exist for example 4 track groups, 3 of them of audio or text
     *
     * Anyway, in order to manipulate track selection, you need to know how to retrieve those trackgroups
     * and also, how to retrieve the tracks inside them, and check which one is selected...etc
     *
     * I do all of this here.
     */
    private fun analyzeTracksExo(media: MediaFile) {
        media.audioExoTracks.clear()
        media.subtitleExoTracks.clear()
        val tracks = exoplayer.currentTracks
        for (group in tracks.groups) {
            val trackGroup = group.mediaTrackGroup
            val trackType = group.type
            if (trackType == C.TRACK_TYPE_AUDIO || trackType == C.TRACK_TYPE_TEXT) {
                for (i in (0 until trackGroup.length)) {
                    val format = trackGroup.getFormat(i)
                    val index = trackGroup.indexOf(format)

                    /** Creating a custom Track instance for every track in a track group **/
                    val exoTrack = ExoTrack()
                    exoTrack.trackGroup = trackGroup
                    exoTrack.trackType = trackType
                    exoTrack.index = index
                    exoTrack.format = format
                    exoTrack.selected.value = group.isTrackSelected(index)

                    if (trackType == C.TRACK_TYPE_TEXT) {
                        media.subtitleExoTracks.add(exoTrack)
                    } else {
                        media.audioExoTracks.add(exoTrack)
                    }
                }
            }
        }
    }

    fun injectVideo(activity: WatchActivity, uri: Uri? = null, isUrl: Boolean = false) {
        when (this@HighLevelPlayer.engine) {
            EXOPLAYER -> activity.injectVideoExo(uri, isUrl)
            MPV -> activity.injectVideoMpv(uri, isUrl)
            else -> {}
        }
    }


    private fun WatchActivity.injectVideoMpv(uri: Uri? = null, isUrl: Boolean = false) {
        /* Changing UI (hiding artwork, showing media controls) */
        hasVideoG.value = true

        lifecycleScope.launch(Dispatchers.Main) {
            /* Creating a media file from the selected file */
            if (uri != null || media == null) {
                media = MediaFile()
                media?.uri = uri

                /* Obtaining info from it (size and name) */
                if (isUrl) {
                    media?.url = uri.toString()
                    media?.collectInfoURL()
                } else {
                    media?.collectInfo(applicationContext)
                }

                /* Checking mismatches with others in room */
                //checkFileMismatches(p) TODO
            }
            /* Injecting the media into exoplayer */
            try {

                delay(2000)
                uri?.let {
                    resolveUri(it)?.let { it2 ->
                        Log.e("mpv", "Final path $it2")
                        mpvView = binding.mpvview
                        mpvView.initialize(applicationContext.filesDir.path)
                        mpvObserverAttach()
                        mpvView.playFile(it2)
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
                toasty("There was a problem loading this file.")
            }

            /* Finally, show a a toast to the user that the media file has been added */
            toasty(string(R.string.room_selected_vid, "${media?.fileName}"))
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
        } catch(e: Exception) {
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
        } catch(e: Exception) { } finally { ins?.close() }
        return null
    }

    private fun Context.getFilePathFromUri(uri: Uri): String? {
        var filePath: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val fileName = cursor.getString(index)
                cursor.close()
                val file: File = File(getCacheDir(), fileName)
                try {
                    contentResolver.openInputStream(uri).use { inputStream ->
                        FileOutputStream(file).use { outputStream ->
                            val buffer = ByteArray(4 * 1024) // Adjust buffer size as per your requirement
                            var bytesRead: Int
                            while (inputStream!!.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }
                            outputStream.flush()
                            filePath = file.absolutePath
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } else if (uri.scheme == "file") {
            filePath = uri.path
        }
        return filePath
    }

    /** Injects a media into exoplayer. If no uri is provided, the method will re-inject the current media */
    private fun WatchActivity.injectVideoExo(uri: Uri? = null, isUrl: Boolean = false) {
        /* Changing UI (hiding artwork, showing media controls) */
        hasVideoG.value = true

        lifecycleScope.launch(Dispatchers.Main) {
            /* Creating a media file from the selected file */
            if (uri != null || media == null) {
                media = MediaFile()
                media?.uri = uri

                /* Obtaining info from it (size and name) */
                if (isUrl) {
                    media?.url = uri.toString()
                    media?.collectInfoURL()
                } else {
                    media?.collectInfo(applicationContext)
                }

                /* Checking mismatches with others in room */
                //checkFileMismatches(p) TODO
            }

            /* Injecting the media into exoplayer */
            try {
                /* This is the builder responsible for building a MediaItem component for ExoPlayer **/
                val vid = MediaItem.Builder()
                    .setUri(media?.uri)
                    .setMediaId(media?.uri.toString())
                    .apply {
                        setSubtitleConfigurations(Collections.singletonList(media?.externalSub ?: return@apply))
                    }
                    .build()

                /* Injecting it into ExoPlayer and getting relevant info **/
                player?.exoplayer?.setMediaItem(vid) /* This loads the media into ExoPlayer **/
                player?.exoplayer?.prepare() /* This prepares it and makes the first frame visible */

                /* Goes back to the beginning for everyone */
                if (!isSoloMode()) {
                    p.currentVideoPosition = 0.0
                }

                /* Updating play button */
                player?.exoplayer?.duration?.let { timeFull.longValue = if (it < 0) 0 else it }

                /* Seeing if we have to start over TODO **/
                //if (startFromPosition != (-3.0).toLong()) myExoPlayer?.seekTo(startFromPosition)
            } catch (e: IOException) {
                /* If, for some reason, the video didn't wanna load */
                e.printStackTrace()
                toasty("There was a problem loading this file.")
            }

            /* Finally, show a a toast to the user that the media file has been added */
            toasty(string(R.string.room_selected_vid, "${media?.fileName}"))
        }

    }

    /** This is the global event listener for ExoPlayer (responsible for many core functionality features */
    private fun WatchActivity.exoObserverAttach() {
        exoplayer.addListener(object : Player.Listener {

            /* This detects when the player loads a file/URL, so we tell the server */
            override fun onIsLoadingChanged(isLoading: Boolean) {
                super.onIsLoadingChanged(isLoading)

                if (!isLoading) {
                    /* Updating our timeFull */
                    val duration = exoplayer.duration / 1000.0
                    timeFull.longValue = abs(duration.toLong())

                    if (isSoloMode()) return
                    if (duration != media?.fileDuration) {
                        media?.fileDuration = duration
                        p.sendPacket(JsonSender.sendFile(media ?: return))
                    }
                }
            }

            /* This detects when the user pauses or plays */
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)

                if (exoplayer.mediaItemCount != 0) {
                    if (exoplayer.playbackState != ExoPlayer.STATE_BUFFERING) {
                        isNowPlaying.value = isPlaying //Just to inform UI

                        //Tell server about playback state change
                        if (!isSoloMode()) {
                            sendPlayback(isPlaying)
                            p.paused = !isPlaying
                        }
                    }
                }
            }

            /* This detects when the user seeks */
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int,
            ) {
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)

            }

            /* This detects when a media track change has happened (such as loading a custom sub) */
            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)

                /* Updating our HUD's timeFull here again, just in case. */
                //val duration = exoplayer.duration / 1000.0
                //timeFull.longValue = kotlin.math.abs(duration.toLong())

                /* Repopulate audio and subtitle track lists with the new analysis of tracks **/
                analyzeTracksExo(media ?: return)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PLAYER", error.message ?: "")
            }
        })
    }


    var mpvPos: Long = 0L

    private fun WatchActivity.mpvObserverAttach() {
        mpvView.addObserver(object: MPVLib.EventObserver {
            override fun eventProperty(property: String) {
            }

            override fun eventProperty(property: String, value: Long) {
                when (property) {
                    "time-pos" -> mpvPos = value * 1000
                    "duration" -> timeFull.longValue = value
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
                        if (timeFull.longValue.toDouble() != media?.fileDuration) {
                            media?.fileDuration = timeFull.longValue.toDouble()
                            p.sendPacket(JsonSender.sendFile(media ?: return))
                        }
                    }
                }
            }

        })
    }
}