package app.preferences

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import app.i18n.strings
import app.utils.ioDispatcher
import app.utils.rememberFileSaver
import app.utils.writeBytesCompat
import io.github.vinceglb.filekit.readString
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LogoDev
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.filled.BlurOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.edit
import app.theme.defaultTheme
import app.room.models.MessagePalette
import app.preferences.settings.SettingRow
import app.preferences.settings.TrustedDomainsPopup
import app.uicomponents.PopupMediaDirs.MediaDirsPopup
import app.utils.Platform
import app.utils.appName
import app.utils.availablePlatformPlayerEngines
import app.utils.generateTimestampMillis
import app.utils.get
import app.utils.clearLogs
import app.utils.getMpvConfFilePath
import app.utils.loggy
import app.utils.logFilesForExport
import app.utils.writeFilesCompat
import app.utils.readFileBytes
import app.utils.writeFileBytes
import app.utils.platform
import app.utils.platformCallback
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import app.preferences.settings.SETTINGS_GLOBAL
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.Text
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize

/**
 * Every preference of the app: its stored key, its typed default and, when it has one, its
 * settings row.
 */
object Preferences {

    /**
     * Call this to make sure that every preference exists.
     *
     * A Kotlin object initialises on first access, and every [Pref] registers itself when it is
     * constructed. Code that reads [PrefRegistry] before anything touched this object sees an
     * empty registry, and the settings export would then write nothing.
     */
    fun ensureAllRegistered() = Unit

    const val SYNKPLAY_PREFS = "syncplayprefs.preferences_pb"

    /** ------------ Miscellaneous -------------*/
    val USER_ID = Pref<String?>("misc_user_id", null)

    /**
     * The salt for the controlled rooms of the hosted server. It is created once, so operator
     * passwords stay valid after a restart.
     */
    val SERVER_SALT = Pref("misc_server_salt", "")

    /** How many cold starts have shown the tips. The tips stop on their own after a few. */
    val TIPS_SHOWN_COUNT = Pref("misc_tips_shown_count", 0)
    val JOIN_CONFIG = Pref<String?>("misc_join_config", null)
    val PLAYER_ENGINE = Pref("misc_player_engine", availablePlatformPlayerEngines.first { it.isDefault }.name)
    val GESTURES = Pref("misc_gestures", true)

    /** Whether the room follows the device's rotation instead of being held in landscape. */
    val ROOM_ALLOW_PORTRAIT = Pref("pref_room_allow_portrait", false) {
        title = { it.settingRoomPortraitTitle }
        summary = { it.settingRoomPortraitSummary }
        icon = Icons.Filled.ScreenRotation
    }
    val CURRENT_THEME = Pref("misc_current_theme", defaultTheme.asString())
    val CUSTOM_THEMES = Pref<Set<String>>("misc_custom_themes", emptySet())
    val KLIPY_FAVORITES = Pref<Set<String>>("misc_klipy_favorites", emptySet())

    /** Where each recently watched file was left, as JSON. See [app.player.ResumePoint]. */
    val RESUME_POSITIONS = Pref("misc_resume_positions", "")

    /** Whether to offer to resume a file where it was left. The offer appears only in solo mode. */
    val RESUME_PLAYBACK = Pref("pref_resume_playback", true) {
        title = { it.settingResumeTitle }
        summary = { it.settingResumeSummary }
        icon = Icons.Filled.History
    }

    /**
     * Whether the GIF panel keeps the same id with the GIF service from one launch to the next.
     * When on, the service can show the user's own picks under Recents. When off, each launch
     * gets a new id, so nothing links two launches, and Recents comes back empty.
     */
    val GIF_REMEMBER_RECENTS = Pref("pref_inroom_gif_remember_recents", true) {
        title = { it.settingGifRecentsTitle }
        summary = { it.settingGifRecentsSummary }
        icon = Icons.Filled.Gif
    }
    /** When true, the "Undo seek" action skips its confirmation dialog. The dialog's "Don't ask
     * again" button sets it. It has no SettingConfig, so it never appears in the settings UI. */
    val UNDO_SEEK_NO_CONFIRM = Pref("misc_undo_seek_no_confirm", false)

    /** ------------ General -------------*/
    val REMEMBER_INFO = Pref("pref_remember_info", true) {
        title = { it.settingRememberJoinInfoTitle }
        summary = { it.settingRememberJoinInfoSummary }
        icon = Icons.Filled.Face
    }
    val NEVER_SHOW_TIPS = Pref("pref_never_show_tips", false) {
        title = { it.settingNeverShowTipsTitle }
        summary = { it.settingNeverShowTipsSummary }
        icon = Icons.Filled.Lightbulb
    }
    /** Shows the explanation under every settings row, not only after a long press. */
    val SHOW_SETTING_DESCRIPTIONS = Pref("pref_show_setting_descriptions", false) {
        title = { it.settingsShowDescriptionsTitle }
        summary = { it.settingsShowDescriptionsSummary }
        icon = Icons.Filled.Lightbulb
    }
    /**
     * The desktop window's last size, position and placement, as "x,y,w,h,placement". It never
     * shows as a row.
     */
    val DESKTOP_WINDOW = Pref("pref_desktop_window", "")

    /**
     * Makes transitions and animations instant. The platform's reduced-motion setting has the
     * same effect on its own.
     */
    val REDUCE_MOTION = Pref("pref_reduce_motion", false) {
        title = { it.settingReduceMotionTitle }
        summary = { it.settingReduceMotionSummary }
        icon = Icons.Filled.Timer
    }

    /** The control haptics (a rocker flip, a seek landing), separate from the room event pulses. */
    val HAPTICS_ON_CONTROLS = Pref("pref_haptics_on_controls", true) {
        title = { it.settingsHapticsControlsTitle }
        summary = { it.settingsHapticsControlsSummary }
        icon = Icons.Filled.Vibration
    }
    val ERASE_SHORTCUTS = Pref("pref_erase_shortcuts", "") {
        title = { it.settingEraseShortcutsTitle }
        summary = { it.settingEraseShortcutsSummary }
        icon = Icons.Filled.BookmarkRemove

        extraConfig = PrefExtraConfig.YesNoDialog(
            rationale = { it.settingEraseShortcutsDialog },
            destructive = true,
            onYes = {
                platformCallback.onEraseConfigShortcuts()
            }
        )
    }
    val MEDIA_DIRECTORIES = Pref<Set<String>>("pref_syncplay_media_directories", emptySet()) {
        title = { it.mediaDirectories }
        summary = { it.mediaDirectoriesSettingSummary }
        icon = Icons.AutoMirrored.Filled.QueueMusic

        extraConfig = PrefExtraConfig.ShowComposable(
            composable = { MediaDirsPopup(this) }
        )
    }

    /** ------------ Language -------------*/
    /** Blank means the device's language. A language code sets the app to that language. */
    val DISPLAY_LANG = Pref("pref_lang", "") {
        title = { it.settingDisplayLanguageTitle }
        summary = { it.settingDisplayLanguageSummry(appName) }
        icon = Icons.Filled.Translate

        /* The same picker on every platform. The app holds its own strings, so a choice takes
         * effect at once, with no restart and no trip to the system settings. */
        extraConfig = PrefExtraConfig.MultiChoice(
            entries = {
                linkedMapOf(strings.settingDisplayLanguageSystem to "") +
                    strings.languageNames.zip(strings.languageCodes).toMap()
            }
        )
    }
    val AUDIO_LANG = Pref("pref_audio_preferred_lang", "eng") {
        title = { it.settingAudioDefaultLanguageTitle }
        summary = { it.settingAudioDefaultLanguageSummry }
        icon = Icons.Filled.SpatialAudio

        extraConfig = PrefExtraConfig.MultiChoice(
            entries = { mediaLanguageEntries() }
        )
    }
    val CC_LANG = Pref("pref_cc_preferred_lang", "eng") {
        title = { it.settingCcDefaultLanguageTitle }
        summary = { it.settingCcDefaultLanguageSummry }
        icon = Icons.Filled.ClosedCaptionOff

        extraConfig = PrefExtraConfig.MultiChoice(
            entries = { mediaLanguageEntries() }
        )
    }

    /**
     * The language of the OpenSubtitles "download from web" search. It holds an ISO 639-1
     * (2-letter) code, not the 3-letter [mediaLanguages] codes of the track preferences, because
     * the OpenSubtitles API uses 2-letter codes. The value "all" means "do not filter by language".
     * The subtitle search sheet picks it inline, so it has no settings row of its own.
     */
    val SUBTITLE_SEARCH_LANG = Pref("pref_subtitle_search_lang", "en")

    /** ------------ Syncing -------------*/
    val READY_FIRST_HAND = Pref("pref_ready_first_hand", true) {
        title = { it.settingReadyFirsthandTitle }
        summary = { it.settingReadyFirsthandSummary }
        icon = Icons.Filled.TaskAlt
    }
    /** Whether the room starts on its own once everyone with a file says that they are ready. */
    val AUTOPLAY = Pref("pref_inroom_autoplay", false) {
        title = { it.settingAutoplayTitle }
        summary = { it.settingAutoplaySummary }
        icon = Icons.Filled.PlayCircle
    }
    val UNPAUSE_ACTION = Pref("pref_unpause_action", "IfOthersReady") {
        title = { it.settingUnpauseActionTitle }
        summary = { it.settingUnpauseActionSummary }
        icon = Icons.Filled.PlayArrow

        extraConfig = PrefExtraConfig.MultiChoice(
            entries = {
                mapOf(
                    strings.settingUnpauseActionIfReady to "IfAlreadyReady",
                    strings.settingUnpauseActionIfOthersReady to "IfOthersReady",
                    strings.settingUnpauseActionIfMinUsersReady to "IfMinUsersReady",
                    strings.settingUnpauseActionAlways to "Always"
                )
            }
        )
    }
    val PAUSE_ON_SOMEONE_LEAVE = Pref("pref_pause_if_someone_left", false) {
        title = { it.settingPauseIfSomeoneLeftTitle }
        summary = { it.settingPauseIfSomeoneLeftSummary }
        icon = Icons.Filled.FrontHand
    }
    val FILE_MISMATCH_WARNING = Pref("pref_file_mismatch_warning", true) {
        title = { it.settingWarnFileMismatchTitle }
        summary = { it.settingWarnFileMismatchSummary }
        icon = Icons.Filled.ErrorOutline
    }
    val HASH_FILENAME = Pref("pref_hash_filename", "1") {
        title = { it.settingFileinfoBehaviourNameTitle }
        summary = { it.settingFileinfoBehaviourNameSummary }
        icon = Icons.Filled.DesignServices

        extraConfig = PrefExtraConfig.MultiChoice(
            entries = {
                mapOf(
                    strings.settingFileinfoBehaviorA to "1",
                    strings.settingFileinfoBehaviorB to "2",
                    strings.settingFileinfoBehaviorC to "3"
                )
            }
        )
    }
    val HASH_FILESIZE = Pref("pref_hash_filesize", "1") {
        title = { it.settingFileinfoBehaviourSizeTitle }
        summary = { it.settingFileinfoBehaviourSizeSummary }
        icon = Icons.Filled.DesignServices

        extraConfig = PrefExtraConfig.MultiChoice(
            entries = {
                mapOf(
                    strings.settingFileinfoBehaviorA to "1",
                    strings.settingFileinfoBehaviorB to "2",
                    strings.settingFileinfoBehaviorC to "3"
                )
            }
        )
    }

    /** ------------ Network -------------*/
    val NETWORK_ENGINE = Pref("pref_network_engine", if (platform == Platform.IOS) "swiftnio" else "netty") {
        title = { it.settingNetworkEngineTitle }
        summary = { it.settingNetworkEngineSummary }
        icon = Icons.Filled.Lan

        extraConfig = PrefExtraConfig.MultiChoice(
            entries = {
                buildMap {
                    if (platform == Platform.IOS) {
                        put(strings.settingNetworkEngineSwiftNio, "swiftnio")
                    } else {
                        // Android and desktop run the Netty engine.
                        put(strings.settingNetworkEngineNetty, "netty")
                    }

                    put(strings.settingNetworkEngineKtor, "ktor")
                }
            }
        )
    }
    val TLS_ENABLE = Pref("pref_tls", true) {
        title = { it.settingTlsTitle }
        summary = { it.settingTlsSummary }
        detail = { it.settingTlsDetail }
        icon = Icons.Filled.Key
    }
    /** With this on, a server that cannot encrypt is refused instead of joined in plain text. */
    /** Server certificates trusted by their fingerprint, as JSON. See CertificatePins. */
    val TLS_PINS = Pref("pref_tls_pins", "")

    val TLS_REQUIRED = Pref("pref_tls_required", false) {
        title = { it.settingTlsRequiredTitle }
        summary = { it.settingTlsRequiredSummary }
        detail = { it.settingTlsRequiredDetail }
        icon = Icons.Filled.Lock
    }
    /** When true, a page URL (YouTube, SoundCloud and so on) entered as media goes through the
     *  platform's native extractor before it reaches the player. A quick check skips the
     *  extractor when the URL is already direct media, so the common case costs nothing. */
    val MEDIA_RESOLVER_ENABLED = Pref("pref_media_resolver_enabled", true) {
        title = { it.settingMediaResolverTitle }
        summary = { it.settingMediaResolverSummary }
        detail = { it.settingMediaResolverDetail }
        icon = Icons.Filled.Language
    }

    /** One switch that turns the frosted glass effect off everywhere.
     *
     *  When true: no Haze (the blur library) capture or blur anywhere, panels use a solid tonal
     *  surface, no platform window blur, and the Android players go back to SurfaceView. A
     *  SurfaceView can use a hardware overlay plane (lower power, HDR passthrough) but cannot be
     *  captured for blur. Glass and the overlay path exclude each other, so this is one switch,
     *  not two.
     *
     *  The surface type is fixed when the player view is inflated, so a change takes effect on the
     *  next room entry, like the other engine options. */
    val DISABLE_FROSTED_GLASS = Pref("pref_disable_frosted_glass", false) {
        title = { it.settingDisableGlassTitle }
        summary = { it.settingDisableGlassSummary }
        detail = { it.settingDisableGlassDetail }
        icon = Icons.Filled.BlurOff
    }

    /** ------------ Security -------------*/
    val TRUSTED_DOMAINS = Pref("pref_trusted_domains", "youtube.com\nyoutu.be") {
        title = { it.settingTrustedDomainsTitle }
        summary = { it.settingTrustedDomainsSummary }
        detail = { it.settingTrustedDomainsDetail }
        icon = Icons.Filled.Web

        extraConfig = PrefExtraConfig.ShowComposable(
            composable = { TrustedDomainsPopup(this) }
        )
    }

    /** ------------ Sync Mechanisms (In-Room) -------------*/
    val SYNC_FASTFORWARD = Pref("pref_inroom_sync_fastforward", true) {
        title = { it.uisettingSyncFastforwardTitle }
        summary = { it.uisettingSyncFastforwardSummary }
        icon = Icons.Filled.FastForward
    }
    val SYNC_SLOWDOWN = Pref("pref_inroom_sync_slowdown", true) {
        title = { it.uisettingSyncSlowdownTitle }
        summary = { it.uisettingSyncSlowdownSummary }
        icon = Icons.Filled.SlowMotionVideo
    }
    val SYNC_REWIND = Pref("pref_inroom_sync_rewind", true) {
        title = { it.uisettingSyncRewindTitle }
        summary = { it.uisettingSyncRewindSummary }
        icon = Icons.Filled.FastRewind
    }
    /**
     * How far this client may run ahead of the room before it rewinds to the room's position, in
     * tenths of a second. The Syncplay PC client's default is 4 seconds, and it refuses anything
     * under 3, because below that ordinary jitter would trigger it all the time.
     */
    val SYNC_REWIND_THRESHOLD = Pref("pref_inroom_sync_rewind_threshold", 40) {
        title = { it.uisettingSyncRewindThresholdTitle }
        summary = { it.uisettingSyncRewindThresholdSummary }
        icon = Icons.Filled.FastRewind
        dependencyEnable = { SYNC_REWIND.value() }

        extraConfig = PrefExtraConfig.Slider(minValue = 30, maxValue = 150, unit = "s", formatValue = { formatTenths(it) })
    }

    /** How far this client may run ahead before playback slows for the room to catch up, in tenths. */
    val SYNC_SLOWDOWN_THRESHOLD = Pref("pref_inroom_sync_slowdown_threshold", 15) {
        title = { it.uisettingSyncSlowdownThresholdTitle }
        summary = { it.uisettingSyncSlowdownThresholdSummary }
        icon = Icons.Filled.SlowMotionVideo
        dependencyEnable = { SYNC_SLOWDOWN.value() }

        extraConfig = PrefExtraConfig.Slider(minValue = 5, maxValue = 60, unit = "s", formatValue = { formatTenths(it) })
    }

    /** How far this client may fall behind before it jumps forward to the room, in tenths. */
    val SYNC_FASTFORWARD_THRESHOLD = Pref("pref_inroom_sync_fastforward_threshold", 50) {
        title = { it.uisettingSyncFastforwardThresholdTitle }
        summary = { it.uisettingSyncFastforwardThresholdSummary }
        icon = Icons.Filled.FastForward
        dependencyEnable = { SYNC_FASTFORWARD.value() }

        extraConfig = PrefExtraConfig.Slider(minValue = 20, maxValue = 200, unit = "s", formatValue = { formatTenths(it) })
    }

    /**
     * How far this client's copy of the file runs ahead of the room's, in tenths of a second. The
     * stored value is offset by 600, so the slider covers minus sixty to plus sixty seconds.
     *
     * Two rips of the same film can differ by an intro, a logo card or a few frames of black. The
     * Syncplay PC client has the same per-user offset. It shifts only local playback.
     */
    val USER_TIME_OFFSET = Pref("pref_inroom_user_time_offset", 600) {
        title = { it.uisettingUserOffsetTitle }
        summary = { it.uisettingUserOffsetSummary }
        icon = Icons.Filled.MoreTime

        extraConfig = PrefExtraConfig.Slider(
            minValue = 0, maxValue = 1200, unit = "s",
            formatValue = { formatTenths(it, zeroPoint = 600, signed = true) },
        )
    }

    val SYNC_DONT_SLOW_WITH_ME = Pref("pref_inroom_sync_dont_slow_with_me", false) {
        title = { it.uisettingSyncDontSlowWithMeTitle }
        summary = { it.uisettingSyncDontSlowWithMeSummary }
        icon = Icons.Filled.Speed
    }

    /** ------------ Chat Colors -------------*/
    /** The chat's own default colours. The rows below start from them, and a reset returns to them. */
    private val chatDefaults = MessagePalette()

    /** One entry that gathers the COLOR_* prefs below as a nested page, so that the room's
     *  settings panel can show them beside the chat that they colour. */
    val CHAT_COLORS_ENTRY = Pref("pref_inroom_chat_colors_entry", "") {
        title = { it.uisettingCategChatColors }
        summary = { it.uisettingChatColorsEntrySummary }
        icon = Icons.Filled.Palette

        extraConfig = PrefExtraConfig.Nested {
            COLOR_TIMESTAMP.SettingRow()
            COLOR_SELFTAG.SettingRow()
            COLOR_FRIENDTAG.SettingRow()
            COLOR_SYSTEMMSG.SettingRow()
            COLOR_USERMSG.SettingRow()
            COLOR_ERRORMSG.SettingRow()
        }
    }

    val COLOR_TIMESTAMP = Pref("pref_inroom_color_timestamp", chatDefaults.timestampColor.toArgb()) {
        title = { it.uisettingTimestampColorTitle }
        summary = { it.uisettingTimestampSummary }
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick
    }
    val COLOR_SELFTAG = Pref("pref_inroom_color_selftag", chatDefaults.selftagColor.toArgb()) {
        title = { it.uisettingSelfColorTitle }
        summary = { it.uisettingSelfColorSummary }
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick
    }
    val COLOR_FRIENDTAG = Pref("pref_inroom_color_friendtag", chatDefaults.friendtagColor.toArgb()) {
        title = { it.uisettingFriendColorTitle }
        summary = { it.uisettingFriendColorSummary }
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick
    }
    val COLOR_SYSTEMMSG = Pref("pref_inroom_color_systemmsg", chatDefaults.systemmsgColor.toArgb()) {
        title = { it.uisettingSystemColorTitle }
        summary = { it.uisettingSystemColorSummary }
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick
    }
    val COLOR_USERMSG = Pref("pref_inroom_color_usermsg", chatDefaults.usermsgColor.toArgb()) {
        title = { it.uisettingHumanColorTitle }
        summary = { it.uisettingHumanColorSummary }
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick
    }
    val COLOR_ERRORMSG = Pref("pref_inroom_color_errormsg", chatDefaults.errormsgColor.toArgb()) {
        title = { it.uisettingErrorColorTitle }
        summary = { it.uisettingErrorColorSummary }
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick
    }

    /** ------------ Hosted server (persisted so a host does not retype them) ------------ */
    val SERVER_PORT = Pref("pref_server_port", "8999") {
        title = { it.serverHostPort }
        icon = Icons.Filled.Keyboard
        extraConfig = PrefExtraConfig.TextField(keyboardType = 1)
    }
    val SERVER_PASSWORD = Pref("pref_server_password", "") {
        title = { it.serverHostPassword }
        summary = { it.serverHostPasswordDetail }
        icon = Icons.Filled.Keyboard
        extraConfig = PrefExtraConfig.TextField()
    }
    val SERVER_MOTD = Pref("pref_server_motd", "") {
        title = { it.serverHostMotd }
        icon = Icons.Filled.Keyboard
        extraConfig = PrefExtraConfig.TextField()
    }
    val SERVER_ISOLATE_ROOMS = Pref("pref_server_isolate_rooms", true) {
        title = { it.serverHostIsolateRooms }
        icon = Icons.Filled.Pin
    }
    val SERVER_DISABLE_CHAT = Pref("pref_server_disable_chat", false) {
        title = { it.serverHostDisableChat }
        icon = Icons.Filled.Pin
    }
    val SERVER_DISABLE_READY = Pref("pref_server_disable_ready", false) {
        title = { it.serverHostDisableReady }
        icon = Icons.Filled.Pin
    }
    /** Off by default: Syncplay for PC refuses the host's own certificate, so PC users could not join. */
    val SERVER_TLS = Pref("pref_server_tls", false) {
        title = { it.serverHostTls }
        summary = { it.serverHostTlsSummary }
        icon = Icons.Filled.Lock
    }

    /** ------------ Chat Properties -------------*/
    /** Zero switches the outline off. There is no separate switch. */
    val MSG_OUTLINE_THICKNESS = Pref("pref_inroom_msg_outline_thickness", 2) {
        title = { it.uisettingMsgoutlineTitle }
        summary = { it.uisettingMsgoutlineSummary }
        icon = Icons.Filled.BorderColor

        extraConfig = PrefExtraConfig.Slider(maxValue = 30, minValue = 0, zeroMeansOff = true)
    }
    val MSG_SHADOW_ACTIVATE = Pref("pref_inroom_msg_shadow_activate", false) {
        title = { it.uisettingMsgshadowTitle }
        summary = { it.uisettingMsgshadowSummary }
        icon = Icons.Filled.BorderColor
    }
    val MSG_BG_OPACITY = Pref("pref_inroom_msg_bg_opacity", 0) {
        title = { it.uisettingMessageryAlphaTitle }
        summary = { it.uisettingMessageryAlphaSummary }
        icon = Icons.Filled.Opacity

        extraConfig = PrefExtraConfig.Slider(maxValue = 255, minValue = 0)
    }
    /** 5 to 24, default 10. Saved values stay as they are. MessageStyle uses the same floor of 5. */
    val MSG_FONTSIZE = Pref("pref_inroom_msg_fontsize", 10) {
        title = { it.uisettingMsgsizeTitle }
        summary = { it.uisettingMsgsizeSummary }
        icon = Icons.Filled.FormatSize

        extraConfig = PrefExtraConfig.Slider(maxValue = 24, minValue = 5)
    }
    /** How many recent unseen lines the fading layout shows over the video. */
    val MSG_MAXCOUNT = Pref("pref_inroom_msg_maxcount", 3) {
        title = { it.uisettingMsgcountTitle }
        summary = { it.uisettingMsgcountSummary }
        icon = Icons.Filled.FormatListNumbered

        extraConfig = PrefExtraConfig.Slider(maxValue = 10, minValue = 1)
    }
    val MSG_FADING_DURATION = Pref("pref_inroom_fading_msg_duration", 3) {
        title = { it.uisettingMsglifeTitle }
        summary = { it.uisettingMsglifeSummary }
        icon = Icons.Filled.Timer

        extraConfig = PrefExtraConfig.Slider(maxValue = 10, minValue = 1, unit = "s")
    }
    val MSG_BOX_ACTION = Pref("pref_inroom_msg_box_action", true) {
        title = { it.uisettingMsgboxactionTitle }
        summary = { it.uisettingMsgboxactionSummary }
        icon = Icons.Filled.Keyboard
    }

    val OSD_DURATION = Pref("pref_inroom_osd_duration", 2) {
        title = { it.uisettingOsdDurationTitle }
        summary = { it.uisettingOsdDurationSummary }
        icon = Icons.Filled.Timer

        extraConfig = PrefExtraConfig.Slider(maxValue = 10, minValue = 0, unit = "s")
    }

    /** ------------ OSD Notification Filters -------------
     *  These mirror the "Messages" tab toggles of the Syncplay PC client (showSameRoomOSD,
     *  showNonControllerOSD, showDifferentRoomOSD, showSlowdownOSD, showOSDWarnings). They decide
     *  which room events show as an OSD (on-screen) message through [RoomViewmodel.dispatchOSD].
     *  They do not affect the chat log. */
    /** Routine room events stay in chat by default, so that notices do not crowd the video. */
    val OSD_SAME_ROOM = Pref("pref_inroom_osd_same_room", false) {
        title = { it.uisettingOsdSameroomTitle }
        summary = { it.uisettingOsdSameroomSummary }
        icon = Icons.Filled.SupervisedUserCircle
    }
    val OSD_NON_OPERATOR = Pref("pref_inroom_osd_non_operator", true) {
        title = { it.uisettingOsdNonoperatorTitle }
        summary = { it.uisettingOsdNonoperatorSummary }
        icon = Icons.Filled.Face
        dependencyEnable = { OSD_SAME_ROOM.value() }
    }
    /** Off by default, like SHOW_DIFFERENT_ROOM_OSD = False in the Syncplay PC client. */
    val OSD_OTHER_ROOM = Pref("pref_inroom_osd_other_room", false) {
        title = { it.uisettingOsdOtherroomTitle }
        summary = { it.uisettingOsdOtherroomSummary }
        icon = Icons.Filled.Web
    }
    val OSD_SLOWDOWN = Pref("pref_inroom_osd_slowdown", true) {
        title = { it.uisettingOsdSlowdownTitle }
        summary = { it.uisettingOsdSlowdownSummary }
        icon = Icons.Filled.SlowMotionVideo
    }
    val OSD_WARNINGS = Pref("pref_inroom_osd_warnings", true) {
        title = { it.uisettingOsdWarningsTitle }
        summary = { it.uisettingOsdWarningsSummary }
        icon = Icons.Filled.ErrorOutline
    }

    /** ------------ Player Settings -------------*/
    val SUBTITLE_SIZE = Pref("pref_inroom_subtitle_size", 16) {
        title = { it.uisettingSubtitleSizeTitle }
        summary = { it.uisettingSubtitleSizeSummary }
        icon = Icons.Filled.SortByAlpha

        extraConfig = PrefExtraConfig.Slider(
            maxValue = 200, minValue = 2,
            onValueChanged = { v ->
                roomWeakRef?.get()?.player?.changeSubtitleSize(v)
            }
        )
    }

    val CUSTOM_SEEK_AMOUNT = Pref("pref_inroom_custom_seek_amount", 90) {
        title = { it.uisettingCustomSeekAmountTitle }
        summary = { it.uisettingCustomSeekAmountSummary }
        icon = Icons.Filled.Update

        extraConfig = PrefExtraConfig.Slider(maxValue = 300, minValue = 30, unit = "s")
    }
    val CUSTOM_SEEK_FRONT = Pref("pref_inroom_custom_seek_front", false) {
        title = { it.uisettingCustomSeekFrontTitle }
        summary = { it.uisettingCustomSeekFrontSummary }
        icon = Icons.Filled.Update

    }
    val SEEK_FORWARD_JUMP = Pref("pref_inroom_seek_forward_jump", 10) {
        title = { it.uisettingSeekForwardJumpTitle }
        summary = { it.uisettingSeekForwardJumpSummary }
        icon = Icons.Filled.FastForward

        extraConfig = PrefExtraConfig.Slider(maxValue = 120, minValue = 1, unit = "s")

    }
    val SEEK_BACKWARD_JUMP = Pref("pref_inroom_seek_backward_jump", 10) {
        title = { it.uisettingSeekBackwardJumpTitle }
        summary = { it.uisettingSeekBackwardJumpSummary }
        icon = Icons.Filled.FastRewind

        extraConfig = PrefExtraConfig.Slider(maxValue = 120, minValue = 1, unit = "s")
    }

    val SHOW_CHAPTER_DOTS = Pref("pref_inroom_show_chapter_dots", true) {
        title = { it.uisettingShowChapterDotsTitle }
        summary = { it.uisettingShowChapterDotsSummary }
        icon = Icons.Filled.FormatListNumbered
    }

    val CHAPTER_DOTS_CLICKABLE = Pref("pref_inroom_chapter_dots_clickable", false) {
        title = { it.uisettingChapterDotsClickableTitle }
        summary = { it.uisettingChapterDotsClickableSummary }
        icon = Icons.Filled.TouchApp
        dependencyEnable = { SHOW_CHAPTER_DOTS.value() }
    }

    /** Off by default, because a double tap to seek conflicts with a tap to show the HUD. */
    val DOUBLETAP_SEEK = Pref("pref_inroom_doubletap_seek", false) {
        title = { it.uisettingDoubletapSeekTitle }
        summary = { it.uisettingDoubletapSeekSummary }
        detail = { it.uisettingDoubletapSeekDetail }
        icon = Icons.Filled.TouchApp
    }

    val SWIPE_GESTURES = Pref("pref_inroom_swipe_gestures", true) {
        title = { it.uisettingSwipeGesturesTitle }
        summary = { it.uisettingSwipeGesturesSummary }
        icon = Icons.Filled.Swipe
    }

    /** Idle seconds during playback before the HUD hides. Zero keeps it up until a tap hides it. */
    val HUD_AUTO_HIDE_SECONDS = Pref("pref_inroom_hud_auto_hide_seconds", 15) {
        title = { it.roomHudAutoHideTitle }
        summary = { it.roomHudAutoHideSummary }
        icon = Icons.Filled.Timer

        extraConfig = PrefExtraConfig.Slider(maxValue = 30, minValue = 0, unit = "s", zeroMeansOff = true)
    }

    /**
     * The roster view: "compact", or "standard" for the expanded view. Any other saved value,
     * such as the old "files", shows the expanded view.
     */
    val USER_INFO_VIEW = Pref("pref_inroom_user_info_view", "standard")

    /**
     * Whether to show the audio visualizer. The key keeps its old KitePlayer name, so that saved
     * choices stay valid.
     */
    val AUDIO_VISUALIZATION = Pref("pref_kite_audio_viz", false) {
        title = { it.uisettingKiteAudioVizTitle }
        summary = { it.uisettingKiteAudioVizSummary }
        icon = Icons.Filled.MusicNote
    }
    /**
     * Whether the visualizer's director (its automatic drawing picker) changes the drawing with
     * the music. The tracks card sets it.
     */
    val KITE_AUDIO_VIZ_DIRECTOR = Pref("pref_kite_audio_viz_director", true)
    /** ------------ KitePlayer Settings -------------*/
    val KITE_COMPOSE_RENDERER = Pref("pref_kite_compose_renderer", false) {
        title = { it.uisettingKiteComposeRendererTitle }
        summary = { it.uisettingKiteComposeRendererSummary }
        icon = Icons.Filled.Layers
    }
    val KITE_HARDWARE_ACCELERATION = Pref("pref_kite_hw", true) {
        title = { it.uisettingKiteHwTitle }
        summary = { it.uisettingKiteHwSummary }
        icon = Icons.Filled.Speed
    }
    val KITE_SUBTITLE_AUTOSELECT = Pref("pref_kite_sub_autoselect", true) {
        title = { it.uisettingKiteSubAutoselectTitle }
        summary = { it.uisettingKiteSubAutoselectSummary }
        icon = Icons.Filled.Subtitles
    }
    val KITE_SUBTITLE_DELAY_MS = Pref("pref_kite_sub_delay_ms", 0) {
        title = { it.uisettingKiteSubDelayTitle }
        summary = { it.uisettingKiteSubDelaySummary }
        icon = Icons.Filled.Timer
    }
    val KITE_AUDIO_DELAY_MS = Pref("pref_kite_audio_delay_ms", 0) {
        title = { it.uisettingKiteAudioDelayTitle }
        summary = { it.uisettingKiteAudioDelaySummary }
        icon = Icons.Filled.Timer
    }
    val KITE_PRESERVE_PITCH = Pref("pref_kite_preserve_pitch", true) {
        title = { it.uisettingKitePreservePitchTitle }
        summary = { it.uisettingKitePreservePitchSummary }
        icon = Icons.Filled.MusicNote
    }
    val KITE_SUBTITLE_POS = Pref("pref_kite_sub_pos", 100) {
        title = { it.uisettingKiteSubPosTitle }
        summary = { it.uisettingKiteSubPosSummary }
        icon = Icons.Filled.VerticalAlignBottom
    }
    val KITE_EQ_BRIGHTNESS = Pref("pref_kite_eq_brightness", 0) {
        title = { it.uisettingKiteEqBrightnessTitle }
        summary = { it.uisettingKiteEqBrightnessSummary }
        icon = Icons.Filled.BrightnessMedium
    }
    val KITE_EQ_CONTRAST = Pref("pref_kite_eq_contrast", 100) {
        title = { it.uisettingKiteEqContrastTitle }
        summary = { it.uisettingKiteEqContrastSummary }
        icon = Icons.Filled.Contrast
    }
    val KITE_EQ_SATURATION = Pref("pref_kite_eq_saturation", 100) {
        title = { it.uisettingKiteEqSaturationTitle }
        summary = { it.uisettingKiteEqSaturationSummary }
        icon = Icons.Filled.Palette
    }
    val KITE_EQ_HUE = Pref("pref_kite_eq_hue", 0) {
        title = { it.uisettingKiteEqHueTitle }
        summary = { it.uisettingKiteEqHueSummary }
        icon = Icons.Filled.Colorize
    }
    val KITE_DEBUG_STATS = Pref("pref_kite_debug_stats", false) {
        title = { it.uisettingKiteDebugStatsTitle }
        summary = { it.uisettingKiteDebugStatsSummary }
        icon = Icons.Filled.Adb
    }

    /** ------------ MPV Settings -------------*/
    val MPV_HARDWARE_ACCELERATION = Pref("pref_mpv_hw", true) {
        title = { it.uisettingMpvHardwareAccelerationTitle }
        summary = { it.uisettingMpvHardwareAccelerationSummary }
        icon = Icons.Filled.Speed
    }
    val MPV_GPU_NEXT = Pref("pref_mpv_gpunext", true) {
        title = { it.uisettingMpvGpunextTitle }
        summary = { it.uisettingMpvGpunextSummary }
        icon = Icons.Filled.Memory
    }
    val MPV_DEBUG_MODE = Pref("pref_mpv_debug_mode", 0) {
        title = { it.uiSettingMpvDebugTitle }
        summary = { it.uiSettingMpvDebugSummary }
        icon = Icons.Filled.Adb
    }
    val MPV_VIDSYNC = Pref("pref_mpv_video_sync", "audio") {
        title = { it.uiSettingMpvVidsyncTitle }
        summary = { it.uiSettingMpvVidsyncSummary }
        detail = { it.uiSettingMpvVidsyncDetail }
        icon = Icons.Filled.SlowMotionVideo
    }
    val MPV_PROFILE = Pref("pref_mpv_profile", "fast") {
        title = { it.uiSettingMpvProfileTitle }
        summary = { it.uiSettingMpvProfileSummary }
        detail = { it.uiSettingMpvProfileDetail }
        icon = Icons.Filled.SupervisedUserCircle
    }
    val MPV_INTERPOLATION = Pref("pref_mpv_interpolation", false) {
        title = { it.uiSettingMpvInterpolationTitle }
        summary = { it.uiSettingMpvInterpolationSummary }
        detail = { it.uiSettingMpvInterpolationDetail }
        icon = Icons.Filled.Animation
    }

    /** ------------ ExoPlayer Settings -------------*/
    val EXO_MAX_BUFFER = Pref("pref_max_buffer_size", 30) {
        title = { it.settingMaxBufferTitle }
        summary = { it.settingMaxBufferSummary }
        icon = Icons.Filled.HourglassTop

        extraConfig = PrefExtraConfig.Slider(maxValue = 60, minValue = 1, unit = "s")
    }
    val EXO_MIN_BUFFER = Pref("pref_min_buffer_size", 15) {
        title = { it.settingMinBufferTitle }
        summary = { it.settingMinBufferSummary }
        icon = Icons.Filled.HourglassBottom

        extraConfig = PrefExtraConfig.Slider(maxValue = 30, minValue = 1, unit = "s")
    }
    val EXO_SEEK_BUFFER = Pref("pref_seek_buffer_size", 5000) {
        title = { it.settingPlaybackBufferTitle }
        summary = { it.settingPlaybackBufferSummary }
        icon = Icons.Filled.HourglassEmpty

        extraConfig = PrefExtraConfig.Slider(maxValue = 15000, minValue = 100, unit = "ms")
    }

    /** ------------ Haptics -------------*/
    val HAPTIC_ON_JOINED = Pref("pref_haptic_on_joined", false) {
        title = { it.uisettingHapticOnJoinedTitle }
        summary = { it.uisettingHapticOnJoinedSummary }
        icon = Icons.Filled.Vibration
    }
    val HAPTIC_ON_LEFT = Pref("pref_haptic_on_left", true) {
        title = { it.uisettingHapticOnLeftTitle }
        summary = { it.uisettingHapticOnLeftSummary }
        icon = Icons.Filled.Vibration
    }
    val HAPTIC_ON_CHAT = Pref("pref_haptic_on_chat", true) {
        title = { it.uisettingHapticOnChatTitle }
        summary = { it.uisettingHapticOnChatSummary }
        icon = Icons.Filled.Vibration
    }
    val HAPTIC_ON_PAUSED = Pref("pref_haptic_on_paused", false) {
        title = { it.uisettingHapticOnPausedTitle }
        summary = { it.uisettingHapticOnPausedSummary }
        icon = Icons.Filled.Vibration
    }
    val HAPTIC_ON_PLAYED = Pref("pref_haptic_on_played", false) {
        title = { it.uisettingHapticOnPlayedTitle }
        summary = { it.uisettingHapticOnPlayedSummary }
        icon = Icons.Filled.Vibration
    }
    val HAPTIC_ON_SEEKED = Pref("pref_haptic_on_seeked", false) {
        title = { it.uisettingHapticOnSeekedTitle }
        summary = { it.uisettingHapticOnSeekedSummary }
        icon = Icons.Filled.Vibration
    }
    val HAPTIC_ON_PLAYLIST = Pref("pref_haptic_on_playlist", false) {
        title = { it.uisettingHapticOnPlaylistTitle }
        summary = { it.uisettingHapticOnPlaylistSummary }
        icon = Icons.Filled.Vibration
    }
    val HAPTIC_ON_CONNECTION = Pref("pref_haptic_on_connection", false) {
        title = { it.uisettingHapticOnConnectionTitle }
        summary = { it.uisettingHapticOnConnectionSummary }
        icon = Icons.Filled.Vibration
    }

    /**
     * The colour behind the video picture (the letterbox area). It is black by default, because a
     * letterbox in any other colour looks like a rendering bug. The picker is there for users who
     * want another colour. Opaque ARGB, stored like the chat colour prefs.
     */
    val VIDEO_BACKGROUND_COLOR = Pref("pref_video_background_color", androidx.compose.ui.graphics.Color.Black.toArgb()) {
        title = { it.uisettingVideoBgColorTitle }
        summary = { it.uisettingVideoBgColorSummary }
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick
    }

    /** ------------ Advanced -------------*/
    val RECONNECTION_INTERVAL = Pref("pref_inroom_reconnection_interval", 2) {
        title = { it.uisettingReconnectIntervalTitle }
        summary = { it.uisettingReconnectIntervalSummary }
        icon = Icons.Filled.Web

        extraConfig = PrefExtraConfig.Slider(maxValue = 15, minValue = 0, unit = "s")
    }

    val GLOBAL_RESET_DEFAULTS: Pref<String> = Pref("global_reset_defaults", "") {
        title = { it.settingResetdefaultTitle }
        summary = { it.settingResetdefaultSummary }
        icon = Icons.Filled.ClearAll

        extraConfig = PrefExtraConfig.YesNoDialog(
            rationale = { it.settingResetdefaultDialog },
            destructive = true,
            onYes = {
                // Only the global categories' keys. Identity, themes, favourites and the saved
                // join config are not settings and survive a reset.
                datastore.edit { preferences ->
                    SETTINGS_GLOBAL.flatMap { it.settings }.forEach { preferences.remove(it.anyKey) }
                }
            }
        )
    }

    val INROOM_RESET_DEFAULTS: Pref<String> = Pref("inroom_reset_defaults", "") {
        title = { it.uisettingResetdefaultTitle }
        summary = { it.uisettingResetdefaultSummary }
        icon = Icons.Filled.ClearAll

        extraConfig = PrefExtraConfig.YesNoDialog(
            rationale = { it.settingResetdefaultDialog },
            destructive = true,
            onYes = {
                // Every in-room and engine key, by prefix or in IN_ROOM_EXTRA_KEYS. Nothing else.
                datastore.edit { preferences ->
                    preferences.asMap().keys
                        .filter { key -> IN_ROOM_KEY_PREFIXES.any { key.name.startsWith(it) } || key.name in IN_ROOM_EXTRA_KEYS }
                        .forEach { preferences.remove(it) }
                }
            }
        )
    }

    private val IN_ROOM_KEY_PREFIXES = listOf("pref_inroom_", "pref_kite_", "pref_mpv_", "pref_haptic_", "pref_vlc_", "vlc_")
    private val IN_ROOM_EXTRA_KEYS = setOf(
        "pref_max_buffer_size", "pref_min_buffer_size", "pref_seek_buffer_size",
        "pref_video_background_color", "pref_room_ui_opacity",
    )

    /**
     * Writes every setting that the app shows into a file. Anything private stays behind: the
     * install id, the saved join config, the server salt, the server password and the watch
     * positions.
     */
    val EXPORT_SETTINGS = Pref<String>("settings_export", "") {
        title = { it.settingExportSettingsTitle }
        summary = { it.settingExportSettingsSummary }
        icon = Icons.Filled.FileDownload

        extraConfig = PrefExtraConfig.ShowComposable(
            composable = {
                val scope = rememberCoroutineScope { ioDispatcher }
                var result by remember { mutableStateOf<String?>(null) }
                val done = strings.settingsExportDone
                val failed = strings.settingsFileError
                val saver = rememberFileSaver { file ->
                    if (file == null) return@rememberFileSaver
                    scope.launch {
                        // Writing a file can fail for many reasons, and silence looks
                        // exactly like success.
                        result = runCatching { file.writeBytesCompat(buildSettingsBackup().encodeToByteArray()) }
                            .fold(onSuccess = { done }, onFailure = { failed })
                    }
                }
                LaunchedEffect(null) {
                    saver.launch(suggestedName = "${appName}Settings", extension = "json")
                }
                OutcomeModal(result) { result = null }
            }
        )
    }

    /**
     * Reads a settings file back in. A value that does not fit its setting is skipped, not
     * guessed, because someone will edit a settings file by hand.
     */
    val IMPORT_SETTINGS = Pref<String>("settings_import", "") {
        title = { it.settingImportSettingsTitle }
        summary = { it.settingImportSettingsSummary }
        icon = Icons.Filled.FileUpload

        extraConfig = PrefExtraConfig.ShowComposable(
            composable = {
                val scope = rememberCoroutineScope { ioDispatcher }
                var result by remember { mutableStateOf<String?>(null) }
                val s = strings
                val picker = rememberFilePickerLauncher(type = FileKitType.File(listOf("json"))) { file ->
                    if (file == null) return@rememberFilePickerLauncher
                    scope.launch {
                        val raw = runCatching { file.readString() }.getOrNull()
                        if (raw == null) {
                            result = s.settingsFileError
                            return@launch
                        }
                        val (values, outcome) = readSettingsBackup(raw)
                        if (outcome.error != null) {
                            loggy("Settings import refused: ${outcome.error}")
                            result = s.settingsImportRefused(outcome.error)
                            return@launch
                        }
                        // One transaction: a half-applied settings file is worse than none.
                        val wrote = runCatching {
                            datastore.edit { preferences ->
                                values.forEach { (pref, value) -> preferences[pref.anyKey] = value }
                            }
                        }.isSuccess
                        loggy("Settings imported: ${outcome.applied} applied, ${outcome.skipped} skipped")
                        result = if (wrote) s.settingsImportDone(outcome.applied, outcome.skipped) else s.settingsFileError
                    }
                }
                LaunchedEffect(null) { picker.launch() }
                OutcomeModal(result) { result = null }
            }
        )
    }

    val CLEAR_LOGS = Pref("log_clear", "") {
        title = { it.settingClearLogsTitle }
        summary = { it.settingClearLogsSummary }
        icon = Icons.Filled.ClearAll

        extraConfig = PrefExtraConfig.YesNoDialog(
            rationale = { it.settingClearLogsDialog },
            destructive = true,
            onYes = { clearLogs() }
        )
    }

    val EXPORT_LOGS = Pref<String>("log_saver", "") {
        title = { it.settingExportLogTitle }
        summary = { it.settingExportLogSummary }
        icon = Icons.Filled.LogoDev

        extraConfig = PrefExtraConfig.ShowComposable(
            composable = {
                val scope = rememberCoroutineScope { ioDispatcher }

                val logSaver = rememberFileSaver { file ->
                    scope.launch {
                        file?.writeFilesCompat(logFilesForExport())
                    }
                }

                LaunchedEffect(null) {
                    logSaver.launch(
                        suggestedName = "${appName}Log_${generateTimestampMillis()}",
                        extension = "txt"
                    )
                }
            }
        )
    }

    /**
     * Extra command-line flags passed as they are to LibVLC on iOS (`VLCLibrary(args)`).
     * [tokenizeVlcFlags] splits them on whitespace but keeps `"` and `'` quoted runs together,
     * so a value can contain spaces (for example `--sub-text-scale="1.5"`). A change takes effect
     * the next time VLCKit initializes.
     */
    val VLC_CUSTOM_FLAGS = Pref("pref_vlc_custom_flags", "") {
        title = { it.uisettingVlcCustomFlagsTitle }
        summary = { it.uisettingVlcCustomFlagsSummary }
        detail = { it.uisettingVlcCustomFlagsDetail }
        icon = Icons.Filled.Keyboard
        extraConfig = PrefExtraConfig.TextField()
    }

    /**
     * Imports an mpv.conf from the user's storage. It overwrites the file that mpv reads at
     * `{filesDir}/mpv.conf`. Only the mpv engine's own settings category shows this row, so
     * platforms without mpv never see it. On those platforms, [getMpvConfFilePath] returns null
     * and the import does nothing.
     *
     * The new config takes effect the next time mpv starts (for example when a video loads
     * again), because mpv reads its config directory only when a core starts.
     */
    val MPV_IMPORT_CONF = Pref<String>("mpv_import_conf", "") {
        title = { it.uisettingMpvImportConfTitle }
        summary = { it.uisettingMpvImportConfSummary }
        icon = Icons.Filled.FileUpload

        extraConfig = PrefExtraConfig.ShowComposable(
            composable = {
                val scope = rememberCoroutineScope { ioDispatcher }
                val picker = rememberFilePickerLauncher(type = FileKitType.File()) { file ->
                    if (file == null) return@rememberFilePickerLauncher
                    scope.launch {
                        runCatching {
                            val bytes = file.readBytes()
                            val dest = getMpvConfFilePath()
                            if (dest != null) {
                                writeFileBytes(dest, bytes)
                                loggy("mpv.conf imported to $dest (${bytes.size} bytes)")
                            } else {
                                loggy("mpv.conf import: platform does not support a config path.")
                            }
                        }.onFailure {
                            loggy("mpv.conf import failed: ${it.message}")
                        }
                    }
                }
                LaunchedEffect(null) { picker.launch() }
            }
        )
    }

    /**
     * Exports the active mpv.conf, if there is one, to a place that the user picks. On Android it
     * reads `{filesDir}/mpv.conf`. When that file does not exist yet, nothing is written, and only
     * the log says so.
     */
    val MPV_EXPORT_CONF = Pref<String>("mpv_export_conf", "") {
        title = { it.uisettingMpvExportConfTitle }
        summary = { it.uisettingMpvExportConfSummary }
        icon = Icons.Filled.FileDownload

        extraConfig = PrefExtraConfig.ShowComposable(
            composable = {
                val scope = rememberCoroutineScope { ioDispatcher }
                val saver = rememberFileSaver { file ->
                    if (file == null) return@rememberFileSaver
                    scope.launch {
                        runCatching {
                            val src = getMpvConfFilePath()
                            val bytes = src?.let { readFileBytes(it) }
                            if (bytes != null) {
                                file.writeBytesCompat(bytes)
                                loggy("mpv.conf exported (${bytes.size} bytes)")
                            } else {
                                loggy("mpv.conf export: no config file to export yet.")
                            }
                        }.onFailure {
                            loggy("mpv.conf export failed: ${it.message}")
                        }
                    }
                }
                LaunchedEffect(null) {
                    saver.launch(suggestedName = "mpv", extension = "conf")
                }
            }
        )
    }
}

/**
 * A one-line answer to "did that work?" after a settings import or export.
 *
 * With only a log line, a refused file and a successful one would look the same to the user:
 * the sheet closes, and the settings are whatever they are.
 */
@Composable
private fun OutcomeModal(text: String?, onDismiss: () -> Unit) {
    Modal(
        open = text != null,
        onDismiss = onDismiss,
        size = ModalSize.Ask,
        actions = { AccentAction(strings.okay, onClick = onDismiss) },
    ) {
        Text(text.orEmpty(), style = Type.note, color = palette.ink)
    }
}
