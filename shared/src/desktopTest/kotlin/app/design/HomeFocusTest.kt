package app.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalPlatformWindowInsets
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import androidx.compose.ui.platform.PlatformWindowInsetsProviderNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
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
 * A window that changes size must never replace a text field of the home form. A replaced field
 * loses its focus, and on Android a lost focus closes the keyboard.
 *
 * The resize tests change the size of one live scene and check that the focused field keeps its
 * focus, its text and its caret, and takes more typing without another tap. The sizes cross
 * every threshold the form has: the split into two columns, and the short tier that puts
 * username and room in one row. The keyboard tests give the window a keyboard inset instead, the way an edge-to-edge
 * Android window and an iOS window meet their keyboards: nothing resizes, the bottom is covered.
 *
 * The insets come from Compose internals (`InternalComposeUiApi`), the only door the harness has
 * to a keyboard. A Compose upgrade that moves them breaks this file at compile time, not the app.
 *
 * What surrounds focus is checked here too: the IME actions, the background tap that closes the
 * keyboard, and that a screen reader still reaches the form behind that tap.
 */
@OptIn(InternalComposeUiApi::class)
class HomeFocusTest {

    @Test
    fun anOpeningKeyboardKeepsFocusAndMovesNothing() {
        withHome(PHONE_WINDOW) { form ->
            form.tap(USERNAME)
            form.select(USERNAME, 2, 2)
            val username = form.bounds(USERNAME)
            val room = form.bounds(ROOM)
            val join = form.textBounds(JOIN)
            form.keyboard(KEYBOARD)
            assertTrue(form.isFocused(USERNAME), "username lost focus when the keyboard opened")
            assertEquals(TextRange(2, 2), form.selection(USERNAME), "username caret reset when the keyboard opened")
            assertEquals(username, form.bounds(USERNAME), "username moved when the keyboard opened")
            assertEquals(room, form.bounds(ROOM), "room name moved when the keyboard opened")
            assertEquals(join, form.textBounds(JOIN), "the join key moved when the keyboard opened")
            form.insert(USERNAME, MARK)
            assertEquals("yu${MARK}royami", form.text(USERNAME))
            form.keyboard(0.dp)
            assertTrue(form.isFocused(USERNAME), "username lost focus when the keyboard closed")
            assertEquals(username, form.bounds(USERNAME), "username moved when the keyboard closed")
        }
    }

    @Test
    fun aFieldTheKeyboardWouldCoverScrollsAboveIt() {
        withHome(PHONE_WINDOW, config = CUSTOM) { form ->
            val keyboardTop = form.px(PHONE_WINDOW.height - KEYBOARD)
            form.tap(PASSWORD)
            assertTrue(form.bounds(PASSWORD).bottom > keyboardTop, "the test needs a field that the keyboard covers: ${form.bounds(PASSWORD)}")
            form.keyboard(KEYBOARD)
            assertTrue(form.isFocused(PASSWORD), "password lost focus when the keyboard opened")
            assertTrue(form.bounds(PASSWORD).bottom <= keyboardTop, "password is under the keyboard: ${form.bounds(PASSWORD)}, keyboard top at $keyboardTop")
            assertTrue(form.bounds(PASSWORD).top >= 0f, "password scrolled off the top: ${form.bounds(PASSWORD)}")
        }
    }

    @Test
    fun usernameKeepsFocusWhenThePhoneWindowShortens() {
        withHome(PHONE) { form ->
            assertTrue(form.identityIsStacked(), "$PHONE should stack username and room")
            form.assertEditorSurvives(USERNAME, PHONE, PHONE_SHORT) {
                assertTrue(identityIsPaired(), "$PHONE_SHORT should put username and room in one row")
            }
            assertTrue(form.identityIsStacked(), "restoring $PHONE should stack them again")
        }
    }

    @Test
    fun roomKeepsFocusWhenThePhoneWindowShortens() {
        withHome(PHONE) { form ->
            form.assertEditorSurvives(ROOM, PHONE, PHONE_SHORT) {
                assertTrue(identityIsPaired(), "$PHONE_SHORT should put username and room in one row")
            }
        }
    }

    /** A 300dp column is too narrow for two fields in a row, however short the window is. */
    @Test
    fun narrowPhoneKeepsIdentityStackedWhenShort() {
        withHome(NARROW_PHONE) { form ->
            form.assertEditorSurvives(USERNAME, NARROW_PHONE, NARROW_PHONE_SHORT) {
                assertTrue(identityIsStacked(), "$NARROW_PHONE_SHORT should keep username and room stacked")
            }
        }
    }

    @Test
    fun editorsKeepFocusWhenTheWideWindowShortens() {
        for (name in listOf(USERNAME, ROOM)) {
            withHome(WIDE) { form ->
                form.assertEditorSurvives(name, WIDE, WIDE_SHORT) {
                    assertTrue(isTwoColumns(), "$WIDE_SHORT should still be two columns")
                    assertTrue(identityIsPaired(), "$WIDE_SHORT should put username and room in one row")
                }
            }
        }
    }

    @Test
    fun editorsKeepFocusWhenTheFormSplitsIntoTwoColumns() {
        for (name in listOf(USERNAME, ROOM)) {
            withHome(NARROW_TALL) { form ->
                assertFalse(form.isTwoColumns(), "$NARROW_TALL should be one column")
                form.assertEditorSurvives(name, NARROW_TALL, WIDE) {
                    assertTrue(isTwoColumns(), "$WIDE should be two columns")
                }
                assertFalse(form.isTwoColumns(), "restoring $NARROW_TALL should be one column again")
            }
        }
    }

    @Test
    fun customServerEditorsKeepFocusThroughEveryRearrangement() {
        for (name in listOf(ADDRESS, PORT, PASSWORD)) {
            withHome(WIDE, config = CUSTOM) { form ->
                assertEquals("192.168.1.20", form.text(ADDRESS))
                assertEquals("8999", form.text(PORT))
                assertEquals("secret", form.text(PASSWORD))
                form.assertEditorSurvives(name, WIDE, WIDE_SHORT) {
                    assertTrue(isTwoColumns() && identityIsPaired(), "$WIDE_SHORT should rearrange the wide form")
                }
            }
            withHome(NARROW_TALL, config = CUSTOM) { form ->
                form.assertEditorSurvives(name, NARROW_TALL, WIDE) {
                    assertTrue(isTwoColumns(), "$WIDE should be two columns")
                }
            }
        }
    }

    @Test
    fun largeTextPhoneKeepsFocusWhenTheWindowShortens() {
        withHome(LARGE_TEXT_PHONE, fontScale = 1.3f) { form ->
            form.assertEditorSurvives(USERNAME, LARGE_TEXT_PHONE, LARGE_TEXT_SHORT) {
                assertTrue(identityIsPaired(), "$LARGE_TEXT_SHORT at 1.3 should put username and room in one row")
            }
        }
    }

    /** A window dragged shorter and taller again, through the height where the identity row forms. */
    @Test
    fun aWindowDraggedAcrossTheShortTierNeverMovesFocusOrTheCaret() {
        val heights = listOf(568, 540, 500, 460, 430, 400, 380, 360, 330, 360, 380, 430, 500, 568)
        withHome(PHONE) { form ->
            form.tap(USERNAME)
            form.replace(USERNAME, "caret")
            form.select(USERNAME, 2, 2)
            var sawPaired = false
            var sawStacked = false
            for (height in heights) {
                form.resize(DpSize(PHONE.width, height.dp))
                assertTrue(form.isFocused(USERNAME), "username lost focus at height $height")
                assertFalse(form.isFocused(ROOM), "focus jumped to room at height $height")
                assertEquals("caret", form.text(USERNAME), "username text reset at height $height")
                assertEquals(TextRange(2, 2), form.selection(USERNAME), "username caret reset at height $height")
                if (form.identityIsPaired()) sawPaired = true
                if (form.identityIsStacked()) sawStacked = true
            }
            assertTrue(sawPaired && sawStacked, "the series never crossed the short tier, so it proved nothing")
            form.insert(USERNAME, "X")
            assertEquals("caXret", form.text(USERNAME))
        }
    }

    /** The background tap that closes the keyboard must not hide the form it sits behind. */
    @Test
    fun aScreenReaderReachesTheFieldsAndTheJoinKey() {
        withHome(PHONE) { form ->
            val nodes = form.screenReaderNodes()
            for (value in listOf("yuroyami", "movie-night")) {
                assertTrue(
                    nodes.any { it.config.getOrNull(SemanticsProperties.EditableText)?.text == value },
                    "no editable '$value' in the merged tree:\n${form.describe(nodes)}",
                )
            }
            assertTrue(
                nodes.any { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == JOIN } == true },
                "no '$JOIN' in the merged tree:\n${form.describe(nodes)}",
            )
        }
    }

    @Test
    fun invitePasteAfterResizeStillFillsTheForm() {
        withHome(WIDE, config = CUSTOM) { form ->
            form.resize(WIDE_SHORT)
            form.tap(ROOM)
            form.replace(ROOM, InviteLink.build(JoinConfig(room = "invite-room", ip = "10.0.0.8", port = 1234, pw = "hunter2")))
            assertEquals("invite-room", form.text(ROOM))
            assertEquals("10.0.0.8", form.text(ADDRESS))
            assertEquals("1234", form.text(PORT))
            assertEquals("hunter2", form.text(PASSWORD))
        }
    }

    @Test
    fun nextDoneAndBackgroundTapStillMoveFocus() {
        withHome(PHONE) { form ->
            form.tap(USERNAME)
            assertTrue(form.isFocused(USERNAME))
            form.imeAction(USERNAME)
            assertTrue(form.isFocused(ROOM), "Next from username should focus room")
            assertFalse(form.isFocused(USERNAME))
            form.imeAction(ROOM)
            assertFalse(form.isFocused(ROOM), "Done on room should clear focus")
            form.tap(ROOM)
            assertTrue(form.isFocused(ROOM))
            form.tapBackground()
            assertFalse(form.isFocused(ROOM), "a background tap should clear room focus")
            assertFalse(form.isFocused(USERNAME))
        }
    }

    @Test
    fun validationErrorsStayCurrentAfterResize() {
        withHome(PHONE) { form ->
            form.tap(USERNAME)
            form.replace(USERNAME, "")
            form.tapJoin()
            assertTrue(form.hasText(USERNAME_EMPTY), "empty username should show its error before resize")
            form.resize(PHONE_SHORT)
            assertTrue(form.hasText(USERNAME_EMPTY), "the username error vanished after the resize")
            form.replace(USERNAME, "ok")
            assertFalse(form.hasText(USERNAME_EMPTY), "typing should clear the username error after resize")
        }
    }

    private fun withHome(
        size: DpSize,
        fontScale: Float = 1f,
        config: JoinConfig = OFFICIAL,
        screen: @Composable (HomeViewmodel) -> Unit = { HomeScreenUI(it) },
        block: (HomeForm) -> Unit,
    ) {
        DesignHarness.initDatastore()
        val previousTips = Preferences.NEVER_SHOW_TIPS.value()
        val previousJoin = Preferences.JOIN_CONFIG.value()
        saveJoin(config)
        val store = ViewModelStore()
        var scene: ImageComposeScene? = null
        try {
            val windowSize = mutableStateOf(size)
            val keyboardPx = mutableStateOf(0)
            val insets = KeyboardInsets { keyboardPx.value }
            val density = Density(2f, fontScale)
            val viewmodel = HomeViewmodel(mutableStateListOf(Screen.Home))
            store.put("home-focus", viewmodel)
            // The scene is as big as the biggest window a test asks for; the form lives in a
            // state-backed box inside it, so a resize never rebuilds the scene.
            val homeScene = DesignHarness.onUiThread {
                ImageComposeScene(
                    width = (SCENE.width.value * density.density).toInt(),
                    height = (SCENE.height.value * density.density).toInt(),
                    density = density,
                    coroutineContext = Dispatchers.Main.immediate,
                ) {
                    DesignHarness.Frame(TRINITY) {
                        CompositionLocalProvider(
                            LocalPlatformWindowInsets provides insets,
                            LocalInputModeManager provides TouchInput,
                        ) {
                            Box(Modifier.size(windowSize.value).then(WindowInsetsElement(insets))) {
                                screen(viewmodel)
                            }
                        }
                    }
                }
            }
            scene = homeScene
            val form = HomeForm(homeScene, windowSize, keyboardPx, density)
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
        assertEquals(json, Preferences.JOIN_CONFIG.value(), "the join config never reached the datastore")
        tries = 0
        while (!Preferences.NEVER_SHOW_TIPS.value() && tries++ < 50) Thread.sleep(20)
        assertTrue(Preferences.NEVER_SHOW_TIPS.value(), "the tips switch never reached the datastore")
    }

    /**
     * A phone has touch input. Under keyboard input the form gives username focus on arrival,
     * after a delay in real time, which would race every test here.
     */
    private object TouchInput : InputModeManager {
        override val inputMode: InputMode get() = InputMode.Touch
        override fun requestInputMode(inputMode: InputMode): Boolean = false
    }

    /** A window whose only inset is a soft keyboard. The harness has no insets of its own. */
    private class KeyboardInsets(heightPx: () -> Int) : PlatformWindowInsets {
        override val ime: PlatformInsets = PlatformInsets(getBottom = heightPx)
    }

    /**
     * Insets reach Compose on two roads: the `WindowInsets` getters read a composition local, and
     * the padding modifiers such as `imePadding` read a node in the layout tree. A platform window
     * feeds both, so the harness has to as well. This is the node.
     */
    private class WindowInsetsNode(var insets: PlatformWindowInsets) : PlatformWindowInsetsProviderNode() {
        override fun calculatePlatformInsets(ancestorWindowInsets: PlatformWindowInsets): PlatformWindowInsets = insets
    }

    private data class WindowInsetsElement(val insets: PlatformWindowInsets) : ModifierNodeElement<WindowInsetsNode>() {
        override fun create() = WindowInsetsNode(insets)
        override fun update(node: WindowInsetsNode) {
            node.insets = insets
        }
    }

    /** The live form, found and driven through its unmerged semantics tree. */
    private class HomeForm(
        private val scene: ImageComposeScene,
        private val windowSize: MutableState<DpSize>,
        private val keyboardPx: MutableState<Int>,
        private val density: Density,
    ) {
        private var frame = 0L

        fun settle(frames: Int = 30) {
            repeat(frames) {
                DesignHarness.onUiThread { scene.render(frame++ * 16_000_000L) }
            }
        }

        fun resize(size: DpSize) {
            DesignHarness.onUiThread { windowSize.value = size }
            settle()
        }

        /** Slides a keyboard of [height] over the bottom of the window; zero closes it. */
        fun keyboard(height: Dp) {
            DesignHarness.onUiThread { keyboardPx.value = px(height).toInt() }
            settle()
        }

        fun px(value: Dp): Float = value.value * density.density

        /** Where the node is laid out, clipped or not: a node under the keyboard still has a place. */
        private fun SemanticsNode.placed(): Rect = Rect(positionInRoot, size.toSize())

        fun bounds(name: String): Rect = field(name).placed()

        fun textBounds(value: String): Rect = assertNotNull(textNode(value), "No text '$value'. ${dump()}").placed()

        /** Focus [name] at [from], go to [to] and back, and require the same live editor throughout. */
        fun assertEditorSurvives(name: String, from: DpSize, to: DpSize, atTarget: HomeForm.() -> Unit = {}) {
            tap(name)
            val original = text(name)
            assertTrue(original.isNotEmpty(), "$name should start with saved text")
            val caret = (original.length / 2).coerceAtLeast(1)
            select(name, caret, caret)
            assertTrue(isFocused(name), "$name should be focused before the window changes")
            assertEquals(TextRange(caret, caret), selection(name))
            resize(to)
            atTarget()
            assertTrue(isFocused(name), "$name lost focus when the window went from $from to $to")
            assertEquals(original, text(name), "$name text reset at $to")
            assertEquals(TextRange(caret, caret), selection(name), "$name caret reset at $to")
            insert(name, MARK)
            val typed = original.substring(0, caret) + MARK + original.substring(caret)
            assertEquals(typed, text(name), "$name did not accept further input without another tap")
            resize(from)
            assertTrue(isFocused(name), "$name lost focus when the window went back to $from")
            assertEquals(typed, text(name), "$name text reset when the window went back")
            assertEquals(TextRange(caret + MARK.length, caret + MARK.length), selection(name), "$name caret reset when the window went back")
        }

        fun tap(name: String) {
            val bounds = field(name).boundsInRoot
            assertTrue(bounds.width > 0 && bounds.height > 0, "$name has no visible size: $bounds")
            press(bounds.center)
        }

        fun tapJoin() {
            val node = assertNotNull(textNode(JOIN), "No text '$JOIN'. ${dump()}")
            press(node.boundsInRoot.center)
        }

        fun tapBackground() = press(Offset(4f * density.density, 80f * density.density))

        private fun press(at: Offset) {
            val window = windowSize.value
            val visibleBottom = px(window.height) - keyboardPx.value
            assertTrue(at.x in 0f..px(window.width) && at.y in 0f..visibleBottom, "the press at $at misses the visible window ($window, keyboard ${keyboardPx.value}px)")
            DesignHarness.onUiThread {
                scene.sendPointerEvent(PointerEventType.Press, at)
                scene.sendPointerEvent(PointerEventType.Release, at)
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

        fun hasText(value: String): Boolean = textNode(value) != null

        /** Room name under username, with the same left edge. */
        fun identityIsStacked(): Boolean {
            val user = field(USERNAME).boundsInRoot
            val room = field(ROOM).boundsInRoot
            return room.top >= user.bottom && room.left == user.left
        }

        /** Room name beside username, on the same line. */
        fun identityIsPaired(): Boolean {
            val user = field(USERNAME).boundsInRoot
            val room = field(ROOM).boundsInRoot
            return room.left >= user.right && room.top == user.top
        }

        /** The join key sits in a second column, to the right of the identity fields. */
        fun isTwoColumns(): Boolean {
            val join = assertNotNull(textNode(JOIN), "No text '$JOIN'. ${dump()}").boundsInRoot
            return join.left > field(USERNAME).boundsInRoot.right
        }

        private fun textNode(value: String): SemanticsNode? = allNodes().firstOrNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true
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

        /** The merged tree, which is the one a screen reader reads. */
        fun screenReaderNodes(): List<SemanticsNode> = DesignHarness.onUiThread {
            scene.semanticsOwners.flatMap { listOf(it.rootSemanticsNode) + descendants(it.rootSemanticsNode) }
        }

        fun describe(nodes: List<SemanticsNode>): String = nodes.joinToString("\n") { node ->
            val desc = node.config.getOrNull(SemanticsProperties.ContentDescription)
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            val edit = node.config.getOrNull(SemanticsProperties.EditableText)?.text
            "desc=$desc text=$text edit=$edit bounds=${node.boundsInRoot}"
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
        const val MARK = "X"

        /** A whole phone window and its keyboard, for the tests that use a keyboard inset. */
        val PHONE_WINDOW = DpSize(360.dp, 640.dp)
        val KEYBOARD = 300.dp

        /** Windows with their system bars already taken off, as in HomeGolden. */
        val PHONE = DpSize(360.dp, 568.dp)
        val PHONE_SHORT = DpSize(360.dp, 330.dp)
        val NARROW_PHONE = DpSize(300.dp, 568.dp)
        val NARROW_PHONE_SHORT = DpSize(300.dp, 330.dp)
        val NARROW_TALL = DpSize(360.dp, 720.dp)
        val WIDE = DpSize(800.dp, 720.dp)
        val WIDE_SHORT = DpSize(800.dp, 312.dp)
        val LARGE_TEXT_PHONE = DpSize(412.dp, 867.dp)
        val LARGE_TEXT_SHORT = DpSize(412.dp, 500.dp)
        val SCENE = DpSize(800.dp, 900.dp)

        val OFFICIAL = JoinConfig(user = "yuroyami", room = "movie-night", ip = "syncplay.pl", port = 8997)
        val CUSTOM = JoinConfig(user = "yuroyami", room = "movie-night", ip = "192.168.1.20", port = 8999, pw = "secret")
    }
}
