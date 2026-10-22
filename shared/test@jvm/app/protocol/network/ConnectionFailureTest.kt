package app.protocol.network

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModelStore
import app.Screen
import app.design.DesignHarness
import app.design.RoomRig
import app.home.JoinConfig
import app.room.RoomViewmodel
import app.sync.ClockEngine
import app.sync.waitFor
import app.utils.platformCallback
import java.io.IOException
import java.net.ConnectException
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A failed join says why: the name, a refusal, or no answer. */
class ConnectionFailureTest {

    @Test
    fun theJvmFailuresFallIntoTheirClasses() {
        assertEquals(ConnectionFailure.NameNotFound, classifyDialFailure(UnknownHostException("syncplay.invalid")))
        assertEquals(ConnectionFailure.Refused, classifyDialFailure(IOException("dial", ConnectException("Connection refused"))))
        assertEquals(ConnectionFailure.TimedOut, classifyDialFailure(SocketTimeoutException("connect timed out")))
        assertNull(classifyDialFailure(IOException("the socket closed")))
    }

    @Test
    fun aRoomThatDialsAClosedPortSaysTheServerRefused() {
        DesignHarness.initDatastore()
        val port = ServerSocket(0).use { it.localPort }
        val field = Class.forName("app.utils.PlatformUtilsKt").getDeclaredField("platformCallback").apply { isAccessible = true }
        val previous = field.get(null)
        platformCallback = RoomRig.inertPlatform(pictureInPicture = false)
        val store = ViewModelStore()
        try {
            val config = JoinConfig(user = "alice", room = "lobby", ip = "127.0.0.1", port = port)
            val room = RoomViewmodel(joinConfig = config, backStack = mutableStateListOf(Screen.Home, Screen.Room(config)), engineOverride = ClockEngine)
            store.put("room", room)
            waitFor("the refused dial", timeoutMs = 20_000) { room.networkManager.lastFailure.value != null }
            assertEquals(ConnectionFailure.Refused, room.networkManager.lastFailure.value)
        } finally {
            store.clear()
            field.set(null, previous)
        }
    }
}
