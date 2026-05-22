package app.room.sharedplaylist

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.preferences.Preferences
import app.preferences.datastore
import app.preferences.datastoreStateFlow
import app.preferences.set
import app.preferences.value
import app.utils.loggy
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.fromBookmarkData
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Persistent, cross-platform access registry for local media used by the shared playlist.
 *
 * ## Why this exists
 * The shared-playlist protocol only ever transmits **filenames** — never paths or URLs (except
 * for remote `http(s)`/`ftp` URLs, which are stored verbatim). Each client is responsible for
 * locating a file *of that name* in its own storage and loading it. That mapping
 * (`filename` → an actually-openable file handle) is exactly what platform sandboxing makes
 * hard, and what this registry solves once for both platforms:
 *
 *  - **iOS**: a file/folder picked by the user is a *security-scoped* `NSURL`. The grant
 *    evaporates the moment the picker's scope is released — a bare path string is useless
 *    afterwards. The only way to regain access later (or after a process restart) is a
 *    **security-scoped bookmark**.
 *  - **Android**: SAF `content://` URIs from `ACTION_OPEN_DOCUMENT(_TREE)` are revocable and do
 *    not survive a process restart unless the app calls `takePersistableUriPermission`.
 *
 * FileKit's [bookmarkData]/[fromBookmarkData] abstract both: on iOS they create/resolve a
 * security-scoped bookmark; on Android `bookmarkData` takes the persistable permission and
 * returns the URI bytes. We persist those opaque bytes (Base64) in DataStore and resolve them
 * back to a ready-to-open [PlatformFile] at playback time. This is why a path string captured
 * at pick time must NEVER be the thing we persist — only the bookmark is durable.
 *
 * ## Two namespaces
 *  - **Directories** (`dirId` → bookmark): every media directory the user added. Keyed by the
 *    directory's [PlatformFile.path] so it lines up with [Preferences.MEDIA_DIRECTORIES].
 *  - **Files** (`filename` → bookmark): every individual media file we have a durable handle
 *    for — either because the user picked it directly, or because we discovered it while
 *    indexing a remembered directory. This is the fast path for [resolvePlayableFile].
 */
object MediaAccessRegistry {

    private val DIR_BOOKMARKS = stringPreferencesKey("pref_media_dir_bookmarks_v1")
    private val FILE_BOOKMARKS = stringPreferencesKey("pref_media_file_bookmarks_v1")

    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    /* ----------------------------- Remembering ----------------------------- */

    /**
     * Persists durable access to [dir] and registers it under [Preferences.MEDIA_DIRECTORIES].
     * Safe to call repeatedly with the same directory (idempotent). Failures are swallowed and
     * logged — a directory we couldn't bookmark simply won't be resolvable later, which the
     * resolver degrades on gracefully.
     */
    suspend fun rememberDirectory(dir: PlatformFile) {
        val id = dir.path
        runCatching { dir.bookmarkData().bytes }
            .onSuccess { putBookmark(DIR_BOOKMARKS, id, it) }
            .onFailure { loggy("MediaAccessRegistry: failed to bookmark directory $id — ${it.message}") }

        val paths = Preferences.MEDIA_DIRECTORIES.value().toMutableSet()
        if (paths.add(id)) Preferences.MEDIA_DIRECTORIES.set(paths)
    }

    /**
     * Persists durable access to picked [files], each keyed by its filename so the playlist
     * (which stores only names) can resolve it later. All bookmarks are written in a single
     * store update.
     */
    suspend fun rememberFiles(files: List<PlatformFile>) {
        val bookmarks = LinkedHashMap<String, ByteArray>()
        for (file in files) {
            val name = file.name
            if (name.isBlank()) continue
            runCatching { file.bookmarkData().bytes }
                .onSuccess { bookmarks[name] = it }
                .onFailure { loggy("MediaAccessRegistry: failed to bookmark file $name — ${it.message}") }
        }
        rememberFileBookmarks(bookmarks)
    }

    /** Bulk-persists `filename → bookmark-bytes` discovered by [indexMediaTree]. */
    suspend fun rememberFileBookmarks(bookmarks: Map<String, ByteArray>) {
        if (bookmarks.isEmpty()) return
        mergeBookmarks(FILE_BOOKMARKS, bookmarks)
    }

    /* ------------------------------ Resolving ------------------------------ */

    /**
     * Resolves [filename] to a [PlatformFile] that is ready to be opened by the player
     * (security scope re-established on iOS, persisted permission on Android), or null if it
     * cannot be found in any remembered location.
     *
     * Resolution order:
     *  1. **Direct file bookmark** — instant; covers files the user picked or any file already
     *     discovered by a previous index of a media directory.
     *  2. **Self-healing directory re-index** — if the name isn't known yet (e.g. a peer added a
     *     file that lives in one of our media directories but landed there after we last
     *     indexed), we re-walk each remembered directory, persist everything we find, and retry
     *     the direct lookup. Stops as soon as the file turns up.
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
    private fun directFile(filename: String): PlatformFile? {
        val bytes = readBookmark(FILE_BOOKMARKS, filename) ?: return null
        val file = runCatching { PlatformFile.fromBookmarkData(bytes) }.getOrNull() ?: return null
        // exists() can throw on a stale/revoked handle; treat that as "still try it" only when
        // the check itself failed to run, but treat a definitive `false` as not-found.
        val present = runCatching { file.exists() }.getOrElse { true }
        return if (present) file else null
    }

    /** Resolves a media-directory handle from its bookmark, falling back to the raw id. */
    private fun resolveDirectory(dirId: String): PlatformFile? {
        readBookmark(DIR_BOOKMARKS, dirId)?.let { bytes ->
            runCatching { PlatformFile.fromBookmarkData(bytes) }.getOrNull()?.let { return it }
        }
        // Legacy entries (added before bookmarking existed) or same-session Android URIs: best
        // effort. On iOS this won't be accessible, and indexMediaTree will simply yield nothing.
        return runCatching { PlatformFile(dirId) }.getOrNull()
    }

    /* ----------------------------- Maintenance ----------------------------- */

    /** Drops a directory's bookmark (called when the user removes it from media directories). */
    suspend fun forgetDirectory(dirId: String) = removeBookmark(DIR_BOOKMARKS, dirId)

    /** Clears every persisted handle. Mirrors "clear all media directories". */
    suspend fun clear() {
        datastore.edit { prefs ->
            prefs.remove(DIR_BOOKMARKS)
            prefs.remove(FILE_BOOKMARKS)
        }
    }

    /* ------------------------------ Storage -------------------------------- */

    @OptIn(ExperimentalEncodingApi::class)
    private fun readBookmark(key: androidx.datastore.preferences.core.Preferences.Key<String>, id: String): ByteArray? {
        val raw = datastoreStateFlow.value[key] ?: return null
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
