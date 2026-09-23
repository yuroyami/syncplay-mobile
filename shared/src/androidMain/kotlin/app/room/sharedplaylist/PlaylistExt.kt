package app.room.sharedplaylist

import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.utils.contextObtainer
import app.utils.isPlayableMediaFilename
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import java.io.File

/**
 * The Android walk of a media directory. Picked directories arrive as SAF tree URIs
 * (`content://…/tree/…`) and are walked with [DocumentFile]. A raw filesystem path (legacy, not
 * SAF) is walked with [java.io.File]. Each media file is stored as the bytes of its content URI
 * or path. Reads work through the persistable permission that FileKit took on the tree when the
 * directory was remembered, so this code takes no per-file permissions (it cannot for tree
 * children).
 */
actual suspend fun PlatformFile.indexMediaTree(): Map<String, ByteArray> {
    val out = LinkedHashMap<String, ByteArray>()
    val identifier = this.path

    if (identifier.startsWith("content://", ignoreCase = true)) {
        val context = contextObtainer.invoke()
        val root = DocumentFile.fromTreeUri(context, identifier.toUri()) ?: return out

        fun walk(doc: DocumentFile) {
            for (child in doc.listFiles()) {
                if (child.isDirectory) {
                    walk(child)
                } else {
                    val childName = child.name ?: continue
                    if (isPlayableMediaFilename(childName) && !out.containsKey(childName)) {
                        out[childName] = child.uri.toString().encodeToByteArray()
                    }
                }
            }
        }
        walk(root)
    } else {
        val root = File(identifier)
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
    }

    return out
}
