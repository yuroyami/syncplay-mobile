package app.utils

import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import app.R
import app.protocol.JsonSender.sendPlaylistChange
import app.protocol.JsonSender.sendPlaylistIndex
import app.sharedplaylist.DirectoriesActivity
import app.ui.activities.WatchActivity
import app.utils.ExoUtils.injectVideo
import app.utils.MiscUtils.getFileName
import app.utils.UIUtils.toasty
import app.wrappers.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Shared Playlists also have their fair amount of methods related to it which I believe can be
 * wrapped into their own class.**/
object SharedPlaylistUtils {

    /** Adding a file to the playlist: This basically adds one file name to the playlist, then,
     * adds the parent directory to the known media directories, after that, it informs the server
     * about it. The server will send back the new playlist which will invoke playlist updating */
    fun WatchActivity.addFilesToPlaylist(uris: List<Uri>) {
        lifecycleScope.launch(Dispatchers.IO) {
            for (uri in uris) {
                /** We get the file name */
                val filename = getFileName(uri) ?: return@launch

                /** If the playlist already contains this file name, prevent adding it */
                if (p.session.sharedPlaylist.contains(filename)) return@launch

                /** If there is no duplicate, then we proceed, we check if the list is empty */
                if (p.session.sharedPlaylist.isEmpty() && p.session.sharedPlaylistIndex == -1) {
                    /* If it is empty, then we load the media file */
                    media = MediaFile()
                    media!!.uri = uri
                    media!!.collectInfo(this@addFilesToPlaylist)
                    //TODO: injectVideo(uri.toString(), true)
                    p.sendPacket(sendPlaylistIndex(0))
                }
                p.session.sharedPlaylist.add(filename)
            }
            p.sendPacket(sendPlaylistChange(p.session.sharedPlaylist))

        }
    }

    fun WatchActivity.addFolderToPlaylist(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            /* First, we save it in our media directories as a common directory */
            saveFolderPathAsMediaDirectory(uri)

            /* Now we get children files */
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri)
            )

            /** Obtaining the children tree from the path **/
            val tree = DocumentFile.fromTreeUri(this@addFolderToPlaylist, childrenUri) ?: return@launch
            val files = tree.listFiles()

            /** We iterate through the children file tree and add them to playlist */
            val newList = mutableListOf<String>()
            for (file in files) {
                if (file.isDirectory) continue
                val filename = file.name!!
                if (!p.session.sharedPlaylist.contains(filename)) newList.add(filename)
            }
            newList.sort()
            if (p.session.sharedPlaylistIndex == -1) {
                retrieveFile(newList.first())
                p.sendPacket(sendPlaylistIndex(0))
            }
            p.sendPacket(sendPlaylistChange(p.session.sharedPlaylist + newList))
        }
    }

    fun WatchActivity.saveFolderPathAsMediaDirectory(uri: Uri) {
        val list = DirectoriesActivity.getFolderList(
            p.gson,
            DirectoriesActivity.prefKey,
            this
        )
        if (!list.contains(uri.toString())) list.add(uri.toString())
        DirectoriesActivity.writeFolderList(
            list,
            p.gson,
            DirectoriesActivity.prefKey,
            this
        )
    }

    /** This is to send a playlist selection change to the server */
    fun WatchActivity.sendPlaylistSelection(index: Int) {
        p.sendPacket(sendPlaylistIndex(index))
    }

    /** This is to change playlist selection in response other users' selection */
    fun WatchActivity.changePlaylistSelection(index: Int) {
        if (p.session.sharedPlaylist.size < (index + 1)) return /* In rare cases when this was called on an empty list */
        if (index != p.session.sharedPlaylistIndex) {
            /* If the file on that index isn't playing, play the file */
            retrieveFile(p.session.sharedPlaylist[index])
        }
    }

    /** This will search all media directories specified by the user in settings to look for a file
     * name and load it into ExoPlayer. This is executed on a separate thread since the IO operation
     * is heavy.
     */
    fun WatchActivity.retrieveFile(fileName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            /** We have to know whether the file name is an URL or just a file name */
            if (fileName.contains("http://", true) ||
                fileName.contains("https://", true) ||
                fileName.contains("ftp://", true)
            ) {

                /** Fetching the file as it is a URL */
                injectVideo(fileName.toUri())
                media = MediaFile()
                media?.uri = fileName.toUri()
                media?.url = fileName
                media?.collectInfoURL()
            } else {
                /** We search our media directories which were added by the user in settings */
                val sp = PreferenceManager.getDefaultSharedPreferences(this@retrieveFile)
                val folderJson = sp.getString("SHARED_PLAYLIST_MEDIA_DIRECTORIES", "[]")
                val paths = p.gson.fromJson<List<String>>(folderJson, List::class.java).toMutableList()

                if (paths.isEmpty()) {
                    //TODO: broadcastMessage(getString(R.string.room_shared_playlist_no_directories), false)
                }

                var fileUri2Play: Uri? = null
                /** We iterate through the media directory paths spreading their children tree **/
                for (path in paths) {
                    val uri = Uri.parse(path)

                    /** Will NOT work if Uri hasn't been declared persistent upon retrieving it.
                     * @see [DirectoriesActivity] */
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                        uri,
                        DocumentsContract.getTreeDocumentId(uri)
                    )

                    /** Obtaining the children tree from the path **/
                    val tree = DocumentFile.fromTreeUri(this@retrieveFile, childrenUri) ?: return@launch
                    val files = tree.listFiles()

                    /** We iterate through the children file tree, and we search for the specific file */
                    for (file in files) {
                        val filename = file.name!!
                        if (filename == fileName) {
                            fileUri2Play = tree.findFile(filename)?.uri ?: return@launch
                            /** Changing current file variable **/
                            media = MediaFile()
                            media?.uri = fileUri2Play
                            media?.collectInfo(this@retrieveFile)
                            /** Loading the file into our player **/
                            injectVideo(fileUri2Play)

                            break
                        }
                    }
                }
                if (fileUri2Play == null) {
                    if (media?.fileName != fileName) {
                        toasty(getString(R.string.room_shared_playlist_not_found))
                        //broadcastMessage(getString(R.string.room_shared_playlist_not_found), false)
                    }
                }
            }
        }
    }

    /** This will delete an item from playlist at a given index 'i' */
    fun WatchActivity.deleteItemFromPlaylist(i: Int) {
        p.session.sharedPlaylist.removeAt(i)
        p.sendPacket(sendPlaylistChange(p.session.sharedPlaylist))
        if (p.session.sharedPlaylist.isEmpty()) {
            p.session.sharedPlaylistIndex = -1
        }
    }

    /** Shuffles the playlist
     * @param mode False to shuffle all playlist, True to shuffle only the rest*/
    fun WatchActivity.shuffle(mode: Boolean) {
        /** If the shared playlist is empty, do nothing */
        if (p.session.sharedPlaylistIndex < 0 || p.session.sharedPlaylist.isEmpty()) return

        /** Shuffling as per the mode selected: False = shuffle all, True = Shuffle rest */
        if (mode) {
            /** Shuffling the rest of playlist is a bit trickier, we split the shared playlist into two
             * grp1 is gonna be the group that doesn't change (everything until current index)
             * grp2 is the group to be shuffled since it's the 'remaining group' */

            val grp1 = p.session.sharedPlaylist.take(p.session.sharedPlaylistIndex + 1).toMutableList()
            val grp2 = p.session.sharedPlaylist.takeLast(p.session.sharedPlaylist.size - grp1.size).shuffled()
            grp1.addAll(grp2)
            p.session.sharedPlaylist.clear()
            p.session.sharedPlaylist.addAll(grp1)
        } else {
            /** Shuffling everything is easy as Kotlin gives us the 'shuffle()' method */
            p.session.sharedPlaylist.shuffle()

            /** Index won't change, but the file at the given index did change, play it */
            retrieveFile(p.session.sharedPlaylist[p.session.sharedPlaylistIndex])
        }

        /** Announcing a new updated list to the room members */
        p.sendPacket(sendPlaylistChange(p.session.sharedPlaylist))
    }

    /** Saves the playlist as a plain text file (.txt) to the designated folder with a timestamp of the current time
     * @param folderUri The uri of the save folder */
    fun WatchActivity.saveSHP(folderUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val folder = DocumentFile.fromTreeUri(this@saveSHP, folderUri)
            val txt = folder?.createFile("text/plain", "SharedPlaylist_${System.currentTimeMillis()}.txt")

            /** Converting the shared playlist to a text string, line by line */
            var string = ""
            for (f in p.session.sharedPlaylist) {
                string += f
                if (p.session.sharedPlaylist.indexOf(f) != p.session.sharedPlaylist.size - 1) {
                    string += "\n"
                }
            }
            string = string.trim()

            /** Writing to the file */
            val s = contentResolver.openOutputStream(txt?.uri ?: return@launch)
            s?.write(string.toByteArray(Charsets.UTF_8))
            s?.flush()
            s?.close()
        }

    }

    /** After selecting a txt file, this will attempt to load the contents of the plain text file
     * line by line as separate individual file name entries.
     * @param fileUri Uri of the selected txt file
     * @param shuffle Determines whether the content of the file should be shuffled or not */
    fun WatchActivity.loadSHP(fileUri: Uri, shuffle: Boolean) {
        /** Opening the input stream of the file */
        val s = contentResolver.openInputStream(fileUri) ?: return
        val string = s.readBytes().decodeToString()
        s.close()

        /** Reading content */
        val lines = string.split("\n").toMutableList()

        /** If the user chose the shuffling option along with it, then we shuffle it */
        if (shuffle) {
            lines.shuffle()
        }

        /** Updating the shared playlist */
        p.sendPacket(sendPlaylistChange(lines))
    }

    /** Adds URLs from the url adding popup */
    fun WatchActivity.addURLs(string: List<String>) {
        val l = mutableListOf<String>()
        l.addAll(p.session.sharedPlaylist)
        for (s in string) {
            if (!p.session.sharedPlaylist.contains(s)) l.add(s)
        }
        p.sendPacket(sendPlaylistChange(l))
    }

    /** Clears the shared playlist */
    fun WatchActivity.clearPlaylist() {
        if (p.session.sharedPlaylist.isEmpty()) return
        p.sendPacket(sendPlaylistChange(emptyList()))
    }

}