package app.room.sharedplaylist

import io.github.vinceglb.filekit.PlatformFile

/**
 * Empty, for now.
 *
 * Walking a folder needs the File System Access API, which asks the user for a directory handle
 * and is Chromium-only. Until that is wired, a web client can still receive and play a shared
 * playlist; it just contributes nothing by scanning a folder of its own.
 */
actual suspend fun PlatformFile.indexMediaTree(): Map<String, ByteArray> = emptyMap()
