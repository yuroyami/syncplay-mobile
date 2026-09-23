package app.room.sharedplaylist

import io.github.vinceglb.filekit.PlatformFile

/**
 * Returns an empty map: the web cannot scan a media folder.
 *
 * Walking a folder needs the File System Access API, which asks the user for a directory handle
 * and exists only in Chromium. A web client can still follow a shared playlist (the file list
 * that everyone in a room follows); it only cannot add files from a folder of its own.
 */
actual suspend fun PlatformFile.indexMediaTree(): Map<String, ByteArray> = emptyMap()
