package app.design

import app.LocalRoomViewmodel
import app.room.RoomScreenUI
import app.room.models.Message
import kotlin.test.Test

/**
 * The whole room in a window wider than tall, for a look by eye and for a comparison before and
 * after a change to the portrait room. The portrait room must leave these renders as they are.
 */
class LandscapeRoomGoldenTest {

    private fun chat() = listOf(
        Message(sender = null, content = "Mia joined the room", timestamp = DesignHarness.CHAT_TIME),
        Message(sender = "Sam", content = "ok, starting from the cold open", timestamp = DesignHarness.CHAT_TIME),
        Message(sender = "Ari", content = "same file? mine look fine", isMainUser = true, timestamp = DesignHarness.CHAT_TIME),
    )

    @Test
    fun rendersTheLandscapeRoom() {
        for ((width, height) in listOf(844 to 390, 960 to 540)) {
            val states = mapOf<String, (app.room.RoomViewmodel) -> Unit>(
                "controls" to { _ -> },
                "watching" to { room -> room.uiState.visibleHUD.value = false },
                "panel" to { room -> room.uiState.toggleRoomPreferences(true) },
                "strip" to { room -> room.uiState.toggleControlPanel(true) },
                "users-and-strip" to { room ->
                    room.uiState.toggleUserInfo(true)
                    room.uiState.toggleControlPanel(true)
                },
                "more" to { room -> room.uiState.railActionsExpanded.value = true },
            )
            for ((state, setup) in states) {
                RoomRig.render(
                    name = "landscape-room-$state-${height}h",
                    widthDp = width,
                    heightDp = height,
                    solo = false,
                    withVideo = true,
                    setup = { room ->
                        room.session.messageSequence.value = chat()
                        setup(room)
                    },
                ) { RoomScreenUI(LocalRoomViewmodel.current) }
            }
            RoomRig.render("landscape-room-no-video-${height}h", width, height, solo = false, withVideo = false, setup = { room ->
                room.session.messageSequence.value = chat()
            }) { RoomScreenUI(LocalRoomViewmodel.current) }
            RoomRig.render("landscape-room-solo-${height}h", width, height, solo = true, withVideo = true) {
                RoomScreenUI(LocalRoomViewmodel.current)
            }
        }
    }
}
