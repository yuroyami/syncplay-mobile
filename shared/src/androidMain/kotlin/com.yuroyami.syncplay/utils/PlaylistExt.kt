package com.yuroyami.syncplay.utils

import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.managers.SharedPlaylistManager
import com.yuroyami.syncplay.managers.protocol.PacketCreator
import kotlinx.coroutines.launch

actual suspend fun SharedPlaylistManager.addFolderToPlaylist(uri: String) {
    /* First, we save it in our media directories as a common directory */
    saveFolderPathAsMediaDirectory(uri)

    /* Now we get children files */
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
        uri.toUri(),
        DocumentsContract.getTreeDocumentId(uri.toUri())
    )

    /** Obtaining the children tree from the path **/
    val tree = DocumentFile.fromTreeUri(contextObtainer.obtainAppContext(), childrenUri) ?: return

    /** We iterate through the children file tree and add them to playlist */
    val newList = mutableListOf<String>()
    iterateDirectory(tree) {
        newList.add(it)
    }
    newList.sort()

    if (viewmodel.session.spIndex.intValue == -1) {
        retrieveFile(newList.first())
        viewmodel.networkManager.send<PacketCreator.PlaylistIndex> {
            index = 0
        }.await()
    }
    viewmodel.networkManager.send<PacketCreator.PlaylistChange> {
        files = viewmodel.session.sharedPlaylist + newList
    }.await()
}

fun SharedPlaylistManager.iterateDirectory(dir: DocumentFile, onFileDetected: (String) -> Unit) {
    val files = dir.listFiles()

    for (file in files) {
        if (file.isDirectory) {
            iterateDirectory(file, onFileDetected)
        } else {
            if (file.name == null) continue

            if (!viewmodel.session.sharedPlaylist.contains(file.name!!)) {
                onFileDetected(file.name!!)
            }
        }
    }
}

actual suspend fun iterateDirectory(uri: String, target: String, onFileFound: suspend (String) -> Unit) {
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
        loggy("Iterating: ${file.name}")

        suspend fun stuff(filename: String) {
            if (filename == target) {
                onFileFound.invoke((tree.findFile(filename)?.uri).toString())
            }
        }

        if (file.isDirectory) {
            iterateDirectory(file.uri.toString(), target) {
                stuff(it)
            }
        } else {
            stuff(file.name!!)
        }
    }
}


actual fun SharedPlaylistManager.savePlaylistLocally(toFolderUri: String) {
    val context = contextObtainer.obtainAppContext()
    val folder = DocumentFile.fromTreeUri(context, toFolderUri.toUri())
    val txt = folder?.createFile("text/plain", "SharedPlaylist_${System.currentTimeMillis()}.txt")

    /** Converting the shared playlist to a text string, line by line */
    val stringBuilder = StringBuilder()
    for (f in viewmodel.session.sharedPlaylist) {
        stringBuilder.appendLine(f)
    }
    val string = stringBuilder.appendLine().trim().toString()

    /** Writing to the file */
    val s = context.contentResolver.openOutputStream(txt?.uri ?: return)
    s?.write(string.toByteArray(Charsets.UTF_8))
    s?.flush()
    s?.close()
}

actual fun SharedPlaylistManager.loadPlaylistLocally(fromUri: String, alsoShuffle: Boolean) {
    val context = contextObtainer.obtainAppContext()

    /** Opening the input stream of the file */
        val s = context.contentResolver.openInputStream(fromUri.toUri()) ?: return
        val string = s.readBytes().decodeToString()
        s.close()

    /** Reading content */
            val lines = string.split("\n").toMutableList()


    /** If the user chose the shuffling option along with it, then we shuffle it */
    if (alsoShuffle) lines.shuffle()

    /** Updating the shared playlist */
    viewmodel.viewModelScope.launch {
        viewmodel.networkManager.send<PacketCreator.PlaylistChange> {
            files = lines
        }.await()
    }
}
