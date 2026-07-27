package app.desktop

import app.player.mpvjvm.LibMpv
import app.player.mpvjvm.MpvNativeLoader
import com.sun.jna.Memory
import kotlin.system.exitProcess

/**
 * Headless libmpv check — `./gradlew :desktopApp:mpvSmokeTest [-PmpvSmokeUrl=https://...]`.
 * The gradle task points compose.application.resources.dir at the fetched natives, exercising
 * the exact bundled-first discovery the packaged app uses. Prints the mpv version; with a URL,
 * plays it (no video out) for 12s and reports whether time-pos advances.
 */
fun main(args: Array<String>) {
    val mpv = MpvNativeLoader.library ?: run {
        System.err.println("MPV SMOKE FAILED — libmpv not loadable")
        exitProcess(1)
    }
    println("MPV SMOKE loaded from ${MpvNativeLoader.loadedFrom}")

    val handle = mpv.mpv_create() ?: run {
        System.err.println("MPV SMOKE FAILED — mpv_create")
        exitProcess(1)
    }
    mpv.mpv_set_option_string(handle, "vo", "null")
    mpv.mpv_set_option_string(handle, "ao", "null")
    mpv.mpv_set_option_string(handle, "terminal", "no")
    check(mpv.mpv_initialize(handle) >= 0) { "mpv_initialize failed" }

    fun getString(name: String): String? =
        mpv.mpv_get_property_string(handle, name)?.let { p ->
            val s = p.getString(0, "UTF-8"); mpv.mpv_free(p); s
        }

    println("MPV SMOKE OK — ${getString("mpv-version")} (libmpv ABI 0x${mpv.mpv_client_api_version().toString(16)})")

    args.firstOrNull()?.let { url ->
        @Suppress("UNCHECKED_CAST")
        mpv.mpv_command(handle, arrayOf("loadfile", url, "replace") as Array<String?>)
        Thread.sleep(12_000)
        val pos = Memory(8)
        val time = if (mpv.mpv_get_property(handle, "time-pos", LibMpv.FORMAT_DOUBLE, pos) >= 0) pos.getDouble(0) else -1.0
        val dur = if (mpv.mpv_get_property(handle, "duration", LibMpv.FORMAT_DOUBLE, pos) >= 0) pos.getDouble(0) else -1.0
        println("MPV SMOKE media time=${time}s duration=${dur}s")
    }

    mpv.mpv_terminate_destroy(handle)
    exitProcess(0)
}
