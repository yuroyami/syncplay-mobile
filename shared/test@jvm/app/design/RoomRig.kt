package app.design

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import app.LocalChatPalette
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.LocalScreen
import app.PlatformCallback
import app.Screen
import app.home.JoinConfig
import app.player.PlayerImpl
import app.player.PlayerManager
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import app.player.models.Track
import app.preferences.Preferences.HUD_AUTO_HIDE_SECONDS
import app.preferences.flow
import app.preferences.set
import app.preferences.settings.SettingCategory
import app.room.RoomScreenUI
import app.room.RoomViewmodel
import app.room.models.MessagePalette
import app.uicomponents.LocalScreenReaderActive
import app.utils.platformCallback
import io.github.vinceglb.filekit.PlatformFile
import java.lang.reflect.Proxy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.exoplayer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The real room screen for focus and screen reader tests, with no network and no real engine.
 *
 * The room model starts two things on its own: an engine, then the connection. The rig cancels
 * that start before it runs and puts [InertPlayer] in place of the engine. So a test can walk the
 * room with a remote, or read it as a screen reader does, without a device.
 */
internal object RoomRig {

    /** A room under test: its model, and the driver that presses keys against it. */
    class Room(val viewmodel: RoomViewmodel, val driver: DesignHarness.Driver) {
        val ui get() = viewmodel.uiState

        private fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)

        /** Every node as the layout holds it, including nodes a screen reader never reaches. */
        fun allNodes(): List<SemanticsNode> = DesignHarness.onUiThread {
            driver.scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
        }

        /** The nodes a screen reader reads: merged, and without the ones Compose reports hidden. */
        fun spokenNodes(): List<SemanticsNode> = DesignHarness.onUiThread {
            driver.scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
        }

        /** The node that holds key focus, or null. */
        fun focused(): SemanticsNode? = allNodes().lastOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }

        /** The spoken name of the focused node: its description, or else the text inside it. */
        fun focusedName(): String? = focused()?.let(::nameOf)

        fun nameOf(node: SemanticsNode): String? {
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()?.takeIf { it.isNotBlank() }?.let { return it }
            return walk(node).firstNotNullOfOrNull { n ->
                n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }?.takeIf { it.isNotBlank() }
            }
        }

        /** Where [node] is laid out, in scene pixels. Unlike boundsInRoot, a clipped node keeps its size. */
        fun boundsOf(node: SemanticsNode): Rect = Rect(node.positionInRoot, node.size.toSize())

        /** The first node whose description is exactly [name]. */
        fun described(name: String): SemanticsNode? = allNodes().firstOrNull {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(name) == true
        }
    }

    /**
     * Composes a room and runs [drive] against it.
     *
     * @param solo a room with no server. A room with a server shows the people panel on the rail.
     * @param withVideo a loaded file, which the control panel button needs.
     * @param pictureInPicture what the platform says about its picture-in-picture support.
     * @param screenReader whether a screen reader runs.
     */
    fun drive(
        solo: Boolean = true,
        television: Boolean = true,
        withVideo: Boolean = false,
        pictureInPicture: Boolean = true,
        screenReader: Boolean = false,
        widthDp: Int = 960,
        heightDp: Int = 540,
        drive: Room.() -> Unit,
    ) = inRoom(solo, withVideo, pictureInPicture) { viewmodel, destination ->
        DesignHarness.drive(widthDp = widthDp, heightDp = heightDp, television = television, content = {
            CompositionLocalProvider(
                LocalRoomViewmodel provides viewmodel,
                LocalScreen provides destination,
                LocalChatPalette provides MessagePalette(),
                LocalScreenReaderActive provides screenReader,
            ) {
                RoomScreenUI(viewmodel)
            }
        }) {
            Room(viewmodel, this).drive()
        }
    }

    /**
     * Renders [content] once as a golden, inside a room model built as [drive] builds it. [setup]
     * fills the room before the first frame, and the room is closed after the render.
     */
    fun render(
        name: String,
        widthDp: Int,
        heightDp: Int = 800,
        fontScale: Float = 1f,
        solo: Boolean = true,
        withVideo: Boolean = false,
        overVideo: Boolean = false,
        setup: (RoomViewmodel) -> Unit = {},
        content: @Composable () -> Unit,
    ): DesignHarness.Result = inRoom(solo, withVideo, pictureInPicture = true) { viewmodel, destination ->
        setup(viewmodel)
        DesignHarness.render(name, widthDp, heightDp, fontScale, overVideo = overVideo) {
            CompositionLocalProvider(
                LocalRoomViewmodel provides viewmodel,
                LocalRoomUiState provides viewmodel.uiState,
                LocalScreen provides destination,
                LocalChatPalette provides MessagePalette(),
                LocalScreenReaderActive provides false,
            ) {
                content()
            }
        }
    }

    /** Builds a room with no network and an [InertPlayer], runs [block], and closes the room. */
    private fun <T> inRoom(solo: Boolean, withVideo: Boolean, pictureInPicture: Boolean, block: (RoomViewmodel, Screen.Room) -> T): T {
        DesignHarness.initDatastore()
        val destroyField = PlayerManager::class.java.getDeclaredField("pendingDestroy").apply { isAccessible = true }
        val previousDestroy = destroyField.get(null)
        val callbackField = Class.forName("app.utils.PlatformUtilsKt")
            .getDeclaredField("platformCallback").apply { isAccessible = true }
        val previousCallback = callbackField.get(null)
        // The room waits for this before it builds an engine, so its start cannot race the cancel.
        val held = Job()
        val store = ViewModelStore()
        var room: RoomViewmodel? = null
        try {
            destroyField.set(null, held)
            platformCallback = inertPlatform(pictureInPicture)
            val joinConfig = if (solo) null else JoinConfig(user = "tester", room = "rig", ip = "localhost", port = 8999)
            val destination = Screen.Room(joinConfig)
            val backstack = mutableStateListOf<Screen>(Screen.Home, destination)
            val viewmodel = RoomViewmodel(joinConfig = joinConfig, backStack = backstack)
            room = viewmodel
            store.put("rig-room", viewmodel)
            // The start is the only child so far. Nothing else launches until the screen composes.
            viewmodel.viewModelScope.coroutineContext[Job]?.children?.forEach { it.cancel() }
            viewmodel.playerManager.player = InertPlayer(viewmodel)
            viewmodel.playerManager.isPlayerReady.value = true
            if (withVideo) {
                viewmodel.playerManager.media.value = MediaFile(location = MediaFileLocation.Remote("https://example.com/clip.mp4"), fileName = "clip.mp4")
            }
            return block(viewmodel, destination)
        } finally {
            try {
                store.clear()
                room?.let { viewmodel ->
                    runBlocking { withTimeout(2_000) { viewmodel.viewModelScope.coroutineContext[Job]?.join() } }
                }
            } finally {
                held.complete()
                destroyField.set(null, previousDestroy)
                callbackField.set(null, previousCallback)
            }
        }
    }

    /** Runs [block] with the controls' idle timer set to [seconds], then restores the default. */
    fun withIdleSeconds(seconds: Int, block: () -> Unit) {
        DesignHarness.initDatastore()
        val pref = HUD_AUTO_HIDE_SECONDS
        runBlocking {
            pref.set(seconds)
            withTimeout(2_000) { pref.flow().first { it == seconds } }
        }
        try {
            block()
        } finally {
            runBlocking { pref.set(pref.default) }
        }
    }

    /** A platform that does nothing and reports no brightness control. */
    internal fun inertPlatform(pictureInPicture: Boolean): PlatformCallback = Proxy.newProxyInstance(
        PlatformCallback::class.java.classLoader,
        arrayOf(PlatformCallback::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "getSupportsPictureInPicture" -> pictureInPicture
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "RoomRig platform"
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Float.TYPE -> 0.5f
                else -> null
            }
        }
    } as PlatformCallback
}

/** An engine that plays nothing and answers every question with a neutral value. */
internal class InertPlayer(viewmodel: RoomViewmodel) : PlayerImpl(viewmodel, FakeEngine("Inert", Res.drawable.exoplayer)) {
    override val supportsChapters = false
    override val trackerJobInterval: Duration = 1.seconds
    override fun initialize() { isInitialized = true }
    override suspend fun destroy() = Unit
    override suspend fun configurableSettings(): SettingCategory? = null
    override suspend fun hasMedia() = false
    override suspend fun isPlaying() = false
    override suspend fun analyzeTracks(mediafile: MediaFile) = Unit
    override suspend fun selectTrack(track: Track?, type: TrackType) = Unit
    override suspend fun analyzeChapters(mediafile: MediaFile) = Unit
    override suspend fun reapplyTrackChoices() = Unit
    override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) = Unit
    override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) = Unit
    override suspend fun injectVideoFileImpl(location: MediaFileLocation.Local) = Unit
    override suspend fun pause() = Unit
    override suspend fun play() = Unit
    override suspend fun setSpeed(speed: Double) = Unit
    override suspend fun isSeekable() = true
    override fun currentPositionMs() = 0L
    override suspend fun switchAspectRatio() = ""
    override suspend fun changeSubtitleSize(newSize: Int) = Unit
    @Composable override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) = Box(modifier)
    override fun getEngineVolume() = 50
    override fun setEngineVolume(percent: Int) = Unit
}
