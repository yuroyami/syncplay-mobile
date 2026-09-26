package app.room.sharedplaylist

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.preferences.Preferences
import app.preferences.datastore
import app.utils.durableBookmark
import app.utils.platformFileAt
import app.utils.platformFileFromBookmark
import app.utils.stillExists
import kotlinx.coroutines.flow.first
import app.preferences.set
import app.preferences.value
import app.utils.LogRedactor
import app.utils.loggy
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A lasting, cross-platform registry of access to the local media of the shared playlist (the
 * file list that everyone in a room follows). A room is the group of people watching together.
 *
 * ## Why this exists
 * The shared playlist protocol sends only **file names**, never paths. The exception is a remote
 * `http(s)` or `ftp` URL, which is stored as it is. Each client must find a file *of that name* in
 * its own storage and load it. Platform sandboxing makes that mapping (from a file name to a file
 * handle that opens) hard, and this registry solves it once for both platforms:
 *
 *  - **iOS**: a file or folder that the user picks is a *security-scoped* `NSURL`. The grant ends
 *    as soon as the picker's scope is released, and a bare path string is useless after that. A
 *    **security-scoped bookmark** is the only way to get access back later, also after a process
 *    restart.
 *  - **Android**: SAF `content://` URIs from `ACTION_OPEN_DOCUMENT(_TREE)` can be revoked, and
 *    they do not survive a process restart unless the app calls `takePersistableUriPermission`.
 *
 * [durableBookmark] and [platformFileFromBookmark] cover both. On iOS they create and resolve a
 * security-scoped bookmark. On Android, `bookmarkData` takes the persistable permission and
 * returns the URI bytes. The registry saves those opaque bytes (Base64) in DataStore and resolves
 * them back to a [PlatformFile] that is ready to open at playback time. Never save a path string
 * captured at pick time instead: only the bookmark lasts.
 *
 * ## Two namespaces
 *  - **Directories** (`dirId` to bookmark): every media directory that the user added. The key is
 *    the directory's [PlatformFile.path], so it lines up with [Preferences.MEDIA_DIRECTORIES].
 *  - **Files** (`filename` to bookmark): every media file with a lasting handle, either picked by
 *    the user or found while indexing a remembered directory. This is the fast path for
 *    [resolvePlayableFile].
 */
object MediaAccessRegistry {

    private val DIR_BOOKMARKS = stringPreferencesKey("pref_media_dir_bookmarks_v1")
    private val FILE_BOOKMARKS = stringPreferencesKey("pref_media_file_bookmarks_v1")

    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    /* ----------------------------- Remembering ----------------------------- */

    /**
     * Saves lasting access to [dir] and adds it to [Preferences.MEDIA_DIRECTORIES]. Safe to call
     * again with the same directory. A failure is logged, not thrown. A directory without a
     * bookmark cannot be resolved from a bookmark later, so the resolver falls back to its path.
     */
    suspend fun rememberDirectory(dir: PlatformFile) {
        val id = dir.path
        LogRedactor.register(LogRedactor.Kind.File, id)
        runCatching { dir.durableBookmark() }
            .onSuccess { putBookmark(DIR_BOOKMARKS, id, it) }
            .onFailure { loggy("MediaAccessRegistry: failed to bookmark directory $id — ${it.message}") }

        val paths = Preferences.MEDIA_DIRECTORIES.value().toMutableSet()
        if (paths.add(id)) Preferences.MEDIA_DIRECTORIES.set(paths)
    }

    /**
     * Saves lasting access to the picked [files], each under its file name, so the playlist
     * (which stores only names) can resolve it later. All bookmarks go into the store in one
     * update.
     */
    suspend fun rememberFiles(files: List<PlatformFile>) {
        val bookmarks = LinkedHashMap<String, ByteArray>()
        for (file in files) {
            val name = file.name
            if (name.isBlank()) continue
            LogRedactor.register(LogRedactor.Kind.File, name)
            runCatching { file.durableBookmark() }
                .onSuccess { bookmarks[name] = it }
                .onFailure { loggy("MediaAccessRegistry: failed to bookmark file $name — ${it.message}") }
        }
        rememberFileBookmarks(bookmarks)
    }

    /**
     * Saves many file bookmarks (file name to bookmark bytes) in one update, such as the result
     * of [indexMediaTree].
     */
    suspend fun rememberFileBookmarks(bookmarks: Map<String, ByteArray>) {
        if (bookmarks.isEmpty()) return
        mergeBookmarks(FILE_BOOKMARKS, bookmarks)
    }

    /* ------------------------------ Resolving ------------------------------ */

    /**
     * Resolves [filename] to a [PlatformFile] that the player can open (security scope restored
     * on iOS, saved permission on Android). Returns null when no remembered location has it.
     *
     * Resolution order:
     *  1. **Direct file bookmark**: instant. It covers files that the user picked and files that
     *     an earlier index of a media directory found.
     *  2. **Directory re-index**: when the name is not known yet, walk each remembered directory
     *     again, save everything found and retry the direct lookup. This finds a file that a peer
     *     added and that arrived in a media directory after the last index. The walk stops as
     *     soon as the file turns up.
     */
    suspend fun resolvePlayableFile(filename: String): PlatformFile? {
        directFile(filename)?.let { return it }

        for (dirId in Preferences.MEDIA_DIRECTORIES.value()) {
            val dir = resolveDirectory(dirId) ?: continue
            val index = runCatching { dir.indexMediaTree() }
                .onFailure { loggy("MediaAccessRegistry: indexing $dirId failed — ${it.message}") }
                .getOrDefault(emptyMap())
            if (index.isNotEmpty()) rememberFileBookmarks(index)
            directFile(filename)?.let { return it }
        }
        return null
    }

    /** Resolves the stored bookmark for [filename] and confirms the target still exists. */
    private suspend fun directFile(filename: String): PlatformFile? {
        val bytes = readBookmark(FILE_BOOKMARKS, filename) ?: return null
        val file = runCatching { platformFileFromBookmark(bytes) }.getOrNull() ?: return null
        // stillExists() can throw on a stale or revoked handle. When the check itself fails,
        // still try the file. A definite `false` means not found.
        val present = runCatching { file.stillExists() }.getOrElse { true }
        return if (present) file else null
    }

    /** Resolves a media-directory handle from its bookmark, falling back to the raw id. */
    private suspend fun resolveDirectory(dirId: String): PlatformFile? {
        readBookmark(DIR_BOOKMARKS, dirId)?.let { bytes ->
            runCatching { platformFileFromBookmark(bytes) }.getOrNull()?.let { return it }
        }
        // Best effort for an entry without a bookmark (saved by an older version, or a failed
        // bookmark) and for an Android URI from this session. On iOS such a path is not
        // accessible, and indexMediaTree returns nothing.
        return runCatching { platformFileAt(dirId) }.getOrNull()
    }

    /* ----------------------------- Maintenance ----------------------------- */

    /** Drops a directory's bookmark (called when the user removes it from media directories). */
    suspend fun forgetDirectory(dirId: String) = removeBookmark(DIR_BOOKMARKS, dirId)

    /** Clears every saved handle, for the "clear all media directories" action. */
    suspend fun clear() {
        datastore.edit { prefs ->
            prefs.remove(DIR_BOOKMARKS)
            prefs.remove(FILE_BOOKMARKS)
        }
    }

    /* ------------------------------ Storage -------------------------------- */

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun readBookmark(key: androidx.datastore.preferences.core.Preferences.Key<String>, id: String): ByteArray? {
        // The hot snapshot lags behind its own writes by one collection, so a file remembered a
        // moment ago would read back as missing. Read the store itself: a bookmark lookup is not
        // a hot path.
        val raw = datastore.data.first()[key] ?: return null
        val map = decode(raw)
        val b64 = map[id] ?: return null
        return runCatching { Base64.decode(b64) }.getOrNull()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun putBookmark(key: androidx.datastore.preferences.core.Preferences.Key<String>, id: String, bytes: ByteArray) {
        datastore.edit { prefs ->
            val map = decode(prefs[key]).toMutableMap()
            map[id] = Base64.encode(bytes)
            prefs[key] = json.encodeToString(mapSerializer, map)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun mergeBookmarks(key: androidx.datastore.preferences.core.Preferences.Key<String>, bookmarks: Map<String, ByteArray>) {
        datastore.edit { prefs ->
            val map = decode(prefs[key]).toMutableMap()
            for ((id, bytes) in bookmarks) map[id] = Base64.encode(bytes)
            prefs[key] = json.encodeToString(mapSerializer, map)
        }
    }

    private suspend fun removeBookmark(key: androidx.datastore.preferences.core.Preferences.Key<String>, id: String) {
        datastore.edit { prefs ->
            val map = decode(prefs[key]).toMutableMap()
            if (map.remove(id) != null) prefs[key] = json.encodeToString(mapSerializer, map)
        }
    }

    private fun decode(raw: String?): Map<String, String> =
        if (raw.isNullOrBlank()) emptyMap()
        else runCatching { json.decodeFromString(mapSerializer, raw) }.getOrDefault(emptyMap())
}
