package com.yuroyami.syncplay.utils

import com.yuroyami.syncplay.protocol.JsonSender
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.settings.writeValue
import com.yuroyami.syncplay.watchroom.dispatchOSD
import com.yuroyami.syncplay.watchroom.lyricist
import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext

object PlaylistUtils {

    /** Shuffles the current playlist and sends it to the server.
     * @param mode False to shuffle all playlist, True to shuffle only the remaining non-played items in queue.*/
    suspend fun shuffle(mode: Boolean) {
        /* If the shared playlist is empty, do nothing */
        if (viewmodel!!.p.session.sharedPlaylistIndex < 0 || viewmodel!!.p.session.sharedPlaylist.isEmpty()) return


        /* Shuffling as per the mode selected: False = shuffle all, True = Shuffle rest */
        if (mode) {
            /* Shuffling the rest of playlist is a bit trickier, we split the shared playlist into two
             * grp1 is gonna be the group that doesn't change (everything until current index)
             * grp2 is the group to be shuffled since it's the 'remaining group' */

            val grp1 = viewmodel!!.p.session.sharedPlaylist.take(viewmodel!!.p.session.sharedPlaylistIndex + 1).toMutableList()
            val grp2 = viewmodel!!.p.session.sharedPlaylist.takeLast(viewmodel!!.p.session.sharedPlaylist.size - grp1.size).shuffled()
            grp1.addAll(grp2)
            viewmodel!!.p.session.sharedPlaylist.clear()
            viewmodel!!.p.session.sharedPlaylist.addAll(grp1)
        } else {
            /* Shuffling everything is easy as Kotlin gives us the 'shuffle()' method */
            viewmodel!!.p.session.sharedPlaylist.shuffle()

            /* Index won't change, but the file at the given index did change, play it */
            retrieveFile(viewmodel!!.p.session.sharedPlaylist[viewmodel!!.p.session.sharedPlaylistIndex])
        }

        /* Announcing a new updated list to the room members */
        viewmodel!!.p.sendPacket(JsonSender.sendPlaylistChange(viewmodel!!.p.session.sharedPlaylist))
    }

    /** Adds URLs from the url adding popup */
    fun addURLs(string: List<String>) {
        val l = mutableListOf<String>()
        l.addAll(viewmodel!!.p.session.sharedPlaylist)
        for (s in string) {
            if (!viewmodel!!.p.session.sharedPlaylist.contains(s)) l.add(s)
        }
        viewmodel!!.p.sendPacket(JsonSender.sendPlaylistChange(l))
    }

    /** Adding a file to the playlist: This basically adds one file name to the playlist, then,
     * adds the parent directory to the known media directories, after that, it informs the server
     * about it. The server will send back the new playlist which will invoke playlist updating */
    fun addFiles(uris: List<String>) {
        for (uri in uris) {
            /* We get the file name */
            val filename = getFileName(uri) ?: return

            /* If the playlist already contains this file name, prevent adding it */
            if (viewmodel!!.p.session.sharedPlaylist.contains(filename)) return

            /* If there is no duplicate, then we proceed, we check if the list is empty */
            if (viewmodel!!.p.session.sharedPlaylist.isEmpty() && viewmodel!!.p.session.sharedPlaylistIndex == -1) {
                viewmodel?.player?.injectVideo(uri, true)
                viewmodel?.p?.sendPacket(JsonSender.sendPlaylistIndex(0))
            }
            viewmodel!!.p.session.sharedPlaylist.add(filename)
        }
        viewmodel!!.p.sendPacket(JsonSender.sendPlaylistChange(viewmodel?.p?.session!!.sharedPlaylist))
    }


    /** Clears the shared playlist */
    fun clearPlaylist() {
        if (viewmodel!!.p.session.sharedPlaylist.isEmpty()) return
        viewmodel!!.p.sendPacket(JsonSender.sendPlaylistChange(emptyList()))
    }

    /** This will delete an item from playlist at a given index 'i' */
    fun deleteItemFromPlaylist(i: Int) {
        viewmodel?.p?.session?.sharedPlaylist?.removeAt(i)
        viewmodel?.p?.sendPacket(JsonSender.sendPlaylistChange(viewmodel?.p?.session!!.sharedPlaylist))
        if (viewmodel?.p!!.session.sharedPlaylist.isEmpty()) {
            viewmodel?.p?.session?.sharedPlaylistIndex = -1
        }
    }

    /** This is to send a playlist selection change to the server.
     * This occurs when a user selects a different item from the shared playlist. */
    fun sendPlaylistSelection(index: Int) {
        viewmodel?.p?.sendPacket(JsonSender.sendPlaylistIndex(index))
    }

    /** This is to change playlist selection in response other users' selection */
    suspend fun changePlaylistSelection(index: Int) {
        if (viewmodel!!.p.session.sharedPlaylist.size < (index + 1)) return /* In rare cases when this was called on an empty list */
        if (index != viewmodel!!.p.session.sharedPlaylistIndex) {
            /* If the file on that index isn't playing, play the file */
            retrieveFile(viewmodel?.p!!.session.sharedPlaylist[index])
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
            viewmodel?.player?.injectVideo(fileName, isUrl = true)
        } else {
            /* We search our media directories which were added by the user in settings */
            val paths = valueBlockingly(DataStoreKeys.PREF_SP_MEDIA_DIRS, emptySet<String>())

            if (paths.isEmpty()) {
                RoomUtils.broadcastMessage(lyricist.strings.roomSharedPlaylistNoDirectories, false)
            }

            var fileUri2Play: String? = null

            /* We iterate through the media directory paths spreading their children tree **/
            for (path in paths) {
                println("Iterating media dirs....")

                iterateDirectory(uri = path, target = fileName) {
                    fileUri2Play  = it

                    /** Loading the file into our player **/
                    viewmodel?.player?.injectVideo(fileUri2Play)
                }

                println("Iterating done?....")

            }
            if (fileUri2Play == null) {
                if (viewmodel?.media?.fileName != fileName) {
                    val s = lyricist.strings.roomSharedPlaylistNotFound
                    CoroutineScope(currentCoroutineContext()).dispatchOSD(s)
                    RoomUtils.broadcastMessage(s, false)
                }
            }

        }
    }

}