package app.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.LocalRoomViewmodel
import app.room.CertificateAsk
import app.protocol.network.UntrustedCertificate
import app.server.tls.HostCertificate
import app.protocol.models.ConnectionState
import app.protocol.network.ConnectionFailure
import app.i18n.strings
import app.klipy.KlipyMedia
import app.player.models.Chapter
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import app.preferences.settings.GLOBAL_GENERAL
import app.preferences.settings.GLOBAL_LANGUAGE
import app.preferences.settings.GLOBAL_SYNCING
import app.preferences.settings.INROOM_ADVANCED
import app.preferences.settings.INROOM_HAPTICS
import app.preferences.settings.SettingsCategoryBody
import app.room.models.Message
import app.room.models.MessagePalette
import app.room.sharedplaylist.MediaAccessRegistry.FolderState
import app.room.ui.bottombar.ChaptersModal
import app.room.ui.bottombar.RoomControlPanelCard
import app.room.ui.bottombar.SubtitleSearchModal
import app.room.ui.bottombar.SubtitleSearchResults
import app.room.ui.chat.FadingMessageLayout
import app.room.ui.chat.GifResults
import app.room.ui.chat.MessageRow
import app.room.ui.chat.MessageStyle
import app.room.ui.rightcards.CardAddMedia
import app.room.ui.rightcards.CardSharedPlaylist
import app.room.ui.statinfo.RoomStatusInfoSection
import app.room.ui.tabs.RoomRail
import app.server.ServerHostSession
import app.server.ServerStatus
import app.server.ui.ServerHostPanel
import app.subtitles.SubtitleResult
import app.theme.ThemeCreatorScreenUI
import app.theme.ThemeMenu
import app.uicomponents.PopupMediaDirs
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.PrimaryAction
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Text
import app.uicomponents.frames.ModalFrame
import app.uicomponents.frames.ModalSize
import app.uicomponents.frames.PanelSurface
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Goldens for the surfaces that had none: each one at 360 dp, at the default text size and at
 * 1.3, with the empty, loading and error states that it has. Every render fails on clipped text
 * and on a control with no spoken name.
 */
class SurfaceGoldens {

    private val scales = listOf(1f, 1.3f)

    private fun DesignHarness.Result.texts() = textLayouts.map { it.layoutInput.text.text }

    @Test
    fun hostingPanelShowsItsAddressesOrWhyThereIsNone() {
        val session = ServerHostSession
        val before = listOf(session.serverStatus.value, session.deviceIpAddress.value, session.publicIpAddress.value, session.publicIpLoading.value)
        try {
            for (scale in scales) {
                DesignHarness.render("host-stopped", 360, heightDp = 1400, fontScale = scale) { ServerHostPanel() }.assertAllTextFits()
            }
            session.serverStatus.value = ServerStatus.Running
            session.publicIpLoading.value = false
            for (scale in scales) {
                session.deviceIpAddress.value = null
                session.publicIpAddress.value = null
                val none = DesignHarness.render("host-no-address", 360, heightDp = 1400, fontScale = scale) { ServerHostPanel() }
                none.assertAllTextFits()
                assertTrue(none.texts().any { it.startsWith("No network address found") }, "The panel must say why there is no address")

                session.deviceIpAddress.value = "192.168.1.20"
                session.publicIpAddress.value = "203.0.113.5"
                val both = DesignHarness.render("host-addresses", 360, heightDp = 1400, fontScale = scale) { ServerHostPanel() }
                both.assertAllTextFits()
                assertTrue(both.texts().none { it.startsWith("No network address found") })
            }
        } finally {
            session.serverStatus.value = before[0] as ServerStatus
            session.deviceIpAddress.value = before[1] as String?
            session.publicIpAddress.value = before[2] as String?
            session.publicIpLoading.value = before[3] as Boolean
        }
    }

    @Test
    fun themePickerAndEditorFit() {
        for (scale in scales) {
            DesignHarness.render("theme-picker", 360, heightDp = 1200, fontScale = scale) { ThemeMenu(visible = true, onDismiss = {}) }.assertAllTextFits()
            DesignHarness.render("theme-editor", 360, heightDp = 2400, fontScale = scale) { ThemeCreatorScreenUI() }.assertAllTextFits()
        }
    }

    @Test
    fun aPersonsMessagesAndTheFadingChatFit() {
        val first = Message(sender = "Christopher_Lee", content = "Did everyone get the new episode? Mine stopped at the part where the train leaves the station.", epochMs = 1_000)
        val grouped = Message(sender = "Christopher_Lee", content = "Never mind, it works now.", epochMs = 20_000)
        val mine = Message(sender = "Alexandra_Morgan", content = "Ready when you are", isMainUser = true, epochMs = 200_000)
        for (scale in scales) {
            RoomRig.render("chat-person", 360, heightDp = 400, fontScale = scale, solo = false) {
                val style = MessageStyle(12, outline = null, shadow = false, showTime = true)
                Column {
                    MessageRow(first, null, MessagePalette(), style)
                    MessageRow(grouped, first, MessagePalette(), style)
                    MessageRow(mine, grouped, MessagePalette(), style)
                }
            }.assertAllTextFits()

            val fading = RoomRig.render("chat-fading", 360, heightDp = 400, fontScale = scale, solo = false, overVideo = true, setup = { room ->
                room.uiState.visibleHUD.value = false
                room.session.messageSequence.value = listOf(first, mine)
            }) { FadingMessageLayout() }
            fading.assertAllTextFits()
            assertTrue(fading.texts().any { it.contains("train leaves the station") }, "The fading chat must show the message")
        }
    }

    @Test
    fun gifResultsShowLoadingFailureEmptyAndTheGrid() {
        val tiles = (1L..6L).map { KlipyMedia(id = it, title = "Cat $it", previewUrl = "https://example.com/$it.gif", fullUrl = "https://example.com/$it.gif") }
        val states = mapOf(
            "loading" to Triple(true, false, emptyList()),
            "failed" to Triple(false, true, emptyList()),
            "empty" to Triple(false, false, emptyList()),
            "grid" to Triple(false, false, tiles),
        )
        for (scale in scales) for ((state, s) in states) {
            val result = DesignHarness.render("gif-$state", 360, heightDp = 320, fontScale = scale, overVideo = true) {
                PanelSurface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        GifResults(s.first, s.second, s.third, isLoadingMore = false, gridState = rememberLazyGridState(), isHUDVisible = true,
                            untitledGif = "GIF", onRetry = {}, onSend = {}, onLongPress = {})
                    }
                }
            }
            // Loading and the grid draw no text of their own, only a bar and the tiles.
            if (state == "failed" || state == "empty") result.assertAllTextFits()
        }
    }

    @Test
    fun statusLineRailAndControlPanelFit() {
        for (scale in scales) {
            RoomRig.render("room-status", 360, heightDp = 200, fontScale = scale, solo = false, withVideo = true) { RoomStatusInfoSection() }.assertAllTextFits()
            // A failed join keeps its reason in the status line until an attempt connects.
            val failed = RoomRig.render("room-status-failed", 360, heightDp = 200, fontScale = scale, solo = false, withVideo = true, setup = { room ->
                room.networkManager.state.value = ConnectionState.DISCONNECTED
                room.networkManager.lastFailure.value = ConnectionFailure.NameNotFound
            }) { RoomStatusInfoSection() }
            failed.assertAllTextFits()
            assertTrue(failed.texts().contains("Name not found"), failed.texts().toString())
            RoomRig.render("room-rail", 360, heightDp = 600, fontScale = scale, solo = false, withVideo = true) { RoomRail() }
            RoomRig.render("room-rail-horizontal", 360, heightDp = 120, fontScale = scale, solo = false, withVideo = true) { RoomRail(horizontal = true) }
            // The control panel is glyphs only: the render checks that each one has a spoken name.
            RoomRig.render("room-control-panel", 360, heightDp = 600, fontScale = scale, solo = false, withVideo = true) {
                RoomControlPanelCard(Modifier)
            }
        }
    }

    @Test
    fun playlistPanelsAndMediaFoldersFit() {
        val long = "[Group] A Very Long Series Name That Never Ends - S02E11 - The Episode Title (1080p).mkv"
        for (scale in scales) {
            RoomRig.render("playlist-empty", 360, heightDp = 500, fontScale = scale, solo = false) {
                CardSharedPlaylist.SharedPlaylistCard()
            }.assertAllTextFits()

            val rows = RoomRig.render("playlist-rows", 360, heightDp = 300, fontScale = scale, solo = false) {
                PanelSurface(Modifier.fillMaxSize()) {
                    Column {
                        ListRow(selected = true) { with(CardSharedPlaylist) { PlaylistEntry("Episode 01.mkv", playing = true, missing = false) } }
                        ListRow { with(CardSharedPlaylist) { PlaylistEntry(long, playing = false, missing = true) } }
                        ListRow { with(CardSharedPlaylist) { PlaylistEntry("https://example.com/stream.m3u8", playing = false, missing = false) } }
                    }
                }
            }
            rows.assertAllTextFits()
            val cut = rows.texts().single { it.contains('…') }
            assertTrue(cut.endsWith("(1080p).mkv"), "A long name must keep its ending: $cut")

            RoomRig.render("add-media", 360, heightDp = 600, fontScale = scale, solo = false) {
                CardAddMedia.AddMediaPanel(androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
            }.assertAllTextFits()

            DesignHarness.render("media-folders", 360, heightDp = 700, fontScale = scale) {
                ModalFrame(ModalSize.Panel, strings.mediaFolders, true, {}, actions = {
                    SecondaryAction(strings.mediaDirectoriesClearAll, onClick = {})
                    PrimaryAction(strings.mediaDirectoriesAddFolder, onClick = {})
                }, inset = false) {
                    PopupMediaDirs.MediaFolderRow("/storage/Movies", FolderState.Checking, {}, {})
                    PopupMediaDirs.MediaFolderRow("/storage/Old shows", FolderState.Lost, {}, {})
                    PopupMediaDirs.MediaFolderRow("/storage/Series", FolderState.Open(12), {}, {})
                    PopupMediaDirs.MediaFolderRow("/storage/One", FolderState.Open(1), {}, {})
                }
            }.assertAllTextFits()

            DesignHarness.render("playlist-missing-ask", 360, heightDp = 500, fontScale = scale) {
                ModalFrame(ModalSize.Ask, strings.roomSharedPlaylistMissingTitle, true, {}, actions = {
                    SecondaryAction(strings.roomSharedPlaylistButtonSetMediaDirectories, onClick = {})
                    PrimaryAction(strings.roomSharedPlaylistFindFile, onClick = {})
                }) {
                    Text(strings.roomSharedPlaylistNotFound("Episode 02.mkv"))
                }
            }.assertAllTextFits()
        }
    }

    @Test
    fun subtitleSearchAndChaptersFit() {
        val results = listOf(
            SubtitleResult(fileId = 1, filename = "episode.srt", releaseInfo = "A.Series.S02E11.1080p.WEB.x264", language = "en", downloadCount = 1200, hearingImpaired = true),
            SubtitleResult(fileId = 2, filename = "episode.fr.srt", releaseInfo = "", language = "fr", downloadCount = 35, hearingImpaired = false),
        )
        val states = mapOf(
            "empty" to Triple(false, null, emptyList<SubtitleResult>()),
            "searching" to Triple(true, null, emptyList()),
            "error" to Triple(false, "The subtitle service did not answer. Try again in a minute.", emptyList()),
            "results" to Triple(false, null, results),
        )
        for (scale in scales) {
            for ((state, s) in states) {
                DesignHarness.render("subtitle-search-$state", 360, heightDp = 600, fontScale = scale) {
                    ModalFrame(ModalSize.Panel, strings.roomSubSearchTitle, true, {}, actions = null, inset = false) {
                        SubtitleSearchResults(s.first, s.second, s.third, downloading = null, downloadedOk = null, onPick = {})
                    }
                }.assertAllTextFits()
            }
            RoomRig.render("subtitle-search-open", 360, heightDp = 700, fontScale = scale, solo = false) {
                SubtitleSearchModal(open = true, onDismiss = {})
            }.assertAllTextFits()

            RoomRig.render("chapters", 360, heightDp = 700, fontScale = scale, solo = false, setup = { room ->
                room.playerManager.media.value = MediaFile(location = MediaFileLocation.Remote("https://example.com/clip.mp4"), fileName = "clip.mp4").apply {
                    chapters.addAll(listOf(Chapter(0, "Opening", 0), Chapter(1, "The long middle part of the story", 90_000), Chapter(2, "Credits", 1_380_000)))
                }
            }) { ChaptersModal(open = true, onDismiss = {}) }.assertAllTextFits()
            RoomRig.render("chapters-empty", 360, heightDp = 400, fontScale = scale, solo = false, withVideo = true) {
                ChaptersModal(open = true, onDismiss = {})
            }.assertAllTextFits()
        }
    }

    @Test
    fun certificatePromptAndHostFingerprintFit() {
        val fingerprint = HostCertificate.create().fingerprint
        for (scale in scales) {
            for ((name, pinned) in listOf("new" to null, "changed" to "AA:BB")) {
                val ask = RoomRig.render("tls-ask-$name", 360, heightDp = 700, fontScale = scale, solo = false, setup = { room ->
                    room.networkManager.untrustedCertificate.value = UntrustedCertificate("192.168.1.20", 8999, fingerprint, pinned)
                }) { CertificateAsk(LocalRoomViewmodel.current) }
                ask.assertAllTextFits()
                assertTrue(ask.texts().any { it.startsWith(fingerprint.take(23)) }, "The prompt must show the fingerprint")
            }
        }
        val session = ServerHostSession
        try {
            session.serverStatus.value = ServerStatus.Running
            session.deviceIpAddress.value = "192.168.1.20"
            session.tlsFingerprint.value = fingerprint
            for (scale in scales) {
                val panel = DesignHarness.render("host-tls", 360, heightDp = 1600, fontScale = scale) { ServerHostPanel() }
                panel.assertAllTextFits()
                assertTrue(panel.texts().contains("Certificate fingerprint"))
            }
        } finally {
            session.serverStatus.value = ServerStatus.Stopped
            session.deviceIpAddress.value = null
            session.tlsFingerprint.value = null
        }
    }

    @Test
    fun theRemainingSettingsCategoriesFit() {
        val categories = mapOf("general" to GLOBAL_GENERAL, "language" to GLOBAL_LANGUAGE, "syncing" to GLOBAL_SYNCING, "room-haptics" to INROOM_HAPTICS, "room-advanced" to INROOM_ADVANCED)
        for (scale in scales) for ((name, category) in categories) {
            val result = DesignHarness.render("settings-$name", 360, heightDp = 2400, fontScale = scale) { SettingsCategoryBody(category) }
            assertTrue(result.contentHeightDp < 2400, "The $name category ran past the canvas")
            result.assertAllTextFits()
        }
    }
}
