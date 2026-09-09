package app.player.kite

import io.github.vinceglb.filekit.PlatformFile

/**
 * Something KitePlayer's FFmpeg backend can open, together with the native resource that keeps
 * that answer valid. Android resolutions may own a file descriptor; ordinary paths and URLs own
 * nothing.
 */
internal class KiteMediaPath(
    /** The string to hand to `MediaItem(uri = ...)`. */
    val uri: String,

    /**
     * Demuxer options that must accompany [uri], for `MediaItem(openOptions = ...)`. Empty for an
     * ordinary path. Carries `"fd"` when the answer is a descriptor rather than a path.
     */
    val openOptions: Map<String, String> = emptyMap(),

    private val releaseAction: () -> Unit = {},
) {
    private var released = false

    /** Frees anything the resolution held open. Safe to call more than once. */
    fun release() {
        if (released) return
        released = true
        releaseAction()
    }
}

/**
 * Platform bridge from the app's file picker to KitePlayer. The already-platform-specific engine
 * registry injects this into [KiteEngine], keeping the player implementation in common code
 * without adding another application-level expect/actual seam.
 */
internal fun interface KiteMediaResolver {
    /**
     * Resolves [file], or returns null when this platform cannot reach it. The caller owns the
     * result and must [KiteMediaPath.release] it when media changes or the player is destroyed.
     */
    fun resolve(file: PlatformFile): KiteMediaPath?
}

/**
 * Wraps an already-openable string (a remote URL) as a path that holds nothing. Whether the
 * linked FFmpeg can actually reach that scheme is its own answer, reported as a typed failure.
 */
internal fun kiteMediaPathOf(uri: String): KiteMediaPath = KiteMediaPath(uri)
