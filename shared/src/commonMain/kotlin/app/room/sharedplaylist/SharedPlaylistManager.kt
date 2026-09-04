package app.room.sharedplaylist

import androidx.lifecycle.viewModelScope
import app.AbstractManager
import app.preferences.Preferences
import app.preferences.value
import app.protocol.WireMessage
import app.protocol.wire.PlaystateData
import app.protocol.wire.StateData
import app.room.RoomViewmodel
import app.utils.PLAYLIST_MAX_CHARACTERS
import app.utils.PLAYLIST_MAX_ITEMS
import app.utils.appName
import app.utils.playlistIsValid
import app.utils.generateTimestampMillis
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.writeString
import app.preferences.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_shared_playlist_limit
import syncplaymobile.shared.generated.resources.room_shared_playlist_no_directories
import syncplaymobile.shared.generated.resources.room_shared_playlist_export_failed
import syncplaymobile.shared.generated.resources.room_shared_playlist_exported
import syncplaymobile.shared.generated.resources.room_shared_playlist_not_found
import syncplaymobile.shared.generated.resources.room_untrusted_domain_warning

class SharedPlaylistManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    private val session get() = viewmodel.session

    /**
     * The playlist entry (filename or URL) most recently started loading into the player.
     *
     * [changePlaylistSelection] guards on this, NOT [Session.spIndex], to decide whether an
     * incoming index change needs a (re)load. The receive path sets `spIndex` to the new index
     * before `changePlaylistSelection` runs, so an `index != spIndex` guard would always be false
     * and the file would never load. Tracking the loaded source works for both local files and
     * remote URLs and skips the redundant reload when our own index echo returns.
     */
    private var lastLoadedSource: String? = null

    /**
     * When the playlist index last moved, from any source. Auto-advance refuses to fire again
     * within the near-end window: at the end of a file every client in the room reaches EOF at
     * about the same moment, and without this each one sends its own "next item", walking the room
     * several entries forward at once. Mirrors PC's notJustChangedPlaylist.
     */
    private var lastIndexChangeAtMs: Long = 0L

    /** True while the index has just moved, so an end-of-file advance must stand down. */
    fun justChangedIndex(withinMs: Long): Boolean =
        lastIndexChangeAtMs != 0L && generateTimestampMillis() - lastIndexChangeAtMs < withinMs

    /** The playlist entry currently selected, or null when the playlist has no selection. */
    val selectedEntry: String?
        get() = session.sharedPlaylist.getOrNull(session.spIndex.intValue)

    /**
     * True when this client is playing the entry the playlist currently points at. Compared on the
     * playlist's own string, which is its unit of identity for both filenames and URLs.
     */
    val isPlayingSelectedEntry: Boolean
        get() = lastLoadedSource != null && lastLoadedSource == selectedEntry

    /** The index [lastLoadedSource] was loaded from, so a playlist with the same name twice can move between them. */
    private var lastLoadedIndex: Int = -1

    /**
     * URLs the *local user* explicitly added (typed in, or imported from a local playlist file).
     *
     * These are exempt from the trusted-domain gate in [isUrlTrusted]: adding a URL yourself is an
     * explicit act of consent, so it should always be allowed to load even with no trusted domains
     * configured. The gate exists to block auto-switching onto *peer-pushed* untrusted URLs, which
     * never pass through here.
     */
    private val locallyAddedUrls = linkedSetOf<String>()

    /**
     * A peer-pushed URL from a host nobody has trusted, waiting on an answer.
     *
     * Blocking it outright was a dead end: the room said "untrusted" and the file simply never
     * played, with the only way forward buried in a settings field the user had to guess the
     * syntax of. Asking is the same safety with a way through it.
     */
    data class UntrustedUrl(val url: String, val domain: String)

    private val _pendingUntrusted = MutableStateFlow<UntrustedUrl?>(null)
    val pendingUntrusted = _pendingUntrusted.asStateFlow()

    /** Answers the prompt. [always] adds the host to the trusted list for good. */
    fun allowPendingUrl(always: Boolean) {
        val pending = _pendingUntrusted.value ?: return
        _pendingUntrusted.value = null
        if (always) {
            val existing = Preferences.TRUSTED_DOMAINS.value().trim()
            val updated = if (existing.isEmpty()) pending.domain else existing + "\n" + pending.domain
            onIOThread { Preferences.TRUSTED_DOMAINS.set(updated) }
        }
        // Consent given here is consent either way, so this one plays even without the list.
        rememberLocalUrl(pending.url)
        onIOThread { retrieveFile(pending.url) }
    }

    /** Declines. The file stays unplayed and nothing is remembered. */
    fun dismissPendingUrl() {
        _pendingUntrusted.value = null
    }

    /**
     * The playlists this room had before the last few edits.
     *
     * A shuffle, a clear or a wrong delete is one tap and reaches everyone, and until now there
     * was no way back except retyping the list. The stack is small on purpose: this is an undo,
     * not a history.
     */
    private val undoStack = ArrayDeque<List<String>>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    /** Called before any edit that replaces the whole list. */
    private fun rememberForUndo() {
        undoStack.addLast(session.sharedPlaylist.toList())
        while (undoStack.size > MAX_UNDO_STEPS) undoStack.removeFirst()
        _canUndo.value = true
    }

    /** Puts the previous playlist back and tells the room. */
    fun undoLastPlaylistChange() {
        val previous = undoStack.removeLastOrNull() ?: return
        _canUndo.value = undoStack.isNotEmpty()
        viewmodel.networkManager.sendAsync(WireMessage.playlistChange(previous))
    }

    /** Remembers a consented URL, forgetting the oldest past the cap so a long session stays bounded. */
    private fun rememberLocalUrl(url: String) {
        locallyAddedUrls.remove(url)
        locallyAddedUrls.add(url)
        while (locallyAddedUrls.size > MAX_LOCAL_URLS) locallyAddedUrls.remove(locallyAddedUrls.first())
    }

    /** Shuffles the current playlist and sends it to the server.
     * @param mode False to shuffle all playlist, True to shuffle only the remaining non-played items in queue.*/
    suspend fun shuffle(mode: Boolean) {
        rememberForUndo()
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

            /* Only the tail past the current index moved, so the current file stays put — announce
             * the new list and we're done. */
            viewmodel.networkManager.send(WireMessage.playlistChange(session.sharedPlaylist.toList()))
        } else {
            /* Shuffling everything is easy as Kotlin gives us the 'shuffle()' method */
            session.sharedPlaylist.shuffle()

            /* A full shuffle moves every entry, so the old index now points at a different file.
             * Match PC's shuffleEntirePlaylist: reset to index 0 and broadcast BOTH the new list and
             * the new index, so every peer re-selects index 0 instead of staying on its stale index
             * (which would land each client on a different file). */
            session.spIndex.intValue = 0
            viewmodel.networkManager.send(WireMessage.playlistChange(session.sharedPlaylist.toList()))
            viewmodel.networkManager.send(WireMessage.playlistIndex(0))
            retrieveFile(session.sharedPlaylist[0])
        }
    }

    /**
     * Rejects playlists exceeding the protocol's caps (python's `playlistIsValid`,
     * 250 items / 10000 characters) BEFORE broadcasting. The official server refuses
     * an oversized `playlistChange` and re-sends the old playlist — without this gate
     * the user's additions just silently vanish a round-trip later.
     * @return true when the list is over the limits (caller must abort the broadcast).
     */
    private fun rejectsOversizedPlaylist(files: List<String>): Boolean {
        if (playlistIsValid(files)) return false
        val warning: suspend () -> String =
            { getString(Res.string.room_shared_playlist_limit, PLAYLIST_MAX_ITEMS, PLAYLIST_MAX_CHARACTERS) }
        viewmodel.dispatchOSD(getter = warning)
        viewmodel.dispatcher.broadcastMessage(message = warning, isChat = false, isError = true)
        return true
    }

    /** Adds URLs from the url adding popup, de-duplicating against the existing list and within
     *  the incoming batch itself. */
    fun addURLs(urls: List<String>) {
        val merged = session.sharedPlaylist.toMutableList()
        for (raw in urls) {
            val url = raw.trim()
            if (url.isNotEmpty() && !merged.contains(url)) {
                merged.add(url)
                // The local user typed this URL in, so it's trusted by consent regardless of the
                // trusted-domains setting (see [locallyAddedUrls] / [isUrlTrusted]).
                if (isRemoteUrl(url)) rememberLocalUrl(url)
            }
        }
        if (merged.size == session.sharedPlaylist.size) return
        if (rejectsOversizedPlaylist(merged)) return
        rememberForUndo()
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
        if (rejectsOversizedPlaylist(session.sharedPlaylist + toAdd.map { it.name })) return

        MediaAccessRegistry.rememberFiles(toAdd)
        for (file in toAdd) session.sharedPlaylist.add(file.name)

        viewmodel.networkManager.send(WireMessage.playlistChange(session.sharedPlaylist.toList()))

        if (playlistWasEmpty) {
            // Load the first added file locally right away (works in solo mode too) from the live,
            // still-scoped handle, and mark it as the loaded source so our own index echo doesn't
            // reload it.
            val first = toAdd.first()
            lastLoadedSource = first.name
            lastLoadedIndex = 0
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
        if (rejectsOversizedPlaylist(merged)) return

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
        rememberForUndo()
        viewmodel.networkManager.sendAsync(WireMessage.playlistChange(emptyList()))
    }

    /** This will delete an item from playlist at a given index 'i' */
    fun deleteItemFromPlaylist(i: Int) {
        if (i !in session.sharedPlaylist.indices) return
        rememberForUndo()
        session.sharedPlaylist.removeAt(i)
        viewmodel.networkManager.sendAsync(WireMessage.playlistChange(session.sharedPlaylist.toList()))

        // The highlighted entry keeps pointing at the same file: an item removed above it moves
        // it up one, and removing the current one leaves the highlight on what took its place.
        val current = session.spIndex.intValue
        session.spIndex.intValue = when {
            session.sharedPlaylist.isEmpty() -> -1
            i < current -> current - 1
            else -> current.coerceAtMost(session.sharedPlaylist.lastIndex)
        }
    }

    /** Selects a playlist item. Online, this announces the index to the server, whose echo
     * drives the (synchronized) load on every client including us. In solo mode there is no
     * server round-trip, so we apply the selection directly. */
    fun sendPlaylistSelection(i: Int) {
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
     * Applies a playlist index selection: records it as the room position and loads the file at
     * that index unless it is already the loaded one.
     *
     * Called for both local and remote selections (see [app.protocol.event.RoomCallback.onPlaylistIndexChanged]).
     * The guard is [lastLoadedSource], NOT [Session.spIndex]: the receive path already advanced
     * spIndex by the time we get here, so an spIndex-equality guard would always short-circuit
     * and nothing would ever play.
     */
    suspend fun changePlaylistSelection(index: Int) {
        if (index !in session.sharedPlaylist.indices) return /* In rare cases when this was called on an empty/short list */
        session.spIndex.intValue = index
        lastIndexChangeAtMs = generateTimestampMillis()
        val target = session.sharedPlaylist[index]
        // Name AND index: a playlist holding the same filename twice could not move between the
        // two entries, because the name alone always matched what was already loaded.
        if (target == lastLoadedSource && index == lastLoadedIndex) return
        lastLoadedIndex = index
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
                // Ask instead of refusing. The answer is the user's, and the safe default
                // (nothing plays until they say so) is unchanged.
                _pendingUntrusted.value = UntrustedUrl(fileName, domain)
                val warning = getString(Res.string.room_untrusted_domain_warning, domain)
                viewmodel.dispatcher.broadcastMessage(message = { warning }, isChat = false, isError = true)
                return
            }
            lastLoadedSource = fileName
            viewmodel.player.injectVideoURL(fileName)
            return
        }

        // A peer's selection arrives on the main thread; resolving may walk every remembered
        // media folder, which on a SAF tree is seconds of IO.
        val resolved = withContext(Dispatchers.IO) { MediaAccessRegistry.resolvePlayableFile(fileName) }
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
            val saved = runCatching { destination.writeString(snapshot.joinToString("\n")) }.isSuccess
            viewmodel.dispatchOSD {
                getString(if (saved) Res.string.room_shared_playlist_exported else Res.string.room_shared_playlist_export_failed)
            }
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
     * Checks whether a remote URL is allowed to auto-load.
     *
     * Mirrors PC's `_isURITrustableAndTrusted` (client.py:565) with `onlySwitchToTrustedDomains`
     * at its safe default (on): a peer-pushed http(s) URL is only trusted if it matches a
     * configured trusted-domain entry. Crucially, when *no* trusted domains are configured we
     * do NOT blanket-allow every URL — that would let any peer silently auto-switch the room
     * onto an arbitrary URL. URLs the local user added themselves are exempt (see
     * [locallyAddedUrls]), since adding one is explicit consent.
     */
    private fun isUrlTrusted(url: String): Boolean {
        // The local user added this URL — explicit consent, always allowed.
        if (locallyAddedUrls.contains(url)) return true

        val trustedRaw = Preferences.TRUSTED_DOMAINS.value().trim()
        val trustedEntries = trustedRaw.split("\n", ",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        // Nothing configured: do not auto-trust arbitrary peer-pushed URLs (PC safe default).
        if (trustedEntries.isEmpty()) return false

        val urlDomain = extractDomain(url)
        val urlPath = extractPath(url)

        return trustedEntries.any { entry -> trustedEntryMatches(entry, urlDomain, urlPath) }
    }

    /** Extracts the path component, e.g. "https://h.com/videos/x.mp4" → "/videos/x.mp4". */
    private fun extractPath(url: String): String {
        val afterScheme = url.substringAfter("://")
        val slash = afterScheme.indexOf('/')
        return if (slash >= 0) afterScheme.substring(slash) else ""
    }

    companion object {
        /** How many playlist edits can be taken back. An undo, not a history. */
        private const val MAX_UNDO_STEPS = 10

        private const val MAX_LOCAL_URLS = 500

        /**
         * One trusted-domain entry against one URL, with PC's exact matching rules
         * (client.py:565-602):
         *  - entry `host/path` splits on the first `/`; the path is a required URL-path prefix
         *  - the host matches itself and its `www.` variant, NOT arbitrary subdomains
         *  - explicit wildcards are supported: each `*` matches exactly one label,
         *    e.g. `*.example.com` trusts `cdn.example.com` but not `a.b.example.com`
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
