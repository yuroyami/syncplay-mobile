package app.room.sharedplaylist

import app.utils.isPlayableMediaFilename
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import java.io.File

/**
 * Desktop directory walk — plain filesystem recursion, no SAF and no security scopes.
 * Each discovered media file is stored as its absolute-path bytes, re-openable directly.
 */
actual suspend fun PlatformFile.indexMediaTree(): Map<String, ByteArray> {
    val out = LinkedHashMap<String, ByteArray>()
    val root = File(this.path)
    if (!root.isDirectory) return out

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
