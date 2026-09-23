package app.player.kite

import io.github.vinceglb.filekit.PlatformFile

/**
 * Something that KitePlayer's FFmpeg backend can open, together with the native resource that
 * keeps it valid. An Android resolution may own a file descriptor; ordinary paths and URLs own
 * nothing.
 */
internal class KiteMediaPath(
    /** The string to hand to `MediaItem(uri = ...)`. */
    val uri: String,

    /**
     * Demuxer options that must go with [uri], for `MediaItem(openOptions = ...)`. Empty for an
     * ordinary path. Carries `"fd"` when the resolution is a descriptor, not a path.
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
 * Platform bridge from the app's file picker to KitePlayer. Each platform's engine list, which is
 * platform code already, passes its resolver into [KiteEngine]. So the player implementation
 * stays in shared code without one more expect/actual declaration.
 */
internal fun interface KiteMediaResolver {
    /**
     * Resolves [file], or returns null when this platform cannot reach it. The caller owns the
     * result and must [KiteMediaPath.release] it when media changes or the player is destroyed.
     */
    fun resolve(file: PlatformFile): KiteMediaPath?
}

/**
 * Wraps a string that FFmpeg can open as it is (a remote URL) as a path that holds nothing. If the
 * linked FFmpeg cannot open that scheme, KitePlayer reports a typed failure.
 */
internal fun kiteMediaPathOf(uri: String): KiteMediaPath = KiteMediaPath(uri)
