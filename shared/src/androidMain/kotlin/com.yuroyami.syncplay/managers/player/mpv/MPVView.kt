package com.yuroyami.syncplay.managers.player.mpv

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_MPV_GPU_NEXT
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_MPV_HARDWARE_ACCELERATION
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_MPV_INTERPOLATION
import com.yuroyami.syncplay.managers.datastore.DatastoreManager.Companion.pref
import com.yuroyami.syncplay.managers.player.PlayerOptions
import com.yuroyami.syncplay.utils.contextObtainer
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_DOUBLE
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_FLAG
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_INT64
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_NONE
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_STRING
import kotlin.reflect.KProperty

internal class MPVView(context: Context, attrs: AttributeSet) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    fun initialize(configDir: String, cacheDir: String) {
        MPVLib.create(contextObtainer.invoke())
        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", configDir)
        for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir"))
            MPVLib.setOptionString(opt, cacheDir)
        initOptions() // do this before init() so user-supplied config can override our choices
        MPVLib.init()
        /* Hardcoded options: */
        // we need to call write-watch-later manually
        MPVLib.setOptionString("save-position-on-quit", "no")
        // would crash before the surface is attached
        MPVLib.setOptionString("force-window", "no")
        // "no" wouldn't work and "yes" is not intended by the UI
        MPVLib.setOptionString("idle", "once")

        /** Applying syncplay-specific options */
        val playerOptions = PlayerOptions.get()
        MPVLib.setOptionString("alang", playerOptions.audioPreference)
        MPVLib.setOptionString("slang", playerOptions.ccPreference)

        MPVLib.setPropertyBoolean("pause", true)

        holder.addCallback(this)
        observeProperties()
    }

    var voInUse: String = ""
    private fun initOptions() {
        // apply phone-optimized defaults
        MPVLib.setOptionString("profile", "fast")


        voInUse = if (pref(PREF_MPV_GPU_NEXT, true)) "gpu-next" else "gpu"
        val hwdec = if (pref(PREF_MPV_HARDWARE_ACCELERATION, true)) "auto" else "no"

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
        MPVLib.setOptionString("display-fps-override", refreshRate.toString())

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
            MPVLib.setOptionString(mpv_option, "")
        }

        // set more options

        val debandMode = "" //TODO: Preferencize: sharedPreferences.getString("video_debanding", "")
        if (debandMode == "gradfun") {
            // lower the default radius (16) to improve performance
            MPVLib.setOptionString("vf", "gradfun=radius=12")
        } else if (debandMode == "gpu") {
            MPVLib.setOptionString("deband", "yes")
        }

        MPVLib.setOptionString("video-sync", "audio")


        if (pref(PREF_MPV_INTERPOLATION, false))
            MPVLib.setOptionString("interpolation", "yes")

        MPVLib.setOptionString("gpu-debug", "no")

        if (false /* TODO: sharedPreferences.getBoolean("video_fastdecode", false) */) {
            MPVLib.setOptionString("vd-lavc-fast", "yes")
            MPVLib.setOptionString("vd-lavc-skiploopfilter", "nonkey")
        }

        MPVLib.setOptionString("vo", voInUse)
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("hwdec", hwdec)
        MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        MPVLib.setOptionString("tls-verify", "yes")
        MPVLib.setOptionString("tls-ca-file", "${this.context.filesDir.path}/cacert.pem")
        MPVLib.setOptionString("input-default-bindings", "yes")
        // Limit demuxer cache since the defaults are too high for mobile devices
        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        MPVLib.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")
        //
        val screenshotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        screenshotDir.mkdirs()
        MPVLib.setOptionString("screenshot-directory", screenshotDir.path)
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
            MPVLib.observeProperty(name, format)
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
        val count = MPVLib.getPropertyInt("chapter-list/count")!!
        for (i in 0 until count) {
            val title = MPVLib.getPropertyString("chapter-list/$i/title")
            val time = MPVLib.getPropertyDouble("chapter-list/$i/time")!!
            chapters.add(
                Chapter(
                index=i,
                title= title.toString(),
                    time = time
            )
            )
        }
        return chapters
    }

    // Property getters/setters

    var paused: Boolean
        get() = MPVLib.getPropertyBoolean("pause") == true
        set(value) = MPVLib.setPropertyBoolean("pause", value)

    var timePos: Int?
        get() = MPVLib.getPropertyInt("time-pos")
        set(progress) = MPVLib.setPropertyInt("time-pos", progress!!)

    val hwdecActive: String
        get() = MPVLib.getPropertyString("hwdec-current") ?: "no"

    var playbackSpeed: Double?
        get() = MPVLib.getPropertyDouble("speed")
        set(speed) = MPVLib.setPropertyDouble("speed", speed!!)

    var subDelay: Double?
        get() = MPVLib.getPropertyDouble("sub-delay")
        set(speed) = MPVLib.setPropertyDouble("sub-delay", speed!!)

    var secondarySubDelay: Double?
        get() = MPVLib.getPropertyDouble("secondary-sub-delay")
        set(speed) = MPVLib.setPropertyDouble("secondary-sub-delay", speed!!)

    val estimatedVfFps: Double?
        get() = MPVLib.getPropertyDouble("estimated-vf-fps")

    val videoOutAspect: Double?
        get() = MPVLib.getPropertyDouble("video-out-params/aspect")

    val videoOutRotation: Int?
        get() = MPVLib.getPropertyInt("video-out-params/rotate")

    class TrackDelegate(private val name: String) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            val v = MPVLib.getPropertyString(name)
            // we can get null here for "no" or other invalid value
            return v?.toIntOrNull() ?: -1
        }
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            if (value == -1)
                MPVLib.setPropertyString(name, "no")
            else
                MPVLib.setPropertyInt(name, value)
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
        return when (MPVLib.getPropertyString("loop-playlist").toString() +
                MPVLib.getPropertyString("loop-file").toString()) {
            "noinf" -> 2
            "infno" -> 1
            else -> 0
        }
    }

    fun cycleRepeat() {
        val state = getRepeat()
        when (state) {
            0, 1 -> {
                MPVLib.setPropertyString("loop-playlist", if (state == 1) "no" else "inf")
                MPVLib.setPropertyString("loop-file", if (state == 1) "inf" else "no")
            }

            2 -> MPVLib.setPropertyString("loop-file", "no")
        }
    }

    fun getShuffle(): Boolean {
        return MPVLib.getPropertyBoolean("shuffle") == true
    }

    fun changeShuffle(cycle: Boolean, value: Boolean = true) {
        // Use the 'shuffle' property to store the shuffled state, since changing
        // it at runtime doesn't do anything.
        val state = getShuffle()
        val newState = if (cycle) state.xor(value) else value
        if (state == newState)
            return
        MPVLib.command(arrayOf(if (newState) "playlist-shuffle" else "playlist-unshuffle"))
        MPVLib.setPropertyBoolean("shuffle", newState)
    }

    // Surface callbacks

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.w(TAG, "attaching surface")
        MPVLib.attachSurface(holder.surface)
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        MPVLib.setOptionString("force-window", "yes")

        if (filePath != null) {
            MPVLib.command(arrayOf("loadfile", filePath!!))
            filePath = null
        } else {
            // We disable video output when the context disappears, enable it back
            MPVLib.setPropertyString("vo", voInUse)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.w(TAG, "detaching surface")
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setOptionString("force-window", "no")
        MPVLib.detachSurface()
    }

    companion object {
        private const val TAG = "mpv"
    }
}