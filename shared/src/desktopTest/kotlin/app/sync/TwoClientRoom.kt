package app.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import app.Screen
import app.design.DesignHarness
import app.design.RoomRig
import app.home.JoinConfig
import app.player.Playback
import app.player.PlayerEngine
import app.player.PlayerImpl
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import app.player.models.Track
import app.preferences.Preferences.UNPAUSE_ACTION
import app.preferences.flow
import app.preferences.set
import app.preferences.settings.SettingCategory
import app.protocol.models.ConnectionState
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import app.server.ClientConnection
import app.server.SyncplayServer
import app.server.model.ServerConfig
import app.utils.SyncClock
import app.utils.platformCallback
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.exoplayer
import java.util.Collections
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Two real room models and the app's own server in one process, joined by an in-memory link.
 *
 * Each room runs its real protocol stack: the network manager, the protocol manager, the sync
 * decision and the event dispatcher. Only two parts are stand-ins. [LoopbackTransport] hands each
 * line straight to a [ClientConnection] of the in-process [SyncplayServer], and [ClockPlayer] is
 * an engine whose playhead is a clock. Time is real, so a test waits in hundreds of milliseconds.
 */
internal class TwoClientRoom(
    /** The link from each client to the server. A test can pass one that behaves like another server. */
    private val transport: (RoomViewmodel, SyncplayServer) -> NetworkManager = ::LoopbackTransport,
) : AutoCloseable {

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val server = SyncplayServer(ServerConfig(isolateRooms = false), serverScope)
    private val stores = mutableListOf<ViewModelStore>()
    private val callbackField = Class.forName("app.utils.PlatformUtilsKt")
        .getDeclaredField("platformCallback").apply { isAccessible = true }
    private val previousCallback = callbackField.get(null)

    val alice: Client
    val bob: Client

    init {
        DesignHarness.initDatastore()
        runBlocking {
            // A play asks nobody's readiness first, so a test can start playback in one step.
            UNPAUSE_ACTION.set("Always")
            withTimeout(2_000) { UNPAUSE_ACTION.flow().first { it == "Always" } }
        }
        platformCallback = RoomRig.inertPlatform(pictureInPicture = false)
        alice = join("alice")
        bob = join("bob")
    }

    private fun join(name: String): Client {
        val config = JoinConfig(user = name, room = "harness", ip = "loopback", port = 8999)
        val destination = Screen.Room(config)
        val viewmodel = RoomViewmodel(
            joinConfig = config,
            backStack = mutableStateListOf(Screen.Home, destination),
            engineOverride = ClockEngine,
            transportOverride = { transport(it, server) },
        )
        stores += ViewModelStore().also { it.put("room-$name", viewmodel) }
        return Client(name, viewmodel)
    }

    /** One member of the room, as the tests drive it: by the same calls the room screen makes. */
    class Client(val name: String, val viewmodel: RoomViewmodel) {
        val player: ClockPlayer get() = viewmodel.player as ClockPlayer
        fun positionMs(): Long = player.currentPositionMs()
        fun play() = viewmodel.dispatcher.controlPlayback(Playback.PLAY, tellServer = true)
        fun pause() = viewmodel.dispatcher.controlPlayback(Playback.PAUSE, tellServer = true)
        fun seek(targetMs: Long) = viewmodel.dispatcher.seek(targetMs)

        val connected: Boolean
            get() = viewmodel.playerManager.isPlayerReady.value &&
                viewmodel.networkManager.state.value == ConnectionState.CONNECTED
    }

    /** Both members connected, both holding the same file, and each seeing the other. */
    fun joinAndLoad() {
        waitUntil("both clients connect") { alice.connected && bob.connected }
        runBlocking {
            alice.player.injectVideoURL(CLIP)
            bob.player.injectVideoURL(CLIP)
        }
        waitUntil("both hold the file") { alice.viewmodel.media != null && bob.viewmodel.media != null }
        // A loaded file asks the server for the user list, so this comes after the load.
        waitUntil("each client sees the other") {
            listOf(alice, bob).all { client -> client.viewmodel.session.userList.value.any { it.name != client.name } }
        }
        /* The server's first State after a join is a forced seek, 100 ms in. On loopback a whole
         * test fits in those 100 ms, and that seek would move a playhead the test is watching.
         * So wait for a plain State that came after the load, and give its actions time to land. */
        val loadedAt = SyncClock.now()
        waitUntil("both clients hear from the server") {
            listOf(alice, bob).all { client -> client.viewmodel.protocol.lastStateReceivedAt?.let { it > loadedAt } == true }
        }
        Thread.sleep(200)
    }

    fun gapMs(): Long = abs(alice.positionMs() - bob.positionMs())

    /** Polls [condition] until it holds, or fails with [what] and both playheads. */
    fun waitUntil(what: String, timeoutMs: Long = 6_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (!condition()) {
            if (System.nanoTime() > deadline) {
                error("Timed out waiting until $what. alice ${describe(alice)}, bob ${describe(bob)}")
            }
            Thread.sleep(20)
        }
    }

    private fun describe(client: Client): String = runCatching {
        "at ${client.positionMs()} ms, playing ${client.player.playing}, seeks ${client.player.seeks}, speeds ${client.player.speeds}"
    }.getOrDefault("not ready")

    override fun close() {
        try {
            stores.forEach { it.clear() }
            runBlocking { withTimeout(2_000) { server.shutdown() } }
        } finally {
            serverScope.cancel()
            callbackField.set(null, previousCallback)
            runBlocking { UNPAUSE_ACTION.set(UNPAUSE_ACTION.default) }
        }
    }

    companion object {
        const val CLIP = "https://example.com/clip.mp4"
        const val CLIP_LENGTH_MS = 600_000L
    }
}

/** An in-memory socket: each line goes straight to a [ClientConnection] of [server], and back. */
internal open class LoopbackTransport(viewmodel: RoomViewmodel, private val server: SyncplayServer) : NetworkManager(viewmodel) {
    override val engine = NetworkEngine.KTOR

    @Volatile
    private var connection: ClientConnection? = null

    override suspend fun connectSocket() {
        lateinit var link: ClientConnection
        link = ClientConnection(
            server = server,
            sendFn = { line -> if (connection === link) handlePacket(line) },
            dropFn = { lost(link) },
        )
        connection = link
    }

    /** The server dropped this link, the way a socket closes from the other side. */
    private fun lost(link: ClientConnection) {
        if (connection !== link) return
        connection = null
        when (state.value) {
            ConnectionState.CONNECTING -> viewmodel.callback.onConnectionFailed()
            ConnectionState.CONNECTED -> viewmodel.callback.onDisconnected()
            else -> Unit
        }
    }

    override fun terminateExistingConnection() {
        val link = connection ?: return
        connection = null
        link.onConnectionLost()
    }

    override suspend fun writeActualString(s: String) {
        val link = connection ?: throw SocketGoneException()
        link.handlePacket(s.trimEnd('\r', '\n'))
    }

    override fun supportsTLS() = false

    override suspend fun upgradeTls() = Unit
}

/** The engine behind [ClockPlayer]. */
internal object ClockEngine : PlayerEngine {
    override val name = "Clock"
    override val isDefault = false
    override val isAvailable = true
    override val img = Res.drawable.exoplayer

    // A real engine starts from its view. This one has no view, so it starts at once.
    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl = ClockPlayer(viewmodel).also { it.initialize() }
}

/**
 * An engine whose playhead is a clock: it moves at the playback rate while it plays. It records
 * every seek and speed change, so a test can tell a correction from a room that held still.
 */
internal class ClockPlayer(viewmodel: RoomViewmodel) : PlayerImpl(viewmodel, ClockEngine) {
    override val supportsChapters = false
    override val trackerJobInterval: Duration = 100.milliseconds

    @Volatile private var anchorMs = 0L
    @Volatile private var anchorAt = SyncClock.nowMillis()
    @Volatile private var rate = 1.0

    @Volatile
    var playing = false
        private set

    val seeks: MutableList<Long> = Collections.synchronizedList(mutableListOf())
    val speeds: MutableList<Double> = Collections.synchronizedList(mutableListOf())

    override fun initialize() {
        isInitialized = true
        startTrackingProgress()
    }

    // Like a real engine, the playhead stays inside the file.
    override fun currentPositionMs(): Long =
        (if (playing) anchorMs + ((SyncClock.nowMillis() - anchorAt) * rate).toLong() else anchorMs)
            .coerceIn(0L, TwoClientRoom.CLIP_LENGTH_MS)

    private fun moveTo(positionMs: Long) {
        anchorMs = positionMs.coerceIn(0L, TwoClientRoom.CLIP_LENGTH_MS)
        anchorAt = SyncClock.nowMillis()
    }

    /** Moves the playhead without telling anyone, the way a stalled decoder or a skip would. */
    fun jumpBy(deltaMs: Long) = moveTo(currentPositionMs() + deltaMs)

    /**
     * The engine stops by itself, as on a stream error or while it opens the next file. A [marked]
     * stop is recorded as expected first, which is how every engine keeps such a stop local.
     */
    fun stopByItself(marked: Boolean) {
        if (marked) viewmodel.protocol.noteExpectedPlaybackState(paused = true)
        moveTo(currentPositionMs())
        playing = false
        playerManager.isNowPlaying.value = false
    }

    /** A buffering stall: the engine still means to play, so only the buffering flag changes. */
    fun stall(stalled: Boolean) {
        playerManager.isBuffering.value = stalled
    }

    /** The real end of the file, as MpvImpl.onFileEnded handles it: an unmarked stop, then the end hook. */
    fun reachEnd() {
        moveTo(TwoClientRoom.CLIP_LENGTH_MS)
        playing = false
        playerManager.isNowPlaying.value = false
        onPlaybackEnded()
    }

    override fun seekTo(toPositionMs: Long) {
        super.seekTo(toPositionMs)
        moveTo(toPositionMs)
        seeks += toPositionMs
    }

    override suspend fun play() {
        moveTo(currentPositionMs())
        playing = true
        playerManager.isNowPlaying.value = true
    }

    override suspend fun pause() {
        moveTo(currentPositionMs())
        playing = false
        playerManager.isNowPlaying.value = false
    }

    override suspend fun setSpeed(speed: Double) {
        moveTo(currentPositionMs())
        rate = speed
        speeds += speed
    }

    override suspend fun injectVideoURLImpl(location: MediaFileLocation.Remote) {
        moveTo(0L)
        playing = false
        playerManager.timeFullMillis.value = TwoClientRoom.CLIP_LENGTH_MS
        // A real engine announces once it knows the duration. This one knows it at once.
        announceFileLoaded()
    }

    override suspend fun injectVideoFileImpl(location: MediaFileLocation.Local) = Unit
    override suspend fun isPlaying() = playing
    override suspend fun hasMedia() = playerManager.media.value != null
    override suspend fun isSeekable() = true
    override suspend fun destroy() = Unit
    override suspend fun configurableSettings(): SettingCategory? = null
    override suspend fun analyzeTracks(mediafile: MediaFile) = Unit
    override suspend fun selectTrack(track: Track?, type: TrackType) = Unit
    override suspend fun analyzeChapters(mediafile: MediaFile) = Unit
    override suspend fun reapplyTrackChoices() = Unit
    override suspend fun loadExternalSubImpl(uri: PlatformFile, extension: String) = Unit
    override suspend fun switchAspectRatio() = ""
    override suspend fun changeSubtitleSize(newSize: Int) = Unit
    @Composable override fun VideoPlayer(modifier: Modifier, onPlayerReady: () -> Unit) = Unit
    override fun getEngineVolume() = 100
    override fun setEngineVolume(percent: Int) = Unit
}
