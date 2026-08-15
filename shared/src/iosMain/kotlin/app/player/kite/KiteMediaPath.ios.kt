package app.player.kite

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path

/**
 * iOS needs no resolution at all.
 *
 * A file picked through FileKit resolves to a real filesystem path, and its security scope is
 * already held open around the whole playback by [app.player.PlayerImpl.injectVideoFile], so
 * FFmpeg can open the path directly and there is nothing here to release.
 */
internal object IosKiteMediaResolver : KiteMediaResolver {
    override fun resolve(file: PlatformFile): KiteMediaPath? =
        file.path.takeIf { it.isNotBlank() }?.let { KiteMediaPath(it) }
}
