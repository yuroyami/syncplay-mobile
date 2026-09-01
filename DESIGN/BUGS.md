# Bugs found while designing

Defects the surface audits turned up in the current code. None of these is a design opinion;
each is a behaviour that does not do what its own code says it does. Each is owned by the folder
that rebuilds its surface and is fixed as that folder lands. Paths are under
`shared/src/commonMain/kotlin/app/` unless stated; line numbers are as of the audit.

## Home

- The shortcut join path strips and trims but skips the username and room length caps that the
  normal join applies. `home/HomeScreen.kt:559-560` vs `:519-520`.
- The tips popup never advances its index, so it shows one random tip forever.
  `home/components/PopupDidYaKnow.kt:44, 82`.
- On iOS the shortcut is encoded as a joined string and decoded as JSON, so it never parses.
  `iosMain/kotlin/app/PlatformCallbackImpl.kt:118`, `iosMain/kotlin/app/SyncplayAppDelegate.kt:70`.
- The Android shortcut intent defaults a missing port to 80. `androidMain/kotlin/app/SyncplayActivity.kt:299`.
- The form's `SpaceAround` arrangement collapses once content exceeds the viewport.
  `HomeScreen.kt:195`.

## Theming

- Deleting the active theme compares a `StateFlow` to a theme, which is never equal, so the
  deleted theme stays active as an orphan. `SyncplayViewmodel.kt:116`.
- Saving an edited theme deletes the old copy in one coroutine and saves the new one in another;
  a fast save sees the duplicate, refuses, and the delete then lands. The theme is gone.
  `theme/ThemeCreatorScreen.kt:151-154`, `SyncplayViewmodel.kt:94-120`.
- The contrast slider writes zero into the theme on open. `ThemeCreatorScreen.kt:415-421`.
- The creator's preview wraps only the colour scheme, so type and shapes fall back to Material
  defaults inside it. `ThemeCreatorScreen.kt:114-119`.
- The palette is regenerated on every keystroke and drag frame: an unremembered
  `derivedStateOf` over a per-instance lazy scheme. `ThemeCreatorScreen.kt:109-110`,
  `theme/SaveableTheme.kt:27`.
- The colour (22, 22, 22) cannot be picked; the picker uses it as a sentinel.
  `ThemeCreatorScreen.kt:591`.
- The Preview section is dead (empty click handlers), every edit is logged, `BLANK_THEME` is
  unreferenced. `ThemeCreatorScreen.kt:105-107, 509-551`, `theme/BuiltinThemes.kt:11-20`.
- Delete has no confirmation; the picker's per-item scheme is remembered by slot, not key.
  `theme/ThemePicker.kt:209-218, 266`.

## Server host

- Start is guarded only against Running; a second tap during Starting creates a second server
  and engine and orphans the first. `server/ServerHostSession.kt:63`.
- Session lines and server lines share one list, and the merge drops by the list's total size,
  so after a restart the new server's log is swallowed. `ServerHostSession.kt:91, 110-160`.
- Logs are unbounded, and the server copies its whole list per entry.
  `ServerHostSession.kt:53`, `server/SyncplayServer.kt:355`.
- A failed start leaves the server, engine and collectors assigned.
  `ServerHostSession.kt:86-105` vs `:123-127`.
- The Android notification always says zero clients; its clients extra is never passed.
  `androidMain/kotlin/app/server/SyncplayServerService.kt:57`.
- On iOS the running state shows "keeps running in the background" under a warning that says
  the opposite. `server/ui/ServerHostScreen.kt:187-195, 220-228`.
- The log discards its timestamps and auto-scrolls over a reader who scrolled up.
  `ServerHostScreen.kt:329-331, 410-416`.
- Config is not persisted and the port is validated only after Start.
  `ServerHostSession.kt:36-41, 65-66`.

## Room: transport and seeking

- Slider seeks are not recorded for undo in solo mode; every other seek path records them.
  `room/ui/bottombar/RoomSeekbar.kt:138-141`.
- Undo seeks the engine before announcing. `room/ui/bottombar/RoomControlPanel.kt:925-929`.
- The play key renders from `isNowPlaying` and decides from a live `isPlaying()` probe.
  `room/ui/misc/RoomPlayButton.kt:39, 51`.
- The subtitle picker accepts extensions the loader then rejects with a generic error.
  `utils/CommonUtils.kt:89` vs `player/PlayerImpl.kt:250-251`.
- AVPlayer's aspect ratio notice shows a raw `AVLayerVideoGravity` constant; mpv and KitePlayer
  show English literals; every engine says "NO PLAYER FOUND".
  `iosMain/kotlin/app/player/avplayer/AVPlayerEngine.kt:442-452`,
  `androidMain/kotlin/app/player/mpv/MpvImpl.kt:382-394`, `player/kite/KiteImpl.kt:614-627`.
- `supportsScreenshot` gates a button that no longer exists. `PlayerImpl.kt:82`.
- The custom skip notice passes a bare second count where the seek-to notice passes a timecode,
  into the same string. `room/ui/bottombar/PopupSeekToPosition.kt:234, 263-264`.
- Chapters are snapshotted on the file name, so chapters an engine discovers later never draw.
  `RoomSeekbar.kt:68`.
- The `"???"` duration branch is unreachable. `RoomSeekbar.kt:97`.
- Long press waits two seconds before the first seek, then announces five seeks a second.
  `room/ui/misc/RoomGestureInterceptor.kt:185-197`.
- Double taps announce one seek per tap. `RoomGestureInterceptor.kt:230, 239`.
- Double tap ripple release is 200 ms on the left and 150 ms on the right.
  `RoomGestureInterceptor.kt:235, 244`.

## Room: shell, status, panels

- The portrait layout is unreachable; its only writer sits inside `if (false)`.
  `room/ui/tabs/RoomSectionTabs.kt:199-210`, `room/RoomUiStateManager.kt:20`.
- Notices do not render in solo mode; the whole status block is gated on it.
  `room/ui/statinfo/RoomSectionStatusInfo.kt:43`.
- There is one notice slot and every dispatch cancels the previous one, so an info kills a
  warning. `room/RoomViewmodel.kt:202-208`.
- Connecting and waiting to reconnect render as disconnected. `RoomSectionStatusInfo.kt:59-63`.
- The episode badge hides while a notice shows. `RoomSectionStatusInfo.kt:92`.
- The gesture readout is not suppressed in picture in picture.
  `RoomGestureInterceptor.kt:346-349`.
- Room entry force-opens the people panel in solo mode, where it never renders.
  `room/RoomScreenUI.kt:342-346`, `room/ui/rightcards/RoomSectionSlidingCards.kt:68`.
- The engine settings category is inserted at `size - 2` of a four entry list.
  `room/ui/rightcards/CardRoomPrefs.kt:53`.
- The Android media chooser hands out non-persistable URIs. `SyncplayActivity.kt:599-604`.
- A failed link resolve silently falls back to the raw URL. `PlayerImpl.kt:269-270`.
- The playlist's paste uses the deprecated clipboard manager while the app has its own helper.
  `room/ui/rightcards/CardSharedPlaylist.kt:487-533`.

## Chat

- A whitespace-only send still clears the draft and the GIF panel.
  `room/ui/chat/RoomSectionChat.kt:162-167`.
- The fading layout ignores the font size, outline and shadow preferences and draws 8sp in
  picture in picture. `room/ui/chat/RoomFadingChatLayout.kt:80-109`.
- `MSG_MAXCOUNT` is a shipped preference read by nothing. `preferences/Preferences.kt:686`.
- A failed GIF request is indistinguishable from an empty result. `klipy/KlipyUtils.kt:64, 90, 116`.
- Two `derivedStateOf` without `remember`. `RoomSectionChat.kt:148`, `PopupSeekToPosition.kt:186`.

## Preferences

- The chooser calls `.first` on a stored value that may match no option and crashes; a network
  engine saved on one platform and read on another reaches it.
  `preferences/SettingComposable.kt:262`, `Preferences.kt:523-533`.
- A row's dependency check is a plain function call over a non-snapshot read, so the disabled
  state is stale until something else recomposes. `SettingComposable.kt:82`, `preferences/Pref.kt:104-106`.
- A slider's live callback fires on every drag frame straight into the engine.
  `SettingComposable.kt:223-231`.
- The boolean callback fires from both the row click and the switch, before the write.
  `SettingComposable.kt:100, 157`.
- Engines mutate the shared lazily built configs permanently, every time the room card opens.
  `player/kite/KiteImpl.kt:322-373`, `androidMain/kotlin/app/player/mpv/MpvImpl.kt:112-144`.
- Both reset rows clear the entire datastore, identity, themes, favourites and saved rooms
  included. `Preferences.kt:1030-1034, 1045-1049`.
- `INROOM_HAPTICS` is unreachable; the five `OSD_*` switches have no row; `AUDIO_DELAY` and
  `SUBTITLE_DELAY` are read by nothing. `preferences/settings/MySettings.kt:186-226`,
  `Preferences.kt:706-740, 760-767`.
- The three launcher preferences (export logs, mpv import and export) open their picker once
  and never again in the same composition. `SettingComposable.kt:253, 293-295`.
- The colour picker's reset does not close the popup. `SettingComposable.kt:247-249`.
- String preferences are written on every keystroke. `SettingComposable.kt:161-173`.
- The settings scrollbar is zero tall on the first frame. `preferences/settings/SettingsUI.kt:246-254`.

## Navigation, focus and popups

- `NavDisplay.onBack` is empty; a back press outside a dialog is consumed and ignored on
  Android, and nothing answers back on iOS or Escape on desktop. `AdamScreen.kt:105`.
- `tvFocusable(enabled = false)` returns before its `remember`, so flipping it loses state.
  `uicomponents/TvFocus.kt:44`.
- The focus indicator scales room glyphs by 1.12, which clips inside containers.
  `uicomponents/FlexibleIcon.kt:79`.

## Hardcoded English

- Every string in the trusted domains editor. `preferences/settings/PopupTrustedDomains.kt:61-129`.
- "Undefined" and "Path:". `uicomponents/PopupMediaDirs.kt:159, 213`.
- "HH", "MM", "SS". `PopupSeekToPosition.kt:142, 159, 176`.
- "Audio:" and "Subtitle:" notices. `RoomControlPanel.kt:484, 573`.
- "URLs", the export file name. `CardSharedPlaylist.kt:456-460, 549`.
- "Back", "Powered by Klipy". `ServerHostScreen.kt:115`, `room/ui/chat/GifPanel.kt:205`.
- The 47 language names behind the language preferences. `Preferences.kt:296-344`.
- Every server log line and the Android notification text. `ServerHostSession.kt:67-149`,
  `SyncplayServerService.kt:41, 47`.

## Dead imports worth deleting as each file is touched

`RoomControlPanel.kt` (`AlertDialog`, `DropdownMenu`, `ModalBottomSheet`),
`RoomSectionTabs.kt` and `CardSharedPlaylist.kt` (`DropdownMenu`), `PopupDidYaKnow.kt`,
`SettingComposable.kt` and `PopupMediaDirs.kt` (`AlertDialog`), `HomeScreen.kt` (`sairaFont`,
`FlexibleText`, `Theming`, `Color`, `FontWeight`), `HomeTopBar.kt` (thirteen), `PopupAPropos.kt`
(`synkplay_logo`, `Image`, `vectorResource`), `RoomBackgoundArtwork.kt` (nine),
`ThemeCreatorScreen.kt` (`DropdownMenu`), `ServerHostScreen.kt` (`HomeLeadingTitle`).
