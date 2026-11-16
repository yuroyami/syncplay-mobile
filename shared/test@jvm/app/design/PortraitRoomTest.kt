package app.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.LocalRoomViewmodel
import app.i18n.EnAppStrings
import app.player.models.Chapter
import app.preferences.Pref
import app.preferences.Preferences.CUSTOM_SEEK_FRONT
import app.preferences.Preferences.GIF_PANEL_SOURCE
import app.preferences.Preferences.GIF_PANEL_TYPE
import app.preferences.Preferences.ROOM_ORIENTATION
import app.preferences.flow
import app.preferences.set
import app.room.RoomOrientation
import app.room.RoomScreenUI
import app.room.RoomViewmodel
import app.room.models.Message
import app.room.ui.chat.RoomChatInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The room in a window taller than wide, such as a phone held upright: the stage (the video area)
 * on top, then the tab row, the main box with chat, and the composer (the chat input row) at the
 * bottom. A page is what the main box shows in place of chat: a panel, the GIF drawer, the
 * Controls page or the More page. The harness has no insets, so the stage starts at the top edge.
 */
class PortraitRoomTest {
    private val widthDp = 390
    private val heightDp = 844

    /** The bottom edge of the stage: full width in 16:9. */
    private val stageBottomDp = widthDp * 9f / 16f

    private fun portrait(drive: RoomRig.Room.() -> Unit) =
        RoomRig.drive(solo = false, television = false, withVideo = true, widthDp = widthDp, heightDp = heightDp, drive = drive)

    /** Scene pixels to dp. The harness draws at a density of 2. */
    private fun Float.dp() = this / 2f

    private fun at(xDp: Float, yDp: Float) = Offset(xDp * 2f, yDp * 2f)

    /** Waits out a focus hand-off, which retries in real time, 60 ms apart. */
    private fun RoomRig.Room.settle() = repeat(7) {
        Thread.sleep(100)
        driver.frames(3)
    }

    private fun RoomRig.Room.press(name: String) {
        val node = assertNotNull(described(name), "\"$name\" exists")
        DesignHarness.onUiThread { node.config[SemanticsActions.OnClick].action?.invoke() }
        driver.frames(10)
    }

    /** Runs [block] with [pref] set to [value], then restores the default. */
    private inline fun <reified T> withPref(pref: Pref<T>, value: T, block: () -> Unit) {
        DesignHarness.initDatastore()
        runBlocking {
            pref.set(value)
            withTimeout(2_000) { pref.flow().first { it == value } }
        }
        try {
            block()
        } finally {
            runBlocking { pref.set(pref.default) }
        }
    }

    /** The GIF drawer on its favorites, which never use the network. */
    private fun withGifs(type: String = "GIF", block: () -> Unit) =
        withPref(GIF_PANEL_SOURCE, "FAVORITES") { withPref(GIF_PANEL_TYPE, type, block) }

    private val chat = { listOf(
        Message(content = "Mia joined the room"),
        Message(sender = "Sam", content = "ok, starting from the cold open"),
        Message(sender = "Leo", content = "the subtitles are a second late for me"),
        Message(sender = "Ari", content = "same file? mine look fine", isMainUser = true),
        Message(sender = "Mia", content = "this soundtrack though"),
    ) }

    /** A room with chat and a file 25 minutes long, played to 8:20. */
    private fun fill(room: RoomViewmodel) {
        room.session.messageSequence.value = chat()
        room.playerManager.timeFullMillis.value = 1_500_000L
        room.playerManager.timeCurrentMillis.value = 500_000L
    }

    private val chapters = listOf(
        Chapter(0, "Cold open", 0L),
        Chapter(1, "Opening titles", 95_000L),
        Chapter(2, "The heist", 410_000L),
    )

    @Test
    fun theControlsKeySitsInTheVideoArea() = portrait {
        driver.frames(20)
        val key = assertNotNull(described(EnAppStrings.roomControlPanel), "The Controls key exists")
        assertTrue(boundsOf(key).bottom.dp() <= stageBottomDp, "The Controls key sits in the video area")
    }

    @Test
    fun theTabsSitInARowUnderTheVideoArea() = portrait {
        driver.frames(20)
        val more = assertNotNull(described(EnAppStrings.roomRailMore), "The More tab exists")
        val y = boundsOf(more).center.y.dp()
        assertTrue(y > stageBottomDp && y < stageBottomDp + 48f, "The More tab sits under the video area, at $y dp")
    }

    @Test
    fun theComposerSitsAtTheBottom() = portrait {
        driver.frames(20)
        val field = assertNotNull(described(EnAppStrings.roomChatInput), "The chat field exists")
        assertTrue(boundsOf(field).bottom.dp() > heightDp - 60f, "The chat field sits at the bottom")
    }

    /** With the soft keyboard open, the tab row folds away and the composer sits on the keyboard. */
    @Test
    fun theKeyboardFoldsTheTabRowAndCarriesTheComposer() {
        val keyboardPx = mutableStateOf(0)
        RoomRig.drive(solo = false, television = false, withVideo = true, widthDp = widthDp, heightDp = heightDp, keyboardPx = keyboardPx) {
            driver.frames(20)
            assertNotNull(described(EnAppStrings.roomRailMore), "The tab row shows while the keyboard is closed")
            keyboardPx.value = 600
            driver.frames(30)
            assertNull(described(EnAppStrings.roomRailMore), "The tab row folds away while the keyboard is open")
            val composer = assertNotNull(described(EnAppStrings.roomSend), "The send key exists")
            val keyboardTop = heightDp - 300f
            val bottom = boundsOf(composer).bottom.dp()
            assertTrue(bottom <= keyboardTop && bottom > keyboardTop - 54f, "The composer sits on the keyboard, at $bottom dp")
            keyboardPx.value = 0
            driver.frames(30)
            assertNotNull(described(EnAppStrings.roomRailMore), "The tab row comes back when the keyboard closes")
        }
    }

    /** Before a file loads, the add key sits low in the video area, so the logo above it stays clear. */
    @Test
    fun theAddKeySitsInTheLowerHalfOfTheVideoArea() =
        RoomRig.drive(solo = false, television = false, withVideo = false, widthDp = widthDp, heightDp = heightDp) {
            driver.frames(20)
            // The add tab has the same name, so take the highest one: the key in the video area.
            val key = assertNotNull(
                allNodes().filter { SemanticsActions.OnClick in it.config && nameOf(it) == EnAppStrings.roomButtonDescAdd }
                    .minByOrNull { boundsOf(it).top },
                "The add key exists",
            )
            val bounds = boundsOf(key)
            assertTrue(bounds.bottom.dp() <= stageBottomDp, "The add key sits in the video area")
            assertTrue(bounds.top.dp() >= stageBottomDp / 2f, "The add key sits in the lower half, at ${bounds.top.dp()} dp")
        }

    @Test
    fun onlyATapOnTheVideoAreaHidesTheControls() = portrait {
        driver.frames(20)
        driver.tap(at(widthDp / 2f, 600f))
        driver.frames(10)
        assertTrue(ui.visibleHUD.value, "A tap on the chat leaves the controls up")
        // Between the status line and the play key, where no control sits.
        driver.tap(at(widthDp / 2f, 60f))
        driver.frames(10)
        assertFalse(ui.visibleHUD.value, "A tap on the video area hides the controls")
    }

    @Test
    fun theComposerKeepsItsFocusWhenTheControlsHide() = portrait {
        driver.frames(20)
        DesignHarness.onUiThread { ui.chatFocus.requestFocus() }
        driver.frames(5)
        assertEquals(EnAppStrings.roomChatInput, focusedName(), "The chat field has focus")
        ui.visibleHUD.value = false
        driver.frames(30)
        assertEquals(EnAppStrings.roomChatInput, focusedName(), "The chat field keeps focus after the controls hide")
    }

    @Test
    fun aChatLineCountsAsSeenWhileTheControlsAreHidden() = portrait {
        driver.frames(20)
        ui.visibleHUD.value = false
        driver.frames(30)
        val line = Message(sender = "friend", content = "hello")
        viewmodel.session.messageSequence.update { it + line }
        driver.frames(20)
        assertTrue(line.seen, "The chat shows under the video, so its lines count as seen")
    }

    @Test
    fun theMoreTabOpensItsPageUnderTheTabs() = portrait {
        driver.frames(20)
        val more = assertNotNull(described(EnAppStrings.roomRailMore), "The More tab exists")
        DesignHarness.onUiThread { more.config[SemanticsActions.OnClick].action?.invoke() }
        driver.frames(30)
        val leave = assertNotNull(
            allNodes().lastOrNull { nameOf(it) == EnAppStrings.roomOverflowLeaveRoom },
            "The More page offers to leave the room",
        )
        val y = boundsOf(leave).center.y.dp()
        assertTrue(y > stageBottomDp + 48f, "The leave row sits in the main box, at $y dp")
    }

    /* One page at a time (#239). */

    @Test
    fun aLineOverAPageRaisesTheMarkerAndATapOnItShowsChat() = portrait {
        driver.frames(20)
        ui.toggleRoomPreferences(true)
        driver.frames(30)
        viewmodel.session.messageSequence.update { it + Message(sender = "tester", content = "brb", isMainUser = true) }
        driver.frames(20)
        assertFalse(driver.shows(EnAppStrings.roomChatNewMessages), "An own line raises no marker")
        val line = Message(sender = "friend", content = "hello")
        viewmodel.session.messageSequence.update { it + line }
        driver.frames(20)
        assertTrue(driver.shows(EnAppStrings.roomChatNewMessages), "A line from someone else raises the marker over the page")
        assertFalse(line.seen, "The page covers the line, so it is not seen yet")
        driver.click(EnAppStrings.roomChatNewMessages)
        driver.frames(40)
        assertFalse(ui.anyPageOpen, "A tap on the marker closes the page")
        assertFalse(driver.shows(EnAppStrings.roomChatNewMessages), "The marker leaves with the page")
        assertTrue(line.seen, "Chat shows the new line")
    }

    @Test
    fun aTapOnTheChatFieldShowsChatAndKeepsTheGifDrawer() = withGifs {
        portrait {
            driver.frames(20)
            val field = boundsOf(assertNotNull(described(EnAppStrings.roomChatInput), "The chat field exists")).center
            ui.toggleRoomPreferences(true)
            driver.frames(30)
            driver.tap(field)
            driver.frames(30)
            assertFalse(ui.anyPageOpen, "A tap on the chat field shows chat in place of the page")
            ui.toggleGifPanel(true)
            driver.frames(30)
            driver.tap(field)
            driver.frames(30)
            assertTrue(ui.gifPanelVisible.value, "The GIF drawer stays, because the chat field is its search")
        }
    }

    @Test
    fun theCloseKeyOfEachPageShowsChat() = withGifs {
        portrait {
            driver.frames(20)
            val pages = mapOf<String, () -> Unit>(
                "people" to { ui.toggleUserInfo(true) },
                "playlist" to { ui.toggleSharedPlaylist(true) },
                "preferences" to { ui.toggleRoomPreferences(true) },
                "tracks" to { ui.toggleTracks(true) },
                "gestures" to { ui.toggleGestures(true) },
                "seek to" to { ui.toggleSeekTo(true) },
                "add media" to { ui.toggleAddMedia(true) },
                "GIF drawer" to { ui.toggleGifPanel(true) },
                "Controls" to { ui.toggleControlPanel(true) },
                "More" to { ui.toggleMorePage(true) },
            )
            for ((page, open) in pages) {
                open()
                driver.frames(40)
                assertTrue(ui.anyPageOpen, "The $page page opens")
                press(EnAppStrings.actionClose)
                driver.frames(40)
                assertFalse(ui.anyPageOpen, "The close key of the $page page shows chat")
            }
        }
    }

    /* The Controls page (#240). */

    /** What one key or row did: the panels it left open, the undo question, and the aspect ratio switches. */
    private data class Outcome(val open: Set<String>, val undoAsked: Boolean, val aspectSwitches: Int)

    private val controlNames = listOf(
        EnAppStrings.roomTracks,
        EnAppStrings.roomSeekTo,
        EnAppStrings.roomUndoSeek,
        EnAppStrings.roomAspectRatio,
        EnAppStrings.roomGesturesPanelTitle,
    )

    /** Presses each key of the control strip in landscape, or each row of the Controls page in portrait. */
    private fun controlOutcomes(portrait: Boolean): Map<String, Outcome> {
        val results = linkedMapOf<String, Outcome>()
        val (width, height) = if (portrait) widthDp to heightDp else 960 to 540
        RoomRig.drive(solo = false, television = false, withVideo = true, widthDp = width, heightDp = height) {
            viewmodel.seeks.add(60_000L to 120_000L)
            driver.frames(20)
            val player = viewmodel.player as InertPlayer
            for (name in controlNames) {
                ui.showChat()
                driver.frames(30)
                ui.toggleControlPanel(true)
                driver.frames(40)
                val switches = player.aspectSwitches
                if (portrait) driver.click(name) else press(name)
                driver.frames(20)
                Thread.sleep(100)
                driver.frames(20)
                val open = buildSet {
                    if (ui.tabCardTracks.value) add("tracks")
                    if (ui.tabCardSeekTo.value) add("seek to")
                    if (ui.tabCardGestures.value) add("gestures")
                    if (ui.controlPanel.value) add("Controls")
                }
                results[name] = Outcome(open, driver.shows(EnAppStrings.roomUndoSeekTitle), player.aspectSwitches - switches)
                if (driver.shows(EnAppStrings.roomUndoSeekCancel)) driver.click(EnAppStrings.roomUndoSeekCancel)
                driver.frames(20)
            }
        }
        return results
    }

    @Test
    fun eachRowOfTheControlsPageDoesWhatItsStripKeyDoes() {
        val strip = controlOutcomes(portrait = false)
        val page = controlOutcomes(portrait = true)
        println("CONTROLS strip=$strip page=$page")
        assertEquals(strip, page)
        // The outcomes themselves, so two broken layouts cannot agree by accident.
        assertEquals(setOf("tracks"), page.getValue(EnAppStrings.roomTracks).open)
        assertEquals(setOf("seek to"), page.getValue(EnAppStrings.roomSeekTo).open)
        assertTrue(page.getValue(EnAppStrings.roomUndoSeek).undoAsked)
        assertEquals(1, page.getValue(EnAppStrings.roomAspectRatio).aspectSwitches)
        assertEquals(setOf("gestures"), page.getValue(EnAppStrings.roomGesturesPanelTitle).open)
    }

    @Test
    fun theChaptersRowShowsTheCountAndOpensTheList() =
        RoomRig.drive(solo = false, television = false, withVideo = true, widthDp = widthDp, heightDp = heightDp, chapters = chapters) {
            driver.frames(20)
            ui.toggleControlPanel(true)
            driver.frames(40)
            val row = assertNotNull(
                spokenNodes().firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == EnAppStrings.roomChapters } == true },
                "The Chapters row shows",
            )
            assertTrue(row.config[SemanticsProperties.Text].any { it.text == "3" }, "The Chapters row shows the count")
            driver.click(EnAppStrings.roomChapters)
            driver.frames(30)
            assertTrue(driver.shows("Opening titles"), "The row opens the chapter list")
        }

    @Test
    fun withoutChaptersTheControlsPageHasNoChaptersRow() = portrait {
        driver.frames(20)
        ui.toggleControlPanel(true)
        driver.frames(40)
        assertTrue(driver.shows(EnAppStrings.roomTracks), "The Controls page shows")
        assertFalse(driver.shows(EnAppStrings.roomChapters), "No chapters, no Chapters row")
    }

    @Test
    fun underARemoteFocusEntersTheControlsPageAndReturnsToTheControlsKey() =
        RoomRig.drive(solo = false, television = true, withVideo = true, widthDp = widthDp, heightDp = heightDp) {
            settle()
            DesignHarness.onUiThread { ui.controlsFocus.requestFocus() }
            driver.frames(5)
            assertEquals(EnAppStrings.roomControlPanel, focusedName(), "The Controls key has focus")
            ui.toggleControlPanel(true)
            driver.frames(10)
            settle()
            val inside = assertNotNull(focused(), "Focus is somewhere")
            assertTrue(boundsOf(inside).top.dp() > stageBottomDp, "Focus entered the page, at ${focusedName()}")
            ui.back()
            driver.frames(10)
            settle()
            assertFalse(ui.controlPanel.value, "Back closes the page")
            assertEquals(EnAppStrings.roomControlPanel, focusedName(), "Focus returns to the Controls key")
        }

    @Test
    fun underARemoteFocusEntersTheMorePageAndReturnsToItsTab() =
        RoomRig.drive(solo = false, television = true, withVideo = true, widthDp = widthDp, heightDp = heightDp) {
            settle()
            ui.toggleMorePage(true)
            driver.frames(10)
            settle()
            val inside = assertNotNull(focused(), "Focus is somewhere")
            assertTrue(boundsOf(inside).top.dp() > stageBottomDp + 48f, "Focus entered the More page, at ${focusedName()}")
            ui.back()
            driver.frames(10)
            settle()
            assertEquals(EnAppStrings.roomRailMore, focusedName(), "Focus returns to the More tab")
        }

    /** Under a hardware keyboard, focus on the chat field shows chat, as a tap on it does. */
    @Test
    fun underAKeyboardFocusOnTheChatFieldShowsChat() =
        RoomRig.drive(solo = false, television = true, withVideo = true, widthDp = widthDp, heightDp = heightDp) {
            settle()
            ui.toggleRoomPreferences(true)
            driver.frames(10)
            settle()
            DesignHarness.onUiThread { ui.chatFocus.requestFocus() }
            driver.frames(20)
            assertEquals(EnAppStrings.roomChatInput, focusedName(), "The chat field has focus")
            assertFalse(ui.anyPageOpen, "Focus on the chat field shows chat")
        }

    /* The stage (#241). */

    /** The controls and texts of the stage that a screen reader meets, without the ones that hold others. */
    private fun RoomRig.Room.stageParts(): List<Pair<String, Rect>> {
        val parts = spokenNodes().filter { node ->
            val b = boundsOf(node)
            b.width > 0f && b.height > 0f && b.bottom.dp() <= stageBottomDp + 0.5f &&
                (SemanticsActions.OnClick in node.config || SemanticsProperties.Text in node.config ||
                    SemanticsActions.SetProgress in node.config)
        }.map { (nameOf(it) ?: "unnamed") to boundsOf(it) }
        return parts.filter { (_, outer) ->
            parts.none { (_, inner) -> inner != outer && outer.contains(inner.topLeft) && outer.contains(inner.bottomRight - Offset(1f, 1f)) }
        }
    }

    @Test
    fun theStagePartsDoNotOverlap() {
        for ((width, height) in listOf(390 to 844, 360 to 780)) for (custom in listOf(false, true)) withPref(CUSTOM_SEEK_FRONT, custom) {
            RoomRig.drive(solo = false, television = false, withVideo = true, widthDp = width, heightDp = height, canHoldLandscape = true) {
                viewmodel.playerManager.timeFullMillis.value = 1_500_000L
                driver.frames(30)
                val parts = stageParts()
                println("STAGE ${width}x$height custom=$custom: " + parts.joinToString { (name, b) -> "$name ${b.left.dp()},${b.top.dp()},${b.right.dp()},${b.bottom.dp()}" })
                for (i in parts.indices) for (j in i + 1 until parts.size) {
                    val (a, ab) = parts[i]
                    val (b, bb) = parts[j]
                    assertFalse(ab.overlaps(bb), "At $width by $height dp (custom skip $custom), $a overlaps $b")
                }
                // The row: the play key and every key on its center line, from start to end.
                val play = boundsOf(assertNotNull(described(EnAppStrings.roomPlay), "The play key exists"))
                val keys = allNodes().filter { SemanticsActions.OnClick in it.config }.map(::boundsOf)
                    .filter { abs(it.center.y - play.center.y) < 2f && it.bottom.dp() <= stageBottomDp }
                    .distinct().sortedBy { it.left }
                assertEquals(if (custom) 4 else 3, keys.size, "The keys of the row: $keys")
                assertEquals(play, keys[1], "The play key is second in the row")
                for ((left, right) in keys.zipWithNext()) {
                    val gap = (right.left - left.right).dp()
                    assertTrue(abs(gap - 12f) < 0.6f, "The keys of the row are 12dp apart, not $gap dp")
                    assertTrue(abs(left.center.y - right.center.y) < 1f, "The keys of the row share one center line")
                }
                assertTrue(keys.first().left >= 0f && keys.last().right <= width * 2f, "The row fits the width")
            }
        }
    }

    @Test
    fun theProgressLineHasNoSemanticsNode() = portrait {
        viewmodel.playerManager.timeFullMillis.value = 1_500_000L
        viewmodel.playerManager.timeCurrentMillis.value = 500_000L
        ui.visibleHUD.value = false
        driver.frames(40)
        val line = allNodes().filter { node ->
            val b = boundsOf(node)
            b.height.dp() <= 2.5f && abs(b.bottom.dp() - stageBottomDp) < 1f
        }
        assertTrue(line.isEmpty(), "No semantics node sits on the line: ${line.map { nameOf(it) }}")
    }

    /* The full-screen key (#244, #249). */

    @Test
    fun theFullScreenKeyShowsOnlyWhereTheAppCanTurnTheScreen() {
        withPref(ROOM_ORIENTATION, RoomOrientation.Portrait.name) {
            RoomRig.drive(solo = false, television = false, withVideo = true, widthDp = widthDp, heightDp = heightDp, canHoldLandscape = true) {
                driver.frames(20)
                val key = assertNotNull(described(EnAppStrings.roomFullScreen), "A phone shows the full-screen key")
                assertTrue(boundsOf(key).bottom.dp() <= stageBottomDp + 0.5f, "The key sits in the stage")
                press(EnAppStrings.roomFullScreen)
                assertTrue(ui.orientationSwitched, "The key switches the portrait room to landscape")
            }
        }
        RoomRig.drive(solo = false, television = false, withVideo = true, widthDp = widthDp, heightDp = heightDp, canHoldLandscape = false) {
            driver.frames(20)
            assertNull(described(EnAppStrings.roomFullScreen), "The desktop shows no full-screen key")
        }
    }

    @Test
    fun thePortraitRoomShowsTheKeyBeforeAFileLoads() =
        RoomRig.drive(solo = false, television = false, withVideo = false, widthDp = widthDp, heightDp = heightDp, canHoldLandscape = true) {
            driver.frames(20)
            val key = assertNotNull(described(EnAppStrings.roomFullScreen), "A room with no file still shows the key")
            assertTrue(boundsOf(key).bottom.dp() <= stageBottomDp + 0.5f, "The key sits in the stage")
        }

    @Test
    fun theLandscapeRoomShowsTheKeyWithoutASwitch() {
        RoomRig.drive(solo = false, television = false, withVideo = true, widthDp = 844, heightDp = 390, canHoldLandscape = true) {
            driver.frames(20)
            val key = boundsOf(assertNotNull(described(EnAppStrings.roomFullScreenExit), "The landscape room shows the key"))
            val controls = boundsOf(assertNotNull(described(EnAppStrings.roomControlPanel)))
            val add = boundsOf(assertNotNull(described(EnAppStrings.roomButtonDescAdd)))
            assertTrue(key.right <= controls.left + 1f, "The key sits before the Controls key")
            assertTrue(controls.right <= add.left + 1f, "The Controls key sits before Add Media")
            press(EnAppStrings.roomFullScreenExit)
            assertTrue(ui.orientationSwitched, "With the Landscape choice, the key switches to the portrait room")
        }
        RoomRig.drive(solo = false, television = false, withVideo = true, widthDp = 844, heightDp = 390, canHoldLandscape = false) {
            driver.frames(20)
            assertNull(described(EnAppStrings.roomFullScreenExit), "The desktop shows no key in the landscape room")
        }
    }

    @Test
    fun aRoomThatFollowsTheDeviceShowsTheLockKey() = withPref(ROOM_ORIENTATION, RoomOrientation.Auto.name) {
        RoomRig.drive(solo = false, television = false, withVideo = true, widthDp = 844, heightDp = 390, canHoldLandscape = true) {
            driver.frames(20)
            assertNull(described(EnAppStrings.roomFullScreenExit), "A room that follows the device has no full screen to exit")
            press(EnAppStrings.roomRotationLock)
            assertTrue(ui.orientationSwitched, "The lock key holds the room in landscape")
            press(EnAppStrings.roomRotationUnlock)
            assertFalse(ui.orientationSwitched, "The same key gives the room back to the device")
        }
    }

    /* Renders, for a look by eye. A render fails on an unnamed control. */

    /**
     * The portrait room at two phone widths: with the controls showing and hidden, and before a
     * file loads. Solo mode renders at one width.
     */
    @Test
    fun rendersThePortraitRoom() {
        for ((width, height) in listOf(390 to 844, 360 to 780)) {
            for (controls in listOf(true, false)) {
                RoomRig.render(
                    name = if (controls) "portrait-room-controls" else "portrait-room-watching",
                    widthDp = width,
                    heightDp = height,
                    solo = false,
                    withVideo = true,
                    setup = { room ->
                        fill(room)
                        room.uiState.visibleHUD.value = controls
                    },
                ) { RoomScreenUI(LocalRoomViewmodel.current) }
            }
            withPref(CUSTOM_SEEK_FRONT, true) {
                RoomRig.render("portrait-room-custom-skip", width, height, solo = false, withVideo = true, canHoldLandscape = true, setup = ::fill) {
                    RoomScreenUI(LocalRoomViewmodel.current)
                }
            }
            RoomRig.render("portrait-room-no-video", width, height, solo = false, withVideo = false) {
                RoomScreenUI(LocalRoomViewmodel.current)
            }
        }
        RoomRig.render("portrait-room-solo", widthDp, heightDp, solo = true, withVideo = true) {
            RoomScreenUI(LocalRoomViewmodel.current)
        }
        // A 300dp soft keyboard: the tab row folds away, and the composer sits on the keyboard.
        RoomRig.render("portrait-room-keyboard", widthDp, heightDp, solo = false, withVideo = true, keyboardPx = 600, setup = { room ->
            fill(room)
            room.uiState.msg.value = "starting in five"
        }) { RoomScreenUI(LocalRoomViewmodel.current) }
        // A notice sits under the stage while the controls show, and at the top of the stage while they hide.
        for (controls in listOf(true, false)) {
            RoomRig.render(if (controls) "portrait-room-notice" else "portrait-room-notice-watching", widthDp, heightDp, solo = false, withVideo = true, setup = { room ->
                fill(room)
                room.uiState.visibleHUD.value = controls
                room.dispatchOSD { "Sam paused at 08:20" }
            }) { RoomScreenUI(LocalRoomViewmodel.current) }
        }
    }

    /** Each page in the main box, the GIF drawer on both types, and the Controls page with chapters. */
    @Test
    fun rendersEachPage() = withPref(GIF_PANEL_SOURCE, "FAVORITES") {
        val pages = linkedMapOf<String, (RoomViewmodel) -> Unit>(
            "people" to { it.uiState.toggleUserInfo(true) },
            "playlist" to { it.uiState.toggleSharedPlaylist(true) },
            "preferences" to { it.uiState.toggleRoomPreferences(true) },
            "tracks" to { it.uiState.toggleTracks(true) },
            "gestures" to { it.uiState.toggleGestures(true) },
            "seek-to" to { it.uiState.toggleSeekTo(true) },
            "add-media" to { it.uiState.toggleAddMedia(true) },
            "controls" to { it.uiState.toggleControlPanel(true) },
            "more" to { it.uiState.toggleMorePage(true) },
        )
        for ((page, open) in pages) {
            RoomRig.render("portrait-room-page-$page", widthDp, heightDp, solo = false, withVideo = true, setup = { room ->
                fill(room)
                room.seeks.add(60_000L to 120_000L)
                open(room)
            }) { RoomScreenUI(LocalRoomViewmodel.current) }
        }
        RoomRig.render("portrait-room-page-controls-chapters", widthDp, heightDp, solo = false, withVideo = true, chapters = chapters, setup = { room ->
            fill(room)
            room.uiState.toggleControlPanel(true)
        }) { RoomScreenUI(LocalRoomViewmodel.current) }
        for (type in listOf("GIF", "STICKER")) withPref(GIF_PANEL_TYPE, type) {
            RoomRig.render("portrait-room-page-gifs-${type.lowercase()}", widthDp, heightDp, solo = false, withVideo = true, setup = { room ->
                fill(room)
                room.uiState.toggleGifPanel(true)
            }) { RoomScreenUI(LocalRoomViewmodel.current) }
        }
    }

    /** The composer alone, on the room palette: empty, with text, with focus, and as the GIF search. */
    @Test
    fun rendersTheComposer() {
        val states = linkedMapOf<String, (RoomViewmodel) -> Unit>(
            "empty" to { },
            "text" to { it.uiState.msg.value = "starting in five" },
            "focus" to { },
            "gifs" to { it.uiState.gifPanelVisible.value = true },
            "stickers" to { it.uiState.gifPanelVisible.value = true },
        )
        for ((state, setup) in states) withPref(GIF_PANEL_TYPE, if (state == "stickers") "STICKER" else "GIF") {
            RoomRig.render("portrait-room-composer-$state", widthDp, heightDp = 54, solo = false, overVideo = true, setup = setup) {
                RoomChatInput(Modifier.fillMaxWidth())
                if (state == "focus") {
                    val room = LocalRoomViewmodel.current
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        withFrameNanos { }
                        runCatching { room.uiState.chatFocus.requestFocus() }
                    }
                }
            }
        }
    }

    /** The stage with the full-screen key, as on a phone, and the landscape room with its exit key. */
    @Test
    fun rendersTheFullScreenKey() {
        RoomRig.render("portrait-room-full-screen-key", widthDp, heightDp, solo = false, withVideo = true, canHoldLandscape = true, setup = ::fill) {
            RoomScreenUI(LocalRoomViewmodel.current)
        }
        RoomRig.render("landscape-room-full-screen-key", 844, 390, solo = false, withVideo = true, canHoldLandscape = true, setup = ::fill) {
            RoomScreenUI(LocalRoomViewmodel.current)
        }
    }

    /* Notices under the video area. */

    /** The top and bottom of the first node that shows [text], in dp. */
    private fun RoomRig.Room.spanOf(text: String): ClosedFloatingPointRange<Float> {
        val node = assertNotNull(
            allNodes().firstOrNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { text in it.text } == true },
            "\"$text\" is on screen",
        )
        return boundsOf(node).let { it.top.dp()..it.bottom.dp() }
    }

    @Test
    fun aNoticeUnderTheVideoAreaPushesChatDown() = portrait {
        viewmodel.session.messageSequence.value = chat()
        driver.frames(30)
        val before = spanOf("Mia joined the room").start
        DesignHarness.onUiThread { viewmodel.notices.post("Sam paused at 08:20", holdMs = 600_000L) }
        driver.frames(60)
        val notice = spanOf("Sam paused at 08:20")
        val pushed = spanOf("Mia joined the room").start
        assertTrue(notice.start > stageBottomDp, "With the controls up, the notice sits under the video area, at ${notice.start} dp")
        assertTrue(pushed >= notice.endInclusive, "The first chat line starts under the notice: $pushed dp against ${notice.endInclusive} dp")
        assertTrue(pushed > before, "The notice pushed chat down from $before dp to $pushed dp")
        // With the controls hidden, the notice sits in the video area, and chat goes back.
        ui.visibleHUD.value = false
        driver.frames(60)
        assertTrue(spanOf("Sam paused at 08:20").endInclusive <= stageBottomDp, "With the controls hidden, the notice sits in the video area")
        assertEquals(before, spanOf("Mia joined the room").start, 0.5f, "Chat is back where it was")
    }
}
