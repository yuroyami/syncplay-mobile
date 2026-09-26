package app.room.sharedplaylist

import app.utils.isPlayableMediaFilename
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import java.io.File

/**
 * Desktop directory walk for the shared playlist (the file list that everyone in a room follows):
 * plain filesystem recursion, with no SAF (Android's Storage Access Framework) and no security
 * scopes. Each media file is stored as the bytes of its absolute path, which reopen it directly.
 */
actual suspend fun PlatformFile.indexMediaTree(): Map<String, ByteArray>? {
    val out = LinkedHashMap<String, ByteArray>()
    val root = File(this.path)
    if (!root.isDirectory || !root.canRead()) return null

    fun walk(dir: File) {
        val files = dir.listFiles() ?: return
        for (child in files) {
            if (child.isDirectory) {
                walk(child)
            } else {
                val childName = child.name
                if (isPlayableMediaFilename(childName) && !out.containsKey(childName)) {
                    out[childName] = child.absolutePath.encodeToByteArray()
                }
            }
        }
    }
    walk(root)

    return out
}
