package com.yuroyami.syncplay.managers

import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.managers.datastore.DatastoreManager.Companion.value
import com.yuroyami.syncplay.managers.datastore.DatastoreManager.Companion.writeValue
import com.yuroyami.syncplay.managers.protocol.creator.PacketOut
import com.yuroyami.syncplay.utils.getFileName
import com.yuroyami.syncplay.utils.iterateDirectory
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_shared_playlist_no_directories
import syncplaymobile.shared.generated.resources.room_shared_playlist_not_found

class SharedPlaylistManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    /** Shuffles the current playlist and sends it to the server.
     * @param mode False to shuffle all playlist, True to shuffle only the remaining non-played items in queue.*/
    suspend fun shuffle(mode: Boolean) {
        /* If the shared playlist is empty, do nothing */
        if (viewmodel.session.spIndex.intValue < 0 || viewmodel.session.sharedPlaylist.isEmpty()) return


        /* Shuffling as per the mode selected: False = shuffle all, True = Shuffle rest */
        if (mode) {
            /* Shuffling the rest of playlist is a bit trickier, we split the shared playlist into two
             * grp1 is gonna be the group that doesn't change (everything until current index)
             * grp2 is the group to be shuffled since it's the 'remaining group' */

            val grp1 = viewmodel.session.sharedPlaylist.take(viewmodel.session.spIndex.intValue + 1).toMutableList()
            val grp2 = viewmodel.session.sharedPlaylist.takeLast(viewmodel.session.sharedPlaylist.size - grp1.size).shuffled()
            grp1.addAll(grp2)
            viewmodel.session.sharedPlaylist.clear()
            viewmodel.session.sharedPlaylist.addAll(grp1)
        } else {
            /* Shuffling everything is easy as Kotlin gives us the 'shuffle()' method */
            viewmodel.session.sharedPlaylist.shuffle()

            /* Index won't change, but the file at the given index did change, play it */
            retrieveFile(viewmodel.session.sharedPlaylist[viewmodel.session.spIndex.intValue])
        }

        /* Announcing a new updated list to the room members */
        viewmodel.networkManager.send<PacketOut.PlaylistChange> {
            files = viewmodel.session.sharedPlaylist
        }
    }

    /** Adds URLs from the url adding popup */
    fun addURLs(string: List<String>) {
        val l = mutableListOf<String>()
        l.addAll(viewmodel.session.sharedPlaylist)
        for (s in string) {
            if (!viewmodel.session.sharedPlaylist.contains(s)) l.add(s)
        }
        viewmodel.networkManager.sendAsync<PacketOut.PlaylistChange> {
            files = l
        }
    }

    /** Adding a file to the playlist: This basically adds one file name to the playlist, then,
     * adds the parent directory to the known media directories, after that, it informs the server
     * about it. The server will send back the new playlist which will invoke playlist updating */
    suspend fun addFiles(uris: List<String>) {
        for (uri in uris) {
            /* We get the file name */
            val filename = getFileName(uri) ?: return

            /* If the playlist already contains this file name, prevent adding it */
            if (viewmodel.session.sharedPlaylist.contains(filename)) return

            /* If there is no duplicate, then we proceed, we check if the list is empty */
            if (viewmodel.session.sharedPlaylist.isEmpty() && viewmodel.session.spIndex.intValue == -1) {
                viewmodel.player.injectVideo(uri, true)

                viewmodel.networkManager.send<PacketOut.PlaylistIndex> {
                    index = 0
                }
                //TODO MAKE NON=ASYNC
            }
            viewmodel.session.sharedPlaylist.add(filename)
        }
        viewmodel.networkManager.send<PacketOut.PlaylistChange> {
            files = viewmodel.session.sharedPlaylist
        }
    }


    /** Clears the shared playlist */
    fun clearPlaylist() {
        if (viewmodel.session.sharedPlaylist.isEmpty()) return

        viewmodel.networkManager.sendAsync<PacketOut.PlaylistChange> {
            files = emptyList()
        }
    }

    /** This will delete an item from playlist at a given index 'i' */
    fun deleteItemFromPlaylist(i: Int) {
        viewmodel.session.sharedPlaylist.removeAt(i)
        viewmodel.networkManager.sendAsync<PacketOut.PlaylistChange> {
            files = viewmodel.session.sharedPlaylist
        }

        if (viewmodel.session.sharedPlaylist.isEmpty()) {
            viewmodel.session.spIndex.intValue = -1
        }
    }

    /** This is to send a playlist selection change to the server.
     * This occurs when a user selects a different item from the shared playlist. */
    fun sendPlaylistSelection(i: Int) {
        viewmodel.networkManager.sendAsync<PacketOut.PlaylistIndex> {
            index = i
        }
    }

    /** This is to change playlist selection in response other users' selection */
    suspend fun changePlaylistSelection(index: Int) {
        if (viewmodel.session.sharedPlaylist.size < (index + 1)) return /* In rare cases when this was called on an empty list */
        if (index != viewmodel.session.spIndex.intValue) {
            /* If the file on that index isn't playing, play the file */
            retrieveFile(viewmodel.session.sharedPlaylist[index])
        }
    }


    /****************************************************************************/

    companion object {

        /** Convenient method to add a folder path to the current set of media directories */
        suspend fun saveFolderPathAsMediaDirectory(uri: String) {
            val paths = value(DataStoreKeys.PREF_SP_MEDIA_DIRS, emptySet<String>()).toMutableSet()

            if (!paths.contains(uri)) paths.add(uri)

            writeValue(DataStoreKeys.PREF_SP_MEDIA_DIRS, paths.toSet())
        }
    }

    /**
     * name and load it into ExoPlayer. This is executed on a separate thread since the IO operation
     * is heavy.
     */
    suspend fun retrieveFile(fileName: String) {
        /* We have to know whether the file name is an URL or just a file name */
        if (fileName.contains("http://", true) ||
            fileName.contains("https://", true) ||
            fileName.contains("ftp://", true)
        ) {
            viewmodel.player.injectVideo(fileName, isUrl = true)
        } else {
            /* We search our media directories which were added by the user in settings */
            val paths = value(DataStoreKeys.PREF_SP_MEDIA_DIRS, emptySet<String>())

            if (paths.isEmpty()) {
                viewmodel.actionManager.broadcastMessage(message = { getString(Res.string.room_shared_playlist_no_directories) }, isChat = false)
            }

            var fileUri2Play: String? = null

            /* We iterate through the media directory paths spreading their children tree **/
            for (path in paths) {
                iterateDirectory(uri = path, target = fileName) {
                    fileUri2Play  = it

                    /* Loading the file into our player **/
                    viewmodel.player.injectVideo(fileUri2Play)
                }
            }
            if (fileUri2Play == null) {
                if (viewmodel.media?.fileName != fileName) {
                    val s = getString(Res.string.room_shared_playlist_not_found)
                    viewmodel.osdManager.dispatchOSD { s }
                    viewmodel.actionManager.broadcastMessage(message = { s }, isChat = false)
                }
            }

        }
    }
}