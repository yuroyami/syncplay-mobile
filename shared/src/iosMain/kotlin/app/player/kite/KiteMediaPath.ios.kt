package app.player.kite

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.startAccessingSecurityScopedResource
import io.github.vinceglb.filekit.stopAccessingSecurityScopedResource

/**
 * iOS resolves to the real filesystem path, and claims the file's grant for as long as the
 * resolution is held.
 *
 * The video's own grant is held around the whole playback by [app.player.PlayerImpl], but a file
 * that arrives here without going through that path (a subtitle picked on its own) has no grant of
 * its own, and FFmpeg is then refused when it opens the path. Claiming here is cheap: NSURL counts
 * grants, so a second claim on a file already held is balanced by the release below.
 */
internal object IosKiteMediaResolver : KiteMediaResolver {
    override fun resolve(file: PlatformFile): KiteMediaPath? {
        val path = file.path.takeIf { it.isNotBlank() } ?: return null
        val claimed = runCatching { file.startAccessingSecurityScopedResource() }.getOrDefault(false)
        return KiteMediaPath(
            uri = path,
            releaseAction = { if (claimed) runCatching { file.stopAccessingSecurityScopedResource() } },
        )
    }
}
