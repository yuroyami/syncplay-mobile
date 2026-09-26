package app.room.sharedplaylist

import io.github.vinceglb.filekit.PlatformFile

/**
 * Walks the media directory at this [PlatformFile], with all its subdirectories, and returns a
 * map from file name to lasting bookmark bytes for every playable media file
 * (see [app.utils.isPlayableMediaFilename]).
 *
 * [app.utils.platformFileFromBookmark] can later resolve the returned bytes on the same platform:
 *  - **iOS**: a security-scoped bookmark for each file, created while the directory's scope is
 *    held (so the files inside are reachable). Each bookmark resolves on its own afterwards.
 *  - **Android**: the child document URI bytes. The persistable permission on the parent tree,
 *    taken when the directory was remembered, allows the reads.
 *
 * Implementations open and close every security scope and permission themselves, and must not
 * leave a scope open on return. Returns null when the app cannot open the directory: it is gone,
 * or the app lost access to it. An empty map means the directory holds no media files.
 */
expect suspend fun PlatformFile.indexMediaTree(): Map<String, ByteArray>?
