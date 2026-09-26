package app.subtitles

import io.github.vinceglb.filekit.PlatformFile

/** The web build has no file to read by offset. */
actual suspend fun PlatformFile.readEnds(): FileEnds? = null
