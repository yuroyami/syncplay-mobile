package app.room.sharedplaylist

import app.design.DesignHarness
import app.preferences.Preferences
import app.preferences.set
import app.room.sharedplaylist.MediaAccessRegistry.FolderState
import io.github.vinceglb.filekit.PlatformFile
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/** The media folders list and the playlist marks both ask the registry what this device can open. */
class MediaAccessRegistryTest {

    private val root = createTempDirectory("synkplay-media").toFile()

    private fun reset() = runBlocking {
        DesignHarness.initDatastore()
        MediaAccessRegistry.clear()
        Preferences.MEDIA_DIRECTORIES.set(emptySet())
    }

    @BeforeTest
    fun start() = reset()

    @AfterTest
    fun clean() {
        reset()
        root.deleteRecursively()
    }

    private fun folder(name: String, vararg files: String): File {
        val dir = File(root, name)
        for (file in files) File(dir, file).apply { parentFile.mkdirs() }.writeText("x")
        return dir
    }

    @Test
    fun aFolderCountsItsMediaFilesAndSaysWhenItIsGone() = runBlocking {
        val shows = folder("Shows", "Episode 01.mkv", "Season 2/Episode 02.mp4", "notes.txt")
        MediaAccessRegistry.rememberDirectory(PlatformFile(shows.path))
        assertEquals(FolderState.Open(2), MediaAccessRegistry.folderState(shows.path))

        // A macOS bookmark follows a renamed folder, so only a deleted one is lost on every platform.
        shows.deleteRecursively()
        assertEquals(FolderState.Lost, MediaAccessRegistry.folderState(shows.path))
    }

    @Test
    fun onlyAnEntryWithNoFileOnThisDeviceIsMissing() = runBlocking {
        val movies = folder("Movies", "Movie.mkv")
        MediaAccessRegistry.rememberDirectory(PlatformFile(movies.path))
        val missing = MediaAccessRegistry.missingFiles(listOf("Movie.mkv", "Other.mkv", "https://example.com/stream.m3u8"))
        assertEquals(setOf("Other.mkv"), missing)
    }
}
