package app.room.sharedplaylist

import app.utils.isPlayableMediaFilename
import app.utils.loggy
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.bookmarkData
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSDirectoryEnumerationSkipsHiddenFiles
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsDirectoryKey
import platform.Foundation.NSURLNameKey

/**
 * iOS directory walk. The picked directory's [NSURL] is security-scoped; we hold that scope for
 * the whole enumeration so every descendant is reachable, and — while the scope is held — mint
 * an independent security-scoped bookmark for each media file via FileKit's [bookmarkData].
 * Those per-file bookmarks resolve on their own afterwards (no directory scope required), which
 * is what lets the player keep a file open across a playback session.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual suspend fun PlatformFile.indexMediaTree(): Map<String, ByteArray> {
    val out = LinkedHashMap<String, ByteArray>()
    val dirUrl = this.nsUrl

    val started = dirUrl.startAccessingSecurityScopedResource()
    try {
        val enumerator = NSFileManager.defaultManager.enumeratorAtURL(
            url = dirUrl,
            includingPropertiesForKeys = listOf(NSURLNameKey, NSURLIsDirectoryKey),
            options = NSDirectoryEnumerationSkipsHiddenFiles,
        ) { erroringUrl, error ->
            loggy("indexMediaTree: skipping ${erroringUrl?.path} — ${error?.localizedDescription}")
            true // keep enumerating past unreadable entries
        }

        var obj = enumerator?.nextObject() as? NSURL
        while (obj != null) {
            val current = obj
            if (!current.isDirectoryResource()) {
                val childName = current.lastPathComponent
                if (childName != null && isPlayableMediaFilename(childName) && !out.containsKey(childName)) {
                    // The child is reachable because dirUrl's scope is active; minting a bookmark
                    // now captures an independently-resolvable security scope for it.
                    runCatching { PlatformFile(current).bookmarkData().bytes }
                        .onSuccess { out[childName] = it }
                        .onFailure { loggy("indexMediaTree: bookmark failed for $childName — ${it.message}") }
                }
            }
            obj = enumerator?.nextObject() as? NSURL
        }
    } finally {
        if (started) dirUrl.stopAccessingSecurityScopedResource()
    }

    return out
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun NSURL.isDirectoryResource(): Boolean = memScoped {
    val valuePtr = alloc<ObjCObjectVar<Any?>>()
    val ok = getResourceValue(valuePtr.ptr, forKey = NSURLIsDirectoryKey, error = null)
    if (!ok) return@memScoped false
    when (val v = valuePtr.value) {
        is NSNumber -> v.boolValue
        is Boolean -> v
        else -> false
    }
}
