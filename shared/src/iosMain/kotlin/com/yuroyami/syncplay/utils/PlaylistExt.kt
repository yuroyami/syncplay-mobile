package com.yuroyami.syncplay.utils

import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.protocol.JsonSender
import com.yuroyami.syncplay.protocol.JsonSender.sendPlaylistChange
import com.yuroyami.syncplay.protocol.JsonSender.sendPlaylistIndex
import com.yuroyami.syncplay.utils.PlaylistUtils.retrieveFile
import com.yuroyami.syncplay.watchroom.viewmodel

actual suspend fun addFolderToPlaylist(uri: String) {
    /* First, we save it in our media directories as a common directory */
    PlaylistUtils.saveFolderPathAsMediaDirectory(uri)

    /* Now we get children files */
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
        uri.toUri(),
        DocumentsContract.getTreeDocumentId(uri.toUri())
    )

    /** Obtaining the children tree from the path **/
    val tree = DocumentFile.fromTreeUri(contextObtainer.obtainAppContext(), childrenUri) ?: return
    val files = tree.listFiles() //todo

    /** We iterate through the children file tree and add them to playlist */
    val newList = mutableListOf<String>()
    for (file in files) {
        if (file.isDirectory) continue
        val filename = file.name!!
        if (!viewmodel?.p?.session!!.sharedPlaylist.contains(filename)) newList.add(filename)
    }
    newList.sort()
    if (viewmodel?.p?.session!!.sharedPlaylistIndex == -1) {
        retrieveFile(newList.first())
        viewmodel?.p?.sendPacket(sendPlaylistIndex(0))
    }
    viewmodel?.p?.sendPacket(sendPlaylistChange(viewmodel?.p?.session!!.sharedPlaylist + newList))
}

actual suspend fun iterateDirectory(uri: String, target: String, onFileFound: (String) -> Unit) {
    /** Will NOT work if Uri hasn't been declared persistent upon retrieving it */
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
        uri.toUri(),
        DocumentsContract.getTreeDocumentId(uri.toUri())
    )

    val context = contextObtainer.obtainAppContext()

    /** Obtaining the children tree from the path **/
    val tree = DocumentFile.fromTreeUri(context, childrenUri) ?: return
    val files = tree.listFiles()

    /** We iterate through the children file tree, and we search for the specific file */
    for (file in files) {
        val filename = file.name!!
        if (filename == target) {
            onFileFound.invoke((tree.findFile(filename)?.uri ?: continue).toString())
            break
        }
    }
}


actual fun savePlaylistLocally(toFolderUri: String) {
    val folder = DocumentFile.fromTreeUri(context, toFolderUri.toUri())
    val txt = folder?.createFile("text/plain", "SharedPlaylist_${System.currentTimeMillis()}.txt")

    /** Converting the shared playlist to a text string, line by line */
    val stringBuilder = StringBuilder()
    for (f in viewmodel!!.p.session.sharedPlaylist) {
        stringBuilder.appendLine(f)
    }
    val string = stringBuilder.appendLine().trim().toString()

    /** Writing to the file */
    val s = context.contentResolver.openOutputStream(txt?.uri ?: return)
    s?.write(string.toByteArray(Charsets.UTF_8))
    s?.flush()
    s?.close()
}

actual fun loadPlaylistLocally(fromUri: String, alsoShuffle: Boolean) {
    /** Opening the input stream of the file */
    val s = context.contentResolver.openInputStream(fromUri.toUri()) ?: return
    val string = s.readBytes().decodeToString()
    s.close()

    /** Reading content */
    val lines = string.split("\n").toMutableList()


    /** If the user chose the shuffling option along with it, then we shuffle it */
    if (alsoShuffle) lines.shuffle()

    /** Updating the shared playlist */
    viewmodel?.p?.sendPacket(sendPlaylistChange(lines))
}
