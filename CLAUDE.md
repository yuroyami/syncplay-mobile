# Syncplay Mobile - Codebase Reference

Kotlin Multiplatform (KMP) mobile port of [Syncplay](https://syncplay.pl), a synchronized media playback application. The PC source is in `syncplay-pc-src-master/` for protocol reference.

**App name:** Synkplay | **Version:** 0.19.1 | **Package:** com.yuroyami.syncplay

## Table of Contents
- [Architecture Overview](#architecture-overview)
- [Module Structure](#module-structure)
- [Syncplay Protocol Reference](#syncplay-protocol-reference)
- [Synchronization Algorithm](#synchronization-algorithm)
- [Mobile App Module Breakdown](#mobile-app-module-breakdown)
- [Expect/Actual Declarations](#expectactual-declarations)
- [Feature Parity Matrix](#feature-parity-matrix)
- [Architectural Patterns](#architectural-patterns)
- [Dependency Map](#dependency-map)
- [Known Gaps and TODOs](#known-gaps-and-todos)

---

## Architecture Overview

### PC App (Python, Twisted)
```
syncplay-pc-src-master/syncplay/
  client.py          Core client: sync algorithm, player polling, state management
  server.py          Server: rooms, watchers, state broadcast, controlled rooms
  protocols.py       JSON-over-TCP protocol (Twisted LineReceiver)
  constants.py       All thresholds, timeouts, limits
  clientManager.py   Client startup orchestrator
  messages.py        i18n message framework
  utils.py           Utility functions
  players/           Media player drivers (VLC, MPV, MPlayer, MPC, IINA)
  ui/                GUI (Qt/PySide) and CLI interfaces
```

**Architecture:** Event-driven (Twisted reactor). Client polls player every 0.1s, sends state to server every 1s. Server broadcasts room state to all watchers. JSON newline-delimited protocol over TCP with optional TLS.

### Mobile App (Kotlin Multiplatform)
```
shared/src/
  commonMain/kotlin/app/     Shared business logic + Compose UI
  androidMain/kotlin/app/    Android player engines, Netty networking, services
  iosMain/kotlin/app/        iOS player engines, SwiftNIO networking, bridges
androidApp/                  Android application shell
iosApp/                      iOS application shell (Swift + Xcode)
```

**Architecture:** MVVM with reactive StateFlow. Compose Multiplatform UI. Protocol layer is a direct port of the PC client/server. Platform-specific code handles player engines and TLS networking.

---

## Module Structure

### Gradle Modules
| Module | Purpose |
|--------|---------|
| `:shared` | KMP library: commonMain + androidMain + iosMain |
| `:androidApp` | Android app shell (depends on :shared) |
| `buildSrc` | Build config (AppConfig.kt, NativeBuildConfig.kt) |

### Build Configuration
- **Kotlin:** 2.3.20 | **AGP:** 9.1.0 | **Compose Multiplatform:** 1.11.0-beta02
- **Min SDK (Android):** 26 | **iOS Deployment:** 14.0
- **Java toolchain:** 21 | **NDK:** 29.0.14206865
- **Build flavors (Android):** `exoOnly` (ExoPlayer only) and `full` (ExoPlayer + MPV + VLC)
- **ABI splits:** armeabi-v7a, arm64-v8a, x86, x86_64

### Key Dependencies
| Category | Library | Version |
|----------|---------|---------|
| Networking | Ktor | 3.4.2 |
| Serialization | kotlinx-serialization | 1.10.0 |
| Persistence | DataStore | 1.3.0-alpha07 |
| Coroutines | kotlinx-coroutines | 1.10.2 |
| Media (Android) | Media3/ExoPlayer | 1.10.0 |
| Media (Android) | libVLC | 4.0.0-eap24 |
| Media (iOS) | MobileVLCKit | 3.7.2 |
| TLS (Android) | Netty | 4.1.131.Final |
| TLS (Android) | Conscrypt | 2.5.3 |
| Images | Coil | 3.4.0 |
| Theming | MaterialKolor | 5.0.0-alpha07 |
| Logging | Kermit | 2.1.0 |
| Navigation | Navigation3 | alpha |

---

## Syncplay Protocol Reference

### Wire Format
JSON objects delimited by `\r\n` (CRLF) over TCP. Optional TLS upgrade before Hello.

### Message Types

#### Client -> Server

**Hello** (first message after optional TLS)
```json
{"Hello": {"username": "user", "password": "md5hash", "room": {"name": "room"},
  "version": "1.7.3", "realversion": "1.7.3",
  "features": {"sharedPlaylists": true, "chat": true, "featureList": true,
               "readiness": true, "managedRooms": true}}}
```

**State** (periodic sync, ~1/sec)
```json
{"State": {"playstate": {"position": 123.45, "paused": false, "doSeek": false},
  "ping": {"latencyCalculation": <serverEchoTimestamp>, "clientLatencyCalculation": <clientTimestamp>, "clientRtt": 0.045},
  "ignoringOnTheFly": {"client": 0}}}
```

**Set** (room/file/ready/playlist changes)
```json
{"Set": {"file": {"name": "Movie.mkv", "duration": 7200.5, "size": 5368709120}}}
{"Set": {"ready": {"isReady": true, "manuallyInitiated": true}}}
{"Set": {"room": {"name": "new-room"}}}
{"Set": {"playlistChange": {"files": ["file1.mkv", "file2.mkv"]}}}
{"Set": {"playlistIndex": {"index": 2}}}
{"Set": {"controllerAuth": {"room": "+roomname:hash", "password": "AB-123-456"}}}
```

**Chat**
```json
{"Chat": "message text"}
```

**List** (request user list)
```json
{"List": null}
```

**TLS** (negotiate encryption)
```json
{"TLS": {"startTLS": "send"}}
```

#### Server -> Client

**Hello** (response with assigned username, features, MOTD)
```json
{"Hello": {"username": "user", "room": {"name": "room"}, "version": "1.2.255",
  "realversion": "1.7.5", "features": {...}, "motd": "Welcome!"}}
```

**State** (authoritative room state)
```json
{"State": {"playstate": {"position": 123.45, "paused": false, "doSeek": false, "setBy": "user"},
  "ping": {"latencyCalculation": <clientEchoTimestamp>, "serverRtt": 0.050,
           "clientLatencyCalculation": <serverEchoTimestamp>},
  "ignoringOnTheFly": {"server": 0, "client": 0}}}
```

**Set** (user/room/playlist events)
```json
{"Set": {"user": {"username": {"event": {"joined": {}}}}}}
{"Set": {"user": {"username": {"event": {"left": {}}}}}}
{"Set": {"user": {"username": {"file": {"name": "f.mkv", "duration": 100, "size": 1000}}}}}
{"Set": {"ready": {"username": "user", "isReady": true, "manuallyInitiated": true}}}
{"Set": {"newControlledRoom": {"roomName": "+room:HASH12", "password": "AB-123-456"}}}
{"Set": {"controllerAuth": {"user": "u", "room": "r", "success": true}}}
```

**List** (room/user details)
```json
{"List": {"roomName": {"user1": {"file": {...}, "position": 120.5, "controller": false, "isReady": true}}}}
```

**Chat** / **Error** / **TLS**
```json
{"Chat": {"username": "user", "message": "hello"}}
{"Error": {"message": "error text"}}
{"TLS": {"startTLS": true}}
```

### Connection Sequence
```
Client ──TCP connect──> Server
Client ──TLS {startTLS: "send"}──> Server  (if TLS enabled)
Client <──TLS {startTLS: true/false}── Server
  [TLS handshake if supported]
Client ──Hello──> Server
Client <──Hello── Server  (assigns username, room, features)
Client ──Set(Joined)──> Server
Client <──List── Server  (user list)
Client <──State── Server  (initial room state, triggers first sync)
  [Periodic State exchange begins]
```

### Authentication
- **Server password:** Client sends MD5 hash, server compares with its own MD5 hash
- **Controlled rooms:** Room name format `+baseName:HASH12CHARS`. Hash = SHA1(SHA256(baseName + SHA256(salt)) + SHA256(salt) + password)[:12].upper(). Password format: `XX-###-###`

### Feature Negotiation
Server Hello response includes `features` object. Client adapts behavior:
- `supportsChat`, `supportsReadiness`, `supportsManagedRooms`
- `maxChatMessageLength` (150), `maxUsernameLength` (16), `maxRoomNameLength` (35), `maxFilenameLength` (250)

---

## Synchronization Algorithm

### Constants (matching PC client)
| Constant | Value | Purpose |
|----------|-------|---------|
| SEEK_THRESHOLD | 1s | Min diff to count as seek |
| SLOWDOWN_RATE | 0.95 | Playback speed during slowdown |
| SLOWDOWN_THRESHOLD | 1.5s | Start slowdown when behind by this |
| SLOWDOWN_RESET_THRESHOLD | 0.1s | Revert speed when diff < this |
| REWIND_THRESHOLD | 12s (mobile) / 4s (PC) | Force seek if behind by this |
| FASTFORWARD_BEHIND_THRESHOLD | 1.75s | Detect ahead condition |
| FASTFORWARD_THRESHOLD | 5s | Trigger fastforward after sustained |
| FASTFORWARD_EXTRA_TIME | 0.25s | Overshoot compensation |
| FASTFORWARD_RESET_THRESHOLD | 3s | Cooldown period |
| PROTOCOL_TIMEOUT | 12.5s | Connection assumed dead |
| SERVER_STATE_INTERVAL | 1000ms | Server update frequency |
| PING_MOVING_AVERAGE_WEIGHT | 0.85 | RTT smoothing factor |

**Note:** Mobile uses 12s rewind threshold vs PC's 4s - a deliberate choice for mobile networks.

### Latency Compensation (PingService)
```
RTT = currentTime - echoedTimestamp
averageRtt = 0.85 * averageRtt + 0.15 * RTT    (EMA smoothing)
forwardDelay = averageRtt / 2                    (symmetric case)
             + (clientRtt - serverRtt)           (asymmetry compensation, if server faster)
```

### State Handler Algorithm (State.kt handle())
On receiving server State:
1. Parse `ignoringOnTheFly` counters (feedback suppression)
2. Extract position, paused, doSeek, setBy from playstate
3. Calculate `messageAge` from PingService.forwardDelay
4. If `clientIgnFly == 0`, update global state:
   - Store globalPaused, globalPositionMs (adjusted for drift if playing)
   - On first sync: seek player to position + set pause/play
5. **Seek handling:** If doSeek, reset speed to 1.0, broadcast seek callback
6. **Rewind** (if SYNC_REWIND enabled): diff > rewindThreshold && !doSeek -> rewind to server pos
7. **Fastforward** (if SYNC_FASTFORWARD enabled): diff < -1.75s -> track duration, trigger at -5s with +0.25s extra, cooldown at 3s
8. **Slowdown** (if SYNC_SLOWDOWN enabled): diff > 1.5s && !paused && setBy != self -> speed=0.95; diff < 0.1s -> speed=1.0
9. Pause state change: trigger onSomeonePaused() or onSomeonePlayed()
10. Send acknowledgment State back with current position

### Readiness / Unpause Logic (RoomEventDispatcher)
When user presses play, `instaplayConditionsMet()` checks UNPAUSE_ACTION preference:
- `IfAlreadyReady`: Play only if user is already marked ready
- `IfOthersReady`: Play only if all others are ready
- `IfMinUsersReady`: Play if >= 2 users ready (including self)
- `Always`: No readiness gating

---

## Mobile App Module Breakdown

### Core App (`shared/src/commonMain/kotlin/app/`)

| File | Purpose |
|------|---------|
| `AbstractManager.kt` | Base class for managers with coroutine dispatch utilities (onMainThread, onIOThread) |
| `AdamScreen.kt` | Root composable: navigation via NavDisplay, CompositionLocal providers (theme, viewmodel, palette) |
| `PlatformCallback.kt` | Interface for platform ops: brightness, PiP, orientation, haptics, media session, shortcuts |
| `Screen.kt` | Sealed interface: Home, Room(joinConfig?), ThemeCreator(theme?), ServerHost |
| `SyncplayViewmodel.kt` | App-level VM: backstack, theme management, custom themes, shared playlist toggle |

### Home Screen (`home/`)

| File | Purpose |
|------|---------|
| `HomeScreen.kt` | Join UI: username, room, server (dropdown with syncplay.pl:8995-8999), port, password, engine selector |
| `HomeViewmodel.kt` | Join room logic, snackbar state |
| `JoinConfig.kt` | Serializable config: user, room, ip, port, pw. Persistence via DataStore |
| `components/HomeAnimatedEngineButtonGroup.kt` | Animated player engine selector with gradient borders |
| `components/HomeTextField.kt` | Gradient-bordered text input with icons |
| `components/HomeTopBar.kt` | Logo, settings grid, theme picker, about popup |
| `components/PopupAPropos.kt` | About dialog: version, developer info, solo mode button |
| `components/PopupDidYaKnow.kt` | First-launch tips dialog |

### Player Abstraction (`player/`)

| File | Purpose |
|------|---------|
| `PlayerEngine.kt` | Interface: name, isDefault, isAvailable, img, createImpl() factory |
| `PlayerImpl.kt` | Abstract player: lifecycle (initialize/destroy), playback (play/pause/seekTo/setSpeed), media injection (URL/file), tracks (analyze/select/reapply), chapters (analyze/jump/skip), volume, aspect ratio, screenshot, subtitle loading, VideoPlayer() composable |
| `PlayerManager.kt` | State management: isPlayerReady, media, isNowPlaying, timeCurrentMillis, timeFullMillis, currentTrackChoices persistence |
| `Playback.kt` | Enum: PAUSE(false), PLAY(true) |
| `models/Chapter.kt` | Data: index, name, timeOffsetMillis |
| `models/MediaFile.kt` | Data: location, fileName, fileSize, fileDuration, tracks, chapters. Factory: mediaFromFile(), mediaFromUrl() |
| `models/MediaFileLocation.kt` | Sealed: Local(PlatformFile), Remote(url) |
| `models/PlayerOptions.kt` | ExoPlayer buffer config: maxBuffer, minBuffer, playbackBuffer, audioPreference, ccPreference |
| `models/Track.kt` | Abstract: name, type (AUDIO/SUBTITLE), index, selected |
| `models/TrackChoices.kt` | Engine-specific track selection state persistence across media changes |

### Protocol Client (`protocol/`)

| File | Purpose |
|------|---------|
| `ProtocolManager.kt` | Orchestrator: session, global state, sync thresholds, PingService, JSON serializer with polymorphic message types |
| `Session.kt` | Shared state: server/port/username/room/password, roomFeatures, userList (StateFlow), messageSequence (StateFlow), outboundQueue, sharedPlaylist, spIndex |
| `SyncplayProtocolHandler.kt` | Interface: onHello/State/Set/List/Chat/TLS/ErrorReceived(), routeMessage() |
| `event/ClientMessage.kt` | Sealed class outbound packets: Hello, Joined, EmptyList, Readiness, File, Chat, State, PlaylistChange, PlaylistIndex, ControllerAuth, RoomChange, TLS |
| `event/RoomCallback.kt` | Inbound event handlers: onSomeonePaused/Played/Joined/Left/Seeked/Behind/FastForwarded, onConnected/Disconnected/ConnectionFailed, onReceivedTLS, onNewControlledRoom, etc. Haptic feedback per event |
| `event/RoomEventDispatcher.kt` | Outbound actions: sendHello, sendSeek, sendMessage, controlPlayback (readiness gating), seekBckwd/seekFrwrd, broadcastMessage |
| `models/ConnectionState.kt` | Enum: DISCONNECTED, CONNECTING, CONNECTED, SCHEDULING_RECONNECT |
| `models/PingService.kt` | RTT calculation with EMA smoothing (weight=0.85), asymmetry-aware forwardDelay |
| `models/RoomFeatures.kt` | Serializable: isolateRooms, supportsReadiness/Chat/ManagedRooms, max lengths |
| `models/TlsState.kt` | Enum: TLS_NO, TLS_ASK, TLS_YES |
| `models/User.kt` | Data: index, name, readiness, file, isController |
| `network/NetworkManager.kt` | Abstract: connect/reconnect/send/handlePacket, state (ConnectionState flow), tls, engine enum. Reconnection with interval. Failed transmit queues to outboundQueue (except Hello) |
| `network/KtorNetworkManager.kt` | Ktor sockets implementation. No TLS support (Ktor limitation). Used as fallback on both platforms |
| `network/ServerMessageDeserializer.kt` | JsonContentPolymorphicSerializer: routes by top-level JSON key to typed deserializer |
| `server/Hello.kt` | Server Hello response: username, room, features, motd. Handler: updates session, sends Joined + EmptyList, calls onConnected |
| `server/State.kt` | Server State: playstate + ping + ignoringOnTheFly. Handler: full sync algorithm (see above) |
| `server/Set.kt` | Server Set: user events (join/left/file), room, controllerAuth, ready, playlist changes. Handler: routes to specific callbacks |
| `server/ListResponse.kt` | Server List: room->user map. Handler: builds User objects, emits to userList StateFlow |
| `server/Chat.kt` | Server Chat: username + message. Handler: calls onChatReceived |
| `server/Error.kt` | Server Error: message string |
| `server/TLS.kt` | Server TLS: startTLS boolean. Handler: calls onReceivedTLS |
| `server/FileData.kt` | Shared: name, duration, size |
| `server/ServerMessage.kt` | Sealed interface with handle() method |

### Server Hosting (`server/`)

| File | Purpose |
|------|---------|
| `SyncplayServer.kt` | Main server: addWatcher/removeWatcher, setWatcherRoom, startStateTimer (100ms initial + 1000ms interval), sendState, forcePositionUpdate, sendChat, setReady, setPlaylist, authRoomController, shutdown |
| `ServerRoomManager.kt` | Room lifecycle: moveWatcher, getOrCreateRoom (auto-creates ControlledServerRoom if name matches), deleteRoomIfEmpty, findFreeUsername (appends `_`), broadcast/broadcastRoom |
| `ServerViewmodel.kt` | UI ViewModel: config editing (port/password/motd/flags), start/stop server, public IP fetch (ipify.org), server logs |
| `ClientConnection.kt` | Per-client protocol handler (SyncplayProtocolHandler impl): onHello (validate/auth/addWatcher), onState (update watcher), onSet (route commands), sendHello/sendState/sendList/sendChat/etc. Password check via MD5 |
| `model/ServerConfig.kt` | Data: port (8999), password, isolateRooms, disableReady/Chat, salt, motd, maxLengths |
| `model/ServerRoom.kt` | Room state: watchers, playState (0=paused/1=playing), position (with elapsed calc), playlist. getPosition() uses slowest watcher if stale >1s |
| `model/ControlledServerRoom.kt` | Extends ServerRoom: controllers map, canControl() gate on setPaused/setPosition/setPlaylist |
| `model/ServerWatcher.kt` | Client representation: position (with elapsed), file, ready, features. updateState() detects pause changes and adjusts for messageAge |
| `model/RoomPasswordProvider.kt` | Controlled room crypto: SHA256+SHA1 hash chain, password format XX-###-###, regex validation |
| `network/ServerNetworkEngine.kt` | Expect class: startListening(port), stop(). Actual: Netty (Android), Ktor sockets (iOS) |
| `protocol/InboundMessageHandler.kt` | Routes JSON keys to ClientConnection handlers. Special: Chat comes as string, not object |
| `protocol/OutboundMessageBuilder.kt` | JSON builders for all server->client messages |
| `ui/ServerHostScreen.kt` | Compose UI: config form, start/stop, status, connected clients, IPs, scrollable log |

### Room UI (`room/`)

| File | Purpose |
|------|---------|
| `RoomViewmodel.kt` | Central coordinator: all managers (uiState, playerManager, networkManager, protocol, callback, dispatcher, playlistManager), isSoloMode, player engine loading, connection initiation, file mismatch checking, OSD dispatch |
| `RoomUiStateManager.kt` | UI state: msg, orientation, PiP, visibleHUD, tab cards (mutual exclusion), tabLock, controlPanel, gifPanel, hudInteractionSignal, hasActiveOverlay, uiOpacity, lifecycle forwarding |
| `RoomScreenUI.kt` | Main room composable: landscape/portrait layouts, VideoPlayer, background artwork, gesture interceptor, play button, chat, status, tabs, sliding cards, bottom bar, auto-hide HUD logic, popups (seek-to, chat history, managed room) |
| `models/Message.kt` | Chat message: sender, timestamp, content, isMainUser, isError, seen, isImageUrl. factorize() for styled AnnotatedString |
| `models/MessagePalette.kt` | Colors: timestamp, selftag, friendtag, systemmsg, usermsg, errormsg, includeTimestamp |
| `sharedplaylist/SharedPlaylistManager.kt` | Operations: shuffle, addURLs, addFiles, clearPlaylist, deleteItem, sendPlaylistSelection, changePlaylistSelection, retrieveFile, isUrlTrusted (domain validation) |
| `sharedplaylist/PlaylistExt.kt` | Expect functions: addFolderToPlaylist, iterateDirectory, savePlaylistLocally, loadPlaylistLocally |

### Room UI Components (`room/ui/`)

**bottombar/**
| File | Purpose |
|------|---------|
| `RoomSectionBottomBar.kt` | Container: ready toggle + seekbar + control panel + media add + video controls |
| `RoomSeekbar.kt` | Interactive seekbar: chapter dots, time display, seek bubble, server-synced seeking |
| `RoomReadyButton.kt` | Ready/not-ready toggle (green/red), broadcasts to server |
| `RoomControlsAboveSeekbar.kt` | Fast seek buttons, custom skip, chapter skip |
| `RoomControlPanel.kt` | Advanced: aspect ratio, screenshot, seek-to, undo seek, subtitle/audio/chapter dropdowns |
| `RoomMediaAddButton.kt` | Add from storage/URL menu with URL input popup |
| `PopupSeekToPosition.kt` | HH:MM:SS time input dialog for precise seeking |
| `BlackContrastUnderlay.kt` | Gradient overlay for HUD readability |

**chat/**
| File | Purpose |
|------|---------|
| `RoomSectionChat.kt` | Chat root: text field + chat box or GIF panel |
| `ChatTextField.kt` | Message input with send/GIF/clear buttons, 149 char limit |
| `ChatBox.kt` | Message history (LazyColumn), configurable appearance, inline image thumbnails |
| `RoomFadingChatLayout.kt` | Transient messages when HUD hidden, configurable fade duration |
| `GifPanel.kt` | GIF/sticker search via Klipy API: search, trending, recents, grid layout |

**misc/**
| File | Purpose |
|------|---------|
| `RoomPlayButton.kt` | Center play/pause with animated background circle |
| `RoomGestureInterceptor.kt` | Double-tap seek, vertical swipe brightness (left)/volume (right), long-press haptic |
| `RoomBackgroundArtwork.kt` | Syncplay logo when no video loaded |

**rightcards/**
| File | Purpose |
|------|---------|
| `RoomSectionSlidingCards.kt` | Animated card container (slide horizontal in landscape, vertical in portrait) |
| `CardUserInfo.kt` | User list: readiness icon, controller badge, filename, duration, size |
| `CardSharedPlaylist.kt` | Playlist management: add file/folder/URL, import/export, shuffle, media dirs, clear |
| `CardRoomPrefs.kt` | In-room settings grid with player-specific settings |

**statinfo/**
| File | Purpose |
|------|---------|
| `RoomSectionStatusInfo.kt` | Room name, connection status, OSD messages, currently playing file, reconnect button |

**tabs/**
| File | Purpose |
|------|---------|
| `RoomTabSection.kt` | Tab buttons: settings, playlist, user info, overflow menu (chat history, orientation, PiP, managed room, leave) |
| `RoomTab.kt` | Individual tab card (active/inactive states) |
| `ChatHistoryPopup.kt` | Full message history modal with scrollbar |
| `PopupManagedRoom.kt` | Create managed room (with generated password) or identify as operator |

### Preferences (`preferences/`)

| File | Purpose |
|------|---------|
| `Datastore.kt` | Global DataStore instance, scope, hot StateFlow of all preferences |
| `Pref.kt` | Type-safe preference wrapper: key mapping, sync read (value()), reactive flow, composable watch (watchPref()), async write (set()) |
| `PrefExtraConfig.kt` | Sealed interfaces for UI rendering: PerformAction, BooleanCallback, Slider, MultiChoice, ShowComposable, ColorPick, YesNoDialog, TextField |
| `Preferences.kt` | Central registry of all 60+ preferences (see below) |
| `SettingComposable.kt` | Generic composable renderer for any Pref with all extra config types |
| `settings/MySettings.kt` | Setting categories and groups (SETTINGS_GLOBAL, SETTINGS_ROOM) |
| `settings/SettingCategory.kt` | Category data class with DSL builder |
| `settings/SettingStyling.kt` | Data: titleSize, summarySize, iconSize, paddingUsed |
| `settings/SettingsUI.kt` | Settings grid layout and rendering |
| `settings/PopupColorPicker.kt` | Color selection dialog |
| `settings/PopupTrustedDomains.kt` | Trusted domain management dialog |

### Preference Categories

**Global Settings (SETTINGS_GLOBAL):**
- General: REMEMBER_INFO, NEVER_SHOW_TIPS, ERASE_SHORTCUTS, MEDIA_DIRECTORIES
- Language: DISPLAY_LANG, AUDIO_LANG, CC_LANG
- Syncing: READY_FIRST_HAND, UNPAUSE_ACTION, PAUSE_ON_SOMEONE_LEAVE, FILE_MISMATCH_WARNING, HASH_FILENAME, HASH_FILESIZE
- Network: TLS_ENABLE, NETWORK_ENGINE, TRUSTED_DOMAINS
- Advanced: EXPORT_LOGS, CLEAR_LOGS, GLOBAL_RESET_DEFAULTS

**Room Settings (SETTINGS_ROOM):**
- Sync Mechanisms: SYNC_DONT_SLOW_WITH_ME, SYNC_FASTFORWARD, SYNC_SLOWDOWN, SYNC_REWIND
- Chat Colors: COLOR_TIMESTAMP/SELFTAG/FRIENDTAG/SYSTEMMSG/USERMSG/ERRORMSG
- Chat Properties: MSG_ACTIVATE_STAMP, MSG_OUTLINE_*, MSG_SHADOW_*, MSG_BG_OPACITY, MSG_FONTSIZE, MSG_MAXCOUNT, MSG_FADING_DURATION, MSG_BOX_ACTION
- Player: CUSTOM_SEEK_FRONT/AMOUNT, SUBTITLE_SIZE, SEEK_FORWARD/BACKWARD_JUMP, SHOW_CHAPTER_DOTS, CHAPTER_DOTS_CLICKABLE, DOUBLETAP_SEEK, SWIPE_GESTURES, OSD_DURATION
- Haptics: HAPTIC_ON_JOINED/LEFT/CHAT/PAUSED/PLAYED/SEEKED/PLAYLIST/CONNECTION
- Advanced: ROOM_UI_OPACITY, HUD_AUTO_HIDE_TIMEOUT, RECONNECTION_INTERVAL, INROOM_RESET_DEFAULTS
- MPV-specific: MPV_HARDWARE_ACCELERATION, MPV_GPU_NEXT, MPV_DEBUG_MODE, MPV_VIDSYNC, MPV_PROFILE, MPV_INTERPOLATION
- ExoPlayer-specific: EXO_MAX_BUFFER, EXO_MIN_BUFFER, EXO_SEEK_BUFFER

### Theme System (`theme/`)

| File | Purpose |
|------|---------|
| `BuiltinThemes.kt` | Predefined themes: PYNCSLAY, GrayOLED, ALLEY_LAMP, SILVER_LAKE (default), BLANK_THEME |
| `SaveableTheme.kt` | Serializable theme: primary/secondary/tertiary/neutral colors, contrast, isDark, isAMOLED, PaletteStyle, syncplayGradients |
| `ThemeCreatorScreen.kt` | Theme editor UI: color pickers, palette style, AMOLED, contrast |
| `ThemePicker.kt` | Theme selection grid: built-in + custom themes |
| `Theming.kt` | Utilities: flexibleGradient, backgroundGradient, brand colors (NeoSP1/2/3), semantic colors (MSG_*), spacing tokens, ROOM_ICON_SIZE |

### Klipy GIF Integration (`klipy/`)

| File | Purpose |
|------|---------|
| `KlipyAPI.kt` | Ktorfit API: searchGifs/Stickers, trending, recents, share analytics |
| `GifSearchResponse.kt` | Response models: GifItem, GifFile (hd/md/sm/xs), GifFormat |
| `KlipyUtils.kt` | Client wrapper: search, trending, recents, trackShare. Uses unique user ID |

### UI Components (`uicomponents/`)

| File | Purpose |
|------|---------|
| `AnimatedImage.kt` | Expect/actual: animated GIF/WebP display |
| `FlexibleIcon.kt` | Gradient-tinted icon with shadow and click |
| `FlexibleText.kt` | Layered text rendering: shadow + stroke + gradient fill |
| `Fonts.kt` | Font accessors: lexend, jost, saira, helvetica, syncplay (Directive4) |
| `FreeAnimatedVisibility.kt` | Scope-disambiguated AnimatedVisibility wrapper |
| `MessagePalette.kt` | Derives chat colors from preferences as composable State |
| `MultiChoiceDialog.kt` | Radio-button selection dialog |
| `Overlays.kt` | Modifiers: gradientOverlay, solidOverlay (SrcAtop blend) |
| `PopupMediaDirs.kt` | Media directory management popup |
| `ScreenDimensions.kt` | Screen size accessors (px) |
| `SyncplayPopup.kt` | Gradient-bordered popup dialog base |

### Utilities (`utils/`)

| File | Purpose |
|------|---------|
| `CommonUtils.kt` | Constants (vidExs: 40+ extensions, ccExs, playlistExs), generateClockstamp(), md5(), sha256(), @ProtocolApi annotation |
| `LogUtils.kt` | File logging with 7-day retention, loggy(), logFile export, clearLogs() |
| `PlatformUtils.kt` | Expect declarations: timestamps, file ops, system bars, weak refs, device IP, platform enum, player engines |

---

## Expect/Actual Declarations

### Network Layer
| Expect | Android Actual | iOS Actual |
|--------|---------------|------------|
| `instantiateNetworkManager()` | `NettyNetworkManager` or `KtorNetworkManager` (based on NETWORK_ENGINE pref) | `SwiftNioNetworkManager` or `KtorNetworkManager` |
| `ServerNetworkEngine` | Netty ServerBootstrap | Ktor raw sockets |

### Player Engines
| Expect | Android Actual | iOS Actual |
|--------|---------------|------------|
| `availablePlatformPlayerEngines` | [ExoEngine, MpvEngine, VlcEngine] | [VlcKitEngine, AVPlayerEngine] + MpvKitEngine if bridge registered |

### Playlist Operations
| Expect | Android Actual | iOS Actual |
|--------|---------------|------------|
| `addFolderToPlaylist(uri)` | DocumentsContract tree enumeration | NSFileCoordinator with security-scoped access |
| `iterateDirectory(uri, target, onFound)` | Recursive DocumentsContract walk | NSFileManager enumeration with bookmarks |
| `savePlaylistLocally(uri)` | ContentResolver output stream | NSString file writing |
| `loadPlaylistLocally(uri, shuffle)` | ContentResolver input stream | NSString file reading |

### Platform Utilities
| Expect | Android Actual | iOS Actual |
|--------|---------------|------------|
| `generateTimestampMillis()` | System.currentTimeMillis() | NSDate-based |
| `getFileName(uri)` | ContentResolver query | NSURL attributes |
| `getFileSize(uri)` | ContentResolver query | NSURL attributes |
| `getDeviceIpAddress()` | NetworkInterface enumeration | C interop (ifaddrs) |
| `HideSystemBars()` | WindowInsetsController | UIApplication statusBar |
| `platform` | Platform.Android | Platform.IOS |
| `AnimatedImage` composable | Coil3 AsyncImage | CGImageSource + UIImage.animatedImage |

### Platform Callbacks (PlatformCallback interface)
| Method | Android Impl | iOS Impl |
|--------|-------------|----------|
| `onPictureInPicture()` | PiP params + RemoteAction | AVPictureInPictureController |
| `changeCurrentBrightness()` | WindowManager.LayoutParams | UIScreen.main.brightness |
| `onScreenOrientationChanged()` | Activity.requestedOrientation | UIWindowScene geometry update |
| `performHapticFeedback()` | Vibrator API | UIImpactFeedbackGenerator |
| `mediaSessionInitialize()` | Foreground service start | No-op |
| `serverServiceStart()` | Foreground service start | No-op |
| `onLanguageChanged()` | Per-app language (AppCompatDelegate) | Opens iOS Settings |

---

## Feature Parity Matrix

| Feature | PC Client | Mobile Client | Notes |
|---------|:---------:|:-------------:|-------|
| **Core Protocol** | | | |
| Connect to server | Y | Y | Full wire-format compatibility |
| TLS encryption | Y | Y | Netty (Android), SwiftNIO (iOS), no Ktor TLS yet |
| Room join/switch | Y | Y | |
| Server password auth | Y | Y | MD5 hashing |
| **Synchronization** | | | |
| Position sync | Y | Y | Full algorithm port |
| Pause/play sync | Y | Y | |
| Seek sync | Y | Y | |
| Slowdown (95% speed) | Y | Y | Configurable per-pref |
| Fastforward | Y | Y | |
| Rewind on desync | Y | Y | 12s threshold (vs 4s PC) |
| Latency compensation | Y | Y | EMA-smoothed RTT |
| ignoringOnTheFly | Y | Y | Feedback suppression |
| **User Features** | | | |
| Chat | Y | Y | With inline GIF/image support (mobile-only) |
| User readiness | Y | Y | 4 unpause action modes |
| Shared playlists | Y | Y | Add/remove/shuffle/import/export |
| Playlist auto-advance | Y | Y | On playback end |
| File privacy (hash name/size) | Y | Y | 3 modes each |
| Trusted domains | Y | Y | With popup editor |
| **Controlled Rooms** | | | |
| Create managed room | Y | Y | SHA256+SHA1 password hash |
| Identify as operator | Y | Y | |
| Controller-only state changes | Y | Y | |
| **Server Hosting** | | | |
| Built-in server | Y | Y | Full server port in mobile app |
| Room isolation | Y | Y | PublicServerRoomManager |
| Disable chat/ready | Y | Y | Server config flags |
| Persistent rooms | Y | N | No SQLite database on mobile server |
| Server statistics | Y | N | No stats recording |
| IPv6 support | Y | N | Mobile server is IPv4 only |
| **Player Support** | | | |
| VLC | Y | Y | Android: libVLC 4.0.0-eap24, iOS: MobileVLCKit 3.7.2 |
| MPV | Y | Y | Android: JNI, iOS: MPVKit bridge |
| ExoPlayer | N | Y | Android-only, mobile default |
| AVPlayer | N | Y | iOS-only, native Apple player |
| MPlayer/MPC/IINA | Y | N | Desktop-only players |
| Speed control | Y | Y | All engines |
| Chapter support | Y | Y | MPV, VLC (not ExoPlayer, not AVPlayer) |
| External subtitles | Y | Y | All except AVPlayer |
| **UI Features** | | | |
| GUI | Y (Qt) | Y (Compose) | Full Compose Multiplatform |
| CLI | Y | N | |
| Dark mode | Y | Y | Full theme system with AMOLED, custom themes |
| OSD notifications | Y | Y | Configurable duration |
| **Mobile-Only Features** | | | |
| Picture-in-Picture | N | Y | Android + iOS (AVPlayer) |
| Gesture controls | N | Y | Double-tap seek, swipe brightness/volume |
| Haptic feedback | N | Y | 8 configurable event types |
| GIF chat | N | Y | Klipy API integration |
| Home screen shortcuts | N | Y | Quick join configs |
| Solo/offline mode | N | Y | Play without server |
| Multiple player engines | N | Y | Switch between ExoPlayer/MPV/VLC at runtime |
| Theme creator | N | Y | Custom Material3 themes |
| Android TV support | N | Y | D-pad navigation |
| **Missing from Mobile** | | | |
| File switch manager | Y | N | Auto-find matching files across users |
| Console/CLI interface | Y | N | |
| Per-user offset | Y | N | Manual time offset |
| MPC-HC/MPC-BE/MPlayer/IINA | Y | N | Desktop-specific players |
| Persistent rooms (server) | Y | N | No database |

---

## Architectural Patterns

### State Management
- **MVVM** with `ViewModel` + `StateFlow`/`MutableStateFlow`
- `RoomViewmodel` is the central coordinator, owns all managers
- Managers (`PlayerManager`, `RoomUiStateManager`, `ProtocolManager`, etc.) extend `AbstractManager` for coroutine scope
- UI collects StateFlows in composables via `collectAsState()`

### Dependency Injection
- **Manual DI** via constructor injection and lazy initialization
- No DI framework (no Koin/Hilt/etc.)
- Platform factories via expect/actual pattern
- `CompositionLocal` for UI-scoped dependencies (theme, viewmodel, palette)

### Navigation
- `NavDisplay` (Navigation3 alpha) with `SnapshotStateList<Screen>` backstack
- Screens are `@Serializable` sealed interface subtypes
- Entry decorators for transitions

### Networking
- **Client:** Abstract `NetworkManager` with platform-specific implementations
  - Android default: Netty (TLS support)
  - iOS default: SwiftNIO (TLS support)
  - Fallback: Ktor raw sockets (no TLS)
- **Server:** `ServerNetworkEngine` expect/actual
  - Android: Netty ServerBootstrap
  - iOS: Ktor raw sockets
- Reconnection: configurable interval, queues unsent packets in `outboundQueue`

### Player Architecture
- Abstract `PlayerImpl` defines full interface
- `PlayerEngine` object provides factory + metadata
- Platform implementations:
  - Android: ExoImpl (Media3), MpvImpl (JNI), VlcImpl (libVLC)
  - iOS: AVPlayerEngine (AVFoundation), MpvKitImpl (bridge to Swift), VlcKitImpl (VLCKit)
- Track choices persisted across media switches via `TrackChoices`
- `VideoPlayer()` composable per engine renders native video surface

### Protocol Architecture
- Messages are `@Serializable` data classes implementing `ServerMessage` sealed interface
- Each message type has a `handle()` method that processes itself (visitor-like pattern)
- `ServerMessageDeserializer` (JsonContentPolymorphicSerializer) routes by JSON key
- Outbound messages built via `ClientMessage` sealed class with `toJson()` serialization
- `SyncplayProtocolHandler` interface shared between client and server code

### Preference System
- Type-safe `Pref<T>` wrapper with DataStore backend
- Cached `StateFlow` per preference for reactive UI
- `SettingConfig` DSL for auto-generating settings UI (title, summary, icon, extra config)
- Categories organize settings into groups with icons

---

## Dependency Map

```
SyncplayViewmodel
  ├── backstack: SnapshotStateList<Screen>
  ├── currentTheme / customThemes (Preferences-backed)
  └── homeWeakRef / roomWeakRef

HomeViewmodel
  ├── JoinConfig (serializable)
  └── navigates to → Screen.Room(joinConfig)

RoomViewmodel (central hub)
  ├── RoomUiStateManager (UI state)
  ├── PlayerManager
  │   ├── PlayerImpl (platform-specific)
  │   │   ├── ExoImpl / MpvImpl / VlcImpl (Android)
  │   │   └── AVPlayerImpl / MpvKitImpl / VlcKitImpl (iOS)
  │   └── TrackChoices
  ├── NetworkManager (platform-specific)
  │   ├── NettyNetworkManager (Android default)
  │   ├── SwiftNioNetworkManager (iOS default)
  │   └── KtorNetworkManager (fallback)
  ├── ProtocolManager
  │   ├── Session (shared state)
  │   │   ├── userList: StateFlow<List<User>>
  │   │   ├── messageSequence: StateFlow<List<Message>>
  │   │   ├── sharedPlaylist
  │   │   └── outboundQueue
  │   └── PingService
  ├── RoomCallback (inbound protocol events)
  │   └── → updates Session, PlayerManager, dispatches UI messages
  ├── RoomEventDispatcher (outbound user actions)
  │   └── → sends via NetworkManager
  └── SharedPlaylistManager
      └── → uses Session.sharedPlaylist, NetworkManager

ServerViewmodel
  ├── SyncplayServer
  │   ├── ServerRoomManager
  │   │   ├── ServerRoom / ControlledServerRoom
  │   │   │   └── ServerWatcher[]
  │   │   └── RoomPasswordProvider
  │   └── ClientConnection[] (per-client protocol handlers)
  │       ├── OutboundMessageBuilder
  │       └── InboundMessageHandler
  └── ServerNetworkEngine (platform-specific)
```

---

## Known Gaps and TODOs

### Code-Level TODOs
- `TrackChoices`: Comment says "TODO: Convert to sealed class for type safety" (currently uses engine-specific fields)
- `PlayerManager.timeCurrentMillis/timeFullMillis`: Comments say "TODO: replace with media.fileTimePos/fileDuration"
- `KtorNetworkManager.upgradeTls()`: TODO - opportunistic TLS not supported by Ktor (issue #6623)
- `PlayerImpl.takeScreenshot()`: Returns null on most engines (only partially implemented)

### Missing PC Features
- **File switch manager:** PC auto-finds matching files when one user plays a different file. Mobile has no equivalent
- **Per-user time offset:** PC allows manual offset adjustment per user
- **Persistent rooms (server):** PC server saves room state to SQLite. Mobile server is ephemeral only
- **Server statistics:** PC server records client version stats hourly
- **IPv6 server:** PC server listens on both IPv4 and IPv6. Mobile server is IPv4 only
- **Console UI:** PC has a CLI interface alternative to GUI

### Platform Limitations
- **iOS server hosting:** May not work reliably in background (no foreground services on iOS)
- **iOS TLS (server-side):** Mobile server always responds `TLS: false` (no server TLS)
- **AVPlayer:** No external subtitle support, no chapter support, limited format support
- **ExoPlayer:** No chapter support
- **Ktor networking:** No TLS upgrade support - must use Netty (Android) or SwiftNIO (iOS) for encrypted connections

### Architectural Notes
- No DI framework - manual wiring could become complex as features grow
- Some iOS functionality requires Swift bridges (MpvKitBridge, SwiftNioNetworkManager) registered at app init
- `VlcPlayer4.kt` in iosMain is commented out (legacy code)
- Rewind threshold is 12s on mobile vs 4s on PC - intentional for mobile network conditions
- Server password comparison uses MD5 (protocol requirement, not a security concern since it's over TLS)
