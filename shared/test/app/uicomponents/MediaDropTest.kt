package app.uicomponents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a drop onto the window does, and what the window shows while an item is over it. */
class MediaDropTest {

    private fun file(path: String, isDirectory: Boolean = false) = DroppedItem.File(path, isDirectory)
    private fun open(path: String) = DropPlan.Open(DroppedMedia.File(path))
    private fun refuse(why: DropRefusal) = DropPlan.Refuse(why)

    @Test
    fun aMediaFileOpensWhateverTheCaseOfItsExtension() {
        assertEquals(open("/films/Movie.MKV"), planDrop(listOf(file("/films/Movie.MKV"))))
        assertEquals(open("/music/song.flac"), planDrop(listOf(file("/music/song.flac"))))
    }

    @Test
    fun aWindowsPathOpensAndKeepsItsName() {
        val plan = planDrop(listOf(file("""C:\Films\clip.mp4""")))
        assertEquals(open("""C:\Films\clip.mp4"""), plan)
        assertEquals("clip.mp4", ((plan as DropPlan.Open).media as DroppedMedia.File).name)
    }

    @Test
    fun anotherFileIsRefused() {
        assertEquals(refuse(DropRefusal.NotMedia), planDrop(listOf(file("/notes/todo.txt"))))
        assertEquals(refuse(DropRefusal.NotMedia), planDrop(listOf(file("/films/README"))))
    }

    @Test
    fun aFolderIsRefusedEvenWithAVideoName() {
        assertEquals(refuse(DropRefusal.Folder), planDrop(listOf(file("/films/season.mp4", isDirectory = true))))
    }

    /** The original Syncplay window opens the first dropped item only. */
    @Test
    fun theFirstItemDecides() {
        assertEquals(open("/a.mp4"), planDrop(listOf(file("/a.mp4"), file("/b.txt"))))
        assertEquals(refuse(DropRefusal.NotMedia), planDrop(listOf(file("/b.txt"), file("/a.mp4"))))
    }

    @Test
    fun aLinkOpensFromItsFirstLine() {
        val link = DropPlan.Open(DroppedMedia.Link("https://example.com/v.mp4"))
        assertEquals(link, planDrop(listOf(DroppedItem.Text("  https://example.com/v.mp4  "))))
        // A browser can add the page title on a second line.
        assertEquals(link, planDrop(listOf(DroppedItem.Text("\nhttps://example.com/v.mp4\nA title"))))
    }

    @Test
    fun textThatIsNoLinkIsRefused() {
        for (text in listOf("a film night", "example.com/video.mp4", "https://example.com/a b.mp4", "", "   ")) {
            assertEquals(refuse(DropRefusal.Unsupported), planDrop(listOf(DroppedItem.Text(text))), "text: '$text'")
        }
    }

    @Test
    fun nothingReadableIsRefused() {
        assertEquals(refuse(DropRefusal.Unsupported), planDrop(emptyList()))
    }

    @Test
    fun theTargetShowsWhatADropWillDoWhileAnItemIsOverIt() {
        val plans = mutableListOf<DropPlan>()
        val target = MediaDropTarget { plans += it }
        target.started(listOf(file("/a.mp4")))
        target.entered()
        assertTrue(target.over)
        assertEquals(open("/a.mp4"), target.preview)

        target.exited()
        assertFalse(target.over, "The item left the window")
        target.entered()

        val plan = target.dropped(listOf(file("/a.mp4")))
        assertEquals(open("/a.mp4"), plan)
        assertEquals(listOf(plan), plans)
        assertFalse(target.over)
        assertNull(target.preview)
    }

    /** A refused drop still reaches the screen, so the screen can say why. */
    @Test
    fun aRefusedDropReachesTheScreen() {
        val plans = mutableListOf<DropPlan>()
        val target = MediaDropTarget { plans += it }
        target.started(listOf(file("/b.txt")))
        target.entered()
        assertEquals(refuse(DropRefusal.NotMedia), target.preview)
        assertEquals(refuse(DropRefusal.NotMedia), target.dropped(listOf(file("/b.txt"))))
        assertEquals(listOf<DropPlan>(refuse(DropRefusal.NotMedia)), plans)
    }

    /** Some systems hide the data until the drop. The window then shows a hint, and the drop decides. */
    @Test
    fun dataHiddenUntilTheDropIsDecidedAtTheDrop() {
        val plans = mutableListOf<DropPlan>()
        val target = MediaDropTarget { plans += it }
        target.started(null)
        target.entered()
        assertTrue(target.over)
        assertNull(target.preview, "No plan yet, so the overlay shows the hint")
        assertEquals(open("/a.mp4"), target.dropped(listOf(file("/a.mp4"))))

        target.started(null)
        target.entered()
        assertEquals(refuse(DropRefusal.Unsupported), target.dropped(null), "Data that stays unreadable is refused")
        assertEquals(listOf(open("/a.mp4"), refuse(DropRefusal.Unsupported)), plans)
    }

    @Test
    fun aDragThatEndsWithoutADropClearsTheOverlay() {
        val target = MediaDropTarget { error("No drop happened") }
        target.started(listOf(file("/a.mp4")))
        target.entered()
        target.ended()
        assertFalse(target.over)
        assertNull(target.preview)
    }
}
