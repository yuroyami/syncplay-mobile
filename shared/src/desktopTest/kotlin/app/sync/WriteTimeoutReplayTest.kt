package app.sync

import app.room.RoomViewmodel
import app.server.SyncplayServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A chat line whose write times out still lands a moment later, as a real socket write does. The
 * reconnect that follows must not send it again.
 */
class WriteTimeoutReplayTest {

    /** Alice's link: one chat write outlives the write timeout and lands late. */
    private class LateChat(viewmodel: RoomViewmodel, server: SyncplayServer) : LoopbackTransport(viewmodel, server) {
        override val writeTimeout: Duration = 150.milliseconds
        private val late = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Volatile
        var stallNextChat = false

        override suspend fun writeActualString(s: String) {
            if (stallNextChat && "\"Chat\"" in s) {
                stallNextChat = false
                late.launch {
                    delay(400)
                    writeNow(s)
                }
                awaitCancellation()
            }
            writeNow(s)
        }

        private suspend fun writeNow(s: String) = super.writeActualString(s)
    }

    @Test
    fun aLineWhoseWriteTimedOutReachesTheRoomOnce() = TwoClientRoom(transport = { viewmodel, server ->
        if (viewmodel.joinConfig?.user == "alice") LateChat(viewmodel, server) else LoopbackTransport(viewmodel, server)
    }).use { room ->
        room.waitUntil("both connect", timeoutMs = 10_000) { room.alice.connected && room.bob.connected }
        val link = room.alice.viewmodel.networkManager as LateChat
        fun bobCopies() = room.bob.viewmodel.session.messageSequence.value.count { it.content == "late line" }

        link.stallNextChat = true
        room.alice.viewmodel.dispatcher.sendMessage("late line")
        room.waitUntil("the late write lands", timeoutMs = 5_000) { bobCopies() == 1 }

        link.sever()
        room.waitUntil("alice reconnects", timeoutMs = 20_000) { room.alice.connected }
        Thread.sleep(1_000)
        assertEquals(1, bobCopies(), "the reconnect must not send the line again")
    }
}
