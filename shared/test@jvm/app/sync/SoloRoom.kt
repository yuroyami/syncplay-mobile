package app.sync

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModelStore
import app.Screen
import app.design.DesignHarness
import app.design.RoomRig
import app.room.RoomViewmodel
import app.utils.platformCallback

/** Runs [test] in a room with no server, watching alone on a [ClockPlayer]. */
internal fun withSoloRoom(test: (RoomViewmodel, ClockPlayer) -> Unit) {
    DesignHarness.initDatastore()
    val field = Class.forName("app.utils.PlatformUtilsKt").getDeclaredField("platformCallback").apply { isAccessible = true }
    val previous = field.get(null)
    platformCallback = RoomRig.inertPlatform(pictureInPicture = false)
    val store = ViewModelStore()
    try {
        val viewmodel = RoomViewmodel(
            joinConfig = null,
            backStack = mutableStateListOf(Screen.Home, Screen.Room(null)),
            engineOverride = ClockEngine,
        )
        store.put("solo", viewmodel)
        waitFor("the engine") { viewmodel.playerManager.isPlayerReady.value }
        test(viewmodel, viewmodel.player as ClockPlayer)
    } finally {
        store.clear()
        field.set(null, previous)
    }
}

/** Polls [condition] until it holds, or fails after six seconds. */
internal fun waitFor(what: String, timeoutMs: Long = 6_000, condition: () -> Boolean) {
    val deadline = System.nanoTime() + timeoutMs * 1_000_000
    while (!condition()) {
        check(System.nanoTime() < deadline) { "Timed out waiting for $what" }
        Thread.sleep(20)
    }
}
