package app.room.sharedplaylist

import io.github.vinceglb.filekit.PlatformFile

/**
 * Recursively walks the media directory rooted at this [PlatformFile] and returns a map of
 * `filename → durable bookmark bytes` for every playable media file found
 * (see [app.utils.isPlayableMediaFilename]).
 *
 * The returned bytes are whatever [PlatformFile.Companion.fromBookmarkData] can later resolve on
 * the same platform:
 *  - **iOS**: a security-scoped bookmark for each file, created while the directory's scope is
 *    held (so descendants are reachable). Resolvable independently afterwards.
 *  - **Android**: the child document URI bytes; reads are authorized by the persistable
 *    permission already taken on the parent tree when the directory was remembered.
 *
 * Implementations own all security-scope / permission bracketing internally and must not leave
 * any scope open on return. Returns an empty map if the directory can't be accessed.
 */
expect suspend fun PlatformFile.indexMediaTree(): Map<String, ByteArray>
