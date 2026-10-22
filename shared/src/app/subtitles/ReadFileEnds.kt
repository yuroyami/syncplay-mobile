package app.subtitles

import io.github.vinceglb.filekit.PlatformFile

/**
 * The size and the first and last [HASH_CHUNK_BYTES] of this file, read without reading the rest,
 * or null when the platform cannot open it that way.
 */
expect suspend fun PlatformFile.readEnds(): FileEnds?
