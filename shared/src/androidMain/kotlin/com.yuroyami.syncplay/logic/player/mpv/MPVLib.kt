package `is`.xyz.mpv

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface
import androidx.annotation.Keep
import java.util.concurrent.CopyOnWriteArrayList

//package `is`.xyz.mpv

@Suppress("unused")
object MPVLib {
    init {
        arrayOf("mpv", "player").forEach { System.loadLibrary(it) }
    }

    @JvmStatic external fun create(appctx: Context)
    @JvmStatic external fun init()
    @JvmStatic external fun destroy()
    @JvmStatic external fun attachSurface(surface: Surface)
    @JvmStatic external fun detachSurface()

    @JvmStatic external fun command(cmd: Array<java.lang.String>)

    @JvmStatic external fun setOptionString(name: java.lang.String, value: java.lang.String): Int
    fun setOptionStringK(name: String, value: String): Int{
       return setOptionString(java.lang.String(name), java.lang.String(value))
    }

    @JvmStatic external fun grabThumbnail(dimension: Int): Bitmap

    // FIXME: get methods are actually nullable
    @JvmStatic external fun getPropertyInt(property: java.lang.String): Integer?
    @JvmStatic external fun setPropertyInt(property: java.lang.String, value: Integer)
    @JvmStatic external fun getPropertyDouble(property: java.lang.String): java.lang.Double?
    @JvmStatic external fun setPropertyDouble(property: java.lang.String, value: java.lang.Double)
    @JvmStatic external fun getPropertyBoolean(property: java.lang.String): java.lang.Boolean?
    @JvmStatic external fun setPropertyBoolean(property: java.lang.String, value: java.lang.Boolean)
    @JvmStatic external fun getPropertyString(property: java.lang.String): java.lang.String?
    @JvmStatic external fun setPropertyString(property: java.lang.String, value: java.lang.String)

    @JvmStatic external fun observeProperty(property: java.lang.String, format: Int)

    private val observers = CopyOnWriteArrayList<EventObserver>()

    @JvmStatic
    @Synchronized
    fun addObserver(o: EventObserver) {
        observers.add(o)
    }

    @JvmStatic
    @Synchronized
    fun removeObserver(o: EventObserver) {
        observers.remove(o)
    }

    @Keep
    @JvmStatic
    fun eventProperty(property: String, value: Long) {
        observers.forEach { it.eventProperty(property, value) }
    }

    @Keep
    @JvmStatic
    fun eventProperty(property: String, value: Boolean) {
        observers.forEach { it.eventProperty(property, value) }
    }

    @Keep
    @JvmStatic
    fun eventProperty(property: String, value: Double) {
        observers.forEach { it.eventProperty(property, value) }
    }

    @Keep
    @JvmStatic
    fun eventProperty(property: String, value: String) {
        observers.forEach { it.eventProperty(property, value) }
    }

    @Keep
    @JvmStatic
    fun eventProperty(property: String) {
        observers.forEach { it.eventProperty(property) }
    }

    @Keep
    @JvmStatic
    fun event(eventId: Int) {
        observers.forEach { it.event(eventId) }
    }

    private val logObservers = CopyOnWriteArrayList<LogObserver>()

    @JvmStatic
    @Synchronized
    fun addLogObserver(o: LogObserver) {
        logObservers.add(o)
    }

    @JvmStatic
    @Synchronized
    fun removeLogObserver(o: LogObserver) {
        logObservers.remove(o)
    }

    @Keep
    @JvmStatic
    fun logMessage(prefix: String, level: Int, text: String) {
        logObservers.forEach { it.logMessage(prefix, level, text) }
    }

    interface EventObserver {
        fun eventProperty(property: String)
        fun eventProperty(property: String, value: Long)
        fun eventProperty(property: String, value: Boolean)
        fun eventProperty(property: String, value: String)
        fun eventProperty(property: String, value: Double)
        fun event(eventId: Int)
    }

    interface LogObserver {
        fun logMessage(prefix: String, level: Int, text: String)
    }

    object mpvFormat {
        const val MPV_FORMAT_NONE = 0
        const val MPV_FORMAT_STRING = 1
        const val MPV_FORMAT_OSD_STRING = 2
        const val MPV_FORMAT_FLAG = 3
        const val MPV_FORMAT_INT64 = 4
        const val MPV_FORMAT_DOUBLE = 5
        const val MPV_FORMAT_NODE = 6
        const val MPV_FORMAT_NODE_ARRAY = 7
        const val MPV_FORMAT_NODE_MAP = 8
        const val MPV_FORMAT_BYTE_ARRAY = 9
    }

    object mpvEventId {
        const val MPV_EVENT_NONE = 0
        const val MPV_EVENT_SHUTDOWN = 1
        const val MPV_EVENT_LOG_MESSAGE = 2
        const val MPV_EVENT_GET_PROPERTY_REPLY = 3
        const val MPV_EVENT_SET_PROPERTY_REPLY = 4
        const val MPV_EVENT_COMMAND_REPLY = 5
        const val MPV_EVENT_START_FILE = 6
        const val MPV_EVENT_END_FILE = 7
        const val MPV_EVENT_FILE_LOADED = 8

        @Deprecated("")
        const val MPV_EVENT_IDLE = 11

        @Deprecated("")
        const val MPV_EVENT_TICK = 14

        const val MPV_EVENT_CLIENT_MESSAGE = 16
        const val MPV_EVENT_VIDEO_RECONFIG = 17
        const val MPV_EVENT_AUDIO_RECONFIG = 18
        const val MPV_EVENT_SEEK = 20
        const val MPV_EVENT_PLAYBACK_RESTART = 21
        const val MPV_EVENT_PROPERTY_CHANGE = 22
        const val MPV_EVENT_QUEUE_OVERFLOW = 24
        const val MPV_EVENT_HOOK = 25
    }

    object mpvLogLevel {
        const val MPV_LOG_LEVEL_NONE = 0
        const val MPV_LOG_LEVEL_FATAL = 10
        const val MPV_LOG_LEVEL_ERROR = 20
        const val MPV_LOG_LEVEL_WARN = 30
        const val MPV_LOG_LEVEL_INFO = 40
        const val MPV_LOG_LEVEL_V = 50
        const val MPV_LOG_LEVEL_DEBUG = 60
        const val MPV_LOG_LEVEL_TRACE = 70
    }
}