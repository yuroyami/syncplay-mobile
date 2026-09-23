package app.player.kite

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path

/**
 * Desktop needs no path resolution.
 *
 * A file picked through FileKit on Windows, macOS or Linux already has a real filesystem path, so
 * FFmpeg opens it directly. There is no content provider, no security scope and no descriptor, so
 * nothing is held open and nothing needs a release.
 */
internal object DesktopKiteMediaResolver : KiteMediaResolver {
    override fun resolve(file: PlatformFile): KiteMediaPath? =
        file.path.takeIf { it.isNotBlank() }?.let { KiteMediaPath(it) }
}
