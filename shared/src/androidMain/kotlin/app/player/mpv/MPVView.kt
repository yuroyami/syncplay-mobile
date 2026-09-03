package app.player.mpv

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Build
import android.util.AttributeSet
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import app.uicomponents.glassEnabledNow
import app.player.models.PlayerOptions
import app.preferences.Preferences.MPV_GPU_NEXT
import app.preferences.Preferences.MPV_HARDWARE_ACCELERATION
import app.preferences.Preferences.MPV_INTERPOLATION
import app.preferences.value
import app.utils.contextObtainer
import app.utils.loggy
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_DOUBLE
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_FLAG
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_INT64
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_NONE
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_STRING

/**
 * Hosts mpv's output in whichever surface the user's glass setting calls for; mpv itself only
 * wants an ANativeWindow either way.
 *
 * TextureView keeps the video pixels inside the view hierarchy, so Haze can capture them for glass
 * over video and the SurfaceView hole-punch cannot flicker under an overlay. SurfaceView can take
 * a hardware overlay plane instead (less power, no GPU copy) but is invisible to any in-app
 * effect. They are mutually exclusive, so this is a container with one child of the chosen type
 * rather than two engine classes; everything mpv-facing below is identical for both.
 */
class MPVView(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {

    /** The Surface handed to mpv; owned here, released when its backing view goes away. */
    private var mpvSurface: Surface? = null

    /** The SurfaceView or TextureView actually showing the video. */
    private var surfaceChild: View? = null

    /** The SurfaceView holder callback, kept so [destroy] can unregister it. */
    private var surfaceCallback: SurfaceHolder.Callback? = null

    fun initialize(configDir: String, cacheDir: String) {
        MPVLib.create(contextObtainer.invoke())
        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", configDir)
        for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir"))
            MPVLib.setOptionString(opt, cacheDir)
        initOptions() // run before init() so user-supplied config can override these choices
        MPVLib.init()
        MPVLib.setOptionString("save-position-on-quit", "no")
        // force-window off until a surface is attached, else mpv crashes
        MPVLib.setOptionString("force-window", "no")
        MPVLib.setOptionString("idle", "once")

        val playerOptions = PlayerOptions.get()
        MPVLib.setOptionString("alang", playerOptions.audioPreference)
        MPVLib.setOptionString("slang", playerOptions.ccPreference)

        MPVLib.setPropertyBoolean("pause", true)

        installSurfaceChild()
        observeProperties()
    }

    var voInUse: String = ""
    private fun initOptions() {
        // phone-optimized defaults
        MPVLib.setOptionString("profile", "fast")


        voInUse = if (MPV_GPU_NEXT.value()) "gpu-next" else "gpu"
        val hwdec = if (MPV_HARDWARE_ACCELERATION.value()) "auto" else "no"

        // report the display's actual refresh rate to mpv as display-fps-override
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

        MPVLib.setOptionString("display-fps-override", refreshRate.toString())

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
            MPVLib.setOptionString(mpv_option, "")
        }

        val debandMode = "" //TODO: Preferencize: sharedPreferences.getString("video_debanding", "")
        if (debandMode == "gradfun") {
            // lower the default radius (16) to improve performance
            MPVLib.setOptionString("vf", "gradfun=radius=12")
        } else if (debandMode == "gpu") {
            MPVLib.setOptionString("deband", "yes")
        }

        MPVLib.setOptionString("video-sync", "audio")


        if (MPV_INTERPOLATION.value()) MPVLib.setOptionString("interpolation", "yes")

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
        // Limit demuxer cache; mpv's defaults are too high for mobile devices
        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        MPVLib.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")
    }

    private var filePath: String? = null
        set(value) {
            field = value
            if (value != null) MPVLib.command(arrayOf("loadfile", value))
        }

    fun playFile(filePath: String) {
        this.filePath = filePath
        // Re-assert the surface, matching the original's surfaceCreated(holder) call here.
        mpvSurface?.takeIf { it.isValid }?.let { attachSurface(it) }
    }

    // Called when back button is pressed, or app is shutting down
    fun destroy() {
        // Stop the surface callbacks first so nothing reaches mpv mid-teardown, then hand the
        // surface back BEFORE the core goes: a surface still attached when MPVLib.destroy() runs
        // is released underneath the render thread.
        (surfaceChild as? TextureView)?.surfaceTextureListener = null
        surfaceCallback?.let { (surfaceChild as? SurfaceView)?.holder?.removeCallback(it) }
        surfaceCallback = null
        if (mpvSurface != null) detachSurface()
        removeAllViews()
        surfaceChild = null

        MPVLib.destroy()
    }

    private fun observeProperties() {
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
            Property("video-format"),
            Property("media-title", MPV_FORMAT_STRING),
            Property("hwdec-current")
        )

        for ((name, format) in p) {
            MPVLib.observeProperty(name, format)
        }
    }

    fun addObserver(o: MPVLib.EventObserver) {
        MPVLib.addObserver(o)
    }
    fun removeObserver(o: MPVLib.EventObserver) {
        MPVLib.removeObserver(o)
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

    /** Builds the video child for the current glass setting and wires its surface callbacks. */
    private fun installSurfaceChild() {
        removeAllViews()
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        surfaceChild = if (glassEnabledNow()) {
            TextureView(context).also { tv ->
                tv.layoutParams = lp
                tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        attachSurface(Surface(st))
                        setSurfaceSize(w, h)
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) =
                        setSurfaceSize(w, h)

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        detachSurface()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
                addView(tv)
            }
        } else {
            SurfaceView(context).also { sv ->
                sv.layoutParams = lp
                val callback = object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = attachSurface(holder.surface)

                    override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) =
                        setSurfaceSize(w, h)

                    override fun surfaceDestroyed(holder: SurfaceHolder) = detachSurface()
                }
                surfaceCallback = callback
                sv.holder.addCallback(callback)
                addView(sv)
            }
        }
    }

    private fun setSurfaceSize(width: Int, height: Int) {
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    private fun attachSurface(surface: Surface) {
        loggy("mpv: attaching surface")
        mpvSurface = surface
        MPVLib.attachSurface(surface)
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        MPVLib.setOptionString("force-window", "yes")
        // Restore video output (detachSurface sets it to "null")
        MPVLib.setPropertyString("vo", voInUse)
    }

    private fun detachSurface() {
        loggy("mpv: detaching surface")
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setOptionString("force-window", "no")
        MPVLib.detachSurface()
        // A SurfaceView's Surface is owned by its holder; only release the one we constructed.
        (surfaceChild as? TextureView)?.let { mpvSurface?.release() }
        mpvSurface = null
    }

    companion object {
        val vidsyncEntries = listOf(
            "audio", "display-resample", "display-resample-vdrop", "display-resample-desync", "display-tempo",
            "display-vdrop", "display-adrop", "display-desync", "desync"
        )

        val profileEntries = listOf(
            "fast", "high-quality", "gpu-hq", "low-latency", "sw-fast"
        )
    }
}