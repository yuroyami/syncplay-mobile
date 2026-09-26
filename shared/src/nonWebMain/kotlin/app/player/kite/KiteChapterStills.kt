package app.player.kite

import androidx.compose.ui.graphics.ImageBitmap
import app.player.ChapterStills
import app.player.models.Chapter
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import app.player.stillPositionMs
import app.utils.availablePlatformPlayerEngines
import app.utils.ioDispatcher
import app.utils.loggy
import io.github.yuroyami.kiteplayer.KitePlayerPlatform
import io.github.yuroyami.kiteplayer.MediaItem
import io.github.yuroyami.kiteplayer.ffmpeg.Thumbnails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.time.Duration.Companion.milliseconds

/**
 * Chapter stills through KitePlayer's FFmpeg. It opens the file on its own, next to whichever
 * engine plays it, with the same platform resolver that the KitePlayer engine uses. One still is
 * taken at a time, and the stills of one file are kept until another file comes.
 */
internal object KiteChapterStills : ChapterStills {
    private const val MAX_WIDTH = 320
    private const val MAX_KEPT = 64

    private val lock = Mutex()
    private var keptFor: MediaFileLocation? = null
    private val kept = HashMap<Long, ImageBitmap?>()

    private val resolver: KiteMediaResolver? by lazy {
        availablePlatformPlayerEngines.filterIsInstance<KiteEngine>().firstOrNull()?.mediaResolver
    }

    override suspend fun still(media: MediaFile, chapter: Chapter, chapterEndMs: Long?): ImageBitmap? {
        val location = media.location ?: return null
        if (!KitePlayerPlatform.isAvailable) return null
        val positionMs = stillPositionMs(chapter.timeOffsetMillis, chapterEndMs)
        return lock.withLock {
            if (keptFor !== location || kept.size >= MAX_KEPT) {
                keptFor = location
                kept.clear()
            }
            if (positionMs in kept) return@withLock kept[positionMs]
            withContext(ioDispatcher) { take(location, positionMs) }.also { kept[positionMs] = it }
        }
    }

    private suspend fun take(location: MediaFileLocation, positionMs: Long): ImageBitmap? {
        val path = when (location) {
            is MediaFileLocation.Local -> resolver?.resolve(location.file)
            is MediaFileLocation.Remote -> kiteMediaPathOf(location.url)
        } ?: return null
        return try {
            Thumbnails.at(MediaItem(uri = path.uri, openOptions = path.openOptions), listOf(positionMs.milliseconds), MAX_WIDTH)
                .firstOrNull()?.bytes?.decodeToImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A page link, a file without video or a failed decode: the row keeps its name only.
            loggy("Chapter still at $positionMs ms: none, ${e::class.simpleName}: ${e.message}")
            null
        } finally {
            path.release()
        }
    }
}
