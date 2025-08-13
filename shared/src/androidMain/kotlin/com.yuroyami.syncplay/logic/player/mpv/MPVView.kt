package com.yuroyami.syncplay.logic.player.mpv

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import com.yuroyami.syncplay.logic.datastore.DataStoreKeys.PREF_MPV_GPU_NEXT
import com.yuroyami.syncplay.logic.datastore.DataStoreKeys.PREF_MPV_HARDWARE_ACCELERATION
import com.yuroyami.syncplay.logic.datastore.DataStoreKeys.PREF_MPV_INTERPOLATION
import com.yuroyami.syncplay.logic.datastore.valueBlockingly
import com.yuroyami.syncplay.logic.player.PlayerOptions
import com.yuroyami.syncplay.utils.contextObtainer
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVLib.mpvFormat.MPV_FORMAT_DOUBLE
import `is`.xyz.mpv.MPVLib.mpvFormat.MPV_FORMAT_FLAG
import `is`.xyz.mpv.MPVLib.mpvFormat.MPV_FORMAT_INT64
import `is`.xyz.mpv.MPVLib.mpvFormat.MPV_FORMAT_NONE
import `is`.xyz.mpv.MPVLib.mpvFormat.MPV_FORMAT_STRING
import kotlin.reflect.KProperty

internal class MPVView(context: Context, attrs: AttributeSet) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    fun initialize(configDir: String, cacheDir: String) {
        MPVLib.create(contextObtainer.obtainAppContext())
        MPVLib.setOptionStringK("config", "yes")
        MPVLib.setOptionStringK("config-dir", configDir)
        for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir"))
            MPVLib.setOptionStringK(opt, cacheDir)
        initOptions() // do this before init() so user-supplied config can override our choices
        MPVLib.init()
        /* Hardcoded options: */
        // we need to call write-watch-later manually
        MPVLib.setOptionStringK("save-position-on-quit", "no")
        // would crash before the surface is attached
        MPVLib.setOptionStringK("force-window", "no")
        // "no" wouldn't work and "yes" is not intended by the UI
        MPVLib.setOptionStringK("idle", "once")

        /** Applying syncplay-specific options */
        val playerOptions = PlayerOptions.get()
        MPVLib.setOptionStringK("alang", playerOptions.audioPreference)
        MPVLib.setOptionStringK("slang", playerOptions.ccPreference)

        MPVLib.setPropertyBoolean("pause" as java.lang.String, true as java.lang.Boolean)

        holder.addCallback(this)
        observeProperties()
    }

    var voInUse: String = ""
    private fun initOptions() {
        // apply phone-optimized defaults
        MPVLib.setOptionStringK("profile", "fast")


        voInUse = if (valueBlockingly(PREF_MPV_GPU_NEXT, true)) "gpu-next" else "gpu"
        val hwdec = if (valueBlockingly(PREF_MPV_HARDWARE_ACCELERATION, true)) "auto" else "no"


        // vo: set display fps as reported by android
        val refreshRate = @Suppress("DEPRECATION") if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display.refreshRate
        } else {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                wm.defaultDisplay.mode.refreshRate
            } else {
                60f
            }
        }

        Log.v(TAG, "Display FPS: $refreshRate")
        MPVLib.setOptionStringK("display-fps-override", refreshRate.toString())

        // set non-complex options
        data class Property(val preference_name: String, val mpv_option: String)

        val opts = arrayOf(
            // vo-related
            Property("video_scale", "scale"),
            Property("video_scale_param1", "scale-param1"),
            Property("video_scale_param2", "scale-param2"),

            Property("video_downscale", "dscale"),
            Property("video_downscale_param1", "dscale-param1"),
            Property("video_downscale_param2", "dscale-param2"),

            Property("video_tscale", "tscale"),
            Property("video_tscale_param1", "tscale-param1"),
            Property("video_tscale_param2", "tscale-param2")
        )

        for ((preference_name, mpv_option) in opts) {
            //val preference = sharedPreferences.getString(preference_name, "")
            //if (!preference.isNullOrBlank())
                MPVLib.setOptionStringK(mpv_option, "")
        }

        // set more options

        val debandMode = "" //TODO: Preferencize: sharedPreferences.getString("video_debanding", "")
        if (debandMode == "gradfun") {
            // lower the default radius (16) to improve performance
            MPVLib.setOptionStringK("vf", "gradfun=radius=12")
        } else if (debandMode == "gpu") {
            MPVLib.setOptionStringK("deband", "yes")
        }

        MPVLib.setOptionStringK("video-sync", "audio")


        if (valueBlockingly(PREF_MPV_INTERPOLATION, false))
            MPVLib.setOptionStringK("interpolation", "yes")

        MPVLib.setOptionStringK("gpu-debug", "no")

        if (false /* TODO: sharedPreferences.getBoolean("video_fastdecode", false) */) {
            MPVLib.setOptionStringK("vd-lavc-fast", "yes")
            MPVLib.setOptionStringK("vd-lavc-skiploopfilter", "nonkey")
        }

        MPVLib.setOptionStringK("vo", voInUse)
        MPVLib.setOptionStringK("gpu-context", "android")
        MPVLib.setOptionStringK("opengl-es", "yes")
        MPVLib.setOptionStringK("hwdec", hwdec)
        MPVLib.setOptionStringK("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        MPVLib.setOptionStringK("ao", "audiotrack,opensles")
        MPVLib.setOptionStringK("tls-verify", "yes")
        MPVLib.setOptionStringK("tls-ca-file", "${this.context.filesDir.path}/cacert.pem")
        MPVLib.setOptionStringK("input-default-bindings", "yes")
        // Limit demuxer cache since the defaults are too high for mobile devices
        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        MPVLib.setOptionStringK("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        MPVLib.setOptionStringK("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")
        //
        val screenshotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        screenshotDir.mkdirs()
        MPVLib.setOptionStringK("screenshot-directory", screenshotDir.path)
    }

    private var filePath: String? = null

    fun playFile(filePath: String) {
        this.filePath = filePath
    }

    // Called when back button is pressed, or app is shutting down
    fun destroy() {
        // Disable surface callbacks to avoid using unintialized mpv state
        holder.removeCallback(this)

        MPVLib.destroy()
    }

    private fun observeProperties() {
        // This observes all properties needed by MPVView, MPVActivity or other classes
        data class Property(val name: String, val format: Int = MPV_FORMAT_NONE)
        val p = arrayOf(
            Property("time-pos", MPV_FORMAT_INT64),
            Property("duration", MPV_FORMAT_INT64),
            Property("pause", MPV_FORMAT_FLAG),
            Property("paused-for-cache", MPV_FORMAT_FLAG),
            Property("speed"),
            Property("track-list"),
            // observing double properties is not hooked up in the JNI code, but doing this
            // will restrict updates to when it actually changes
            Property("video-params/aspect", MPV_FORMAT_DOUBLE),
            //
            Property("playlist-pos", MPV_FORMAT_INT64),
            Property("playlist-count", MPV_FORMAT_INT64),
            Property("video-format"),
            Property("media-title", MPV_FORMAT_STRING),
            Property("metadata/by-key/Artist", MPV_FORMAT_STRING),
            Property("metadata/by-key/Album", MPV_FORMAT_STRING),
            Property("loop-playlist"),
            Property("loop-file"),
            Property("shuffle", MPV_FORMAT_FLAG),
            Property("hwdec-current")
        )

        for ((name, format) in p)
            MPVLib.observeProperty(name as java.lang.String, format)
    }

    fun addObserver(o: MPVLib.EventObserver) {
        MPVLib.addObserver(o)
    }
    fun removeObserver(o: MPVLib.EventObserver) {
        MPVLib.removeObserver(o)
    }

    data class Track(val mpvId: Int, val name: String)
    var tracks = mapOf<String, MutableList<Track>>(
        "audio" to arrayListOf(),
        "video" to arrayListOf(),
        "sub" to arrayListOf())

    data class Chapter(val index: Int, val title: String?, val time: Double)

    fun loadChapters(): MutableList<Chapter> {
        val chapters = mutableListOf<Chapter>()
        val count = MPVLib.getPropertyInt("chapter-list/count" as java.lang.String)!!
        for (i in 0 until count.toInt()) {
            val title = MPVLib.getPropertyString("chapter-list/$i/title" as java.lang.String)
            val time = MPVLib.getPropertyDouble("chapter-list/$i/time" as java.lang.String)!!
            chapters.add(
                Chapter(
                index=i,
                title= title.toString(),
                time= time.toDouble()
            )
            )
        }
        return chapters
    }

    // Property getters/setters

    var paused: Boolean
        get() = MPVLib.getPropertyBoolean("pause" as java.lang.String) == true
        set(value) = MPVLib.setPropertyBoolean("pause" as java.lang.String, value as java.lang.Boolean)

    var timePos: Int?
        get() = MPVLib.getPropertyInt("time-pos" as java.lang.String)?.toInt()
        set(progress) = MPVLib.setPropertyInt("time-pos" as java.lang.String, progress!! as Integer)

    val hwdecActive: String
        get() = MPVLib.getPropertyString("hwdec-current" as java.lang.String).toString() ?: "no"

    var playbackSpeed: Double?
        get() = MPVLib.getPropertyDouble("speed" as java.lang.String)?.toDouble()
        set(speed) = MPVLib.setPropertyDouble("speed" as java.lang.String, speed!! as java.lang.Double)

    var subDelay: Double?
        get() = MPVLib.getPropertyDouble("sub-delay" as java.lang.String)?.toDouble()
        set(speed) = MPVLib.setPropertyDouble("sub-delay" as java.lang.String, speed!! as java.lang.Double)

    var secondarySubDelay: Double?
        get() = MPVLib.getPropertyDouble("secondary-sub-delay" as java.lang.String)?.toDouble()
        set(speed) = MPVLib.setPropertyDouble("secondary-sub-delay" as java.lang.String, speed!! as java.lang.Double)

    val estimatedVfFps: Double?
        get() = MPVLib.getPropertyDouble("estimated-vf-fps" as java.lang.String)?.toDouble()

    val videoOutAspect: Double?
        get() = MPVLib.getPropertyDouble("video-out-params/aspect" as java.lang.String)?.toDouble()

    val videoOutRotation: Int?
        get() = MPVLib.getPropertyInt("video-out-params/rotate" as java.lang.String)?.toInt()

    class TrackDelegate(private val name: String) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            val v = MPVLib.getPropertyString(name as java.lang.String)?.toString()
            // we can get null here for "no" or other invalid value
            return v?.toIntOrNull() ?: -1
        }
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            if (value == -1)
                MPVLib.setPropertyString(name as java.lang.String, "no" as java.lang.String)
            else
                MPVLib.setPropertyInt(name as java.lang.String, value as Integer)
        }
    }

    var vid: Int by TrackDelegate("vid")
    var sid: Int by TrackDelegate("sid")
    var secondarySid: Int by TrackDelegate("secondary-sid")
    var aid: Int by TrackDelegate("aid")

    // Commands

    fun cycleSpeed() {
        val speeds = arrayOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
        val currentSpeed = playbackSpeed ?: 1.0
        val index = speeds.indexOfFirst { it > currentSpeed }
        playbackSpeed = speeds[if (index == -1) 0 else index]
    }

    fun getRepeat(): Int {
        return when (MPVLib.getPropertyString("loop-playlist" as java.lang.String).toString() +
                MPVLib.getPropertyString("loop-file" as java.lang.String).toString()) {
            "noinf" -> 2
            "infno" -> 1
            else -> 0
        }
    }

    fun cycleRepeat() {
        val state = getRepeat()
        when (state) {
            0, 1 -> {
                MPVLib.setPropertyString("loop-playlist" as java.lang.String, if (state == 1) "no"  as java.lang.String else "inf"  as java.lang.String)
                MPVLib.setPropertyString("loop-file" as java.lang.String, if (state == 1) "inf"  as java.lang.String else "no"  as java.lang.String)
            }
            2 -> MPVLib.setPropertyString("loop-file" as java.lang.String, "no" as java.lang.String)
        }
    }

    fun getShuffle(): Boolean {
        return MPVLib.getPropertyBoolean("shuffle" as java.lang.String) == true
    }

    fun changeShuffle(cycle: Boolean, value: Boolean = true) {
        // Use the 'shuffle' property to store the shuffled state, since changing
        // it at runtime doesn't do anything.
        val state = getShuffle()
        val newState = if (cycle) state.xor(value) else value
        if (state == newState)
            return
        MPVLib.command(arrayOf(if (newState) "playlist-shuffle" as java.lang.String else "playlist-unshuffle" as java.lang.String))
        MPVLib.setPropertyBoolean("shuffle" as java.lang.String, newState as java.lang.Boolean)
    }

    // Surface callbacks

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        MPVLib.setPropertyString("android-surface-size" as java.lang.String, "${width}x$height" as java.lang.String)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.w(TAG, "attaching surface")
        MPVLib.attachSurface(holder.surface)
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        MPVLib.setOptionStringK("force-window", "yes")

        if (filePath != null) {
            MPVLib.command(arrayOf("loadfile" as java.lang.String, filePath as java.lang.String))
            filePath = null
        } else {
            // We disable video output when the context disappears, enable it back
            MPVLib.setPropertyString("vo" as java.lang.String, voInUse as java.lang.String)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.w(TAG, "detaching surface")
        MPVLib.setPropertyString("vo" as java.lang.String, "null" as java.lang.String)
        MPVLib.setOptionStringK("force-window", "no")
        MPVLib.detachSurface()
    }

    companion object {
        private const val TAG = "mpv"
    }
}