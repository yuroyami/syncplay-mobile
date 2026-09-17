package app.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModelStore
import app.Screen
import app.home.HomeScreenUI
import app.home.HomeViewmodel
import app.home.InviteLink
import app.home.JoinConfig
import app.preferences.Preferences
import app.preferences.datastore
import app.preferences.prefKey
import app.preferences.set
import app.preferences.value
import app.theme.TRINITY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Keyboard padding shortens the home form. The editors must keep focus, text and caret across
 * that resize: replacing them is what dismisses the keyboard on some Android devices.
 */
class HomeFocusTest {

    @Test
    fun phoneWidthUsernameKeepsFocusWhenIdentityPairs() {
        withHome(PHONE_WIDTH, PHONE_TALL) { form ->
            assertFalse(form.identityIsPaired(), "360x$PHONE_TALL should stack the identity fields")
            form.assertEditorSurvivesResize(USERNAME, PHONE_WIDTH, PHONE_SHORT, PHONE_TALL) {
                assertTrue(identityIsPaired(), "360x$PHONE_SHORT should pair the identity fields")
            }
            assertFalse(form.identityIsPaired(), "restoring 360x$PHONE_TALL should stack again")
        }
    }

    @Test
    fun phoneWidthRoomKeepsFocusWhenIdentityPairs() {
        withHome(PHONE_WIDTH, PHONE_TALL) { form ->
            form.assertEditorSurvivesResize(ROOM, PHONE_WIDTH, PHONE_SHORT, PHONE_TALL) {
                assertTrue(identityIsPaired(), "360x$PHONE_SHORT should pair the identity fields")
            }
        }
    }

    @Test
    fun wideFormKeepsFocusWhenOuterColumnsRearrange() {
        withHome(WIDE_WIDTH, WIDE_TALL) { form ->
            assertFalse(form.identityIsPaired(), "800x$WIDE_TALL should stack identity in the tall two-column layout")
            form.assertEditorSurvivesResize(USERNAME, WIDE_WIDTH, WIDE_SHORT, WIDE_TALL) {
                assertTrue(identityIsPaired(), "800x$WIDE_SHORT should pair identity in the short two-column layout")
            }
            assertFalse(form.identityIsPaired(), "restoring 800x$WIDE_TALL should stack identity again")
        }
        withHome(WIDE_WIDTH, WIDE_TALL) { form ->
            form.assertEditorSurvivesResize(ROOM, WIDE_WIDTH, WIDE_SHORT, WIDE_TALL) {
                assertTrue(identityIsPaired(), "800x$WIDE_SHORT should pair identity in the short two-column layout")
            }
        }
    }

    @Test
    fun largeTextPhonePairsOnTheScaledBreakpoint() {
        withHome(LARGE_TEXT_WIDTH, LARGE_TEXT_TALL, fontScale = 1.3f) { form ->
            assertFalse(form.identityIsPaired(), "412x$LARGE_TEXT_TALL at 1.3 should stack")
            form.assertEditorSurvivesResize(USERNAME, LARGE_TEXT_WIDTH, LARGE_TEXT_SHORT, LARGE_TEXT_TALL) {
                assertTrue(identityIsPaired(), "412x$LARGE_TEXT_SHORT at 1.3 should pair")
            }
        }
    }

    @Test
    fun narrowAndLargeTextIdentityStaysStacked() {
        withHome(NARROW_WIDTH, PHONE_TALL) { form ->
            assertFalse(form.identityIsPaired())
            form.assertEditorSurvivesResize(USERNAME, NARROW_WIDTH, PHONE_SHORT, PHONE_TALL) {
                assertFalse(identityIsPaired(), "a 300dp column should keep identity stacked when short")
            }
        }
        withHome(PHONE_WIDTH, LARGE_TEXT_TALL, fontScale = 1.3f) { form ->
            assertFalse(form.identityIsPaired())
            form.assertEditorSurvivesResize(ROOM, PHONE_WIDTH, LARGE_TEXT_SHORT, LARGE_TEXT_TALL) {
                assertFalse(identityIsPaired(), "360dp at 1.3 should keep identity stacked when short")
            }
        }
    }

    @Test
    fun customServerEditorsKeepFocusAcrossWideRearrange() {
        for (name in listOf(ADDRESS, PORT, PASSWORD)) {
            withHome(WIDE_WIDTH, WIDE_TALL, config = CUSTOM) { form ->
                assertEquals("192.168.1.20", form.text(ADDRESS))
                assertEquals("8999", form.text(PORT))
                assertEquals("secret", form.text(PASSWORD))
                form.assertEditorSurvivesResize(name, WIDE_WIDTH, WIDE_SHORT, WIDE_TALL) {
                    assertTrue(identityIsPaired(), "800x$WIDE_SHORT should rearrange the wide form")
                }
                if (name != ADDRESS) assertEquals("192.168.1.20", form.text(ADDRESS))
                if (name != PORT) assertEquals("8999", form.text(PORT))
                if (name != PASSWORD) assertEquals("secret", form.text(PASSWORD))
            }
        }
    }

    @Test
    fun invitePasteAfterResizeStillFillsTheForm() {
        withHome(WIDE_WIDTH, WIDE_TALL, config = CUSTOM) { form ->
            form.resize(WIDE_WIDTH, WIDE_SHORT)
            form.tap(ROOM)
            form.replace(ROOM, InviteLink.build(JoinConfig(room = "invite-room", ip = "10.0.0.8", port = 1234, pw = "hunter2")))
            assertEquals("invite-room", form.text(ROOM))
            assertEquals("10.0.0.8", form.text(ADDRESS))
            assertEquals("1234", form.text(PORT))
            assertEquals("hunter2", form.text(PASSWORD))
        }
    }

    @Test
    fun imeHeightsDoNotResetFocusOrSelection() {
        val heights = listOf(PHONE_TALL, 540, 500, 460, 430, 400, PHONE_SHORT, 360, 330, 360, PHONE_SHORT, 430, 500, PHONE_TALL)
        withHome(PHONE_WIDTH, PHONE_TALL) { form ->
            form.tap(USERNAME)
            form.replace(USERNAME, "caret")
            form.select(USERNAME, 2, 2)
            var sawPaired = false
            var sawStacked = false
            for (height in heights) {
                form.resize(PHONE_WIDTH, height)
                assertTrue(form.isFocused(USERNAME), "username lost focus at ${PHONE_WIDTH}x$height")
                assertFalse(form.isFocused(ROOM), "focus jumped to room at ${PHONE_WIDTH}x$height")
                assertEquals("caret", form.text(USERNAME), "username text reset at ${PHONE_WIDTH}x$height")
                assertEquals(TextRange(2, 2), form.selection(USERNAME), "username caret reset at ${PHONE_WIDTH}x$height")
                if (form.identityIsPaired()) sawPaired = true else sawStacked = true
            }
            assertTrue(sawPaired && sawStacked, "the IME-like series never crossed the pairing threshold")
            form.insert(USERNAME, "X")
            assertEquals("caXret", form.text(USERNAME))
        }
    }

    @Test
    fun nextDoneAndBackgroundTapStillMoveFocus() {
        withHome(PHONE_WIDTH, PHONE_TALL) { form ->
            form.tap(USERNAME)
            assertTrue(form.isFocused(USERNAME))
            form.imeAction(USERNAME)
            assertTrue(form.isFocused(ROOM), "Next from username should focus room")
            assertFalse(form.isFocused(USERNAME))
            form.imeAction(ROOM)
            assertFalse(form.isFocused(ROOM), "Done on room should clear focus")
            assertFalse(form.isFocused(USERNAME))
            form.tap(ROOM)
            assertTrue(form.isFocused(ROOM))
            form.tapBackground()
            assertFalse(form.isFocused(ROOM), "a background tap should clear room focus")
            assertFalse(form.isFocused(USERNAME))
        }
    }

    @Test
    fun validationErrorsStayCurrentAfterLayoutChange() {
        withHome(PHONE_WIDTH, PHONE_TALL) { form ->
            form.tap(USERNAME)
            form.replace(USERNAME, "")
            form.tapJoin()
            assertTrue(form.hasText(USERNAME_EMPTY), "empty username should show its error before resize")
            form.resize(PHONE_WIDTH, PHONE_SHORT)
            assertTrue(form.hasText(USERNAME_EMPTY), "the username error vanished after the layout changed")
            form.replace(USERNAME, "ok")
            assertFalse(form.hasText(USERNAME_EMPTY), "typing should clear the username error after resize")
            form.replace(ROOM, "")
            form.tapJoin()
            assertTrue(form.hasText(ROOM_EMPTY), "empty room should show its error after the layout changed")
        }
    }

    private fun withHome(
        widthDp: Int,
        heightDp: Int,
        fontScale: Float = 1f,
        config: JoinConfig = OFFICIAL,
        block: (HomeForm) -> Unit,
    ) {
        DesignHarness.initDatastore()
        val previousTips = Preferences.NEVER_SHOW_TIPS.value()
        val previousJoin = Preferences.JOIN_CONFIG.value()
        saveJoin(config)
        val store = ViewModelStore()
        var scene: ImageComposeScene? = null
        try {
            val windowSize = mutableStateOf(DpSize(widthDp.dp, heightDp.dp))
            val density = Density(2f, fontScale)
            val viewmodel = HomeViewmodel(mutableStateListOf(Screen.Home))
            store.put("home-focus", viewmodel)
            val homeScene = DesignHarness.onUiThread {
                ImageComposeScene(
                    width = (widthDp * density.density).toInt(),
                    height = (heightDp * density.density).toInt(),
                    density = density,
                    coroutineContext = Dispatchers.Main.immediate,
                ) {
                    DesignHarness.Frame(TRINITY) {
                        val size = windowSize.value
                        Box(Modifier.size(size)) {
                            HomeScreenUI(viewmodel)
                        }
                    }
                }
            }
            scene = homeScene
            val form = HomeForm(homeScene, windowSize, density)
            form.settle()
            Thread.sleep(200)
            form.settle()
            block(form)
        } finally {
            try {
                DesignHarness.onUiThread { scene?.close() }
            } finally {
                store.clear()
                runBlocking {
                    Preferences.NEVER_SHOW_TIPS.set(previousTips)
                    if (previousJoin == null) {
                        datastore.edit { it.remove(Preferences.JOIN_CONFIG.prefKey()) }
                    } else {
                        Preferences.JOIN_CONFIG.set(previousJoin)
                    }
                }
            }
        }
    }

    private fun saveJoin(config: JoinConfig) = runBlocking {
        Preferences.NEVER_SHOW_TIPS.set(true)
        val json = Json.encodeToString(config)
        Preferences.JOIN_CONFIG.set(json)
        var tries = 0
        while (Preferences.JOIN_CONFIG.value() != json && tries++ < 50) Thread.sleep(20)
        tries = 0
        while (!Preferences.NEVER_SHOW_TIPS.value() && tries++ < 50) Thread.sleep(20)
    }

    private class HomeForm(
        private val scene: ImageComposeScene,
        private val windowSize: MutableState<DpSize>,
        private val density: Density,
    ) {
        private var frame = 0L

        fun settle(frames: Int = 30) {
            repeat(frames) {
                DesignHarness.onUiThread { scene.render(frame++ * 16_000_000L) }
            }
        }

        fun resize(widthDp: Int, heightDp: Int) {
            DesignHarness.onUiThread { windowSize.value = DpSize(widthDp.dp, heightDp.dp) }
            settle()
        }

        fun assertEditorSurvivesResize(
            name: String,
            widthDp: Int,
            shortHeightDp: Int,
            tallHeightDp: Int,
            onShort: HomeForm.() -> Unit = {},
        ) {
            tap(name)
            val original = text(name)
            assertTrue(original.isNotEmpty(), "$name should start with saved text")
            val caret = (original.length / 2).coerceAtLeast(1)
            select(name, caret, caret)
            assertTrue(isFocused(name), "$name should be focused before the form shortens")
            assertEquals(TextRange(caret, caret), selection(name))
            resize(widthDp, shortHeightDp)
            onShort()
            assertTrue(isFocused(name), "$name lost focus when the form shortened to ${widthDp}x$shortHeightDp")
            assertEquals(original, text(name), "$name text reset at ${widthDp}x$shortHeightDp")
            assertEquals(TextRange(caret, caret), selection(name), "$name caret reset at ${widthDp}x$shortHeightDp")
            insert(name, MARK)
            val typed = original.substring(0, caret) + MARK + original.substring(caret)
            assertEquals(typed, text(name), "$name did not accept further input without another tap")
            resize(widthDp, tallHeightDp)
            assertTrue(isFocused(name), "$name lost focus when the form grew back to ${widthDp}x$tallHeightDp")
            assertEquals(typed, text(name), "$name text reset when the form grew back")
            assertEquals(TextRange(caret + MARK.length, caret + MARK.length), selection(name), "$name caret reset when the form grew back")
        }

        fun tap(name: String) {
            val bounds = field(name).boundsInRoot
            assertTrue(bounds.width > 0 && bounds.height > 0, "$name has no visible size: $bounds")
            DesignHarness.onUiThread {
                scene.sendPointerEvent(PointerEventType.Press, bounds.center)
                scene.sendPointerEvent(PointerEventType.Release, bounds.center)
            }
            settle()
        }

        fun tapJoin() = tapText(JOIN)

        fun tapBackground() {
            val x = 4f * density.density
            val y = 80f * density.density
            DesignHarness.onUiThread {
                scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
                scene.sendPointerEvent(PointerEventType.Release, Offset(x, y))
            }
            settle()
        }

        fun tapText(label: String) {
            val node = assertNotNull(
                allNodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true },
                "No text '$label'. ${dump()}",
            )
            val bounds = node.boundsInRoot
            DesignHarness.onUiThread {
                scene.sendPointerEvent(PointerEventType.Press, bounds.center)
                scene.sendPointerEvent(PointerEventType.Release, bounds.center)
            }
            settle()
        }

        fun replace(name: String, value: String) {
            val action = assertNotNull(editor(name).config.getOrNull(SemanticsActions.SetText)?.action, "$name has no SetText. ${dump()}")
            DesignHarness.onUiThread { action.invoke(AnnotatedString(value)) }
            settle()
        }

        fun insert(name: String, value: String) {
            val action = assertNotNull(
                editor(name).config.getOrNull(SemanticsActions.InsertTextAtCursor)?.action,
                "$name has no InsertTextAtCursor. ${dump()}",
            )
            DesignHarness.onUiThread { action.invoke(AnnotatedString(value)) }
            settle()
        }

        fun select(name: String, start: Int, end: Int) {
            val action = assertNotNull(
                editor(name).config.getOrNull(SemanticsActions.SetSelection)?.action,
                "$name has no SetSelection. ${dump()}",
            )
            DesignHarness.onUiThread { action.invoke(start, end, false) }
            settle()
        }

        fun imeAction(name: String) {
            val action = assertNotNull(
                editor(name).config.getOrNull(SemanticsActions.OnImeAction)?.action,
                "$name has no OnImeAction. ${dump()}",
            )
            DesignHarness.onUiThread { action.invoke() }
            settle()
        }

        fun text(name: String): String = editor(name).config.getOrNull(SemanticsProperties.EditableText)?.text
            ?: error("$name has no editable text. ${dump()}")

        fun selection(name: String): TextRange = editor(name).config.getOrNull(SemanticsProperties.TextSelectionRange)
            ?: error("$name has no selection. ${dump()}")

        fun isFocused(name: String): Boolean = editor(name).config.getOrNull(SemanticsProperties.Focused) == true

        fun hasText(value: String): Boolean = allNodes().any { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true
        }

        fun identityIsPaired(): Boolean {
            val user = field(USERNAME).boundsInRoot
            val room = field(ROOM).boundsInRoot
            val overlap = minOf(user.bottom, room.bottom) - maxOf(user.top, room.top)
            return overlap > minOf(user.height, room.height) * 0.5f && room.left > user.center.x
        }

        private fun field(name: String): SemanticsNode = assertNotNull(
            allNodes().firstOrNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(name) == true },
            "No field '$name'. ${dump()}",
        )

        private fun editor(name: String): SemanticsNode {
            val root = field(name)
            return (listOf(root) + descendants(root)).firstOrNull { it.config.getOrNull(SemanticsProperties.EditableText) != null }
                ?: error("$name has no editor. ${dump()}")
        }

        private fun descendants(node: SemanticsNode): List<SemanticsNode> =
            node.children.flatMap { listOf(it) + descendants(it) }

        private fun allNodes(): List<SemanticsNode> = DesignHarness.onUiThread {
            scene.semanticsOwners.flatMap { listOf(it.unmergedRootSemanticsNode) + descendants(it.unmergedRootSemanticsNode) }
        }

        private fun dump(): String = allNodes().joinToString("\n") { node ->
            val desc = node.config.getOrNull(SemanticsProperties.ContentDescription)
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            val edit = node.config.getOrNull(SemanticsProperties.EditableText)?.text
            val focused = node.config.getOrNull(SemanticsProperties.Focused)
            "desc=$desc text=$text edit=$edit focused=$focused bounds=${node.boundsInRoot}"
        }
    }

    private companion object {
        const val USERNAME = "Username"
        const val ROOM = "Room name"
        const val ADDRESS = "IP Address"
        const val PORT = "Port"
        const val PASSWORD = "Password (if any)"
        const val JOIN = "Join room"
        const val USERNAME_EMPTY = "Username shouldn't be empty"
        const val ROOM_EMPTY = "Roomname shouldn't be empty"
        const val MARK = "X"
        const val PHONE_WIDTH = 360
        const val PHONE_TALL = 568
        const val PHONE_SHORT = 400
        const val NARROW_WIDTH = 300
        const val WIDE_WIDTH = 800
        const val WIDE_TALL = 720
        const val WIDE_SHORT = 312
        const val LARGE_TEXT_WIDTH = 412
        const val LARGE_TEXT_TALL = 867
        const val LARGE_TEXT_SHORT = 500
        val OFFICIAL = JoinConfig(user = "yuroyami", room = "movie-night", ip = "syncplay.pl", port = 8997)
        val CUSTOM = JoinConfig(user = "yuroyami", room = "movie-night", ip = "192.168.1.20", port = 8999, pw = "secret")
    }
}
