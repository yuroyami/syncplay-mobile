package com.yuroyami.syncplay.utils

import com.yuroyami.syncplay.managers.SharedPlaylistManager


expect suspend fun SharedPlaylistManager.addFolderToPlaylist(uri: String)

expect fun iterateDirectory(uri: String, target: String, onFileFound: (String) -> Unit)

/** Saves the playlist as a plain text file (.txt) to the designated folder with a timestamp of the current time
 * @param toFolderUri The uri of the save folder */
expect fun SharedPlaylistManager.savePlaylistLocally(toFolderUri: String)

/** After selecting a txt file, this will attempt to load the contents of the plain text file
 * line by line as separate individual file name entries.
 * @param fromUri Uri of the selected txt file
 * @param alsoShuffle Determines whether the content of the file should be shuffled or not */
expect fun SharedPlaylistManager.loadPlaylistLocally(fromUri: String, alsoShuffle: Boolean)
