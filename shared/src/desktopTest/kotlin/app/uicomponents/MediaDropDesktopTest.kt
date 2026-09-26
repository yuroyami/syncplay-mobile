package app.uicomponents

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** How a desktop drop turns into items: files come as `file:` URIs, and links come as text. */
@OptIn(ExperimentalComposeUiApi::class)
class MediaDropDesktopTest {

    private val dir: File = Files.createTempDirectory("synkplay-drop").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private class FileList(private val uris: List<String>) : DragData.FilesList {
        override fun readFiles() = uris
    }

    private class Words(private val text: String) : DragData.Text {
        override val bestMimeType = "text/plain"
        override fun readText() = text
    }

    private object Picture : DragData.Image {
        override fun readImage(): Painter = ColorPainter(Color.Red)
    }

    @Test
    fun filesKeepTheirPathsAndAFolderIsMarked() {
        val movie = File(dir, "My Movie.mp4").apply { writeText("x") }
        val extras = File(dir, "extras").apply { mkdir() }
        val items = droppedItems(FileList(listOf(movie.toURI().toString(), extras.toURI().toString())))
        assertEquals(listOf(DroppedItem.File(movie.path, false), DroppedItem.File(extras.path, true)), items)
        assertEquals(DropPlan.Open(DroppedMedia.File(movie.path)), planDrop(items))
    }

    @Test
    fun aFileLinkInTextCountsAsAFile() {
        val movie = File(dir, "clip.mkv").apply { writeText("x") }
        val items = droppedItems(Words(movie.toURI().toString() + "\r\n"))
        assertEquals(listOf(DroppedItem.File(movie.path, false)), items)
    }

    @Test
    fun aWebLinkStaysText() {
        val items = droppedItems(Words("https://example.com/v.mp4"))
        assertEquals(listOf(DroppedItem.Text("https://example.com/v.mp4")), items)
        assertEquals(DropPlan.Open(DroppedMedia.Link("https://example.com/v.mp4")), planDrop(items))
    }

    @Test
    fun aPictureIsRefused() {
        val items = droppedItems(Picture)
        assertEquals(emptyList(), items)
        assertEquals(DropPlan.Refuse(DropRefusal.Unsupported), planDrop(items))
    }
}
