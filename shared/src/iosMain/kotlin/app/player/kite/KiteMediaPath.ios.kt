package app.player.kite

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.startAccessingSecurityScopedResource
import io.github.vinceglb.filekit.stopAccessingSecurityScopedResource

/**
 * Resolves a file to its real filesystem path for KitePlayer (one of the app's video players,
 * built on FFmpeg). It also claims the file's security-scoped grant and holds it until the path
 * is released.
 *
 * FFmpeg is refused when it opens a path that nobody holds a grant for. [app.player.PlayerImpl]
 * holds the grants for the video and for a picked subtitle, and this claim covers any file that
 * reaches the resolver without one. A second claim is cheap: NSURL counts grants, so the release
 * below balances it.
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
