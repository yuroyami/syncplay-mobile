package app.sync

import io.github.vinceglb.filekit.PlatformFile
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.delay
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/** A playlist entry counts as loaded only once the engine opened it, and the newest pick wins. */
class PlaylistLoadTest {

    @Test
    fun anEntryThatFailedToOpenLoadsAgainWhenPickedAgain() = TwoClientRoom().use { room ->
        room.waitUntil("both clients connect") { room.alice.connected && room.bob.connected }
        val alice = room.alice
        val playlist = alice.viewmodel.playlistManager
        alice.player.failNextLoad = true
        playlist.addURLs(listOf(TwoClientRoom.CLIP))
        room.waitUntil("the entry arrives") { alice.viewmodel.session.sharedPlaylist.toList() == listOf(TwoClientRoom.CLIP) }
        playlist.sendPlaylistSelection(0)
        room.waitUntil("the load that fails") { !alice.player.failNextLoad }
        Thread.sleep(200)

        val before = alice.player.loads.size
        playlist.sendPlaylistSelection(0)
        room.waitUntil("the entry loads again") { alice.player.loads.size == before + 1 }
    }

    @Test
    fun aFileThatResolvesAfterANewerPickIsDropped() = withSoloRoom { viewmodel, player ->
        val dir = createTempDirectory("synkplay-playlist").toFile()
        try {
            val slow = File(dir, "slow.mkv").apply { writeText("x") }
            val fast = File(dir, "fast.mkv").apply { writeText("x") }
            val playlist = viewmodel.playlistManager
            playlist.resolveLocalFile = { name ->
                if (name == slow.name) delay(600)
                PlatformFile(File(dir, name).path)
            }
            viewmodel.session.sharedPlaylist.addAll(listOf(slow.name, fast.name))
            viewmodel.viewModelScope.launch { playlist.changePlaylistSelection(0) }
            Thread.sleep(100)
            viewmodel.viewModelScope.launch { playlist.changePlaylistSelection(1) }
            waitFor("the newer pick") { player.loads.isNotEmpty() }
            Thread.sleep(1_000)
            assertEquals(listOf(fast.name), player.loads)
            assertEquals(fast.name, viewmodel.media?.fileName)
        } finally {
            dir.deleteRecursively()
        }
    }
}
