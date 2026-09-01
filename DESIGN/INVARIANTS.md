# Invariants

Behaviours the code gets right that no redesign may lose. Each one was a bug, a crash or a
platform quirk once. Paths are under `shared/src/commonMain/kotlin/app/` unless stated; line
numbers are as of the audit and drift with edits, the file names do not.

## Sync and seeking

- The pending seek origin is single use and negative means none, because zero is a real
  origin. `protocol/event/RoomEventDispatcher.kt:65-72`, consumed once in
  `protocol/event/RoomCallback.kt:165-166`. Losing this brings back the duplicate
  "X jumped from A to B".
- A seek announces before it moves the engine, or the room's rewind correction yanks the local
  player back. `room/ui/bottombar/PopupSeekToPosition.kt:225-228`.
- The seekbar previews while dragging and seeks once, on release; the origin is captured on the
  first drag event from the engine's live position. `room/ui/bottombar/RoomSeekbar.kt:126-143`.
- `isSliderInUse` means pressed or dragged, never focused, so D-pad focus does not freeze the
  track; the position flow does not write the track while it is in use. `RoomSeekbar.kt:84-93`.
- Seeks under one second are suppressed and not recorded for undo. `RoomCallback.kt:189-190`.
  Online, undo history is filled by the inbound echo, not the outbound call. `:203`.
- The seek announcement reports `expectedPlaying`, never a live `isPlaying()`, because VLCKit
  answers late and would broadcast a phantom play. `RoomEventDispatcher.kt:82-86`.
- `noteExpectedPlaybackState` runs before the engine is touched; ExoPlayer reports the change
  synchronously inside `play()`. `RoomEventDispatcher.kt:133`.
- `controlPlayback` returns early in the background, so a lifecycle pause never reaches the
  room; a play refused by readiness marks the user ready instead; a controlled room without a
  controller can never unpause. `RoomEventDispatcher.kt:111-125, 182-214`.
- Every engine play, pause and seek is guarded on `media != null`; VLCKit 4 crashes on a null
  media. `RoomEventDispatcher.kt:148-155`, `RoomCallback.kt:179`.
- The base `seekTo` refuses to seek while backgrounded and every override calls it.
  `player/PlayerImpl.kt:406-408`.
- The broadcast position is `reportableStatePositionSec()`, not the engine position, so a late
  loader does not poison the server. `RoomEventDispatcher.kt:164`.
- Media is installed and the duration wiped **before** the engine loads, because engine load
  events read the current media. `PlayerImpl.kt:320-344, 365`.
- `analyzeChapters` has one caller, the seekbar; every engine clears the list first.
  `RoomSeekbar.kt:70-72`, `room/ui/bottombar/RoomControlPanel.kt:359-362`.
- The base chapter jump only announces; each engine does its own local jump after calling it.
  `PlayerImpl.kt:177-185`.

## Room composition

- The video surface is composed as soon as the player is ready and held at alpha 0 while there
  is no video; the alpha modifier comes before the background modifier. `room/RoomScreenUI.kt:121-132`.
- The HUD is always composed and faded, never removed; chat draft, GIF panel and drag state
  live in it. The gesture interceptor is stacked above it to swallow taps on invisible chrome.
  `RoomScreenUI.kt:167-172, 322-327`.
- Background tap is two stage: keyboard open clears focus only, otherwise the HUD hides. The
  keyboard flag is read through `rememberUpdatedState` because the pointer coroutine never
  restarts. `RoomScreenUI.kt:174-193`.
- The room provides its own glass capture state over the video layer only.
  `RoomScreenUI.kt:102-113`.
- Initial focus goes to the play key with video and the add button without, in keyboard input
  mode only, after a delay, inside `runCatching`, and is cleared when the HUD hides.
  `RoomScreenUI.kt:148-164`, `room/ui/misc/RoomPlayButton.kt:45`,
  `room/ui/bottombar/RoomMediaAddButton.kt:123-131`.
- Android intercepts D-pad keys only while the HUD is hidden and a video is loaded, and any
  D-pad key shows the HUD; media keys work unconditionally.
  `androidMain/kotlin/app/SyncplayActivity.kt:505-549`.
- Entering picture in picture sets the flag before asking the system and forces the HUD off.
  `SyncplayActivity.kt:374-383`.
- `onLifecycleStop` pauses unless in picture in picture; the background flag is volatile.
  `room/RoomUiStateManager.kt:78-79, 97-102`.
- The activity handles its own configuration changes, so rotation restarts nothing; anything a
  long lived pointer coroutine reads goes through `rememberUpdatedState` or `size`.
  `androidApp/src/main/AndroidManifest.xml:29`, `room/ui/misc/RoomGestureInterceptor.kt:73-88, 264-269`.
- `EnterRoomMode` re-hides the system bars after every composition on Android, because popups
  un-hide them; `ExitRoomMode` is the first call on home and on the server host screen.
  `androidMain/kotlin/app/utils/PlatformUtils.android.kt:136-156`, `home/HomeScreen.kt:134`,
  `server/ui/ServerHostScreen.kt:96`.
- The three panels are mutually exclusive in the state manager and sit at `zIndex(10f)`.
  `RoomUiStateManager.kt:45-67`, `RoomScreenUI.kt:241, 284`.
- The contrast underlay is composed only when there is video. `RoomScreenUI.kt:197-199`.
- The tab lock replaces the whole HUD subtree, gesture interceptor included.
  `RoomScreenUI.kt:140-143`.
- `ROOM_UI_OPACITY` has one consumer, the control panel sheet, clamped to 55 percent.
  `RoomControlPanel.kt:374-376`.

## Gestures

- The drag start edge guard uses real `systemGestures`, `displayCutout` and `waterfall` insets
  with an 8 percent of height floor top and bottom (issue #137). `RoomGestureInterceptor.kt:73-88, 264-269`.
- The volume or brightness decision is made per event from the live pointer x.
  `RoomGestureInterceptor.kt:289, 311`.
- Engines reporting a maximum volume above 100 show the raw value with a mark at 100.
  `RoomGestureInterceptor.kt:298-303`.
- The two pointer handlers are keyed on their own preference. `RoomGestureInterceptor.kt:179, 257`.

## Pickers and files

- A file picker launches only after its menu has finished dismissing, or iOS crashes with
  "Already resumed": the media picker, the subtitle picker and the playlist pickers.
  `RoomMediaAddButton.kt:112-121`, `RoomControlPanel.kt:198-207`,
  `room/ui/rightcards/CardSharedPlaylist.kt:284-296, 391-399`.
- The shuffle flag is set before the picker fires because the callback consumes it.
  `CardSharedPlaylist.kt:438-441`.
- Exactly one iOS security scope is held at a time; the same file is not double granted;
  switching to a URL releases it. `PlayerImpl.kt:115-138, 278, 286-289`.
- The registry resolves a direct bookmark first, then re-indexes every remembered directory and
  retries. `room/sharedplaylist/MediaAccessRegistry.kt:117-149`.
- Removing a directory updates the preference and the registry together.
  `uicomponents/PopupMediaDirs.kt:202-208, 285-286`.
- The resolver runs only when its preference is on and the URL is not already direct media.
  `PlayerImpl.kt:292-305`.
- Peer pushed playlist URLs pass the trusted domains check; locally typed ones are trusted by
  consent. `room/sharedplaylist/SharedPlaylistManager.kt:45-47, 106-113, 337-391`.

## Chat

- The cutout inset and 8dp margins are applied per section child, and each child's tap shield
  comes before its inset padding. `room/ui/chat/RoomSectionChat.kt:94-129`.
- The inner text field must not reuse the outer modifier. `RoomSectionChat.kt:175-177`.
- The message cap is the server's `maxChatMessageLength`, floored at 1. `RoomSectionChat.kt:160`.
- Backslashes are never stripped; the JSON layer escapes them. `RoomSectionChat.kt:157-159`.
- Sender and content are wrapped in first-strong isolates separately; system and error lines
  are not. `room/models/Message.kt:10-13, 63, 93, 102, 112-116`.
- Messages are marked seen only while the HUD is visible, and `seen` is a plain field.
  `RoomSectionChat.kt:307-311`, `Message.kt:34`, `room/ui/chat/RoomFadingChatLayout.kt:55`.
- Focus is force-cleared when the HUD hides. `RoomSectionChat.kt:152-154`.
- The keyboard send action is suppressed while the GIF panel is open. `RoomSectionChat.kt:182`.
- Emoji are excluded from any styled span. `RoomSectionChat.kt:232-245`.
- GIF tiles need `fillMaxWidth` on tile and image, and alpha is a parameter, never a modifier,
  on iOS. `room/ui/chat/GifPanel.kt:276-298`, `uicomponents/AnimatedImage.kt:9-14`.
- Klipy keeps `expectSuccess = true`; pages de-duplicate by id; favourites bypass the network.
  `klipy/KlipyUtils.kt:148-156`, `GifPanel.kt:120-131, 168-169`.

## Glass

- The blur style keeps a transparent background colour; a 40 percent dim is applied to the
  capture before the tint; `Quality` performance mode; default `Behind` selection.
  `uicomponents/GlassSurface.kt:137-143, 178-191, 244-265`.
- The backdrop attaches its capture only while a glass surface is on screen.
  `GlassSurface.kt:48-57, 100-118, 166-173`.
- `glassEnabled()` and `glassEnabledNow()` are the only places anything asks whether glass is
  on; the players choose their surface type from the second. `GlassSurface.kt:67, 73`.
- The Android window blur flag is set from inside the dialog window, as the first child or the
  always present slot. `uicomponents/GlassComponents.kt:138-140`, `uicomponents/SyncplayPopup.kt:56`.
- ExoPlayer inflates a `TextureView` when glass is on and a `SurfaceView` when it is off;
  the type is fixed at inflation. `androidMain/res/layout/exoview.xml`, `exoview_surface.xml`.
- One scrim, glass aware. `GlassSurface.kt:133-135`.
- The pill is non-blurred on purpose for chrome that stays composed over video.
  `uicomponents/DarkGlassPill.kt:20-45`.

## Home

- The form renders defaults at once and re-keys on the saved config; the text field keeps its
  callbacks in `rememberUpdatedState` because of that. `home/HomeScreen.kt:138-142, 204-215`,
  `home/components/HomeTextField.kt:83-101`.
- Explicit `onNext` jumps between fields; the clear glyph is not focusable; initial focus only
  in keyboard input mode. `HomeTextField.kt:143-157, 212`, `HomeScreen.kt:220-227, 244, 419, 430`.
- Modifier order: `imePadding` then `verticalScroll` then the focus clearing click.
  `HomeScreen.kt:184-193`.
- Switching to Official resets a non-official port and clears the password; Custom blanks
  both; an old raw official address reads as the official server. `HomeScreen.kt:208, 330-346`.
- A saved engine this build lacks is replaced once with the platform default; unavailable
  engines are refused and never written. `HomeScreen.kt:465-485`.
- The wheel: cancel-previous correction jobs, settle needs a scrolling to not scrolling edge,
  a hand rolled detent for mouse wheels, `settleTick` re-centres a refused engine,
  `CompositingStrategy.Offscreen` for the fade mask, edge flags in `derivedStateOf`.
  `home/components/HomeEngineWheel.kt:108-165, 169, 227-230, 283-296`.
- Username and room are stripped of backslashes, trimmed and capped before a join.
  `HomeScreen.kt:519-520`, `utils/CommonUtils.kt:119`.
- The tips popup waits a second and shows only when no room was entered this session and the
  tips switch is on. `HomeScreen.kt:151-163`.
- A pending shortcut joins once. `HomeScreen.kt:145-149`.

## Preferences

- `Pref.Render()` dispatches on the runtime class of `default`. `preferences/Pref.kt:47-60`.
- `MultiChoice.entries` is a composable lambda because it resolves string resources.
  `preferences/PrefExtraConfig.kt:26`.
- `SUBTITLE_SIZE` reaches the live player only through the room's weak reference.
  `preferences/Preferences.kt:755`.
- `DISABLE_FROSTED_GLASS` takes effect on the next room entry by design. `Preferences.kt:557-559`.
- `RECONNECTION_INTERVAL`, `TLS_ENABLE` and `NETWORK_ENGINE` take effect on the next connection.
- The trusted domains editor splits on newlines and commas and joins on newlines.
  `preferences/settings/PopupTrustedDomains.kt:43-47, 121-124`.
- Four summaries take `appName` as a format argument. `Preferences.kt:368, 391, 403, 539`.

## Theming

- `flexibleGradient` is the active theme's raw seeds, in order, read by twenty one files.
  `theme/Theming.kt:16-23`.
- The stored current theme defaults to `defaultTheme`, so changing Trinity changes the default.
  `Preferences.kt:357`.
- Custom themes de-duplicate on exact JSON equality; saving a theme applies it.
  `SyncplayViewmodel.kt:94-107`.
- The creator previews by re-providing the theme for its own subtree; the stored theme is
  untouched until save. `theme/ThemeCreatorScreen.kt:114-119`.
- The logo's dark lead-in is tuned in Oklab to hit the artwork's `#793695`.
  `uicomponents/SynkplayLogo.kt:36-46`.

## Server host

- The server lives in a process-lifetime singleton with its own scope; the viewmodel has no
  `onCleared`. `server/ServerHostSession.kt:33`, `server/ServerViewmodel.kt:6-11`.
- The public IP is fetched fire and forget after a successful bind; the screen never waits for
  it. `ServerHostSession.kt:112-121`.
- The collectors job is cancelled on stop. `ServerHostSession.kt:58-60`.
- The Android foreground service starts last on success, stops on stop, and is not sticky.
  `ServerHostSession.kt:122, 145`, `androidMain/kotlin/app/server/SyncplayServerService.kt:29-32`.

## Navigation and focus

- Seven CompositionLocals in `AdamScreen.kt:40-57` have no default and throw outside the tree.
- ViewModels are keyed by fixed strings; the decorators are applied saveable state holder first,
  ViewModel store second; the room is reached through a weak reference. `AdamScreen.kt:106-132`.
- `tvFocusable(addFocusable = false)` exists because most hosts are already focusable and a
  second `focusable()` doubles the stop. `uicomponents/TvFocus.kt:42, 66`.
- Every `FlexibleIcon` is a TV focus target through `tvFocusable`. `uicomponents/FlexibleIcon.kt:75-81`.
- The exposed dropdown height cap measures the shorter window side, until the dropdowns go.
  `uicomponents/ScreenDimensions.kt:28-34`.

## Resources and build

- `values-en/strings.xml` is the source; the build copies it to `values/`.
- Compose resources understand only `\n`, `\t`, `\uXXXX` and `\\`; Android style escapes render
  literally.
- Compose resources cannot load SVG on Android; logos are vector drawable XML or PNG.
- Resource fonts do not resolve in the desktop render harness; goldens judge layout, not
  letterforms.
- The desktop test source set needs `compose.desktop.currentOs` for the harness's Skia native.
