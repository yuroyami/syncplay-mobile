package app.sync

import app.preferences.Preferences.UNPAUSE_ACTION
import app.preferences.flow
import app.preferences.set
import app.room.RoomViewmodel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Play in a managed room, pressed by a follower (a person who is not a room operator). A follower
 * can never unpause the room, so Play toggles readiness and says which state is now set.
 */
class FollowerPlayTest {

    @Test
    fun aFollowersPlayTogglesReadinessAndNeverPromisesAnUnpause() = TwoClientRoom(roomName = "+movie:ABCDEF012345").use { room ->
        room.joinAndLoad()
        val alice = room.alice.viewmodel
        val readyBefore = alice.session.ready.value

        room.alice.play()
        val first = if (readyBefore) "You are now set as not ready" else "You are now set as ready"
        room.waitUntil("alice hears her new state") { first in alice.lines() }
        room.waitUntil("bob sees alice's new state") {
            room.bob.viewmodel.session.userList.value.firstOrNull { it.name == "alice" }?.readiness == !readyBefore
        }

        room.alice.play()
        val second = if (readyBefore) "You are now set as ready" else "You are now set as not ready"
        room.waitUntil("alice hears the state flip back") { second in alice.lines() }
        assertEquals(readyBefore, alice.session.ready.value)

        assertFalse(alice.lines().any { "unpause again" in it }, "${alice.lines()}")
        assertFalse(runBlocking { room.alice.player.isPlaying() }, "The follower's engine stays paused")
        assertFalse(runBlocking { room.bob.player.isPlaying() }, "The room stays paused")
    }

    /** Outside a managed room, a blocked Play still says that a second press unpauses. */
    @Test
    fun aBlockedPlayInANormalRoomStillOffersTheUnpause() = TwoClientRoom().use { room ->
        room.joinAndLoad()
        setUnpauseAction("IfAlreadyReady")
        try {
            val alice = room.alice.viewmodel
            alice.session.ready.value = false
            room.alice.play()
            room.waitUntil("alice hears the normal line") {
                "You are now set as ready - unpause again to unpause" in alice.lines()
            }
            assertTrue(alice.session.ready.value)
        } finally {
            setUnpauseAction("Always")
        }
    }

    private fun setUnpauseAction(value: String) = runBlocking {
        UNPAUSE_ACTION.set(value)
        withTimeout(2_000) { UNPAUSE_ACTION.flow().first { it == value } }
    }

    private fun RoomViewmodel.lines(): List<String> =
        session.messageSequence.value.map { it.content.replace("⁨", "").replace("⁩", "") }
}
