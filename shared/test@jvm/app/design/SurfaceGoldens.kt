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
import app.protocol.network.CertificatePins
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
import java.io.File
import io.github.vinceglb.filekit.PlatformFile
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
import app.room.ui.statinfo.RoomStatusLine
import app.room.ui.statinfo.STATE_TEXT_FLOOR
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import app.player.resolver.ResolvedMedia
import app.preferences.Preferences
import app.preferences.set
import app.preferences.value
import app.protocol.models.User
import app.server.ServerLogEntry
import app.server.ServerLogEvent
import app.uicomponents.frames.PanelFrame
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun hostingPanelShowsStartingFailureLoadingAndLog() {
        val session = ServerHostSession
        val status = session.serverStatus.value
        val detail = session.statusDetail.value
        val addresses = listOf(session.deviceIpAddress.value, session.publicIpAddress.value)
        val loading = session.publicIpLoading.value
        val log = session.serverLogs.toList()
        try {
            session.serverLogs.clear()
            session.serverStatus.value = ServerStatus.Starting
            for (scale in scales) {
                val starting = DesignHarness.render("host-starting", 360, heightDp = 1400, fontScale = scale) { ServerHostPanel() }
                starting.assertAllTextFits()
                assertTrue(starting.texts().contains("Starting…"), starting.texts().toString())
            }

            // A failed start says why, in red under the status.
            session.serverStatus.value = ServerStatus.Error
            session.statusDetail.value = ServerLogEvent.PortTaken(8999)
            for (scale in scales) {
                val failed = DesignHarness.render("host-failed", 360, heightDp = 1400, fontScale = scale) { ServerHostPanel() }
                failed.assertAllTextFits()
                assertTrue(failed.texts().contains("Port 8999 is taken"), "The panel must say why the server failed")
            }

            // The public address loads after the local one: a label and a progress bar.
            session.statusDetail.value = null
            session.serverStatus.value = ServerStatus.Running
            session.deviceIpAddress.value = "192.168.1.20"
            session.publicIpAddress.value = null
            session.publicIpLoading.value = true
            for (scale in scales) {
                val lookup = DesignHarness.render("host-public-loading", 360, heightDp = 1400, fontScale = scale) { ServerHostPanel() }
                lookup.assertAllTextFits()
                assertTrue(lookup.texts().contains("Over the internet"), lookup.texts().toString())
            }

            // The end of the log, under its heading and the "errors only" filter tag.
            session.publicIpLoading.value = false
            session.deviceIpAddress.value = null
            session.serverStatus.value = ServerStatus.Stopped
            session.serverLogs.addAll(serverLog)
            for (scale in scales) {
                val rows = DesignHarness.render("host-log", 360, heightDp = 2000, fontScale = scale) { ServerHostPanel() }
                rows.assertAllTextFits()
                // The heading draws its text in capitals.
                assertTrue(rows.texts().any { it.equals("Server Log", ignoreCase = true) } && "Errors only" in rows.texts(), rows.texts().toString())
                assertTrue(rows.texts().contains("Christopher_Lee dropped after 3 wrong operator passwords"), "The log must show its rows")
            }
        } finally {
            session.serverStatus.value = status
            session.statusDetail.value = detail
            session.deviceIpAddress.value = addresses[0]
            session.publicIpAddress.value = addresses[1]
            session.publicIpLoading.value = loading
            session.serverLogs.clear()
            session.serverLogs.addAll(log)
        }
    }

    /** A log with each severity. The times are fixed, so the clock column is the same in every run. */
    private val serverLog = listOf(
        ServerLogEvent.Started(8999),
        ServerLogEvent.Joined("Alexandra_Morgan", "movie-night"),
        ServerLogEvent.DroppedForBadPasswords("Christopher_Lee", 3),
        ServerLogEvent.TimedOut("guest", 30),
        ServerLogEvent.StartFailed("Address already in use"),
        ServerLogEvent.Disconnected("Alexandra_Morgan"),
    ).mapIndexed { i, event -> ServerLogEntry(seq = i + 1L, timestamp = 1_700_000_000_000L + i * 61_000L, event = event) }

    @Test
    fun aPersonsMessagesAndTheFadingChatFit() {
        val first = Message(sender = "Christopher_Lee", content = "Did everyone get the new episode? Mine stopped at the part where the train leaves the station.", epochMs = 1_000, timestamp = DesignHarness.CHAT_TIME)
        val grouped = Message(sender = "Christopher_Lee", content = "Never mind, it works now.", epochMs = 20_000, timestamp = DesignHarness.CHAT_TIME)
        val mine = Message(sender = "Alexandra_Morgan", content = "Ready when you are", isMainUser = true, epochMs = 200_000, timestamp = DesignHarness.CHAT_TIME)
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
    fun statusLineShowsEveryConnectionState() {
        for (scale in scales) {
            for ((name, state) in listOf("connecting" to ConnectionState.CONNECTING, "reconnecting" to ConnectionState.SCHEDULING_RECONNECT)) {
                RoomRig.render("room-status-$name", 360, heightDp = 200, fontScale = scale, solo = false, withVideo = true, setup = { room ->
                    room.networkManager.state.value = state
                }) { RoomStatusInfoSection() }.assertOneRow()
            }
            // Connected with nobody to wait for: the user count.
            RoomRig.render("room-status-users", 360, heightDp = 200, fontScale = scale, solo = false, withVideo = true, setup = { room ->
                room.networkManager.state.value = ConnectionState.CONNECTED
                room.session.userList.value = listOf("Mia", "Sam", "Leo").map { person(it, ready = false, file = null) } +
                    person(room.session.currentUsername, ready = false)
            }) { RoomStatusInfoSection() }.assertOneRow()

            // Who the room waits for, from the real readiness summary. A series file adds its episode
            // tag, which takes room too. At text scale 1 each line fits whole, except the long name.
            // At 1.3 the waiting text may reach its floor and end in "…", and nothing else is cut.
            val waiting = listOf(
                WaitCase("waiting-one", "Waiting for Christopher_Lee", listOf(person("Christopher_Lee", ready = false)), series = false),
                WaitCase("waiting-long", "Waiting for $LONG_NAME", listOf(person(LONG_NAME, ready = false)), series = true),
                WaitCase("waiting-many", "Waiting for 3 people", listOf("Mia", "Sam", "Leo").map { person(it, ready = false) }, series = true),
                WaitCase("ready", "Everyone is ready (3)", listOf("Mia", "Sam").map { person(it, ready = true) }, series = true),
            )
            for (case in waiting) {
                val line = RoomRig.render("room-status-${case.name}", 360, heightDp = 200, fontScale = scale, solo = false, setup = { room ->
                    val file = if (case.series) "A.Series.S02E11.1080p.mkv" else "clip.mp4"
                    room.playerManager.media.value = MediaFile(location = MediaFileLocation.Remote("https://example.com/$file"), fileName = file)
                    room.networkManager.state.value = ConnectionState.CONNECTED
                    room.session.userList.value = case.peers + person(room.session.currentUsername, ready = false)
                    room.readiness.evaluate()
                }) { RoomStatusInfoSection() }
                line.assertGivesWayAtTheEnd(case.line)
                if (scale == 1f && case.name != "waiting-long") line.assertOneRow()
            }

            // The countdown runs on the wall clock, so the line is drawn from its values.
            val countdown = DesignHarness.render("room-status-countdown", 360, heightDp = 200, fontScale = scale) {
                RoomStatusLine(ConnectionState.CONNECTED, encrypted = true, room = "movie-night", state = strings.roomStartingIn(5), episode = "S02E11", onReconnect = {})
            }
            countdown.assertGivesWayAtTheEnd("Starting in 5")
            if (scale == 1f) countdown.assertOneRow()
        }
    }

    /** The status line is one row: no text in it is cut, and none wraps to a second line. */
    private fun DesignHarness.Result.assertOneRow() {
        assertAllTextFits()
        for (layout in textLayouts) assertEquals(1, layout.lineCount, "${file.name}: \"${layout.layoutInput.text.text}\" wraps")
    }

    /**
     * A name too long for the line: the room name and the episode tag stay whole, and the state
     * text shrinks to its floor before it ends in "…". Every text keeps to one line.
     */
    private fun DesignHarness.Result.assertGivesWayAtTheEnd(state: String) {
        assertTrue(texts().contains(state), "${file.name}: no \"$state\" in ${texts()}")
        for (layout in textLayouts) {
            val text = layout.layoutInput.text.text
            assertEquals(1, layout.lineCount, "${file.name}: \"$text\" wraps")
            val cut = layout.multiParagraph.didExceedMaxLines || layout.isLineEllipsized(0)
            if (text != state) {
                assertFalse(cut, "${file.name}: \"$text\" is cut")
            } else if (cut) {
                assertEquals(STATE_TEXT_FLOOR, layout.layoutInput.style.fontSize, "${file.name}: the state text is cut before it reaches its smallest size")
            }
        }
    }

    /** One readiness state of the status line: the people with a file, and the line they give. */
    private class WaitCase(val name: String, val line: String, val peers: List<User>, val series: Boolean)

    private val clip = MediaFile(location = MediaFileLocation.Remote("https://example.com/clip.mp4"), fileName = "clip.mp4")

    private fun person(name: String, ready: Boolean, file: MediaFile? = clip) = User(name = name, readiness = ready, file = file, isController = false)

    @Test
    fun linkFormShowsItsNoteLookupAndFailure() {
        val link = "https://www.youtube.com/watch?v=aqz-KE-bpKQ"
        val found = ResolvedMedia(directUrl = "https://example.com/stream.m3u8", title = "An open movie about a rabbit, a forest and three bullies", durationSec = 634.0)
        val states = listOf(
            "empty" to CardAddMedia.LinkFormState(),
            "looking-up" to CardAddMedia.LinkFormState(url = link, resolving = true),
            "failed" to CardAddMedia.LinkFormState(url = link, failed = true),
            "found" to CardAddMedia.LinkFormState(url = link, preview = found),
        )
        for (scale in scales) for ((name, form) in states) {
            val kind = if (form.url.isEmpty()) CardAddMedia.LinkKind.Empty else CardAddMedia.LinkKind.Resolvable
            val result = RoomRig.render("add-media-link-$name", 360, heightDp = 500, fontScale = scale, solo = false) {
                PanelFrame(title = strings.roomRouteLink, shape = RoundedCornerShape(0.dp), centerTitle = true) {
                    CardAddMedia.LinkFormBody(form, kind, remember { FocusRequester() }, onPaste = {}, onCancel = {}, onPlay = {})
                }
            }
            result.assertAllTextFits()
            val expected = when (name) {
                "empty" -> "A direct link, or a link from a media site"
                "looking-up" -> "Looking it up"
                "failed" -> "Play anyway"
                else -> "An open movie about a rabbit, a forest and three bullies  10:34"
            }
            assertTrue(result.texts().contains(expected), "$name: ${result.texts()}")
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

            val dirs = Preferences.MEDIA_DIRECTORIES.value()
            setMediaFolders(emptySet())
            try {
                val empty = DesignHarness.render("media-folders-empty", 360, heightDp = 700, fontScale = scale) {
                    PopupMediaDirs.MediaDirsPopup(remember { mutableStateOf(true) })
                }
                empty.assertAllTextFits()
                assertTrue(empty.texts().contains("No folders yet."), empty.texts().toString())
                assertTrue(empty.texts().any { it.endsWith("Small folders are faster.") }, "The popup must open with its intro line")
            } finally {
                setMediaFolders(dirs)
            }

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
                // A local file that does not exist: the chapter stills fail at once, with no network.
                room.playerManager.media.value = MediaFile(location = MediaFileLocation.Local(PlatformFile(File("missing/clip.mp4"))), fileName = "clip.mp4").apply {
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
        // A fixed fingerprint. A new certificate would give a new one in every run.
        val fingerprint = CertificatePins.fingerprintOf("synkplay golden certificate".encodeToByteArray())
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

    /** Saves the media folders and waits until the store reads them back. */
    private fun setMediaFolders(dirs: Set<String>) = runBlocking {
        Preferences.MEDIA_DIRECTORIES.set(dirs)
        var tries = 0
        while (Preferences.MEDIA_DIRECTORIES.value() != dirs && tries++ < 50) Thread.sleep(20)
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

    private companion object {
        /** A long name, below the 64 characters that the app allows. */
        const val LONG_NAME = "Alexandra_Morgan_Whitfield"
    }
}
