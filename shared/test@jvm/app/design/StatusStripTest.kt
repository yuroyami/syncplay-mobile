package app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.i18n.EnAppStrings
import app.protocol.models.ConnectionState
import app.room.ui.statinfo.RoomStatus
import app.room.ui.statinfo.RoomStatusStripContent
import app.room.ui.statinfo.STRIP_CLOCK
import app.theme.palette
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The status strip of the portrait room: the band above the video area. The clock sits at the
 * start edge, before the room name, and the state text sits at the end. Both keep clear of the
 * camera cutouts on the top edge. The strip keeps a margin of 18 dp at both sides, and 12 dp on both
 * sides of a cutout. A solo room shows the clock alone.
 */
class StatusStripTest {
    private val connected = RoomStatus(
        connectionState = ConnectionState.CONNECTED,
        encrypted = true,
        room = "Friday movie night with the whole family",
        state = "4 people",
        episode = null,
    )
    private val down = RoomStatus(ConnectionState.DISCONNECTED, encrypted = false, room = "movie-night", state = "Disconnected", episode = null)

    /** The clock of the strip in these tests: a 24-hour time, the widest form of HH:MM. */
    private val clock = "21:45"

    /** The strip as the portrait room holds it: full width and [heightDp] tall, on the bar colour. */
    @Composable
    private fun Strip(
        status: RoomStatus?,
        heightDp: Int,
        cutoutsDp: List<ClosedFloatingPointRange<Float>> = emptyList(),
        middleNotch: Float = 0f,
        rtl: Boolean = false,
        onReconnect: () -> Unit = {},
        time: String = clock,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Box(Modifier.fillMaxWidth().height(heightDp.dp).background(palette.roomBar)) {
                // The cutouts come in pixels, and the harness draws at a density of 2.
                val cutouts = cutoutsDp.map { it.start * 2f..it.endInclusive * 2f }
                RoomStatusStripContent(status, time, cutouts, middleNotch, onReconnect, Modifier.matchParentSize())
            }
        }
    }

    private fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)

    private fun DesignHarness.Driver.nodes(): List<SemanticsNode> = DesignHarness.onUiThread {
        scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
    }

    /** The horizontal span in dp of the node that [matches]. */
    private fun DesignHarness.Driver.spanOf(what: String, matches: (SemanticsNode) -> Boolean): ClosedFloatingPointRange<Float> {
        val node = assertNotNull(nodes().firstOrNull(matches), "The strip shows $what")
        return node.positionInRoot.x / 2f..(node.positionInRoot.x + node.size.width) / 2f
    }

    private fun DesignHarness.Driver.textSpan(text: String) =
        spanOf("\"$text\"") { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true }

    private fun DesignHarness.Driver.clockSpan() =
        spanOf("the clock") { it.config.getOrNull(SemanticsProperties.TestTag) == STRIP_CLOCK }

    /** Whether the text of [node] fits its box: no line is cut and none ends in "…". */
    private fun fitsWhole(node: SemanticsNode): Boolean {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val layout = layouts.single()
        return !layout.didOverflowWidth && (0 until layout.lineCount).none { layout.isLineEllipsized(it) }
    }

    private fun DesignHarness.Driver.lockSpan() =
        spanOf("the lock") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(EnAppStrings.roomConnectionEncrypted) == true }

    private fun strip(widthDp: Int, heightDp: Int, content: @Composable () -> Unit, check: DesignHarness.Driver.() -> Unit) =
        DesignHarness.drive(widthDp, heightDp, television = false, content = content, drive = check)

    @Test
    fun withoutANotchTheNameSitsAtTheStartAndTheStateAtTheEnd() = strip(390, 28, { Strip(connected, 28) }) {
        val lock = lockSpan()
        val name = textSpan(connected.room)
        val state = textSpan(connected.state)
        assertTrue(lock.start >= 18f, "The start part keeps the margin, at ${lock.start} dp")
        assertTrue(abs(state.endInclusive - (390f - 18f)) < 0.5f, "The state text ends at the margin, at ${state.endInclusive} dp")
        assertTrue(name.endInclusive <= state.start - 12f + 0.5f, "The room name gives way to the state text")
    }

    @Test
    fun aLongStateTextLeavesTheRoomNameItsShare() {
        val status = connected.copy(state = "Waiting for Christopher_Lee_and_all_of_his_friends_from_school")
        strip(390, 28, { Strip(status, 28) }) {
            val name = textSpan(status.room)
            val state = textSpan(status.state)
            val stretch = 390f - 2 * 18f
            assertTrue(name.endInclusive - 18f >= 0.3f * stretch, "The start part keeps its share, up to ${name.endInclusive} dp")
            assertTrue(state.endInclusive - state.start >= 0.5f * stretch, "The state text takes the rest, ${state.start} to ${state.endInclusive} dp")
        }
    }

    /** No platform reports the cutouts, and the inset is tall enough for a notch in the middle, as on an iPhone. */
    @Test
    fun theTextKeepsClearOfANotchInTheMiddle() {
        for (rtl in listOf(false, true)) {
            strip(393, 54, { Strip(connected, 54, middleNotch = 0.36f, rtl = rtl) }) {
                val hole = 393f * 0.32f..393f * 0.68f
                val name = textSpan(connected.room)
                val state = textSpan(connected.state)
                val (before, after) = if (rtl) state to name else name to state
                assertTrue(before.endInclusive <= hole.start - 12f + 0.5f, "The text before the notch ends at ${before.endInclusive} dp")
                assertTrue(after.start >= hole.endInclusive + 12f - 0.5f, "The text after the notch starts at ${after.start} dp")
                assertTrue(before.start >= 18f - 0.5f && after.endInclusive <= 393f - 18f + 0.5f, "Both keep the margins")
            }
        }
    }

    /** Android reports a camera hole near the start corner. The start part moves past it. */
    @Test
    fun theTextKeepsClearOfAReportedCornerCutout() = strip(390, 30, { Strip(connected, 30, cutoutsDp = listOf(24f..52f)) }) {
        val lock = lockSpan()
        val name = textSpan(connected.room)
        assertTrue(lock.start >= 52f + 12f, "The start part sits after the cutout, at ${lock.start} dp")
        assertTrue(name.start > lock.endInclusive, "The room name follows the lock")
    }

    /**
     * Beside the notch of an iPhone, each side is narrow. The strip keeps smaller margins and drops
     * the lock, the tag and the people glyph. The clock stays whole, and the room name gives way.
     */
    @Test
    fun besideAWideNotchTheStripIsCompact() {
        val status = connected.copy(room = "movie-night", episode = "S02E11")
        var roomyClock = 0f
        strip(390, 28, { Strip(status, 28) }) { roomyClock = clockSpan().let { it.endInclusive - it.start } }
        strip(375, 44, { Strip(status, 44, middleNotch = 0.56f) }) {
            val hole = 375f * 0.22f..375f * 0.78f
            val time = clockSpan()
            val state = textSpan(status.state)
            assertTrue(time.start >= 12f - 0.5f && time.endInclusive <= hole.start - 6f + 0.5f, "The clock sits beside the notch, at ${time.start} to ${time.endInclusive} dp")
            assertEquals(roomyClock, time.endInclusive - time.start, 0.5f, "The clock keeps its whole width")
            val name = nodes().firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == status.room } == true }
            if (name != null) {
                val start = name.positionInRoot.x / 2f
                assertTrue(start >= time.endInclusive && (start + name.size.width / 2f) <= hole.start - 6f + 0.5f, "The room name gives way, between the clock and the notch")
            }
            assertTrue(state.start >= hole.endInclusive + 6f - 0.5f && state.endInclusive <= 375f - 12f + 0.5f, "The state text sits beside the notch, at ${state.start} dp")
            assertTrue(nodes().none { it.config.getOrNull(SemanticsProperties.ContentDescription) != null }, "A compact strip has no lock")
            assertTrue(nodes().none { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "S02E11" } == true }, "A compact strip has no episode tag")
        }
    }

    /** The clock sits at the start edge, before the room name, and a screen reader does not speak it. */
    @Test
    fun theClockSitsAtTheStartBeforeTheRoomName() {
        for (rtl in listOf(false, true)) {
            strip(390, 28, { Strip(connected, 28, rtl = rtl) }) {
                val time = clockSpan()
                val name = textSpan(connected.room)
                if (rtl) {
                    assertEquals(390f - 18f, time.endInclusive, 0.5f, "The clock ends at the start margin, at ${time.endInclusive} dp")
                    assertTrue(name.endInclusive <= time.start, "The room name comes after the clock")
                } else {
                    assertEquals(18f, time.start, 0.5f, "The clock starts at the start margin, at ${time.start} dp")
                    assertTrue(name.start >= time.endInclusive, "The room name comes after the clock")
                }
                assertTrue(nodes().none { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == clock } == true }, "A screen reader does not speak the clock")
            }
        }
    }

    /**
     * Beside a long state text, the start part keeps the clock and the episode tag whole. The state
     * text comes first among the texts, and the room name gives way.
     */
    @Test
    fun theClockAndTheTagStayWholeBesideALongStateText() {
        val status = connected.copy(episode = "S02E11", state = "Waiting for Christopher_Lee_and_all_of_his_friends_from_school")
        for (width in listOf(360, 390)) {
            strip(width, 28, { Strip(status, 28, time = "12:45") }) {
                val tag = assertNotNull(nodes().firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "S02E11" } == true }, "The tag shows")
                assertTrue(fitsWhole(tag), "${width}dp: the tag shows whole")
                val time = clockSpan()
                assertTrue(time.endInclusive - time.start >= 20f, "${width}dp: the clock keeps its width")
                val state = textSpan(status.state)
                val tagEnd = (tag.positionInRoot.x + tag.size.width) / 2f
                assertTrue(tagEnd <= state.start - 12f + 0.5f, "${width}dp: the tag ends before the state text, at $tagEnd dp")
            }
        }
    }

    /** A solo room has no status, so its strip shows the clock alone, at the start edge. */
    @Test
    fun aSoloStripShowsTheClockAlone() = strip(390, 28, { Strip(null, 28) }) {
        assertEquals(18f, clockSpan().start, 0.5f, "The clock starts at the start margin")
        assertTrue(nodes().none { it.config.getOrNull(SemanticsProperties.Text) != null }, "Nothing else in the strip has text")
        assertTrue(nodes().none { SemanticsActions.OnClick in it.config }, "Nothing in a solo strip reacts to a tap")
    }

    @Test
    fun aCompactStripReconnectsWithTheGlyphAlone() {
        var reconnects = 0
        strip(375, 44, { Strip(down, 44, middleNotch = 0.56f, onReconnect = { reconnects++ }) }) {
            val key = assertNotNull(nodes().firstOrNull { SemanticsActions.OnClick in it.config }, "The strip has a reconnect key while down")
            assertTrue(key.size.width / 2f <= 24f, "The reconnect key is the glyph alone, ${key.size.width / 2f} dp wide")
            assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(down.state) == true }, "The glyph carries the state text")
            tap(Offset(key.positionInRoot.x + key.size.width / 2f, key.positionInRoot.y + key.size.height / 2f))
            assertEquals(1, reconnects, "A tap on the glyph reconnects")
        }
    }

    @Test
    fun whileDownATapOnTheStateTextReconnects() {
        var reconnects = 0
        strip(390, 28, { Strip(down, 28, onReconnect = { reconnects++ }) }) {
            val key = assertNotNull(nodes().firstOrNull { SemanticsActions.OnClick in it.config }, "The strip has a reconnect key while down")
            assertEquals(Role.Button, key.config.getOrNull(SemanticsProperties.Role), "The reconnect key reads as a button")
            assertTrue(abs(key.positionInRoot.x + key.size.width - (390f - 18f) * 2f) < 1f, "The reconnect key sits at the end")
            tap(Offset(key.positionInRoot.x + key.size.width / 2f, key.positionInRoot.y + key.size.height / 2f))
            assertEquals(1, reconnects, "A tap on the reconnect key reconnects")
        }
    }

    @Test
    fun aConnectedStripHasNoReconnectKey() = strip(390, 28, { Strip(connected, 28) }) {
        assertTrue(nodes().none { SemanticsActions.OnClick in it.config }, "Nothing in a connected strip reacts to a tap")
    }

    /* Renders, for a look by eye. */

    @Test
    fun rendersTheStrip() {
        for (scale in listOf(1f, 1.3f)) {
            // The portrait room makes the strip as tall as the room name with a margin, and at least 28 dp.
            val height = maxOf(28, (19 * scale + 8).toInt() + 1)
            DesignHarness.render("portrait-strip-plain", 390, height, fontScale = scale, overVideo = true) {
                Strip(connected.copy(room = "movie-night"), height)
            }.assertAllTextFits()
        }
        DesignHarness.render("portrait-strip-island", 393, 54, overVideo = true) { Strip(connected, 54, middleNotch = 0.36f) }
        DesignHarness.render("portrait-strip-corner", 390, 30, overVideo = true) {
            Strip(connected.copy(episode = "S02E11"), 30, cutoutsDp = listOf(24f..52f))
        }
        DesignHarness.render("portrait-strip-down", 390, 28, overVideo = true) { Strip(down, 28) }.assertAllTextFits()
        DesignHarness.render("portrait-strip-rtl", 393, 54, overVideo = true) { Strip(connected, 54, middleNotch = 0.36f, rtl = true) }
        DesignHarness.render("portrait-strip-notch", 375, 44, overVideo = true) {
            Strip(connected.copy(room = "movie-night", episode = "S02E11"), 44, middleNotch = 0.56f)
        }
        DesignHarness.render("portrait-strip-notch-down", 375, 44, overVideo = true) { Strip(down, 44, middleNotch = 0.56f) }
        DesignHarness.render("portrait-strip-clock-12h", 390, 28, overVideo = true) {
            Strip(connected.copy(room = "movie-night", episode = "S02E11"), 28, time = "9:41")
        }
        DesignHarness.render("portrait-strip-clock-waiting", 360, 28, overVideo = true) {
            Strip(connected.copy(episode = "S02E11", state = "Waiting for Christopher and 3 others"), 28)
        }
        DesignHarness.render("portrait-strip-solo", 390, 28, overVideo = true) { Strip(null, 28) }
        DesignHarness.render("portrait-strip-solo-notch", 375, 44, overVideo = true) { Strip(null, 44, middleNotch = 0.56f) }
    }
}
