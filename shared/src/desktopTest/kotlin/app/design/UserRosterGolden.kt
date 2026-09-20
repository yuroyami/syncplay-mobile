package app.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import app.player.models.MediaFile
import app.protocol.models.User
import app.room.ui.rightcards.UserRosterPanel
import app.room.ui.rightcards.compactRosterFileName
import app.theme.TRINITY
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real roster rows, including the actions that must stay out of the information at rest. */
class UserRosterGolden {
    @Test
    fun expandedRowsShowWholeFilenamesAndMetadataAtNarrowWidthsAndLargeText() {
        for (width in listOf(280, 320, 420)) {
            for (scale in listOf(1f, 2f)) {
                Roster(width, scale, compact = false).use { roster ->
                    roster.save("expanded").assertAllTextFits()
                    assertTrue(roster.labels().containsAll(listOf(MY_FILE, PEER_FILE, "Duration: 24:35", "Size: 650 MB", "Size: 1.7 GB", "No file")))
                    assertFalse(roster.labels().any { it.startsWith("At ") || it.startsWith("at ") })
                    roster.assertNoModeration()
                    roster.assertViewSwitcher(destination = "Compact", current = "Expanded")
                    val self = roster.userNode(SELF)
                    val state = self.config.getOrNull(SemanticsProperties.StateDescription).orEmpty()
                    assertTrue(state.contains("ready") && state.contains("You") && state.contains("Room operator"), state)
                    assertNull(self.config.getOrNull(SemanticsActions.OnClick), "The expanded self row must not offer moderation")
                }
            }
        }
    }

    @Test
    fun compactRowsUseCleanedTitlesAndMiddleEllipsisWithoutMetadata() {
        for (width in listOf(280, 320, 420)) {
            for (scale in listOf(1f, 2f)) {
                Roster(width, scale, compact = true).use { roster ->
                    val result = roster.save("compact")
                    val titleLayouts = result.textLayouts.filter { it.layoutInput.text.text.startsWith("Dand") }
                    assertEquals(2, titleLayouts.size, "Every user with a file must have a compact title")
                    assertFalse(roster.labels().contains(MY_FILE))
                    assertFalse(roster.labels().contains(PEER_FILE))
                    assertFalse(roster.labels().any { it.startsWith("Duration:") || it.startsWith("Size:") })
                    roster.assertNoModeration()
                    roster.assertViewSwitcher(destination = "Expanded", current = "Compact")
                    titleLayouts.forEachIndexed { index, layout ->
                        val displayed = layout.layoutInput.text.text
                        val cleaned = compactRosterFileName(if (index == 0) MY_FILE else PEER_FILE)
                        assertTrue(displayed.endsWith("03"), "The episode ending must remain visible: $displayed")
                        if ('…' in displayed) {
                            val parts = displayed.split('…')
                            assertTrue(cleaned.startsWith(parts[0]) && cleaned.endsWith(parts[1]), displayed)
                        } else {
                            assertEquals(cleaned, displayed)
                        }
                    }
                    assertTrue('…' in titleLayouts[1].layoutInput.text.text, "The long title must abbreviate at ${width}dp / $scale")
                    // The literal middle abbreviation must fit without a second renderer truncation.
                    result.assertAllTextFits()
                }
            }
        }
    }

    @Test
    fun tappingPeopleRevealsOneActionStripAndFilelessPeersHaveWorkingActions() {
        for (compact in listOf(false, true)) {
            for (scale in listOf(1f, 2f)) {
                Roster(320, scale, compact).use { roster ->
                    roster.advance()
                    roster.tap(SELF)
                    roster.assertNoModeration()
                    roster.tap(PEER)
                    roster.assertOneModerationStrip()
                    assertTrue(roster.labels().contains(PEER_FILE), "Selected compact rows must reveal the full filename")
                    roster.save(if (compact) "compact-selected" else "expanded-selected").assertAllTextFits()
                    roster.tap("Mute")
                    assertEquals(listOf(PEER), roster.muteCalls)
                    roster.assertOneModerationStrip(muted = true)

                    roster.tap(FILELESS)
                    roster.assertOneModerationStrip()
                    if (compact) assertFalse(roster.labels().contains(PEER_FILE), "Opening a second person must close the first")
                    roster.tap("Mute")
                    assertEquals(listOf(PEER, FILELESS), roster.muteCalls)
                    roster.tap("Mark ready")
                    assertEquals(listOf(FILELESS), roster.readyCalls)
                    roster.save(if (compact) "compact-fileless-selected" else "expanded-fileless-selected")

                    roster.tap(FILELESS)
                    roster.assertNoModeration()
                    roster.tap(SELF)
                    roster.assertNoModeration()
                }
            }
        }
    }

    @Test
    fun changingDensityClosesTheSelectedPersonsActions() {
        Roster(320, 1f, compact = false).use { roster ->
            roster.advance()
            roster.tap(PEER)
            roster.assertOneModerationStrip()
            roster.tapViewSwitcher("Compact")
            roster.assertNoModeration()
            assertFalse(roster.labels().contains(PEER_FILE))
            roster.assertViewSwitcher(destination = "Expanded", current = "Compact")
            roster.tapViewSwitcher("Expanded")
            roster.assertNoModeration()
            assertTrue(roster.labels().contains(PEER_FILE))
            roster.assertViewSwitcher(destination = "Compact", current = "Expanded")
        }
    }

    @Test
    fun shortRoomPanelsScrollToTheLastPeerAndKeepTheIconSwitcherVisible() {
        for ((width, height) in listOf(320 to 260, 420 to 340)) {
            for (scale in listOf(1f, 2f)) {
                Roster(width, scale, compact = false, height = height).use { roster ->
                    roster.save("short-expanded").assertAllTextFits()
                    roster.scrollToBottom()
                    roster.assertVisible(FILELESS)
                    roster.assertViewSwitcher(destination = "Compact", current = "Expanded")
                    roster.tap(FILELESS)
                    roster.scrollToBottom()
                    roster.assertOneModerationStrip()
                    roster.tap("Mark ready")
                    assertEquals(listOf(FILELESS), roster.readyCalls)
                    roster.save("short-expanded-scrolled").assertAllTextFits()
                }
                Roster(width, scale, compact = true, height = height).use { roster ->
                    roster.save("short-compact").assertAllTextFits()
                    roster.assertViewSwitcher(destination = "Expanded", current = "Compact")
                }
            }
        }
    }

    private class Roster(val width: Int, val scale: Float, compact: Boolean, val height: Int = if (scale == 1f) 700 else 1200) : AutoCloseable {
        val muteCalls = mutableListOf<String>()
        val readyCalls = mutableListOf<String>()
        private val muted = mutableStateSetOf<String>()
        private var compactMode by mutableStateOf(compact)
        private var frame = 0L
        private val scene: ImageComposeScene

        init {
            DesignHarness.initDatastore()
            scene = ImageComposeScene(width = width * 2, height = height * 2, density = Density(2f, scale)) {
                DesignHarness.Frame(TRINITY, overVideo = true) {
                    UserRosterPanel(
                        users = users, me = SELF, myFile = users.first().file,
                        compact = compactMode, onCompactChange = { compactMode = it }, mutedUsers = muted,
                        onToggleMute = { username ->
                            muteCalls += username
                            if (!muted.remove(username)) muted.add(username)
                        },
                        onSetReady = { readyCalls += it.name },
                    )
                }
            }
        }

        fun advance() {
            repeat(30) { scene.render(frame++ * 16_000_000L) }
        }

        private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
        private fun allNodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { nodes(it.unmergedRootSemanticsNode) }

        private fun textLayouts(): List<TextLayoutResult> = buildList {
            allNodes().forEach { node -> node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(this) }
        }

        fun labels(): List<String> = textLayouts().map { it.layoutInput.text.text }

        fun userNode(username: String): SemanticsNode = assertNotNull(allNodes().firstOrNull {
            it.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("$username,") == true
        }, "No spoken readiness row for $username")

        private fun textNode(label: String): SemanticsNode = assertNotNull(allNodes().firstOrNull {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
            }, "No text: $label")

        fun assertVisible(label: String) {
            assertVisible(textNode(label), label)
        }

        private fun assertVisible(node: SemanticsNode, label: String) {
            val bounds = node.boundsInRoot
            assertTrue(bounds.height > 0 && bounds.top >= 0 && bounds.bottom <= height * 2, "$label is outside the visible panel: $bounds")
        }

        private fun viewSwitcher(destination: String): SemanticsNode = assertNotNull(allNodes().firstOrNull {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(destination) == true &&
                it.config.getOrNull(SemanticsActions.OnClick) != null
        }, "No icon switcher for $destination")

        fun assertViewSwitcher(destination: String, current: String) {
            val switcher = viewSwitcher(destination)
            assertVisible(switcher, "$destination icon switcher")
            assertEquals(current, switcher.config.getOrNull(SemanticsProperties.StateDescription))
            val stray = labels().filter { it == "Compact" || it == "Expanded" || it == users.size.toString() }
            assertTrue(stray.isEmpty(), "View mode and user count must not add text to the roster header: $stray in ${labels()}")
        }

        fun tapViewSwitcher(destination: String) {
            val switcher = viewSwitcher(destination)
            assertVisible(switcher, "$destination icon switcher")
            tapNode(switcher)
        }

        fun scrollToBottom() {
            val scroll = assertNotNull(allNodes().firstOrNull {
                it.config.getOrNull(SemanticsActions.ScrollBy) != null
            }, "The roster must provide scrolling in a short panel")
            val action = assertNotNull(scroll.config.getOrNull(SemanticsActions.ScrollBy)?.action)
            assertTrue(action(0f, 10_000f))
            advance()
        }

        fun tap(label: String) {
            assertVisible(label)
            tapNode(textNode(label))
        }

        private fun tapNode(node: SemanticsNode) {
            val bounds = node.boundsInRoot
            scene.sendPointerEvent(PointerEventType.Press, bounds.center)
            scene.sendPointerEvent(PointerEventType.Release, bounds.center)
            // A real pointer does not stay parked on the control it just pressed. Left hovering,
            // a glyph button opens its desktop tooltip after 600 ms and adds its name to the tree.
            scene.sendPointerEvent(PointerEventType.Exit, bounds.center)
            advance()
        }

        fun assertNoModeration() {
            assertFalse(labels().any { it == "Mute" || it == "Unmute" }, "Moderation should require choosing a peer")
        }

        fun assertOneModerationStrip(muted: Boolean = false) {
            assertEquals(1, labels().count { it == "Mute" || it == "Unmute" }, "Exactly one person can have actions open")
            assertEquals(1, labels().count { it == if (muted) "Unmute" else "Mute" })
        }

        fun save(name: String): DesignHarness.Result {
            advance()
            val image = scene.render(frame++ * 16_000_000L)
            val file = File(DesignHarness.outDir, "user-roster-$name-${width}x${height}dp-fs$scale.png")
            file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            println("GOLDEN ${file.absolutePath}")
            return DesignHarness.Result(file, height, textLayouts())
        }

        override fun close() = scene.close()
    }

    private companion object {
        const val SELF = "Alexandra_Morgan"
        const val PEER = "Christopher_Lee"
        const val FILELESS = "WaitingForAFile"
        const val MY_FILE = "[Anime Time] Dandandan S01 - Episode 03.mkv"
        const val PEER_FILE = "[Release Group][1080p] Dandandan - Season 01 - A Very Long Episode Title - Episode 03.mkv"
        val users = listOf(
            User(name = SELF, readiness = true, file = MediaFile(fileName = MY_FILE, fileDuration = 1475.0, fileSize = "650000000"), isController = true, position = 95.0),
            User(name = PEER, readiness = false, file = MediaFile(fileName = PEER_FILE, fileDuration = 1475.0, fileSize = "1700000000"), isController = false, position = 88.0),
            User(name = FILELESS, readiness = false, file = null, isController = false),
        )
    }
}
