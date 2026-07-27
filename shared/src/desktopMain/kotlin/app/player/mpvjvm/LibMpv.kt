package app.player.mpvjvm

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.Structure

/**
 * Minimal JNA binding of the libmpv stable C ABI (client.h + render.h), covering exactly what
 * [MpvJvmImpl] needs: lifecycle, options/properties, commands, the event loop, and the
 * SOFTWARE render API (mpv draws BGRA frames into a caller buffer — no OpenGL context juggling,
 * plugs straight into the shared Skia frame pipeline).
 *
 * JNA is already on the classpath transitively via vlcj. All functions follow libmpv's
 * documented thread-safety: everything may be called from any thread except that
 * mpv_render_context_render must not run inside the update callback.
 */
@Suppress("FunctionName")
interface LibMpv : Library {

    fun mpv_create(): Pointer?
    fun mpv_initialize(handle: Pointer): Int
    fun mpv_terminate_destroy(handle: Pointer)
    fun mpv_client_api_version(): Long
    fun mpv_error_string(error: Int): String

    fun mpv_set_option_string(handle: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(handle: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(handle: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer)

    /** data points to storage matching [format] (int flag / long / double). */
    fun mpv_get_property(handle: Pointer, name: String, format: Int, data: Pointer): Int
    fun mpv_set_property(handle: Pointer, name: String, format: Int, data: Pointer): Int

    /** args = NULL-terminated char** — pass JNA StringArray. */
    fun mpv_command(handle: Pointer, args: Array<String?>): Int

    fun mpv_observe_property(handle: Pointer, replyUserdata: Long, name: String, format: Int): Int

    /** Blocks up to [timeout] seconds; never returns null (returns MPV_EVENT_NONE on timeout). */
    fun mpv_wait_event(handle: Pointer, timeout: Double): MpvEvent

    /* ------------------------------ render API (sw) ------------------------------ */

    fun mpv_render_context_create(result: Pointer /* mpv_render_context** */, handle: Pointer, params: Pointer): Int
    fun mpv_render_context_set_update_callback(ctx: Pointer, callback: RenderUpdateCallback?, callbackCtx: Pointer?)
    fun mpv_render_context_update(ctx: Pointer): Long
    fun mpv_render_context_render(ctx: Pointer, params: Pointer): Int
    fun mpv_render_context_free(ctx: Pointer)

    interface RenderUpdateCallback : Callback {
        fun invoke(callbackCtx: Pointer?)
    }

    /** mpv_event — { mpv_event_id event_id; int error; uint64_t reply_userdata; void *data; } */
    @Structure.FieldOrder("event_id", "error", "reply_userdata", "data")
    class MpvEvent : Structure(), Structure.ByReference {
        @JvmField var event_id: Int = 0
        @JvmField var error: Int = 0
        @JvmField var reply_userdata: Long = 0
        @JvmField var data: Pointer? = null
    }

    /** mpv_event_property — { const char *name; mpv_format format; void *data; } */
    @Structure.FieldOrder("name", "format", "data")
    class MpvEventProperty(p: Pointer) : Structure(p) {
        @JvmField var name: Pointer? = null
        @JvmField var format: Int = 0
        @JvmField var data: Pointer? = null

        init {
            read()
        }
    }

    /** mpv_render_param — { mpv_render_param_type type; void *data; } (int padded to pointer). */
    @Structure.FieldOrder("type", "data")
    class MpvRenderParam : Structure() {
        @JvmField var type: Int = 0
        @JvmField var data: Pointer? = null
    }

    companion object {
        /* mpv_format */
        const val FORMAT_NONE = 0
        const val FORMAT_STRING = 1
        const val FORMAT_FLAG = 3
        const val FORMAT_INT64 = 4
        const val FORMAT_DOUBLE = 5

        /* mpv_event_id (subset) */
        const val EVENT_NONE = 0
        const val EVENT_SHUTDOWN = 1
        const val EVENT_START_FILE = 6
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8
        const val EVENT_VIDEO_RECONFIG = 17
        const val EVENT_PROPERTY_CHANGE = 22

        /* mpv_render_param_type (subset) */
        const val RENDER_PARAM_INVALID = 0
        const val RENDER_PARAM_API_TYPE = 1
        const val RENDER_PARAM_SW_SIZE = 17
        const val RENDER_PARAM_SW_FORMAT = 18
        const val RENDER_PARAM_SW_STRIDE = 19
        const val RENDER_PARAM_SW_POINTER = 20

        /* mpv_render_context_update flags */
        const val RENDER_UPDATE_FRAME = 1L

        /** Builds a NULL-terminated contiguous mpv_render_param array in native memory.
         *  Each entry is (type, dataPointer); the trailing entry is {INVALID, NULL}. */
        fun renderParams(vararg params: Pair<Int, Pointer?>): Memory {
            val paramSize = 16L // int (padded to 8) + pointer, 64-bit
            val mem = Memory(paramSize * (params.size + 1))
            params.forEachIndexed { i, (type, data) ->
                mem.setInt(i * paramSize, type)
                mem.setPointer(i * paramSize + 8, data)
            }
            mem.setInt(params.size * paramSize, RENDER_PARAM_INVALID)
            mem.setPointer(params.size * paramSize + 8, null)
            return mem
        }
    }
}
