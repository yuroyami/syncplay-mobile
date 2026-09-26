package app.sync

import app.design.DesignHarness
import app.preferences.Preferences.TRUSTED_DOMAINS
import app.preferences.flow
import app.preferences.set
import app.protocol.WireMessage
import app.protocol.replay.Redactor
import app.protocol.replay.SessionRecorder
import app.room.switchRoom
import app.server.model.RoomPasswordProvider
import app.utils.generateRoomPassword
import app.utils.instantiateNetworkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.random.Random
import kotlin.test.Test

/**
 * Records the sessions that RecordedSessionReplayTest replays: one on the public server
 * syncplay.pl and one on the app's own server, with the same steps. It needs the network, so it
 * runs only on request:
 *
 *     ./gradlew :shared:desktopTest -PrecordSessions --tests app.sync.RecordSessionsTest
 *
 * The user, room and file names are made up, and the recorder replaces anybody else's.
 */
class RecordSessionsTest {

    @Test
    fun recordOnThePublicServer() {
        assumeTrue("recording not requested (-PrecordSessions)", System.getProperty("synkplay.recordSessions") == "true")
        record("public", host = "syncplay.pl", joinSpacingMs = 3_500) { viewmodel, _ -> viewmodel.instantiateNetworkManager() }
    }

    @Test
    fun recordOnTheAppsOwnServer() {
        assumeTrue("recording not requested (-PrecordSessions)", System.getProperty("synkplay.recordSessions") == "true")
        record("builtin", host = "loopback", joinSpacingMs = 0, transport = ::LoopbackTransport)
    }

    private fun record(
        name: String,
        host: String,
        joinSpacingMs: Long,
        transport: (app.room.RoomViewmodel, app.server.SyncplayServer) -> app.protocol.network.NetworkManager,
    ) {
        DesignHarness.initDatastore()
        // Short, so the managed room name with its hash stays within the 35-character limit.
        val room = "replay-${Random.nextInt(100_000, 999_999)}"
        val recorders = listOf(ALICE, BOB).associateWith { SessionRecorder(Redactor(::isOurs)) }
        val trusted = runBlocking { TRUSTED_DOMAINS.flow().first() }
        setTrusted("$trusted\n$FIXTURE_HOST")
        try {
            TwoClientRoom(transport, host, roomName = room, names = ALICE to BOB, joinSpacingMs = joinSpacingMs, tap = { recorders[it] }).use { r ->
                steps(r)
            }
        } finally {
            setTrusted(trusted)
        }
        recorders.forEach { (user, recorder) -> recorder.write(File(FIXTURES, "$name-${user.removePrefix("replay-")}.jsonl")) }
    }

    /** The steps of both sessions: every kind of message the replay should cover. */
    private fun steps(r: TwoClientRoom) {
        val alice = r.alice
        val bob = r.bob
        r.waitUntil("both connect", timeoutMs = 30_000) { alice.connected && bob.connected }
        runBlocking {
            alice.player.injectVideoURL(CLIP_A)
            bob.player.injectVideoURL(CLIP_A)
        }
        r.waitUntil("both hold the file", timeoutMs = 15_000) { alice.viewmodel.media != null && bob.viewmodel.media != null }
        r.waitUntil("each sees the other", timeoutMs = 15_000) {
            listOf(alice, bob).all { c -> c.viewmodel.session.userList.value.any { it.name != c.name } }
        }
        Thread.sleep(3_000)

        // Readiness both ways, and a chat line.
        ready(alice, true); Thread.sleep(1_500)
        ready(alice, false); Thread.sleep(1_500)
        alice.viewmodel.dispatcher.sendMessage("replay fixture"); Thread.sleep(1_500)

        alice.play()
        r.waitUntil("both play", timeoutMs = 15_000) { alice.player.playing && bob.player.playing }
        Thread.sleep(5_000)

        // A small lead for the rate controller, then leads for the ladder: a slowdown and a rewind.
        bob.player.jumpBy(400); Thread.sleep(6_000)
        bob.player.jumpBy(-400); Thread.sleep(8_000)
        bob.player.jumpBy(2_000); Thread.sleep(6_000)
        bob.player.jumpBy(5_000); Thread.sleep(4_000)

        // A seek by one moves the other, and so do a pause and a play.
        alice.seek(120_000); Thread.sleep(4_000)
        bob.pause()
        r.waitUntil("alice pauses", timeoutMs = 10_000) { !alice.player.playing }
        Thread.sleep(2_000)
        bob.play()
        r.waitUntil("alice plays", timeoutMs = 10_000) { alice.player.playing }
        Thread.sleep(3_000)

        // The shared playlist.
        alice.viewmodel.playlistManager.addURLs(listOf(CLIP_A, CLIP_B)); Thread.sleep(3_000)
        alice.viewmodel.playlistManager.sendPlaylistSelection(1); Thread.sleep(4_000)

        // A room switch and back.
        bob.viewmodel.switchRoom(r.roomName + "-b"); Thread.sleep(3_000)
        bob.viewmodel.switchRoom(r.roomName); Thread.sleep(3_000)

        // A managed room, created the way the managed room popup creates one.
        alice.viewmodel.protocol.beginRoomChange()
        alice.viewmodel.networkManager.sendAsync(
            WireMessage.controllerAuth(room = RoomPasswordProvider.baseName(r.roomName), password = generateRoomPassword()),
        )
        r.waitUntil("alice is in the managed room", timeoutMs = 15_000) { alice.viewmodel.session.currentRoom.startsWith("+") }
        Thread.sleep(4_000)
    }

    private fun ready(client: TwoClientRoom.Client, ready: Boolean) {
        client.viewmodel.session.ready.value = ready
        client.viewmodel.networkManager.sendAsync(WireMessage.readiness(isReady = ready, manuallyInitiated = true))
    }

    private fun setTrusted(value: String) = runBlocking {
        TRUSTED_DOMAINS.set(value)
        withTimeout(2_000) { TRUSTED_DOMAINS.flow().first { it == value } }
    }

    private fun isOurs(name: String) = "replay" in name || name.contains(FIXTURE_HOST)

    private companion object {
        const val ALICE = "replay-alice"
        const val BOB = "replay-bob"
        const val FIXTURE_HOST = "synkplay.invalid"
        const val CLIP_A = "https://$FIXTURE_HOST/replay-clip-a.mp4"
        const val CLIP_B = "https://$FIXTURE_HOST/replay-clip-b.mp4"

        /** Where the fixtures live, from the module folder that tests run in. */
        val FIXTURES = File("src/desktopTest/resources/replay")
    }
}
