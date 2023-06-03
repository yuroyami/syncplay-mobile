package app.player.highlevel

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
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
import app.activities.WatchActivityUI.RoomUI
import app.playback.PlaybackProperties
import app.player.highlevel.HighLevelPlayer.ENGINE.EXOPLAYER
import app.player.highlevel.HighLevelPlayer.ENGINE.MPV
import app.player.highlevel.HighLevelPlayer.ENGINE.VLC
import app.player.mpv.MPVView
import app.protocol.JsonSender
import app.utils.MiscUtils.getFileName
import app.utils.MiscUtils.string
import app.utils.MiscUtils.toasty
import app.utils.RoomUtils.sendPlayback
import app.wrappers.MediaFile
import app.wrappers.Track
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

    /* mpv-related properties */
    var ismpvInit = false

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
            MPV -> MPVLib.getPropertyInt("playlist-count") > 0
            else -> false
        }
    }

    /** @return whether the current player is in a "play" state. When this value is false, it means it's not playing
     * which can mean it's either paused or idle. */
    fun isInPlayState(): Boolean {
        return when (engine) {
            EXOPLAYER -> exoplayer.playbackState == Player.STATE_READY && exoplayer.playWhenReady
            MPV -> {
                if (!ismpvInit) return false
                else return mpvView.paused == false
            }
            else -> false
        }
    }


    /** Aggregates the media file's tracks with the necessary track information
     * to be used for track selection, adapts to whatever player that is used */
    fun analyzeTracks(media: MediaFile) {
        when (engine) {
            EXOPLAYER -> analyzeTracksExo(media)
            MPV -> analyzeTracksMpv(media)
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
        media.audioTracks.clear()
        media.subtitleTracks.clear()
        val tracks = exoplayer.currentTracks
        for (group in tracks.groups) {
            val trackGroup = group.mediaTrackGroup
            val trackType = group.type
            if (trackType == C.TRACK_TYPE_AUDIO || trackType == C.TRACK_TYPE_TEXT) {
                for (i in (0 until trackGroup.length)) {
                    val format = trackGroup.getFormat(i)
                    val index = trackGroup.indexOf(format)

                    /** Creating a custom Track instance for every track in a track group **/
                    val exoTrack = Track(
                        trackGroup = trackGroup,
                        trackType = trackType,
                        index = index,
                        format = format,
                        name = "${format.label} [${format.language?.uppercase() ?: "UND"}]"
                    ).apply {
                        this.selected.value = group.isTrackSelected(index)
                    }

                    exoTrack.selected.value = group.isTrackSelected(index)

                    if (trackType == C.TRACK_TYPE_TEXT) {
                        media.subtitleTracks.add(exoTrack)
                    } else {
                        media.audioTracks.add(exoTrack)
                    }
                }
            }
        }
    }

    private fun analyzeTracksMpv(media: MediaFile) {
        media.subtitleTracks.clear()
        media.audioTracks.clear()
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
                    media.audioTracks.add(
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
                    media.subtitleTracks.add(
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
    /** Selects the track with the given index, based on its type.
     * @param type Whether it's an audio or a text track. Can only be [C.TRACK_TYPE_TEXT] or [C.TRACK_TYPE_AUDIO]
     * @param index The index of the track. Any negative index disables the tracks altogether.
     */
    fun WatchActivity.selectTrack(type: Int, index: Int) {
        if (player?.engine == EXOPLAYER) {
            (player?.exoplayer as ExoPlayer?)?.apply {
                val builder = trackSelector?.parameters?.buildUpon()

                /* First, clearing our subtitle track selection (This helps troubleshoot many issues */
                trackSelector?.parameters = builder?.clearOverridesOfType(type)!!.build()
                currentTrackChoices.lastSubtitleOverride = null

                /* Now, selecting our subtitle track should one be selected */
                if (index >= 0) {
                    when (type) {
                        C.TRACK_TYPE_TEXT -> {
                            currentTrackChoices.lastSubtitleOverride = TrackSelectionOverride(
                                media!!.subtitleTracks[index].trackGroup!!,
                                media!!.subtitleTracks[index].index
                            )
                        }

                        C.TRACK_TYPE_AUDIO -> {
                            currentTrackChoices.lastSubtitleOverride = TrackSelectionOverride(
                                media!!.audioTracks[index].trackGroup!!,
                                media!!.audioTracks[index].index
                            )
                        }
                    }
                    trackSelector?.parameters = builder.addOverride(currentTrackChoices.lastSubtitleOverride!!).build()
                } else {

                }
            }
        }

        if (player?.engine == MPV) {
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
    }


    fun WatchActivity.loadExternalSub(uri: Uri) {
        if (player?.hasAnyMedia() == true) {
            val filename = getFileName(uri = uri).toString()
            val extension = filename.substring(filename.length - 4)

            val mimeType =
                if (extension.contains("srt")) MimeTypes.APPLICATION_SUBRIP
                else if ((extension.contains("ass"))
                    || (extension.contains("ssa"))
                ) MimeTypes.TEXT_SSA
                else if (extension.contains("ttml")) MimeTypes.APPLICATION_TTML
                else if (extension.contains("vtt")) MimeTypes.TEXT_VTT else ""

            if (mimeType != "") {
                when (player?.engine) {
                    EXOPLAYER -> {
                        media?.externalSub = MediaItem.SubtitleConfiguration.Builder(uri)
                            .setUri(uri)
                            .setMimeType(mimeType)
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()

                        player?.injectVideo(this@loadExternalSub)
                    }
                    MPV -> {
                        resolveUri(uri)?.let {
                            MPVLib.command(arrayOf("sub-add", it, "cached"))
                        }
                    }
                    else -> {}
                }
                toasty(string(R.string.room_selected_sub, filename))
            } else {
                toasty(getString(R.string.room_selected_sub_error))
            }
        } else {
            toasty(getString(R.string.room_sub_error_load_vid_first))
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

                delay(500)
                uri?.let {
                    if (isUrl == false) {
                        resolveUri(it)?.let { it2 ->
                            Log.e("mpv", "Final path $it2")
                            mpvView = binding.mpvview
                            if (!ismpvInit) {
                                mpvView.initialize(applicationContext.filesDir.path)
                                ismpvInit = true
                            }
                            mpvObserverAttach()
                            mpvView.playFile(it2)
                            mpvView.surfaceCreated(mpvView.holder)
                        }
                    } else {
                        mpvView = binding.mpvview
                        if (!ismpvInit) {
                            mpvView.initialize(applicationContext.filesDir.path)
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
        } catch(_: Exception) { } finally { ins?.close() }
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
                val file = File(getCacheDir(), fileName)
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
                        lifecycleScope.launch(Dispatchers.IO) {
                            //while (media?.fileSize == "") {}
                            media?.fileDuration = duration
                            Log.e("exo", "SENDING FILE VIA EXO INFO")
                            p.sendPacket(JsonSender.sendFile(media ?: return@launch))
                        }
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

    var mpvPos = 0L

    lateinit var observer: MPVLib.EventObserver

    private fun WatchActivity.mpvObserverAttach() {
        if (::observer.isInitialized) {
            mpvView.removeObserver(observer)
        }
        observer = object: MPVLib.EventObserver {
            override fun eventProperty(property: String) {
            }

            override fun eventProperty(property: String, value: Long) {
                when (property) {
                    "time-pos" -> this@HighLevelPlayer.mpvPos = value * 1000
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
                        lifecycleScope.launch(Dispatchers.IO) {
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
    }

}