package app.player.vlcj

import uk.co.caprica.vlcj.factory.discovery.provider.DiscoveryDirectoryProvider
import java.io.File

/**
 * Points vlcj's native discovery at the libVLC copy bundled inside the app image, so users
 * never need a separate VLC install. Registered via ServiceLoader
 * (META-INF/services/uk.co.caprica.vlcj.factory.discovery.provider.DiscoveryDirectoryProvider)
 * and consulted BEFORE the built-in providers thanks to the high priority.
 *
 * Compose packages desktopApp/resources/<os>-<arch>/ into the app image and exposes the merged
 * directory through the "compose.application.resources.dir" system property. The bundled layout
 * mirrors the official VLC distributions, which is what vlcj's per-OS plugin-path logic expects:
 *  - macOS:   <resources>/vlc/lib/libvlc.dylib  +  <resources>/vlc/plugins/
 *  - Windows: <resources>/vlc/libvlc.dll        +  <resources>/vlc/plugins/
 *
 * When the bundle is absent (e.g. a dev `run` before fetchVlcNatives, or Linux where no
 * portable libVLC exists) this provider contributes nothing and vlcj falls back to its
 * standard discovery of a system-installed VLC.
 */
class BundledVlcDirectoryProvider : DiscoveryDirectoryProvider {

    override fun priority(): Int = 100

    override fun directories(): Array<String> {
        val res = System.getProperty("compose.application.resources.dir") ?: return emptyArray()
        val base = File(res, "vlc")
        // macOS layout keeps the dylibs under lib/; Windows keeps the DLLs at the root.
        return listOf(File(base, "lib"), base)
            .filter { it.isDirectory }
            .map { it.absolutePath }
            .toTypedArray()
    }

    override fun supported(): Boolean = true
}
