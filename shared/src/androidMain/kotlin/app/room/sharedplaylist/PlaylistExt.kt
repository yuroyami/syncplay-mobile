package app.room.sharedplaylist

import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.utils.contextObtainer
import app.utils.isPlayableMediaFilename
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import java.io.File

/**
 * Android directory walk. Picked directories arrive as SAF tree URIs (`content://…/tree/…`),
 * enumerated via [DocumentFile]; a raw filesystem path (legacy / non-SAF) is walked with
 * [java.io.File]. Each discovered media file is stored as its content-URI / path bytes — reads
 * are authorized by the persistable permission FileKit took on the tree when the directory was
 * remembered, so we don't (and can't, for tree children) take per-file permissions here.
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
