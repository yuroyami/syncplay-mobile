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
import app.utils.readLogsForExport
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
import app.uicomponents.CHAT_COLOR_FOLLOWS_THEME
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.Text
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize

/**
 * Centralized preference definitions with type safety
 */
object Preferences {

    /**
     * Touch this to be sure every preference exists.
     *
     * A Kotlin object initialises on first access, and every [Pref] registers itself as it is
     * constructed. Anything that reads [PrefRegistry] before this object has been touched sees
     * an empty registry, which is how export first came back with nothing at all.
     */
    fun ensureAllRegistered() = Unit

    const val SYNKPLAY_PREFS = "syncplayprefs.preferences_pb"

    /** ------------ Miscellaneous -------------*/
    val USER_ID = Pref<String?>("misc_user_id", null)

    /** The hosted server's controlled-room salt, minted once so operator passwords survive a restart. */
    val SERVER_SALT = Pref("misc_server_salt", "")

    /** How many cold starts have shown the tips; they stop on their own after a few. */
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

    /** Offer to pick a file up where it was left. Only ever offered when watching alone. */
    val RESUME_PLAYBACK = Pref("pref_resume_playback", true) {
        title = { it.settingResumeTitle }
        summary = { it.settingResumeSummary }
        icon = Icons.Filled.History
    }

    /**
     * Whether the GIF panel may keep the same id with the GIF service from one launch to the next.
     * On, the service can hand your own picks back under Recents. Off, a fresh id every launch, so
     * nothing links two sessions, and Recents comes back empty.
     */
    val GIF_REMEMBER_RECENTS = Pref("pref_inroom_gif_remember_recents", true) {
        title = { it.settingGifRecentsTitle }
        summary = { it.settingGifRecentsSummary }
        icon = Icons.Filled.Gif
    }
    /** When true, the "Undo Seek" action skips its confirmation dialog. Set by the dialog's
     * "Always do" button. No SettingConfig, so it never appears in the settings UI. */
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
    /** Prints every row's explanation under it, for people who liked the old manuals. */
    val SHOW_SETTING_DESCRIPTIONS = Pref("pref_show_setting_descriptions", false) {
        title = { it.settingsShowDescriptionsTitle }
        summary = { it.settingsShowDescriptionsSummary }
        icon = Icons.Filled.Lightbulb
    }
    /** The control haptics (a rocker flip, a seek landing), separate from the room event pulses. */
    /** Forces every transition to a crossfade; the platform setting does the same on its own. */
    /** The desktop window's last size, position and placement, as "x,y,w,h,placement". Never shown as a row. */
    val DESKTOP_WINDOW = Pref("pref_desktop_window", "")

    val REDUCE_MOTION = Pref("pref_reduce_motion", false) {
        title = { it.settingReduceMotionTitle }
        summary = { it.settingReduceMotionSummary }
        icon = Icons.Filled.Timer
    }

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
    /** Blank means the device's language; a code forces that language on Android. */
    val DISPLAY_LANG = Pref("pref_lang", "") {
        title = { it.settingDisplayLanguageTitle }
        summary = { it.settingDisplayLanguageSummry(appName) }
        icon = Icons.Filled.Translate

        /* The same picker everywhere. The app holds its own strings now, so a choice takes
         * effect where it is made instead of through a restart or the system settings. */
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
     * Language used for the OpenSubtitles "download from web" search. Holds an ISO 639-1 (2-letter)
     * code — NOT the 3-letter [mediaLanguages] codes the player track prefs use — because the
     * OpenSubtitles API speaks 2-letter codes. The sentinel "all" means "don't filter by language".
     * Picked inline in the subtitle-search sheet, so this has no settings-UI entry of its own.
     */
    val SUBTITLE_SEARCH_LANG = Pref("pref_subtitle_search_lang", "en")

    /** ------------ Syncing -------------*/
    val READY_FIRST_HAND = Pref("pref_ready_first_hand", true) {
        title = { it.settingReadyFirsthandTitle }
        summary = { it.settingReadyFirsthandSummary }
        icon = Icons.Filled.TaskAlt
    }
    /** Start the room on its own once everyone with a file says they are ready. */
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
                        // Android and Desktop both run the Netty engine.
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
    val TLS_REQUIRED = Pref("pref_tls_required", false) {
        title = { it.settingTlsRequiredTitle }
        summary = { it.settingTlsRequiredSummary }
        detail = { it.settingTlsRequiredDetail }
        icon = Icons.Filled.Lock
    }
    /** When true, page URLs (YT, SoundCloud, …) entered as media are run through the
     *  platform's native extractor before reaching the player. A heuristic short-circuits when
     *  the URL is already direct media, so there's no cost in the common case. */
    val MEDIA_RESOLVER_ENABLED = Pref("pref_media_resolver_enabled", true) {
        title = { it.settingMediaResolverTitle }
        summary = { it.settingMediaResolverSummary }
        detail = { it.settingMediaResolverDetail }
        icon = Icons.Filled.Language
    }

    /** Master off-switch for the frosted glass system, end to end.
     *
     *  When true: no Haze capture or blur anywhere, panels fall back to a solid tonal surface,
     *  no platform window blur, and the Android players go back to SurfaceView, which can use a
     *  hardware overlay plane (lower power, HDR passthrough) but cannot be captured for blur.
     *  Glass and the overlay fast path are mutually exclusive, so this is one switch, not two.
     *
     *  Surface type is fixed when the player view is inflated, so a change lands on the next
     *  room entry, matching how the other engine options behave. */
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
     * How far ahead of the room you may drift before it asks everyone to come back, in tenths of
     * a second. The reference client's default is 4 seconds, and it refuses anything under 3
     * because below that ordinary jitter would trigger it constantly.
     */
    val SYNC_REWIND_THRESHOLD = Pref("pref_inroom_sync_rewind_threshold", 40) {
        title = { it.uisettingSyncRewindThresholdTitle }
        summary = { it.uisettingSyncRewindThresholdSummary }
        icon = Icons.Filled.FastRewind
        dependencyEnable = { SYNC_REWIND.value() }

        extraConfig = PrefExtraConfig.Slider(minValue = 30, maxValue = 150, unit = "s", formatValue = { formatTenths(it) })
    }

    /** How far ahead you may drift before playback slows to let the room catch up, in tenths. */
    val SYNC_SLOWDOWN_THRESHOLD = Pref("pref_inroom_sync_slowdown_threshold", 15) {
        title = { it.uisettingSyncSlowdownThresholdTitle }
        summary = { it.uisettingSyncSlowdownThresholdSummary }
        icon = Icons.Filled.SlowMotionVideo
        dependencyEnable = { SYNC_SLOWDOWN.value() }

        extraConfig = PrefExtraConfig.Slider(minValue = 5, maxValue = 60, unit = "s", formatValue = { formatTenths(it) })
    }

    /** How far behind you may fall before the room pulls you forward, in tenths. */
    val SYNC_FASTFORWARD_THRESHOLD = Pref("pref_inroom_sync_fastforward_threshold", 50) {
        title = { it.uisettingSyncFastforwardThresholdTitle }
        summary = { it.uisettingSyncFastforwardThresholdSummary }
        icon = Icons.Filled.FastForward
        dependencyEnable = { SYNC_FASTFORWARD.value() }

        extraConfig = PrefExtraConfig.Slider(minValue = 20, maxValue = 200, unit = "s", formatValue = { formatTenths(it) })
    }

    /**
     * How far our copy of the file runs ahead of the room's, in tenths of a second, offset by
     * 600 so the slider can cover minus sixty to plus sixty seconds.
     *
     * Two rips of the same film differ by an intro, a logo card, a few frames of black. The
     * desktop client has had a per-user offset forever; this is the same idea, and it shifts
     * only what we do locally.
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
    /** One entry gathering the COLOR_* prefs below as a nested page, so the room's settings
     *  panel can show them beside the chat they colour. */
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

    val COLOR_TIMESTAMP = Pref("pref_inroom_color_timestamp", CHAT_COLOR_FOLLOWS_THEME) {
        title = { it.uisettingTimestampColorTitle }
        summary = { it.uisettingTimestampSummary }
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick(themeRole = { it.inkFaint })
    }
    val COLOR_SELFTAG = Pref("pref_inroom_color_selftag", CHAT_COLOR_FOLLOWS_THEME) {
        title = { it.uisettingSelfColorTitle }
        summary = { it.uisettingSelfColorSummary }
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick(themeRole = { it.accent })
    }
    val COLOR_FRIENDTAG = Pref("pref_inroom_color_friendtag", CHAT_COLOR_FOLLOWS_THEME) {
        title = { it.uisettingFriendColorTitle }
        summary = { it.uisettingFriendColorSummary }
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick(themeRole = { it.ok })
    }
    val COLOR_SYSTEMMSG = Pref("pref_inroom_color_systemmsg", CHAT_COLOR_FOLLOWS_THEME) {
        title = { it.uisettingSystemColorTitle }
        summary = { it.uisettingSystemColorSummary }
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick(themeRole = { it.inkDim })
    }
    val COLOR_USERMSG = Pref("pref_inroom_color_usermsg", CHAT_COLOR_FOLLOWS_THEME) {
        title = { it.uisettingHumanColorTitle }
        summary = { it.uisettingHumanColorSummary }
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick(themeRole = { it.ink })
    }
    val COLOR_ERRORMSG = Pref("pref_inroom_color_errormsg", CHAT_COLOR_FOLLOWS_THEME) {
        title = { it.uisettingErrorColorTitle }
        summary = { it.uisettingErrorColorSummary }
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick(themeRole = { it.bad })
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

    /** ------------ Chat Properties -------------*/
    /** Zero switches the outline off; there is no separate switch. */
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
    /** 5 to 24, default 10; existing choices are preserved. MessageStyle uses the same floor. */
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
     *  Mirrors Syncplay PC's "Messages" tab toggles (showSameRoomOSD / showNonControllerOSD /
     *  showDifferentRoomOSD / showSlowdownOSD / showOSDWarnings). These gate which event-driven
     *  OSD overlays bubble up via [RoomViewmodel.dispatchOSD]. They do NOT affect the chat log. */
    /** Routine room events stay in chat by default instead of crowding the video with notices. */
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
    /** Default false to match Syncplay PC's SHOW_DIFFERENT_ROOM_OSD = False default. */
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

    /** Off by default: double-tap-to-seek fights with tap-to-reveal-HUD for most users. */
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

    /** Idle seconds during playback before the HUD hides; zero keeps it up until tapped away. */
    val HUD_AUTO_HIDE_SECONDS = Pref("pref_inroom_hud_auto_hide_seconds", 15) {
        title = { it.roomHudAutoHideTitle }
        summary = { it.roomHudAutoHideSummary }
        icon = Icons.Filled.Timer

        extraConfig = PrefExtraConfig.Slider(maxValue = 30, minValue = 0, unit = "s", zeroMeansOff = true)
    }

    /** Compact or expanded roster (stored as "standard"); legacy "files" falls back to expanded. */
    val USER_INFO_VIEW = Pref("pref_inroom_user_info_view", "standard")

    /** Kept under the old key so existing visualizer choices survive the capability migration. */
    val AUDIO_VISUALIZATION = Pref("pref_kite_audio_viz", true) {
        title = { it.uisettingKiteAudioVizTitle }
        summary = { it.uisettingKiteAudioVizSummary }
        icon = Icons.Filled.MusicNote
    }
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
     * The colour behind the video picture (the letterbox area). Black by default, because a
     * letterbox that is anything else reads as a rendering bug; picker for whoever disagrees.
     * Opaque ARGB, same storage convention as the chat colour prefs.
     */
    val VIDEO_BACKGROUND_COLOR = Pref("pref_video_background_color", androidx.compose.ui.graphics.Color.Black.toArgb()) {
        title = { it.uisettingVideoBgColorTitle }
        summary = { it.uisettingVideoBgColorSummary }
        icon = Icons.Filled.Brush
        extraConfig = PrefExtraConfig.ColorPick()
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
                // Every in-room and engine key, by prefix; nothing else.
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
     * Every setting the app shows, into a file. What stays behind is anything private: the
     * install id, the saved join config, the server salt, the server password, watch positions.
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
                        // Writing a file can fail for a dozen reasons, and silence looks
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
     * A settings file back in. A value that does not fit its setting is skipped rather than
     * guessed at, because a settings file is a text file and someone will hand-edit one.
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
                        file?.writeBytesCompat(readLogsForExport())
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
     * Extra command-line flags forwarded verbatim to LibVLC on iOS (`VLCLibrary(args)`).
     * Tokenized by [tokenizeVlcFlags], which splits on whitespace but honours `"`/`'` quoted runs
     * so values with spaces work (e.g. `--sub-text-scale="1.5"`). Takes effect on the next VLCKit
     * (re)initialization.
     */
    val VLC_CUSTOM_FLAGS = Pref("pref_vlc_custom_flags", "") {
        title = { it.uisettingVlcCustomFlagsTitle }
        summary = { it.uisettingVlcCustomFlagsSummary }
        detail = { it.uisettingVlcCustomFlagsDetail }
        icon = Icons.Filled.Keyboard
        extraConfig = PrefExtraConfig.TextField()
    }

    /**
     * Import an mpv.conf from the user's storage, overwriting the one mpv reads from at
     * `{filesDir}/mpv.conf`. Only shown by the mpv engine's own settings category, so platforms
     * without mpv never see it; there [getMpvConfFilePath] returns null and this is a no-op.
     *
     * The new config takes effect the next time mpv is initialized (e.g. after loading a video
     * fresh) because mpv reads its config dir only when a core starts.
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
     * Export the currently active mpv.conf (if any) to a user-chosen location. On Android this
     * reads from `{filesDir}/mpv.conf`; if the file does not exist yet the pref is effectively a
     * no-op (user is informed via logs).
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
 * A one-line answer to "did that work?".
 *
 * Import and export used to write their outcome to the log and nothing else, so a refused file
 * and a successful one looked identical: the sheet closed and the settings were whatever they
 * were.
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
