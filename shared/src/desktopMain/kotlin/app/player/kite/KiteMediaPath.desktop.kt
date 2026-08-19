package app.player.kite

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path

/**
 * Desktop needs no resolution at all.
 *
 * A file picked through FileKit on Windows, macOS or Linux is already a real filesystem path, so
 * FFmpeg opens it directly. There is no content provider, no security scope and no descriptor, so
 * nothing is held open and nothing has to be released.
 */
internal object DesktopKiteMediaResolver : KiteMediaResolver {
    override fun resolve(file: PlatformFile): KiteMediaPath? =
        file.path.takeIf { it.isNotBlank() }?.let { KiteMediaPath(it) }
}
