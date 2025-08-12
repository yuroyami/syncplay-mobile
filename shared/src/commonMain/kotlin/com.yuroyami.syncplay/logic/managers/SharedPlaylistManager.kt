package com.yuroyami.syncplay.logic.managers

import com.yuroyami.syncplay.protocol.sending.Packet
import com.yuroyami.syncplay.logic.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.logic.managers.datastore.valueBlockingly
import com.yuroyami.syncplay.logic.managers.datastore.writeValue
import com.yuroyami.syncplay.utils.getFileName
import com.yuroyami.syncplay.utils.iterateDirectory
import com.yuroyami.syncplay.logic.AbstractManager
import com.yuroyami.syncplay.logic.SyncplayViewmodel
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_shared_playlist_no_directories
import syncplaymobile.shared.generated.resources.room_shared_playlist_not_found

class SharedPlaylistManager(viewmodel: SyncplayViewmodel): AbstractManager(viewmodel) {

    val p = viewmodel.p

    /** Shuffles the current playlist and sends it to the server.
     * @param mode False to shuffle all playlist, True to shuffle only the remaining non-played items in queue.*/
    suspend fun shuffle(mode: Boolean) {
        /* If the shared playlist is empty, do nothing */
        if (p.session.spIndex.intValue < 0 || p.session.sharedPlaylist.isEmpty()) return


        /* Shuffling as per the mode selected: False = shuffle all, True = Shuffle rest */
        if (mode) {
            /* Shuffling the rest of playlist is a bit trickier, we split the shared playlist into two
             * grp1 is gonna be the group that doesn't change (everything until current index)
             * grp2 is the group to be shuffled since it's the 'remaining group' */

            val grp1 = p.session.sharedPlaylist.take(p.session.spIndex.intValue + 1).toMutableList()
            val grp2 = p.session.sharedPlaylist.takeLast(p.session.sharedPlaylist.size - grp1.size).shuffled()
            grp1.addAll(grp2)
            p.session.sharedPlaylist.clear()
            p.session.sharedPlaylist.addAll(grp1)
        } else {
            /* Shuffling everything is easy as Kotlin gives us the 'shuffle()' method */
            p.session.sharedPlaylist.shuffle()

            /* Index won't change, but the file at the given index did change, play it */
            retrieveFile(p.session.sharedPlaylist[p.session.spIndex.intValue])
        }

        /* Announcing a new updated list to the room members */
        p.send<Packet.PlaylistChange> {
            files = p.session.sharedPlaylist
        }
    }

    /** Adds URLs from the url adding popup */
    fun addURLs(string: List<String>) {
        val l = mutableListOf<String>()
        l.addAll(p.session.sharedPlaylist)
        for (s in string) {
            if (!p.session.sharedPlaylist.contains(s)) l.add(s)
        }
        p.send<Packet.PlaylistChange> {
            files = l
        }
    }

    /** Adding a file to the playlist: This basically adds one file name to the playlist, then,
     * adds the parent directory to the known media directories, after that, it informs the server
     * about it. The server will send back the new playlist which will invoke playlist updating */
    fun addFiles(uris: List<String>) {
        for (uri in uris) {
            /* We get the file name */
            val filename = getFileName(uri) ?: return

            /* If the playlist already contains this file name, prevent adding it */
            if (p.session.sharedPlaylist.contains(filename)) return

            /* If there is no duplicate, then we proceed, we check if the list is empty */
            if (p.session.sharedPlaylist.isEmpty() && p.session.spIndex.intValue == -1) {
                viewmodel.player?.injectVideo(uri, true)

                p.send<Packet.PlaylistIndex> {
                    index = 0
                }
                //TODO MAKE NON=ASYNC
            }
            p.session.sharedPlaylist.add(filename)
        }
        p.send<Packet.PlaylistChange> {
            files = p.session.sharedPlaylist
        }
    }


    /** Clears the shared playlist */
    fun clearPlaylist() {
        if (p.session.sharedPlaylist.isEmpty()) return

        p.send<Packet.PlaylistChange> {
            files = emptyList()
        }
    }

    /** This will delete an item from playlist at a given index 'i' */
    fun deleteItemFromPlaylist(i: Int) {
        p.session.sharedPlaylist.removeAt(i)
        p.send<Packet.PlaylistChange> {
            files = p.session.sharedPlaylist
        }

        if (p.session.sharedPlaylist.isEmpty()) {
            p.session.spIndex.intValue = -1
        }
    }

    /** This is to send a playlist selection change to the server.
     * This occurs when a user selects a different item from the shared playlist. */
    fun sendPlaylistSelection(i: Int) {
        p.send<Packet.PlaylistIndex> {
            index = i
        }
    }

    /** This is to change playlist selection in response other users' selection */
    suspend fun changePlaylistSelection(index: Int) {
        if (p.session.sharedPlaylist.size < (index + 1)) return /* In rare cases when this was called on an empty list */
        if (index != p.session.spIndex.intValue) {
            /* If the file on that index isn't playing, play the file */
            retrieveFile(p.session.sharedPlaylist[index])
        }
    }


    /****************************************************************************/

    /** Convenient method to add a folder path to the current set of media directories */
    suspend fun saveFolderPathAsMediaDirectory(uri: String) {
        val paths = valueBlockingly(DataStoreKeys.PREF_SP_MEDIA_DIRS, emptySet<String>()).toMutableSet()

        if (!paths.contains(uri)) paths.add(uri)

        writeValue(DataStoreKeys.PREF_SP_MEDIA_DIRS, paths.toSet())
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
            viewmodel.player?.injectVideo(fileName, isUrl = true)
        } else {
            /* We search our media directories which were added by the user in settings */
            val paths = valueBlockingly(DataStoreKeys.PREF_SP_MEDIA_DIRS, emptySet<String>())

            if (paths.isEmpty()) {
                viewmodel.actionManager.broadcastMessage(message = { getString(Res.string.room_shared_playlist_no_directories) }, isChat = false)
            }

            var fileUri2Play: String? = null

            /* We iterate through the media directory paths spreading their children tree **/
            for (path in paths) {
                iterateDirectory(uri = path, target = fileName) {
                    fileUri2Play  = it

                    /* Loading the file into our player **/
                    viewmodel.player?.injectVideo(fileUri2Play)
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