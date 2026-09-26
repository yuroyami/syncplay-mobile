package app.sync

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModelStore
import app.Screen
import app.design.DesignHarness
import app.design.RoomRig
import app.home.HomeViewmodel
import app.i18n.Localization
import app.room.RoomViewmodel
import app.uicomponents.DropPlan
import app.uicomponents.DropRefusal
import app.uicomponents.DroppedMedia
import app.uicomponents.frames.NoticeSeverity
import app.utils.platformCallback
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Media dropped onto the window opens through the calls of the add-media routes. So the room
 * announces it like a picked file, and media that arrives before the engine is ready waits for it.
 */
class DropIntoRoomTest {

    /** Alice drops a link onto her room, and Bob sees her file: the announcement of a picked link. */
    @Test
    fun aDroppedLinkIsAnnouncedToTheRoom() = TwoClientRoom().use { room ->
        room.waitUntil("both clients connect") { room.alice.connected && room.bob.connected }
        room.alice.viewmodel.onMediaDrop(DropPlan.Open(DroppedMedia.Link(TwoClientRoom.CLIP)))
        room.waitUntil("alice holds the file") { room.alice.viewmodel.media?.fileName == "clip.mp4" }
        room.waitUntil("bob sees alice's file") {
            room.bob.viewmodel.session.userList.value.any { it.name == "alice" && it.file?.fileName == "clip.mp4" }
        }
    }

    /** A refused drop opens nothing, and the room says why in a warning. */
    @Test
    fun aRefusedDropSaysWhy() = TwoClientRoom().use { room ->
        room.waitUntil("alice connects") { room.alice.connected }
        room.alice.viewmodel.onMediaDrop(DropPlan.Refuse(DropRefusal.Folder))
        val why = Localization.strings.mediaDropFolder
        room.waitUntil("alice sees the warning") {
            room.alice.viewmodel.notices.items.any { it.text == why && it.severity == NoticeSeverity.Warn }
        }
        assertNull(room.alice.viewmodel.media)
    }

    /** The home screen's drop: Watch alone, with the dropped media for the new room. */
    @Test
    fun aDropOnTheHomeScreenOpensARoomToWatchAlone() = runBlocking {
        val backStack = mutableStateListOf<Screen>(Screen.Home)
        val media = DroppedMedia.Link(TwoClientRoom.CLIP)
        HomeViewmodel(backStack).joinRoom(null, startMedia = media)
        assertEquals(Screen.Room(null, media), backStack.last())
    }

    /** The room from that drop builds its engine first, then opens the file: nothing is lost on the way. */
    @Test
    fun aRoomOpensItsStartFileOnceTheEngineIsReady() {
        val movie = File.createTempFile("dropped", ".mp4").apply { writeText("not a real video"); deleteOnExit() }
        soloRoom(DroppedMedia.File(movie.path)) { viewmodel ->
            waitUntil("the dropped file opens") { viewmodel.media?.fileName == movie.name }
            assertEquals(movie.name, viewmodel.media?.fileName)
        }
    }

    /** A solo room (no server) on the clock engine, cleared when [check] ends. */
    private fun soloRoom(startMedia: DroppedMedia, check: (RoomViewmodel) -> Unit) {
        DesignHarness.initDatastore()
        val callbackField = Class.forName("app.utils.PlatformUtilsKt").getDeclaredField("platformCallback").apply { isAccessible = true }
        val previousCallback = callbackField.get(null)
        platformCallback = RoomRig.inertPlatform(pictureInPicture = false)
        val store = ViewModelStore()
        try {
            val viewmodel = RoomViewmodel(
                joinConfig = null,
                backStack = mutableStateListOf(Screen.Home, Screen.Room(null, startMedia)),
                startMedia = startMedia,
                engineOverride = ClockEngine,
            )
            store.put("solo", viewmodel)
            check(viewmodel)
        } finally {
            store.clear()
            callbackField.set(null, previousCallback)
        }
    }

    private fun waitUntil(what: String, timeoutMs: Long = 6_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (!condition()) {
            if (System.nanoTime() > deadline) error("Timed out waiting until $what")
            Thread.sleep(20)
        }
    }
}
