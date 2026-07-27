package app.player.mpvjvm

import app.utils.loggy
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import java.io.File

/**
 * Locates and loads libmpv once per process. Search order:
 *  1. Bundled copy in the app image: `<compose.application.resources.dir>/mpv`
 *     (produced by :desktopApp:fetchMpvNatives — a brew-closure bundle on macOS).
 *  2. Well-known system locations (Homebrew on macOS, distro lib dirs on Linux, PATH on
 *     Windows via JNA's default search).
 *
 * The load attempt is lazy and cached; MpvJvmEngine.isAvailable reads [library] != null, so the
 * engine simply greys out in the picker on machines without libmpv instead of crashing.
 */
object MpvNativeLoader {

    val library: LibMpv? by lazy {
        runCatching { load() }
            .onFailure { loggy("mpv(desktop): libmpv not loadable — ${it.message}") }
            .getOrNull()
    }

    /** Absolute path of the loaded dylib/dll/so, for diagnostics. */
    var loadedFrom: String? = null
        private set

    private fun load(): LibMpv {
        val extraDirs = buildList {
            System.getProperty("compose.application.resources.dir")?.let { res ->
                val bundled = File(res, "mpv")
                if (bundled.isDirectory) add(bundled.absolutePath)
            }
            System.getenv("SYNKPLAY_MPV_DIR")?.takeIf { it.isNotBlank() }?.let { add(it) }
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("mac")) {
                add("/opt/homebrew/lib")   // Apple Silicon Homebrew
                add("/usr/local/lib")      // Intel Homebrew / MacPorts-ish
            }
        }

        // NativeLibrary.addSearchPath scopes the extra dirs to these library names only,
        // without clobbering the global jna.library.path (which vlcj also relies on).
        for (name in listOf("mpv", "libmpv-2")) {
            extraDirs.forEach { NativeLibrary.addSearchPath(name, it) }
        }

        val lastError: Throwable
        try {
            val lib = Native.load("mpv", LibMpv::class.java)
            loadedFrom = runCatching { NativeLibrary.getInstance("mpv").file?.absolutePath }.getOrNull()
            loggy("mpv(desktop): loaded libmpv from ${loadedFrom ?: "?"}")
            return lib
        } catch (e: UnsatisfiedLinkError) {
            lastError = e
        }
        // Windows dev builds ship "libmpv-2.dll".
        try {
            val lib = Native.load("libmpv-2", LibMpv::class.java)
            loadedFrom = runCatching { NativeLibrary.getInstance("libmpv-2").file?.absolutePath }.getOrNull()
            loggy("mpv(desktop): loaded libmpv-2 from ${loadedFrom ?: "?"}")
            return lib
        } catch (e: UnsatisfiedLinkError) {
            throw UnsatisfiedLinkError("libmpv not found (tried 'mpv' and 'libmpv-2'): ${lastError.message}")
        }
    }
}
