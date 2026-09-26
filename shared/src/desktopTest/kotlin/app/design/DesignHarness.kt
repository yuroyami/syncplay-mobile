package app.design

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.runtime.SideEffect
import app.uicomponents.LocalIsTelevision
import app.LocalGlobalViewmodel
import app.SyncplayViewmodel
import app.preferences.LocalPrefsState
import app.preferences.createDataStore
import app.preferences.datastore
import app.preferences.datastoreStateFlow
import app.preferences.resetPreferencesForTesting
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.LocalTheme
import app.i18n.EnAppStrings
import app.i18n.LocalAppStrings
import app.i18n.appStrings
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.theme.LocalPalette
import app.theme.Palette
import app.theme.SaveableTheme
import app.theme.TRINITY
import app.theme.DAYLIGHT
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlinx.coroutines.Dispatchers
import javax.swing.SwingUtilities
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Renders real composables headlessly and writes PNGs, so the design tests can measure them. It
 * provides the composition locals that a surface needs outside `AdamScreen`, the app's root
 * screen. Resource fonts do not resolve here, so the golden images judge layout and spacing, not
 * letterforms.
 */
object DesignHarness {

    val outDir: File = File(System.getenv("DESIGN_GOLDEN_OUT") ?: "build/design-goldens").also { it.mkdirs() }

    /** The output of one render: the PNG file, the content height in dp, and the text layouts. */
    data class Result(
        val file: File,
        val contentHeightDp: Int,
        val textLayouts: List<TextLayoutResult>,
        val unnamedControls: List<String> = emptyList(),
    ) {
        fun assertAllTextFits() {
            assertTrue(textLayouts.isNotEmpty(), "No text layouts in ${file.name}")
            for (layout in textLayouts) {
                // An empty label has nothing to clip; a value column with no value draws one.
                if (layout.layoutInput.text.text.isEmpty()) continue
                val label = "${file.name}: ${layout.layoutInput.text.text}"
                assertFalse(layout.multiParagraph.didExceedMaxLines, "Clipped lines: $label")
                for (line in 0 until layout.lineCount) {
                    assertFalse(layout.isLineEllipsized(line), "Truncated text: $label")
                    // Paragraph width may retain loose measurement constraints even when the
                    // text fits its final size. Check the actual line width, not hasVisualOverflow.
                    assertTrue(layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f, "Clipped width: $label")
                    assertTrue(layout.getLineBottom(line) <= layout.size.height + 1f, "Clipped height: $label")
                }
            }
        }
    }

    /**
     * The controls in the spoken tree (what a screen reader reads) that have no name. A control is
     * a node with a click action. Its name is a description or a text that is not blank. A role
     * does not count, because a role says what a control is, not what it does.
     */
    private fun unnamedControls(node: SemanticsNode): List<String> {
        val config = node.config
        val unnamed = config.getOrNull(SemanticsActions.OnClick) != null &&
            SemanticsProperties.HideFromAccessibility !in config &&
            (config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                listOfNotNull(config.getOrNull(SemanticsProperties.EditableText)?.text)).all { it.isBlank() }
        val here = if (unnamed) listOf("a ${config.getOrNull(SemanticsProperties.Role) ?: "control"} at ${node.positionInRoot}, size ${node.size}") else emptyList()
        return here + node.children.flatMap(::unnamedControls)
    }

    private fun textLayouts(node: SemanticsNode): List<TextLayoutResult> {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        node.children.forEach { layouts += textLayouts(it) }
        return layouts
    }

    private var harnessStore: DataStore<Preferences>? = null

    /**
     * Surfaces read preferences (the glass switch, the settings rows), so the harness owns a
     * throwaway datastore. A test that swaps the process-wide store (DatastoreRecoveryTest) leaves
     * its own behind, so every call puts the harness store back. It is created once: a second
     * store on the same file is not allowed.
     */
    @Synchronized
    fun initDatastore() {
        val store = harnessStore ?: run {
            val dir = File(System.getProperty("java.io.tmpdir"), "synkplay-design-harness").also { it.mkdirs() }
            File(dir, "harness.preferences_pb").delete()
            createDataStore { File(dir, "harness.preferences_pb").absolutePath }.also { harnessStore = it }
        }
        if (runCatching { datastore }.getOrNull() !== store) {
            datastore = store
            // The cached snapshot still reads the other store.
            resetPreferencesForTesting()
        }
        datastoreStateFlow.value
    }

    @Composable
    fun Frame(theme: SaveableTheme, overVideo: Boolean = false, language: String = "en", television: Boolean = false, content: @Composable () -> Unit) {
        val base = Palette.from(theme.dynamicScheme, theme)
        val pal = if (overVideo) base.overVideo() else base
        val prefs = datastoreStateFlow.collectAsState()
        val vm = remember { SyncplayViewmodel() }
        CompositionLocalProvider(
            LocalTheme provides theme,
            LocalPalette provides pal,
            LocalPrefsState provides prefs,
            LocalGlobalViewmodel provides vm,
            LocalAppStrings provides (appStrings[language] ?: EnAppStrings),
            // The app pins this in AdamScreen; the harness has to match or Arabic renders mirrored.
            LocalLayoutDirection provides LayoutDirection.Ltr,
            LocalIsTelevision provides television,
        ) {
            run {
                Box(Modifier.fillMaxSize().background(pal.ground)) { content() }
            }
        }
    }

    /** Runs [action] on the Swing event thread, the thread of Compose's callbacks. */
    internal fun <T> onUiThread(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return action()
        var result: kotlin.Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(action) }
        return result!!.getOrThrow()
    }

    /**
     * Composes [content] in a live scene and lets [drive] press keys against it. Focus belongs to the
     * composed tree, so a test can check what a remote can reach without an emulator. A television
     * runs in keyboard input mode, as a real one does. Anything else runs in touch mode.
     */
    fun drive(
        widthDp: Int,
        heightDp: Int = 600,
        television: Boolean = true,
        content: @Composable () -> Unit,
        drive: Driver.() -> Unit,
    ) {
        initDatastore()
        val density = Density(2f, 1f)
        val input = FixedInputMode(if (television) InputMode.Keyboard else InputMode.Touch)
        val driver = Driver()
        val scene = onUiThread {
            ImageComposeScene(
                width = (widthDp * density.density).toInt(),
                height = (heightDp * density.density).toInt(),
                density = density,
                coroutineContext = Dispatchers.Main.immediate,
            ) {
                Frame(TRINITY, television = television) {
                    CompositionLocalProvider(LocalInputModeManager provides input) {
                        val focusManager = LocalFocusManager.current
                        SideEffect { driver.focusManager = focusManager }
                        Box(Modifier.width(widthDp.dp)) { content() }
                    }
                }
            }
        }
        try {
            driver.scene = scene
            // Entrance effects and the first focus requests need frames before any key lands.
            driver.frames(20)
            driver.drive()
        } finally {
            onUiThread { scene.close() }
        }
    }

    /** Presses keys against a live scene and gives it frames to react, the way a real remote would. */
    class Driver {
        internal lateinit var scene: ImageComposeScene
        internal var focusManager: FocusManager? = null
        private var nanos = 0L

        fun frames(n: Int = 5) = repeat(n) {
            nanos += 16_000_000L
            onUiThread { scene.render(nanos) }
        }

        /**
         * One press: key down, a few frames, key up, a few more frames. A focus move runs in an
         * effect, so it needs those frames.
         */
        @OptIn(InternalComposeUiApi::class)
        fun press(key: Key, typed: Char? = null) {
            val codePoint = typed?.code ?: 0
            val taken = onUiThread { scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown, codePoint = codePoint)) }
            // What Android's root view does with a key nothing took: arrows move focus, Center enters.
            if (!taken) androidFocusDirection(key)?.let { direction -> onUiThread { focusManager?.moveFocus(direction) } }
            frames(3)
            onUiThread { scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp, codePoint = codePoint)) }
            frames(5)
        }

        /** One finger tap at [position], in scene pixels, then a few frames for the click to land. */
        fun tap(position: Offset) {
            onUiThread {
                scene.sendPointerEvent(PointerEventType.Press, position, type = PointerType.Touch)
                scene.sendPointerEvent(PointerEventType.Release, position, type = PointerType.Touch)
            }
            frames(3)
        }

        /** A mouse pointer moves to [position], in scene pixels, with no button down. */
        fun hover(position: Offset) {
            onUiThread { scene.sendPointerEvent(PointerEventType.Move, position, type = PointerType.Mouse) }
            frames(3)
        }

        /** A mouse press at [from], a drag in [steps] moves to [to], and a release there. */
        fun mouseDrag(from: Offset, to: Offset, steps: Int = 8) {
            hover(from)
            onUiThread { scene.sendPointerEvent(PointerEventType.Press, from, type = PointerType.Mouse, button = PointerButton.Primary) }
            frames(2)
            for (step in 1..steps) {
                val at = from + (to - from) * (step / steps.toFloat())
                onUiThread { scene.sendPointerEvent(PointerEventType.Move, at, type = PointerType.Mouse) }
                frames(1)
            }
            onUiThread { scene.sendPointerEvent(PointerEventType.Release, to, type = PointerType.Mouse, button = PointerButton.Primary) }
            frames(3)
        }
    }

    /** The mapping in Compose's Android `KeyEvent.toFocusDirection`, which the desktop scene lacks. */
    private fun androidFocusDirection(key: Key): FocusDirection? = when (key) {
        Key.DirectionUp -> FocusDirection.Up
        Key.DirectionDown -> FocusDirection.Down
        Key.DirectionLeft -> FocusDirection.Left
        Key.DirectionRight -> FocusDirection.Right
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> FocusDirection.Enter
        Key.Back, Key.Escape -> FocusDirection.Exit
        else -> null
    }

    private class FixedInputMode(override val inputMode: InputMode) : InputModeManager {
        override fun requestInputMode(inputMode: InputMode): Boolean = false
    }

    fun render(
        name: String,
        widthDp: Int,
        heightDp: Int = 1600,
        fontScale: Float = 1f,
        theme: SaveableTheme = TRINITY,
        overVideo: Boolean = false,
        /** The shipped language to render the screen in. The default is English. */
        language: String = "en",
        /** Fails the render when a control has no spoken name. Only a test that plants one turns it off. */
        requireNamedControls: Boolean = true,
        content: @Composable () -> Unit,
    ): Result {
        initDatastore()
        val density = Density(2f, fontScale)
        var measuredPx = 0
        val scene = onUiThread { ImageComposeScene(
            width = (widthDp * density.density).toInt(),
            height = (heightDp * density.density).toInt(),
            density = density,
            coroutineContext = Dispatchers.Main.immediate,
        ) {
            Frame(theme, overVideo, language) {
                Box(Modifier.width(widthDp.dp).onSizeChanged { measuredPx = it.height }) { content() }
            }
        } }
        return try {
            var image = onUiThread { scene.render(0L) }
            // Animations and resource loading need frames; 30 x 16ms covers every entrance.
            repeat(30) { i -> image = onUiThread { scene.render((i + 1) * 16_000_000L) } }
            val suffix = buildString {
                append("-${widthDp}dp")
                if (fontScale != 1f) append("-fs${fontScale}")
                if (theme !== TRINITY) append("-${theme.name.lowercase().replace(' ', '_')}")
                if (overVideo) append("-video")
                if (language != "en") append("-$language")
            }
            val file = File(outDir, "$name$suffix.png")
            image.encodeToData(EncodedImageFormat.PNG)?.bytes?.let(file::writeBytes)
            val heightDpMeasured = (measuredPx / density.density).toInt()
            println("GOLDEN $name$suffix height=${heightDpMeasured}dp -> ${file.absolutePath}")
            val layouts = onUiThread { scene.semanticsOwners.flatMap { textLayouts(it.unmergedRootSemanticsNode) } }
            val unnamed = onUiThread { scene.semanticsOwners.flatMap { unnamedControls(it.rootSemanticsNode) } }
            if (requireNamedControls) {
                assertTrue(unnamed.isEmpty(), "A screen reader meets controls with no name in ${file.name}:\n" + unnamed.joinToString("\n"))
            }
            Result(file, heightDpMeasured, layouts, unnamed)
        } finally {
            onUiThread { scene.close() }
        }
    }

    val lightTheme: SaveableTheme get() = DAYLIGHT
}
