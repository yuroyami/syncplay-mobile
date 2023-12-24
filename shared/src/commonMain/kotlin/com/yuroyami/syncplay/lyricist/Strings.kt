package com.yuroyami.syncplay.lyricist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale
import cafe.adriel.lyricist.LanguageTag
import cafe.adriel.lyricist.Lyricist

interface Strings {
    val yes: String
    val no: String
    val okay: String
    val cancel: String
    val dontShowAgain: String
    val play: String
    val pause: String
    val delete: String
    val confirm: String
    val done: String
    val close: String
    val off: String
    val on: String
    val tabConnect: String
    val tabSettings: String
    val tabAbout: String
    val connectUsernameA: String
    val connectUsernameB: String
    val connectUsernameC: String
    val connectRoomnameA: String
    val connectRoomnameB: String
    val connectRoomnameC: String
    val connectServerA: String
    val connectServerB: String
    val connectServerC: String
    val connectButtonJoin: String
    val connectButton: String
    val connectButtonSaveshortcut: String
    val connectButtonCurrentEngine: (String) -> String
    val connectFootnote: String
    val connectUsernameEmptyError: String
    val connectRoomnameEmptyError: String
    val connectAddressEmptyError: String
    val connectPortEmptyError: String
    val connectEnterCustomServer: String
    val connectCustomServerPassword: String
    val connectPort: String
    val connectNightmodeswitch: String
    val connectSolomode: String
    val roomSelectedVid: (String) -> String
    val roomSelectedSub: (String) -> String
    val roomSelectedSubError: String
    val roomSubErrorLoadVidFirst: String
    val roomTypeMessage: String
    val roomReady: String
    val roomNotReady: String
    val roomPingConnected: (String) -> String
    val roomPingDisconnected: String
    val roomOverflowSub: String
    val roomOverflowMsghistory: String
    val roomOverflowSettings: String
    val roomEmptyMessageError: String
    val roomAttemptingConnect: (String, String) -> String
    val roomConnectedToServer: String
    val roomConnectionFailed: String
    val roomAttemptingReconnection: String
    val roomGuyPlayed: (String) -> String
    val roomGuyPaused: (String, String) -> String
    val roomSeeked: (String, String, String) -> String
    val roomRewinded: (String) -> String
    val roomGuyLeft: (String) -> String
    val roomGuyJoined: (String) -> String
    val roomIsplayingfile: (String, String, String) -> String
    val roomYouJoinedRoom: (String) -> String
    val roomScalingFitScreen: String
    val roomScalingFixedWidth: String
    val roomScalingFixedHeight: String
    val roomScalingFillScreen: String
    val roomScalingZoom: String
    val roomSubTrackChanged: (String) -> String
    val roomAudioTrackChanged: (String) -> String
    val roomAudioTrackNotFound: String
    val roomSubTrackDisable: String
    val roomTrackTrack: String
    val roomSubTrackNotfound: String
    val roomDetailsCurrentRoom: (String) -> String
    val roomDetailsNofileplayed: String
    val roomDetailsFileProperties: (String, String) -> String
    val roomFileMismatchWarningCore: (String) -> String
    val roomFileMismatchWarningName: String
    val roomFileMismatchWarningDuration: String
    val roomFileMismatchWarningSize: String
    val roomSharedPlaylist: String
    val roomSharedPlaylistBrief: String
    val roomSharedPlaylistUpdated: (String) -> String
    val roomSharedPlaylistChanged: (String) -> String
    val roomSharedPlaylistNoDirectories: String
    val roomSharedPlaylistNotFound: String
    val roomSharedPlaylistNotFeatured: String
    val roomSharedPlaylistButtonAddFile: String
    val roomSharedPlaylistButtonAddFolder: String
    val roomSharedPlaylistButtonAddUrl: String
    val roomSharedPlaylistButtonShuffle: String
    val roomSharedPlaylistButtonShuffleRest: String
    val roomSharedPlaylistButtonOverflow: String
    val roomSharedPlaylistButtonPlaylistImport: String
    val roomSharedPlaylistButtonPlaylistImportNShuffle: String
    val roomSharedPlaylistButtonPlaylistExport: String
    val roomSharedPlaylistButtonSetMediaDirectories: String
    val roomSharedPlaylistButtonSetTrustedDomains: String
    val roomSharedPlaylistButtonUndo: String
    val roomButtonDescAspectRatio: String
    val roomButtonDescSharedPlaylist: String
    val roomButtonDescAudioTracks: String
    val roomButtonDescSubtitleTracks: String
    val roomButtonDescRewind: String
    val roomButtonDescToggle: String
    val roomButtonDescPlay: String
    val roomButtonDescPause: String
    val roomButtonDescFfwd: String
    val roomButtonDescAdd: String
    val roomButtonDescLock: String
    val roomButtonDescMore: String
    val roomAddmediaOffline: String
    val roomAddmediaOnline: String
    val roomAddmediaOnlineUrl: String
    val mediaDirectories: String
    val mediaDirectoriesBrief: String
    val mediaDirectoriesSettingSummary: String
    val mediaDirectoriesSave: String
    val mediaDirectoriesClearAll: String
    val mediaDirectoriesClearAllConfirm: String
    val mediaDirectoriesAddFolder: String
    val mediaDirectoriesDelete: String
    val mediaDirectoriesShowFullPath: String
    val settingsCategGeneral: String
    val settingsCategPlayer: String
    val settingsCategRoom: String
    val settingsCategVideo: String
    val settingsCategMisc: String
    val settingNightModeTitle: String
    val settingNightModeSummary: String
    val settingRememberJoinInfoTitle: String
    val settingRememberJoinInfoSummary: String
    val settingDisplayLanguageTitle: String
    val settingDisplayLanguageSummry: String
    val settingDisplayLanguageToast: String
    val settingAudioDefaultLanguageTitle: String
    val settingAudioDefaultLanguageSummry: String
    val settingCcDefaultLanguageTitle: String
    val settingCcDefaultLanguageSummry: String
    val settingUseBufferTitle: String
    val settingUseBufferSummary: String
    val settingMaxBufferTitle: String
    val settingMaxBufferSummary: String
    val settingMinBufferSummary: String
    val settingMinBufferTitle: String
    val settingPlaybackBufferSummary: String
    val settingPlaybackBufferTitle: String
    val settingReadyFirsthandSummary: String
    val settingReadyFirsthandTitle: String
    val settingRewindThresholdSummary: String
    val settingRewindThresholdTitle: String
    val settingTlsSummary: String
    val settingTlsTitle: String
    val settingResetdefaultTitle: String
    val settingResetdefaultSummary: String
    val settingResetdefaultDialog: String
    val settingPauseIfSomeoneLeftTitle: String
    val settingPauseIfSomeoneLeftSummary: String
    val settingWarnFileMismatchTitle: String
    val settingWarnFileMismatchSummary: String
    val settingFileinfoBehaviourNameTitle: String
    val settingFileinfoBehaviourNameSummary: String
    val settingFileinfoBehaviourSizeTitle: String
    val settingFileinfoBehaviourSizeSummary: String
    val uisettingApply: String
    val uisettingTimestampSummary: String
    val uisettingTimestampTitle: String
    val uisettingMsgoutlineSummary: String
    val uisettingMsgoutlineTitle: String
    val uisettingMsgshadowSummary: String
    val uisettingMsgshadowTitle: String
    val uisettingMsgboxactionSummary: String
    val uisettingMsgboxactionTitle: String
    val uisettingOverviewAlphaSummary: String
    val uisettingOverviewAlphaTitle: String
    val uisettingMessageryAlphaSummary: String
    val uisettingMessageryAlphaTitle: String
    val uisettingMsgsizeSummary: String
    val uisettingMsgsizeTitle: String
    val uisettingMsgcountSummary: String
    val uisettingMsgcountTitle: String
    val uisettingMsglifeSummary: String
    val uisettingMsglifeTitle: String
    val uisettingTimestampColorSummary: String
    val uisettingTimestampColorTitle: String
    val uisettingSelfColorSummary: String
    val uisettingSelfColorTitle: String
    val uisettingFriendColorSummary: String
    val uisettingFriendColorTitle: String
    val uisettingSystemColorSummary: String
    val uisettingSystemColorTitle: String
    val uisettingHumanColorSummary: String
    val uisettingHumanColorTitle: String
    val uisettingErrorColorSummary: String
    val uisettingErrorColorTitle: String
    val uisettingSubtitleSizeSummary: String
    val uisettingSubtitleSizeTitle: String
    val uisettingSubtitleDelaySummary: String
    val uisettingSubtitleDelayTitle: String
    val uisettingAudioDelaySummary: String
    val uisettingAudioDelayTitle: String
    val uisettingSeekForwardJumpSummary: String
    val uisettingSeekForwardJumpTitle: String
    val uisettingSeekBackwardJumpSummary: String
    val uisettingSeekBackwardJumpTitle: String
    val uisettingPipSummary: String
    val uisettingPipTitle: String
    val uisettingReconnectIntervalSummary: String
    val uisettingReconnectIntervalTitle: String
    val uisettingResetdefaultSummary: String
    val uisettingResetdefaultTitle: String
    val settingFileinfoBehaviorA: String
    val settingFileinfoBehaviorB: String
    val settingFileinfoBehaviorC: String
    val en: String
    val ar: String
    val zh: String
    val fr: String
    val es: String
}

object Locales {
    const val En = "en"
    const val Fr = "fr"
    const val Es = "es"
    const val Ar = "ar"
    const val Zh = "zh"
}

val Stringies: Map<LanguageTag, Strings> = mapOf(
    Locales.En to EnStrings,
    Locales.Fr to FrStrings,
    Locales.Es to EsStrings,
    Locales.Ar to ArStrings,
    Locales.Zh to ZhStrings
)

val LocalStrings: ProvidableCompositionLocal<Strings> =
    staticCompositionLocalOf { EnStrings }

@Composable
fun rememberStrings(
    defaultLanguageTag: LanguageTag = "en",
    currentLanguageTag: LanguageTag = Locale.current.toLanguageTag(),
): Lyricist<Strings> =
    cafe.adriel.lyricist.rememberStrings(Stringies, defaultLanguageTag, currentLanguageTag)

@Composable
fun ProvideStrings(
    lyricist: Lyricist<Strings>,
    content: @Composable () -> Unit
) {
    cafe.adriel.lyricist.ProvideStrings(lyricist, LocalStrings, content)
}