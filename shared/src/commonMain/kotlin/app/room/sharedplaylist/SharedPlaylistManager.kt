package app.room.sharedplaylist

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.i18n.Localization
import app.preferences.Preferences
import app.preferences.value
import app.protocol.WireMessage
import app.protocol.wire.PlaystateData
import app.protocol.wire.StateData
import app.room.RoomViewmodel
import app.utils.PLAYLIST_MAX_CHARACTERS
import app.utils.PLAYLIST_MAX_ITEMS
import app.utils.appName
import app.utils.ioDispatcher
import app.utils.playlistIsValid
import app.utils.generateTimestampMillis
import app.utils.urlHost
import app.utils.urlPath
import app.utils.writeTextCompat
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readString
import app.preferences.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SharedPlaylistManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    private val session get() = viewmodel.session

    /**
     * The playlist entry (file name or URL) that most recently started loading into the player.
     * The shared playlist is the file list that everyone in a room follows, and a room is the
     * group of people watching together.
     *
     * [changePlaylistSelection] checks this and [lastLoadedIndex], never
     * [app.protocol.Session.spIndex], to decide whether an index change needs a load. The receive
     * path sets `spIndex` to the new index before `changePlaylistSelection` runs, so an
     * `index != spIndex` check would always be false and the file would never load. Tracking the
     * loaded entry works for local files and remote URLs, and it skips the extra load when the
     * echo of this client's own index change comes back.
     */
    private var lastLoadedSource: String? = null

    /**
     * When the playlist index last moved, from any source. Auto-advance does not fire again inside
     * the near-end window. At the end of a file, every client in the room reaches the end at about
     * the same moment. Without this guard, each client sends its own "next item", and the room
     * moves several entries forward at once. Mirrors PC's notJustChangedPlaylist.
     */
    private var lastIndexChangeAtMs: Long = 0L

    /**
     * True when the index moved less than [withinMs] ago, so an end-of-file advance must not fire.
     */
    fun justChangedIndex(withinMs: Long): Boolean =
        lastIndexChangeAtMs != 0L && generateTimestampMillis() - lastIndexChangeAtMs < withinMs

    /** The playlist entry currently selected, or null when the playlist has no selection. */
    val selectedEntry: String?
        get() = session.sharedPlaylist.getOrNull(session.spIndex.intValue)

    /**
     * True when this client plays the entry that the playlist points at. The check compares the
     * playlist's own string, which identifies both file names and URLs.
     */
    val isPlayingSelectedEntry: Boolean
        get() = lastLoadedSource != null && lastLoadedSource == selectedEntry

    /**
     * The index that [lastLoadedSource] was loaded from. With it, a playlist that holds the same
     * name twice can move between the two entries.
     */
    private var lastLoadedIndex: Int = -1

    /**
     * URLs that the *local user* added (typed in, imported from a local playlist file, or allowed
     * at the prompt).
     *
     * These skip the trusted-domain check in [isUrlTrusted]. Adding a URL yourself is explicit
     * consent, so the URL loads even with no trusted domains set. The check exists to stop an
     * automatic switch to an untrusted URL that a *peer* added. Such a URL comes here only after
     * the user allows it at the prompt.
     */
    private val locallyAddedUrls = linkedSetOf<String>()

    /**
     * A URL that a peer added, from a host that is not trusted, waiting for the user's answer.
     *
     * Asking keeps the safety of a block and adds a way forward. A plain block would leave the
     * file unplayed, and the only fix would be a settings field with a syntax to guess.
     */
    data class UntrustedUrl(val url: String, val domain: String)

    val pendingUntrusted: StateFlow<UntrustedUrl?>
        field = MutableStateFlow(null)

    /** Allows the pending URL. When [always] is true, the host also goes into the trusted list. */
    fun allowPendingUrl(always: Boolean) {
        val pending = pendingUntrusted.value ?: return
        pendingUntrusted.value = null
        if (always) {
            val existing = Preferences.TRUSTED_DOMAINS.value().trim()
            val updated = if (existing.isEmpty()) pending.domain else existing + "\n" + pending.domain
            onIOThread { Preferences.TRUSTED_DOMAINS.set(updated) }
        }
        // The user consented here either way, so this URL plays even when its host is not listed.
        rememberLocalUrl(pending.url)
        onIOThread { retrieveFile(pending.url) }
    }

    /** Declines. The file stays unplayed and nothing is remembered. */
    fun dismissPendingUrl() {
        pendingUntrusted.value = null
    }

    /** The server said it has no shared playlists, so nothing about one is sent to it. */
    private val playlistsRefused: Boolean
        get() = !session.roomFeatures.supportsSharedPlaylists

    /** A list that the room lost in a dropped connection, with the index that was selected in it. */
    data class LostPlaylist(val entries: List<String>, val index: Int)

    /** Set while the room offers to restore a lost list. The room asks once. */
    val restoreOffer: StateFlow<LostPlaylist?>
        field = MutableStateFlow(null)

    /** The list at the moment the connection dropped, kept until the room's first list after it. */
    private var heldAcrossDrop: LostPlaylist? = null

    /**
     * Called when the connection drops. Keeps the list, so a room that comes back without it can
     * get it back.
     */
    fun noteConnectionLost() {
        if (heldAcrossDrop == null && session.sharedPlaylist.isNotEmpty()) {
            heldAcrossDrop = LostPlaylist(session.sharedPlaylist.toList(), session.spIndex.intValue)
        }
    }

    /**
     * Called when the server replaces the list. It finds the loaded entry in the new list, so an
     * index that follows that entry does not load the file again. A room that came back empty is
     * offered its old list.
     */
    fun onServerPlaylist(setBy: String) {
        realignLoadedIndex()
        val held = heldAcrossDrop ?: return
        heldAcrossDrop = null
        if (!cameBackEmpty(held.entries, session.sharedPlaylist, setBy)) return
        // Undo can bring the list back too, for a user who dismisses the question and then changes
        // their mind.
        undoStack.addLast(held.entries)
        while (undoStack.size > MAX_UNDO_STEPS) undoStack.removeFirst()
        canUndo.value = true
        restoreOffer.value = held
    }

    /** Sends the lost list back with its selection, unless someone filled the playlist meanwhile. */
    fun restoreLostPlaylist() {
        val lost = restoreOffer.value ?: return
        restoreOffer.value = null
        if (playlistsRefused || session.sharedPlaylist.isNotEmpty()) return
        viewmodel.networkManager.sendAsync(WireMessage.playlistChange(lost.entries))
        if (lost.index in lost.entries.indices) viewmodel.networkManager.sendAsync(WireMessage.playlistIndex(lost.index))
    }

    fun dismissRestoreOffer() {
        restoreOffer.value = null
    }

    /**
     * Finds the loaded entry in a new list: at its old index if it is still there, or else its
     * first match.
     */
    private fun realignLoadedIndex() {
        val source = lastLoadedSource ?: return
        val list = session.sharedPlaylist
        if (lastLoadedIndex in list.indices && list[lastLoadedIndex] == source) return
        lastLoadedIndex = list.indexOf(source)
    }

    /**
     * The playlists of this room before the last few edits.
     *
     * A shuffle, a clear or a wrong delete is one tap and reaches everyone, so it needs a way
     * back. The stack is small on purpose: this is an undo, not a history.
     */
    private val undoStack = ArrayDeque<List<String>>()

    val canUndo: StateFlow<Boolean>
        field = MutableStateFlow(false)

    /** Called before any edit that replaces the whole list. */
    private fun rememberForUndo() {
        undoStack.addLast(session.sharedPlaylist.toList())
        while (undoStack.size > MAX_UNDO_STEPS) undoStack.removeFirst()
        canUndo.value = true
    }

    /** Puts the previous playlist back and tells the room. */
    fun undoLastPlaylistChange() {
        if (playlistsRefused) return
        val previous = undoStack.removeLastOrNull() ?: return
        canUndo.value = undoStack.isNotEmpty()
        viewmodel.networkManager.sendAsync(WireMessage.playlistChange(previous))
    }

    /**
     * Remembers a URL that the user consented to. Past the cap, the oldest URL is forgotten, so a
     * long session stays bounded.
     */
    private fun rememberLocalUrl(url: String) {
        locallyAddedUrls.remove(url)
        locallyAddedUrls.add(url)
        while (locallyAddedUrls.size > MAX_LOCAL_URLS) locallyAddedUrls.remove(locallyAddedUrls.first())
    }

    /**
     * Shuffles the current playlist and sends it to the server.
     * @param mode False shuffles the whole playlist. True shuffles only the entries after the
     *   current one.
     */
    suspend fun shuffle(mode: Boolean) {
        if (playlistsRefused) return
        rememberForUndo()
        if (session.spIndex.intValue < 0 || session.sharedPlaylist.isEmpty()) return

        if (mode) {
            /* Split the playlist in two. grp1 (up to and including the current index) stays as it
             * is, and grp2 (the rest) is shuffled. */

            val grp1 = session.sharedPlaylist.take(session.spIndex.intValue + 1).toMutableList()
            val grp2 = session.sharedPlaylist.takeLast(session.sharedPlaylist.size - grp1.size).shuffled()
            grp1.addAll(grp2)
            session.sharedPlaylist.clear()
            session.sharedPlaylist.addAll(grp1)

            /* Only the entries after the current index moved, so the current file stays. Sending
             * the new list is all that is needed. */
            viewmodel.networkManager.send(WireMessage.playlistChange(session.sharedPlaylist.toList()))
        } else {
            session.sharedPlaylist.shuffle()

            /* A full shuffle moves every entry, so the old index now points at a different file.
             * Like PC's shuffleEntirePlaylist, reset to index 0 and send both the new list and the
             * new index. Every peer then selects index 0, instead of keeping its stale index and
             * landing on a different file. */
            session.spIndex.intValue = 0
            viewmodel.networkManager.send(WireMessage.playlistChange(session.sharedPlaylist.toList()))
            viewmodel.networkManager.send(WireMessage.playlistIndex(0))
            retrieveFile(session.sharedPlaylist[0])
        }
    }

    /**
     * Rejects a playlist over the protocol limits (Python's `playlistIsValid`: 250 items and 10000
     * characters) before it is sent. The official server refuses an oversized `playlistChange` and
     * sends the old playlist again. Without this check, the user's additions would vanish one
     * round trip later with no message.
     * @return true when the list is over the limits. The caller must then not send it.
     */
    private fun rejectsOversizedPlaylist(files: List<String>): Boolean {
        if (playlistIsValid(files)) return false
        val warning: suspend () -> String =
            { Localization.strings.roomSharedPlaylistLimit(PLAYLIST_MAX_ITEMS, PLAYLIST_MAX_CHARACTERS) }
        viewmodel.dispatchWarning(warning)
        viewmodel.dispatcher.broadcastMessage(message = warning, isChat = false, isError = true)
        return true
    }

    /** Adds URLs from the add-URL popup. Duplicates of the existing list, and duplicates within
     *  the batch itself, are skipped. */
    fun addURLs(urls: List<String>) {
        if (playlistsRefused) return
        val merged = session.sharedPlaylist.toMutableList()
        for (raw in urls) {
            val url = raw.trim()
            if (url.isNotEmpty() && !merged.contains(url)) {
                merged.add(url)
                // The local user typed this URL in, so it is trusted by consent, whatever the
                // trusted-domains setting says (see [locallyAddedUrls] and [isUrlTrusted]).
                if (isRemoteUrl(url)) rememberLocalUrl(url)
            }
        }
        if (merged.size == session.sharedPlaylist.size) return
        if (rejectsOversizedPlaylist(merged)) return
        rememberForUndo()
        viewmodel.networkManager.sendAsync(WireMessage.playlistChange(merged))
    }

    /**
     * Adds files that the user picked to the shared playlist.
     *
     * Each file's name goes into the shared playlist, because the protocol sends only names.
     * [MediaAccessRegistry] saves a lasting bookmark, so the file can open again later. On iOS,
     * the picker's security scope is alive only now, and a path string alone would lose it.
     *
     * The order matters: the playlist change goes out before the index change. Peers get the list
     * first, then the index, so their `changePlaylistSelection()` finds the entry and loads it.
     * When the playlist was empty, the first file also loads locally, straight from the live
     * [PlatformFile] with its scope still open, for an instant start.
     */
    suspend fun addFiles(files: List<PlatformFile>) {
        if (playlistsRefused) return
        val playlistWasEmpty = session.sharedPlaylist.isEmpty() && session.spIndex.intValue == -1

        // Collect the new files. Skip duplicates of the existing playlist and within this batch,
        // by file name (the playlist's unit of identity).
        val toAdd = mutableListOf<PlatformFile>()
        for (file in files) {
            val filename = file.name
            if (filename.isBlank() || session.sharedPlaylist.contains(filename)) continue
            if (toAdd.any { it.name == filename }) continue
            toAdd.add(file)
        }
        if (toAdd.isEmpty()) return
        if (rejectsOversizedPlaylist(session.sharedPlaylist + toAdd.map { it.name })) return

        MediaAccessRegistry.rememberFiles(toAdd)
        for (file in toAdd) session.sharedPlaylist.add(file.name)

        viewmodel.networkManager.send(WireMessage.playlistChange(session.sharedPlaylist.toList()))

        if (playlistWasEmpty) {
            // Load the first added file locally now (also in solo mode), from the live handle with
            // its scope still open. Mark it as the loaded source, so the echo of this client's own
            // index does not load it again.
            val first = toAdd.first()
            lastLoadedSource = first.name
            lastLoadedIndex = 0
            session.spIndex.intValue = 0
            viewmodel.player.injectVideoFile(first)
            viewmodel.networkManager.send(WireMessage.playlistIndex(0))
        }
    }

    /**
     * Adds a whole folder to the shared playlist. It saves lasting access to the folder, walks it
     * for media files (with a lasting handle for each), adds their names to the playlist and,
     * when the playlist had no selection, starts the first one.
     */
    suspend fun addFolderToPlaylist(dir: PlatformFile) {
        if (playlistsRefused) return
        MediaAccessRegistry.rememberDirectory(dir)

        val index = dir.indexMediaTree()
        if (index.isEmpty()) {
            viewmodel.dispatcher.broadcastMessage(
                message = { Localization.strings.roomSharedPlaylistNotFound(appName) },
                isChat = false
            )
            return
        }

        MediaAccessRegistry.rememberFileBookmarks(index)

        val names = index.keys.sorted()
        val playlistWasEmpty = session.spIndex.intValue == -1

        val merged = session.sharedPlaylist.toMutableList()
        for (n in names) if (!merged.contains(n)) merged.add(n)
        if (rejectsOversizedPlaylist(merged)) return

        // Send the new list before the index, as in addFiles. Peers need the entries before their
        // index update can resolve and load one.
        viewmodel.networkManager.send(WireMessage.playlistChange(merged))

        if (playlistWasEmpty && merged.isNotEmpty()) {
            session.spIndex.intValue = 0
            retrieveFile(merged.first()) // sets lastLoadedSource on success
            viewmodel.networkManager.send(WireMessage.playlistIndex(0))
        }
    }

    fun clearPlaylist() {
        if (playlistsRefused || session.sharedPlaylist.isEmpty()) return
        rememberForUndo()
        viewmodel.networkManager.sendAsync(WireMessage.playlistChange(emptyList()))
    }

    fun deleteItemFromPlaylist(i: Int) {
        if (playlistsRefused || i !in session.sharedPlaylist.indices) return
        rememberForUndo()
        session.sharedPlaylist.removeAt(i)
        viewmodel.networkManager.sendAsync(WireMessage.playlistChange(session.sharedPlaylist.toList()))

        // The highlight stays on the same file. Removing an item above it moves it up by one.
        // Removing the current item leaves the highlight on the item that took its place.
        val current = session.spIndex.intValue
        session.spIndex.intValue = when {
            session.sharedPlaylist.isEmpty() -> -1
            i < current -> current - 1
            else -> current.coerceAtMost(session.sharedPlaylist.lastIndex)
        }

        /* Tell the room too. Without this index, the other clients keep the highlight on whatever
         * slid into the old position. One writer sends the queue in order, so the shorter list
         * arrives first and the index after it still names this client's file. */
        val moved = session.spIndex.intValue
        if (moved != current && moved >= 0) {
            viewmodel.networkManager.sendAsync(WireMessage.playlistIndex(moved))
        }
    }

    /**
     * Moves one entry and tells the room. The selection stays on its entry, and no file loads
     * again: the loaded index follows the move here, and every other client finds its entry again
     * in the new list.
     */
    fun moveItem(from: Int, to: Int) {
        val list = session.sharedPlaylist
        if (playlistsRefused || from !in list.indices || to !in list.indices || from == to) return
        rememberForUndo()
        val reordered = list.toList().moved(from, to)
        val current = session.spIndex.intValue
        Snapshot.withMutableSnapshot {
            list.clear()
            list.addAll(reordered)
        }
        if (lastLoadedIndex >= 0) lastLoadedIndex = lastLoadedIndex.afterMove(from, to)
        val selected = current.afterMove(from, to)
        session.spIndex.intValue = selected
        viewmodel.networkManager.sendAsync(WireMessage.playlistChange(reordered))
        if (selected != current && selected >= 0) viewmodel.networkManager.sendAsync(WireMessage.playlistIndex(selected))
    }

    /** Selects a playlist item. Online, this sends the index to the server, and the server's echo
     * starts the synchronized load on every client, this one included. Solo mode has no server
     * round trip, so the selection applies directly. */
    fun sendPlaylistSelection(i: Int) {
        if (playlistsRefused) return
        lastIndexChangeAtMs = generateTimestampMillis()
        if (viewmodel.isSoloMode) {
            viewmodel.viewModelScope.launch { changePlaylistSelection(i) }
            return
        }
        viewmodel.networkManager.sendAsync(WireMessage.playlistIndex(i))
        // PC pauses the room at 0 with the index change, so everyone starts the new file from
        // the top together instead of landing mid-way at the old position.
        viewmodel.protocol.noteExpectedPlaybackState(paused = true)
        viewmodel.networkManager.sendAsync(
            WireMessage.State(StateData(playstate = PlaystateData(position = 0.0, paused = true)))
        )
    }

    /**
     * Applies a playlist index selection: it stores the index as the room's selection and loads
     * the file at that index, unless that file is already loaded.
     *
     * Called for local and remote selections (see
     * [app.protocol.event.RoomCallback.onPlaylistIndexChanged]). The check uses [lastLoadedSource]
     * and [lastLoadedIndex], never [app.protocol.Session.spIndex]. The receive path has already
     * set spIndex at this point, so an spIndex check would always return early and nothing would
     * play.
     */
    suspend fun changePlaylistSelection(index: Int) {
        if (index !in session.sharedPlaylist.indices) return /* In rare cases this runs on an empty or short list. */
        session.spIndex.intValue = index
        lastIndexChangeAtMs = generateTimestampMillis()
        val target = session.sharedPlaylist[index]
        // Check the name and the index. With the name alone, a playlist that holds the same file
        // name twice could not move between the two entries, because the name always matches.
        if (target == lastLoadedSource && index == lastLoadedIndex) return
        lastLoadedIndex = index
        retrieveFile(target)
    }

    /**
     * Resolves a shared playlist entry to real media and loads it into the player.
     *
     * A remote URL must pass the trusted-domains check before it goes straight to the player. An
     * untrusted URL raises the [pendingUntrusted] prompt instead. A local entry (a file name) is
     * resolved through [MediaAccessRegistry], which checks the direct file bookmark first and
     * indexes the remembered media directories again if needed. Only when nothing resolves does
     * the user see a "not found" or "no directories" hint.
     */
    suspend fun retrieveFile(fileName: String) {
        if (isRemoteUrl(fileName)) {
            if (!isUrlTrusted(fileName)) {
                // A URL with no host still needs a name for the prompt, so the raw string stands in.
                val domain = urlHost(fileName) ?: fileName
                // Ask instead of refusing. The user decides, and the safe default stays: nothing
                // plays until the user says so.
                pendingUntrusted.value = UntrustedUrl(fileName, domain)
                val warning = Localization.strings.roomUntrustedDomainWarning(domain)
                viewmodel.dispatcher.broadcastMessage(message = { warning }, isChat = false, isError = true)
                return
            }
            lastLoadedSource = fileName
            viewmodel.player.injectVideoURL(fileName)
            return
        }

        // A peer's selection arrives on the main thread. Resolving may walk every remembered media
        // folder, which takes seconds of IO on a SAF tree.
        val resolved = withContext(ioDispatcher) { MediaAccessRegistry.resolvePlayableFile(fileName) }
        if (resolved != null) {
            lastLoadedSource = fileName
            viewmodel.player.injectVideoFile(resolved)
            return
        }

        // Nothing resolved. Show no warning when the requested file is already the one playing.
        if (viewmodel.media?.fileName == fileName) return

        val message: suspend () -> String = if (Preferences.MEDIA_DIRECTORIES.value().isEmpty()) {
            { Localization.strings.roomSharedPlaylistNoDirectories }
        } else {
            { Localization.strings.roomSharedPlaylistNotFound(appName) }
        }
        viewmodel.dispatchWarning(message)
        viewmodel.dispatcher.broadcastMessage(message = message, isChat = false)
    }

    /** Saves the playlist as a plain-text file (one entry per line) to the file the user chose. */
    fun savePlaylistLocally(destination: PlatformFile) {
        val snapshot = session.sharedPlaylist.toList()
        if (snapshot.isEmpty()) return
        viewmodel.viewModelScope.launch(ioDispatcher) {
            val saved = runCatching { destination.writeTextCompat(snapshot.joinToString("\n")) }.isSuccess
            if (saved) viewmodel.dispatchOSD { Localization.strings.roomSharedPlaylistExported }
            else viewmodel.dispatchWarning { Localization.strings.roomSharedPlaylistExportFailed }
        }
    }

    /**
     * Loads playlist entries from an exported plain-text file, one entry per line.
     * @param alsoShuffle Whether to shuffle the loaded entries before they are sent.
     */
    fun loadPlaylistLocally(source: PlatformFile, alsoShuffle: Boolean) {
        if (playlistsRefused) return
        viewmodel.viewModelScope.launch(ioDispatcher) {
            val content = runCatching { source.readString() }.getOrNull() ?: return@launch
            val lines = content.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if (lines.isEmpty()) return@launch
            if (alsoShuffle) lines.shuffle()
            if (rejectsOversizedPlaylist(lines)) return@launch
            // The local user is importing this playlist, so any URLs in it are trusted by consent.
            lines.filter { isRemoteUrl(it) }.forEach { rememberLocalUrl(it) }
            viewmodel.networkManager.sendAsync(WireMessage.playlistChange(lines))
        }
    }

    /****************************************************************************/

    private fun isRemoteUrl(s: String): Boolean =
        s.startsWith("http://", true) ||
            s.startsWith("https://", true)

    /**
     * Checks whether a remote URL may load on its own.
     *
     * Mirrors PC's `_isURITrustableAndTrusted` (client.py) with `onlySwitchToTrustedDomains` at
     * its safe default (on): an http(s) URL from a peer is trusted only when it matches a
     * trusted-domain entry. When *no* trusted domains are set, every such URL is refused. Allowing
     * all of them would let any peer switch the room to any URL without a question. URLs that the
     * local user added are exempt (see [locallyAddedUrls]), because adding one is explicit consent.
     */
    private fun isUrlTrusted(url: String): Boolean {
        // The local user added this URL. That is explicit consent, so it is always allowed.
        if (locallyAddedUrls.contains(url)) return true

        val trustedRaw = Preferences.TRUSTED_DOMAINS.value().trim()
        val trustedEntries = trustedRaw.split("\n", ",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        // Nothing set: do not trust any URL from a peer (the PC safe default).
        if (trustedEntries.isEmpty()) return false

        // A URL whose host cannot be read is not a URL anyone approved.
        val urlDomain = urlHost(url) ?: return false
        val path = urlPath(url)

        return trustedEntries.any { entry -> trustedEntryMatches(entry, urlDomain, path) }
    }

    companion object {
        /** How many playlist edits can be taken back. An undo, not a history. */
        private const val MAX_UNDO_STEPS = 10

        private const val MAX_LOCAL_URLS = 500

        /**
         * Matches one trusted-domain entry against one URL, with the exact rules of PC's
         * `_isURITrustableAndTrusted` (client.py):
         *  - An entry `host/path` splits on the first `/`. The path must be a prefix of the URL
         *    path.
         *  - The host matches itself and its `www.` form, not any other subdomain.
         *  - Wildcards work: each `*` matches exactly one label. For example, `*.example.com`
         *    trusts `cdn.example.com` but not `a.b.example.com`.
         */
        internal fun trustedEntryMatches(entry: String, urlDomain: String, urlPath: String): Boolean {
            val trustedDomain = entry.substringBefore('/')
            val trustedPath = entry.substringAfter('/', missingDelimiterValue = "")

            val hostMatches = when {
                urlDomain == trustedDomain || urlDomain == "www.$trustedDomain" -> true
                '*' in trustedDomain -> {
                    val pattern = trustedDomain
                        .split('*')
                        .joinToString("([^.]+)") { Regex.escape(it) }
                    Regex("^$pattern$", RegexOption.IGNORE_CASE).matches(urlDomain)
                }
                else -> false
            }
            if (!hostMatches) return false
            if (trustedPath.isNotEmpty() && !urlPath.startsWith("/$trustedPath")) return false
            return true
        }
    }
}
