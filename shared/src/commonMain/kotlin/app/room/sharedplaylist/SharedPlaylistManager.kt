package app.room.sharedplaylist

import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.preferences.Preferences
import app.preferences.value
import app.protocol.WireMessage
import app.room.RoomViewmodel
import app.utils.appName
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_shared_playlist_no_directories
import syncplaymobile.shared.generated.resources.room_shared_playlist_not_found
import syncplaymobile.shared.generated.resources.room_untrusted_domain_warning

class SharedPlaylistManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    private val session get() = viewmodel.session

    /**
     * The playlist entry (filename or URL) we most recently *started loading* into the player.
     *
     * This — not [Session.spIndex] — is what [changePlaylistSelection] guards on to decide
     * whether an incoming index change actually needs a (re)load. Guarding on `spIndex` is wrong
     * because the protocol receive path sets `spIndex` to the new index *before*
     * `changePlaylistSelection` runs, so an `index != spIndex` check would always be false and
     * the file would never load. Tracking the loaded source instead makes the decision robust
     * for both local files and remote URLs, and lets us skip the redundant reload when our own
     * index echo comes back for a file we already loaded locally.
     */
    private var lastLoadedSource: String? = null

    /** Shuffles the current playlist and sends it to the server.
     * @param mode False to shuffle all playlist, True to shuffle only the remaining non-played items in queue.*/
    suspend fun shuffle(mode: Boolean) {
        /* If the shared playlist is empty, do nothing */
        if (session.spIndex.intValue < 0 || session.sharedPlaylist.isEmpty()) return

        /* Shuffling as per the mode selected: False = shuffle all, True = Shuffle rest */
        if (mode) {
            /* Shuffling the rest of playlist is a bit trickier, we split the shared playlist into two
             * grp1 is gonna be the group that doesn't change (everything until current index)
             * grp2 is the group to be shuffled since it's the 'remaining group' */

            val grp1 = session.sharedPlaylist.take(session.spIndex.intValue + 1).toMutableList()
            val grp2 = session.sharedPlaylist.takeLast(session.sharedPlaylist.size - grp1.size).shuffled()
            grp1.addAll(grp2)
            session.sharedPlaylist.clear()
            session.sharedPlaylist.addAll(grp1)
        } else {
            /* Shuffling everything is easy as Kotlin gives us the 'shuffle()' method */
            session.sharedPlaylist.shuffle()

            /* Index won't change, but the file at the given index did change, play it */
            retrieveFile(session.sharedPlaylist[session.spIndex.intValue])
        }

        /* Announcing a new updated list to the room members */
        viewmodel.networkManager.send(WireMessage.playlistChange(session.sharedPlaylist.toList()))
    }

    /** Adds URLs from the url adding popup, de-duplicating against the existing list and within
     *  the incoming batch itself. */
    fun addURLs(urls: List<String>) {
        val merged = session.sharedPlaylist.toMutableList()
        for (raw in urls) {
            val url = raw.trim()
            if (url.isNotEmpty() && !merged.contains(url)) merged.add(url)
        }
        if (merged.size == session.sharedPlaylist.size) return
        viewmodel.networkManager.sendAsync(WireMessage.playlistChange(merged))
    }

    /**
     * Adds locally-picked files to the shared playlist.
     *
     * Each file's name goes into the shared playlist (the only thing the protocol transmits),
     * while a durable bookmark is persisted via [MediaAccessRegistry] so the file can be
     * re-opened later — on iOS the picker's security scope is alive *right now* and would be
     * lost if we only kept its path string.
     *
     * Ordering matters: we broadcast the playlist change *before* the index change so peers
     * receive the list first, then the index, and their `changePlaylistSelection()` finds the
     * entry and auto-loads it. If the playlist was empty we also load the first file locally
     * straight from the live (still-scoped) [PlatformFile] for an instant start.
     */
    suspend fun addFiles(files: List<PlatformFile>) {
        val playlistWasEmpty = session.sharedPlaylist.isEmpty() && session.spIndex.intValue == -1

        // Collect the genuinely-new files, de-duplicating against the existing playlist and
        // within this batch (by filename — the playlist's unit of identity).
        val toAdd = mutableListOf<PlatformFile>()
        for (file in files) {
            val filename = file.name
            if (filename.isBlank() || session.sharedPlaylist.contains(filename)) continue
            if (toAdd.any { it.name == filename }) continue
            toAdd.add(file)
        }
        if (toAdd.isEmpty()) return

        MediaAccessRegistry.rememberFiles(toAdd)
        for (file in toAdd) session.sharedPlaylist.add(file.name)

        viewmodel.networkManager.send(WireMessage.playlistChange(session.sharedPlaylist.toList()))

        if (playlistWasEmpty) {
            // Load the first added file locally right away (works in solo mode too) from the live,
            // still-scoped handle, and mark it as the loaded source so our own index echo doesn't
            // reload it.
            val first = toAdd.first()
            lastLoadedSource = first.name
            session.spIndex.intValue = 0
            viewmodel.player.injectVideoFile(first)
            viewmodel.networkManager.send(WireMessage.playlistIndex(0))
        }
    }

    /**
     * Adds an entire folder to the shared playlist: persists durable access to the folder,
     * walks it for media files (capturing a durable per-file handle for each), appends their
     * names to the playlist, and — if nothing was playing — starts the first one.
     */
    suspend fun addFolderToPlaylist(dir: PlatformFile) {
        MediaAccessRegistry.rememberDirectory(dir)

        val index = dir.indexMediaTree()
        if (index.isEmpty()) {
            viewmodel.dispatcher.broadcastMessage(
                message = { getString(Res.string.room_shared_playlist_not_found, appName) },
                isChat = false
            )
            return
        }

        MediaAccessRegistry.rememberFileBookmarks(index)

        val names = index.keys.sorted()
        val playlistWasEmpty = session.spIndex.intValue == -1

        val merged = session.sharedPlaylist.toMutableList()
        for (n in names) if (!merged.contains(n)) merged.add(n)

        // Broadcast the new list *before* the index (same reasoning as addFiles): peers must
        // have the entries before their index update can resolve and auto-load one.
        viewmodel.networkManager.send(WireMessage.playlistChange(merged))

        if (playlistWasEmpty && merged.isNotEmpty()) {
            session.spIndex.intValue = 0
            retrieveFile(merged.first()) // sets lastLoadedSource on success
            viewmodel.networkManager.send(WireMessage.playlistIndex(0))
        }
    }

    /** Clears the shared playlist */
    fun clearPlaylist() {
        if (session.sharedPlaylist.isEmpty()) return
        viewmodel.networkManager.sendAsync(WireMessage.playlistChange(emptyList()))
    }

    /** This will delete an item from playlist at a given index 'i' */
    fun deleteItemFromPlaylist(i: Int) {
        if (i !in session.sharedPlaylist.indices) return
        session.sharedPlaylist.removeAt(i)
        viewmodel.networkManager.sendAsync(WireMessage.playlistChange(session.sharedPlaylist.toList()))

        if (session.sharedPlaylist.isEmpty()) {
            session.spIndex.intValue = -1
        }
    }

    /** Selects a playlist item. Online, this announces the index to the server, whose echo
     * drives the (synchronized) load on every client including us. In solo mode there is no
     * server round-trip, so we apply the selection directly. */
    fun sendPlaylistSelection(i: Int) {
        if (viewmodel.isSoloMode) {
            viewmodel.viewModelScope.launch { changePlaylistSelection(i) }
            return
        }
        viewmodel.networkManager.sendAsync(WireMessage.playlistIndex(i))
    }

    /**
     * Applies a playlist index selection: records it as the room position and loads the file at
     * that index unless it is already the loaded one.
     *
     * Called for both local and remote selections (see [app.protocol.event.RoomCallback.onPlaylistIndexChanged]).
     * The guard is [lastLoadedSource], NOT [Session.spIndex] — the receive path already advanced
     * spIndex by the time we get here, so an spIndex-equality guard would always short-circuit
     * and nothing would ever play (the long-standing "shared playlist does nothing" bug).
     */
    suspend fun changePlaylistSelection(index: Int) {
        if (index !in session.sharedPlaylist.indices) return /* In rare cases when this was called on an empty/short list */
        session.spIndex.intValue = index
        val target = session.sharedPlaylist[index]
        if (target == lastLoadedSource) return /* Already loaded this exact entry — don't reload */
        retrieveFile(target)
    }

    /**
     * Resolves a shared-playlist entry to actual media and loads it into the player.
     *
     * Remote URLs are gated by the trusted-domains check and handed straight to the player.
     * Local entries (filenames) are resolved through [MediaAccessRegistry], which checks the
     * direct per-file bookmark first and self-heals by re-indexing remembered media directories
     * if needed. Only when nothing resolves do we surface a "not found" / "no directories" hint.
     */
    suspend fun retrieveFile(fileName: String) {
        if (isRemoteUrl(fileName)) {
            if (!isUrlTrusted(fileName)) {
                val domain = extractDomain(fileName)
                val warning = getString(Res.string.room_untrusted_domain_warning, domain)
                viewmodel.dispatchOSD { warning }
                viewmodel.dispatcher.broadcastMessage(message = { warning }, isChat = false, isError = true)
                return
            }
            lastLoadedSource = fileName
            viewmodel.player.injectVideoURL(fileName)
            return
        }

        val resolved = MediaAccessRegistry.resolvePlayableFile(fileName)
        if (resolved != null) {
            lastLoadedSource = fileName
            viewmodel.player.injectVideoFile(resolved)
            return
        }

        // Nothing resolved. Don't nag if the requested file is in fact already the one playing.
        if (viewmodel.media?.fileName == fileName) return

        val message: suspend () -> String = if (Preferences.MEDIA_DIRECTORIES.value().isEmpty()) {
            { getString(Res.string.room_shared_playlist_no_directories) }
        } else {
            { getString(Res.string.room_shared_playlist_not_found, appName) }
        }
        viewmodel.dispatchOSD(getter = message)
        viewmodel.dispatcher.broadcastMessage(message = message, isChat = false)
    }

    /** Saves the playlist as a plain-text file (one entry per line) to the file the user chose. */
    fun savePlaylistLocally(destination: PlatformFile) {
        val snapshot = session.sharedPlaylist.toList()
        if (snapshot.isEmpty()) return
        viewmodel.viewModelScope.launch(Dispatchers.IO) {
            runCatching { destination.writeString(snapshot.joinToString("\n")) }
        }
    }

    /**
     * Loads playlist entries from a previously-exported plain-text file, one per line.
     * @param alsoShuffle whether to shuffle the loaded entries before broadcasting.
     */
    fun loadPlaylistLocally(source: PlatformFile, alsoShuffle: Boolean) {
        viewmodel.viewModelScope.launch(Dispatchers.IO) {
            val content = runCatching { source.readString() }.getOrNull() ?: return@launch
            val lines = content.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if (lines.isEmpty()) return@launch
            if (alsoShuffle) lines.shuffle()
            viewmodel.networkManager.sendAsync(WireMessage.playlistChange(lines))
        }
    }

    /****************************************************************************/

    private fun isRemoteUrl(s: String): Boolean =
        s.startsWith("http://", true) ||
            s.startsWith("https://", true) ||
            s.startsWith("ftp://", true) ||
            s.startsWith("www", true)

    /**
     * Extracts the domain from a URL string (e.g. "https://cdn.example.com/file.mp4" → "cdn.example.com").
     */
    private fun extractDomain(url: String): String {
        return url
            .substringAfter("://")
            .substringBefore("/")
            .substringBefore(":")
            .lowercase()
    }

    /**
     * Checks whether a URL is from a trusted domain.
     * If no trusted domains are configured, all URLs are allowed.
     */
    private fun isUrlTrusted(url: String): Boolean {
        val trustedRaw = Preferences.TRUSTED_DOMAINS.value().trim()
        if (trustedRaw.isEmpty()) return true // No restrictions configured

        val trustedDomains = trustedRaw.split("\n", ",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (trustedDomains.isEmpty()) return true

        val urlDomain = extractDomain(url)

        return trustedDomains.any { trusted ->
            urlDomain == trusted || urlDomain.endsWith(".$trusted")
        }
    }
}
