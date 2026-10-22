package app.design

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import app.LocalChatPalette
import app.LocalRoomViewmodel
import app.LocalScreen
import app.PlatformCallback
import app.Screen
import app.player.PlayerManager
import app.room.RoomScreenUI
import app.room.RoomViewmodel
import app.room.models.MessagePalette
import app.theme.TRINITY
import app.utils.platformCallback
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The room must stay usable while the previous engine (video player) releases its native state. */
class RoomStartupTest {
    @Test
    fun leaveWorksBeforeThePlayerExists() = checkPendingStartup(settingsOpen = false)

    @Test
    fun anOpenSettingsPanelCannotReadThePendingPlayer() = checkPendingStartup(settingsOpen = true)

    private fun checkPendingStartup(settingsOpen: Boolean) {
        DesignHarness.initDatastore()
        val destroyField = PlayerManager::class.java.getDeclaredField("pendingDestroy").apply { isAccessible = true }
        val previousDestroy = destroyField.get(null)
        val callbackField = Class.forName("app.utils.PlatformUtilsKt")
            .getDeclaredField("platformCallback").apply { isAccessible = true }
        val previousCallback = callbackField.get(null)
        val oldEngine = HeldDestroy()
        val store = ViewModelStore()
        var scene: ImageComposeScene? = null
        var room: RoomViewmodel? = null

        try {
            destroyField.set(null, oldEngine)
            platformCallback = inertPlatformCallback()
            val destination = Screen.Room(null)
            val backstack = mutableStateListOf<Screen>(Screen.Home, destination)
            val viewmodel = RoomViewmodel(joinConfig = null, backStack = backstack)
            room = viewmodel
            store.put("pending-room", viewmodel)
            val scopeJob = assertNotNull(viewmodel.viewModelScope.coroutineContext[Job])
            viewmodel.uiState.tabCardRoomPreferences.value = settingsOpen
            runBlocking { withTimeout(2_000) { oldEngine.joinStarted.await() } }

            /* The room's own effects (focus handover, entry focus) must run on the thread that
             * renders this scene: a scene with no dispatcher runs them on a background one, and
             * a focus request from there races the render in Compose's snapshot observer. */
            val roomScene = DesignHarness.onUiThread { ImageComposeScene(
                width = 800,
                height = 480,
                density = Density(1f),
                coroutineContext = Dispatchers.Main.immediate,
            ) {
                DesignHarness.Frame(TRINITY, overVideo = true) {
                    if (backstack.lastOrNull() == destination) {
                        // Match Navigation3's room lifetime without starting networking or a player.
                        DisposableEffect(viewmodel) { onDispose { store.clear() } }
                        CompositionLocalProvider(
                            LocalRoomViewmodel provides viewmodel,
                            LocalScreen provides destination,
                            LocalChatPalette provides MessagePalette(),
                        ) {
                            RoomScreenUI(viewmodel)
                        }
                    }
                }
            } }
            scene = roomScene
            val surface = RoomSurface(roomScene)
            surface.advance()
            assertFalse(viewmodel.playerManager.isPlayerReady.value)
            assertFailsWith<UninitializedPropertyAccessException> { viewmodel.player }

            // Hardware seek keys can arrive even before the on-screen transport exists.
            viewmodel.dispatcher.seek(10_000)
            viewmodel.dispatcher.seekBy(10)
            DesignHarness.onUiThread { roomScene.sendPointerEvent(PointerEventType.Press, Offset(400f, 300f)) }
            DesignHarness.onUiThread { roomScene.sendPointerEvent(PointerEventType.Release, Offset(400f, 300f)) }
            surface.advance()
            assertTrue(viewmodel.uiState.visibleHUD.value, "Startup must keep the exit controls visible")

            surface.tapAction("More")
            assertTrue(viewmodel.uiState.railActionsExpanded.value)
            surface.tapAction("Leave room")
            assertTrue(viewmodel.uiState.askLeave.value)
            surface.tapText("Yes")
            runBlocking {
                withTimeout(2_000) {
                    while (backstack.size != 1) delay(10)
                }
            }
            surface.advance()
            assertEquals(listOf(Screen.Home), backstack.toList())
            assertTrue(scopeJob.isCancelled, "Leaving must cancel the room's pending initialization")
            runBlocking { withTimeout(2_000) { scopeJob.join() } }

            assertTrue(oldEngine.isActive, "Leaving the new room must not cancel the old engine's cleanup")
            oldEngine.complete()
            assertFalse(viewmodel.playerManager.isPlayerReady.value)
            assertFailsWith<UninitializedPropertyAccessException> { viewmodel.player }
        } finally {
            try {
                scene?.let { s -> DesignHarness.onUiThread { s.close() } }
            } finally {
                try {
                    store.clear()
                    room?.let { viewmodel ->
                        runBlocking {
                            withTimeout(2_000) { viewmodel.viewModelScope.coroutineContext[Job]?.join() }
                        }
                    }
                } finally {
                    oldEngine.complete()
                    destroyField.set(null, previousDestroy)
                    callbackField.set(null, previousCallback)
                }
            }
        }
    }

    /** Signals when initialization reaches the real teardown barrier, without any timing guess. */
    private class HeldDestroy(private val completion: CompletableDeferred<Unit> = CompletableDeferred()) : Job by completion {
        val joinStarted = CompletableDeferred<Unit>()

        override suspend fun join() {
            joinStarted.complete(Unit)
            completion.join()
        }

        fun complete() = completion.complete(Unit)
    }

    private class RoomSurface(private val scene: ImageComposeScene) {
        private var frame = 0L

        /* One render per call onto the UI thread: the room's effects run there too, and one long
         * block would hold them all until it ended. */
        fun advance() {
            repeat(30) { DesignHarness.onUiThread { scene.render(frame++ * 16_000_000L) } }
        }

        private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
        private fun allNodes() = scene.semanticsOwners.flatMap { nodes(it.unmergedRootSemanticsNode) }

        fun tapAction(label: String) {
            val node = assertNotNull(allNodes().firstOrNull {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true &&
                    it.config.getOrNull(SemanticsActions.OnClick) != null
            }, "No accessible $label action while the engine is pending")
            tapVisible(node, label)
        }

        fun tapText(label: String) {
            val node = assertNotNull(allNodes().firstOrNull {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
            }, "No $label confirmation")
            tapVisible(node, label)
        }

        private fun tapVisible(node: SemanticsNode, label: String) {
            val bounds = Rect(node.positionInRoot, node.size.toSize())
            assertTrue(bounds.width > 0 && bounds.height > 0, "$label has no visible size")
            assertTrue(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= 801 && bounds.bottom <= 481,
                "$label is outside the room viewport: $bounds")
            DesignHarness.onUiThread { scene.sendPointerEvent(PointerEventType.Press, bounds.center) }
            DesignHarness.onUiThread { scene.sendPointerEvent(PointerEventType.Release, bounds.center) }
            advance()
        }
    }

    private fun inertPlatformCallback(): PlatformCallback = Proxy.newProxyInstance(
        PlatformCallback::class.java.classLoader,
        arrayOf(PlatformCallback::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "mediaSessionInitialize" -> error("A pending room cannot initialize its media session")
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "RoomStartupTest platform"
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Float.TYPE -> 0.5f
                else -> null
            }
        }
    } as PlatformCallback
}
