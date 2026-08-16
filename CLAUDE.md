# Synkplay — Codebase Reference

Kotlin Multiplatform (KMP) port of [Syncplay](https://syncplay.pl), a synchronized media-playback app for Android, iOS and desktop (Windows/macOS/Linux). The reference Python/Twisted desktop client lives in `syncplay-pc-src-master/` and the mobile code is a deliberate port of its protocol and sync algorithm. This document maps the code **as it stands at version 0.23.0** (2026-06-17).

**App name:** Synkplay | **Version:** 0.23.0 | **Min SDK (Android):** 26 | **iOS deployment:** 14.0

| | |
|---|---|
| Package (full flavor) | `com.yuroyami.syncplay` |
| Package (exoOnly flavor) | `com.reddnek.syncplay` |
| iOS bundle base | `com.yuroyami.syncplay.iosApp` |
| Android flavors | `full` (ExoPlayer + MPV + VLC native libs) / `exoOnly` (ExoPlayer only, no native libs) |
| Shared-state SSOT | KiteSSOT plugin `io.github.yuroyami.kitessot` 2.0.2 (appName/version/bundleId/SDKs/logo; values fed from buildSrc `AppConfig`) |

---

## Table of Contents
- [Overview](#overview)
- [Module & Build Structure](#module--build-structure)
- [Key Dependencies](#key-dependencies)
- [Syncplay Protocol (as implemented)](#syncplay-protocol-as-implemented)
- [Synchronization Algorithm](#synchronization-algorithm)
- [Subsystem Breakdown](#subsystem-breakdown)
- [expect/actual Map](#expectactual-map)
- [Player Engines](#player-engines)
- [Built-in Server](#built-in-server)
- [Architectural Patterns](#architectural-patterns)
- [Known Gaps & Platform Limitations](#known-gaps--platform-limitations)

---

## Overview

**Desktop reference (Python, Twisted).** Event-driven: the client polls its external player every 0.1 s and sends a `State` to the server every 1 s; the server broadcasts authoritative room state. JSON objects delimited by `\r\n` over TCP, optional opportunistic TLS.

**Mobile (KMP).** MVVM with reactive `StateFlow`, Compose Multiplatform UI in `commonMain`, platform code in `androidMain`/`iosMain`. The protocol layer is a port of the desktop client and server; platform code handles player engines, TLS networking, and OS integration. A central difference from desktop: **mobile owns its embedded player** (no external player to poll over IPC), which removes the desktop client's out-of-band seek re-derivation and lets sync rely on explicit seek announcements.

---

## Module & Build Structure

| Gradle module | Purpose |
|---|---|
| `:shared` | KMP library: `commonMain` + `androidMain` + `iosMain` + `desktopMain` + `commonTest` |
| `:androidApp` | Android app shell (single Activity, depends on `:shared`) |
| `:desktopApp` | Compose for Desktop shell (`app.desktop.MainKt`), libVLC bundling, jpackage distributions |
| `buildSrc` | Build config helpers: `AppConfig.kt`, `NativeBuildConfig.kt` |
| `iosApp/` | Xcode/SwiftUI shell hosting the Compose UI plus Swift bridges |

**Toolchain (from `gradle.properties` / `libs.versions.toml`):** Kotlin 2.4.10, AGP 9.2.1, Compose Multiplatform 1.12.0-alpha01, compileSdk 37, minSdk 26, targetSdk 37, NDK 29.0.14206865, Java toolchain 21, buildToolsVersion 37.0.0 (pinned for reproducible builds).

**`exoOnly` flag** is the single source of truth resolved by `AppConfig.resolveExoOnly(providers)` (overridable with `-PexoOnly=true`), used by root KiteSSOT to emit the commonMain `EXOPLAYER_ONLY` BuildConfig field and by `androidApp` for flavor selection. `exoOnly` switches the applicationId to `com.reddnek.syncplay`, skips the mpv/VLC native build scripts, and ships no native player libs.

**buildSrc holds all non-trivial build logic; the four `build.gradle.kts` files stay declarative.**
- `AppConfig.kt` — app identity SSOT fed into the plugin block and read by modules (`APP_NAME`, `VERSION_NAME`, `BUNDLE_ID_BASE`/`_EXO`, `SHARED_MODULE_NAME`, `macosPackageVersion` — jpackage rejects 0.x.y), `localProperties(rootDir)` (signing secrets — caller must pass `rootDir` explicitly), Trinity brand colors (`0xFF4FD1FF` cyan / `0xFF5A7CFF` blue / `0xFF7A3CFF` purple), `abiCodes`, `mpvLibs` (9 `.so` files), and the custom propagators `propagateTrinityColors()` (rewrites `ic_launcher_foreground.xml` gradient stops) + `propagateDefaultStrings()` (`values-en/strings.xml` → `values/strings.xml`); `propagateAllCustom()` runs them.
- `NativeBuildConfig.kt` — mpv cross-compile `Exec` task (`runAndroidMpvNativeBuildScripts`, disabled on Windows), `validateNdk()`, plus the libc++ guards: `registerMpvLibcxxGuards()` (full flavor: `restoreMpvLibcxx` copies the NDK r29 `libc++_shared.so` next to the mpv libs, `verifyMpvLibcxx` greps for the `from_chars_floating` marker and fails packaging otherwise) and `registerExoOnlyLibcxxPrune()` (exoOnly reproducibility, issue #105).
- `AndroidReleaseAllTask.kt` — the `androidReleaseAll` task class + root registration (see Release artifacts below).
- `DesktopNativesConfig.kt` — `registerDesktopNativeTasks()` for :desktopApp (`fetchVlcNatives`, `fetchMpvNatives`, `vlcSmokeTest`, `mpvSmokeTest`, package-task hooks, `prepareAppResources` plugins.dat prune). Gotcha encoded there twice: inside a task-config lambda, `extensions` resolves to the TASK's container — project-level lookups must be resolved outside.

**KiteSSOT migration (DONE, 2026-07-24).** The SSOT plugin is `io.github.yuroyami.kitessot` 2.0.2 (Gradle Plugin Portal; successor of kmp-ssot). Root `build.gradle.kts` holds the whole `kiteSsot { }` config, fed from `AppConfig`. Key semantics vs the old kmp-ssot:
- Identity applies in AGP `finalizeDsl` (AFTER module DSL), so the exoOnly applicationId swap lives in the root block (`bundleIdBase = if (exoOnly) …`), NOT in androidApp's `defaultConfig`. A module-level override cannot win anymore.
- Runtime constants are generated directly into `shared/commonMain` by `kiteSsot { buildConfig { } }`; no platform `expect`/`actual` declarations are used. `DEBUG` is true only for an explicit Android debug-only Gradle invocation. KiteSSOT owns one commonMain object per invocation, so mixed/aggregate variant builds conservatively use `DEBUG=false` while remaining buildable.
- versionCode scheme is identical to kmp-ssot (`1xxxyyyzzz`; 0.23.1 → `1000023001`) — store continuity holds.
- Branding is on-demand: launcher assets regenerate ONLY via `kiteSsotSyncAndroidLogo` (no more per-build icon stomping / launcher-PNG git noise). `compileSdk >= 33` in the block makes that task emit `mipmap-anydpi-v33` wrappers with the themed-icon `<monochrome>` layer (issue #143); the previous hand-written v33 files + `drawable/ic_launcher_monochrome.xml` were deleted — the plugin owns all launcher assets now (ownership manifests under `.kitessot/`).
- `kiteSsotDoctor` / `kiteSsotCheck` / `kiteSsotPlan` are the diagnostics; `kiteSsotVerify` gates releases.
- iOS side is NOT auto-synced anymore (kitessot mutates sources only via explicit tasks). `syncIos = true` + `ios { deploymentTarget = "14.0" }` are set in the root block: on a version bump run `kiteSsotSyncIosConfig` (pbxproj identity/version/knownRegions; leaves a gitignored `*.kitessot.bak`), and after logo changes `kiteSsotSyncIosLogo` (regenerates `AppIcon.appiconset`, 1024 universal). The 2026-07-24 sync switched `ASSETCATALOG_COMPILER_APPICON_NAME` from the legacy `logo` set to `AppIcon`; `logo.appiconset` is an unreferenced leftover. Plugin state (`.kitessot/` dirs, `*.kitessot.bak`) is gitignored — regenerable local state, never assets.
- kitessot 2.0.1 shipped the AGP 9.2.x compat widening, and 2.0.2 fixed a real-world config-time bug this migration uncovered (resilient-invocation detection realized requested tasks from `plugins.withId`, observing `:shared`'s compile classpaths before its script body ran — "configuration was observed" / "property 'languageVersion' is final" when requesting `:shared:compileAndroidMain` directly). Do not downgrade below 2.0.2.

**Launcher icon ownership (themed icon, issue #143).** KiteSSOT owns every launcher asset: the per-density PNGs, the `mipmap-anydpi-v26` wrappers, and the `mipmap-anydpi-v33` wrappers whose `<monochrome>` layer (referencing `@mipmap/ic_launcher_foreground` — the launcher only uses its alpha) provides the Android 13+ themed icon. Regeneration is ON-DEMAND via `kiteSsotSyncAndroidLogo` from `logo_fg.png`/`logo_bg.png` — normal builds never touch icons. Do NOT hand-edit generated launcher files; artwork changes go through the logo PNGs (then rerun the sync task) or, for the in-app gradient vector `ic_launcher_foreground.xml` in shared res, through `propagateTrinityColors()`.

**Release artifacts.** `androidReleaseAll` (class + registration in `buildSrc/AndroidReleaseAllTask.kt`) shells out to **three** separate `./gradlew` runs to produce 7 files into `AndroidAppOutput/`: 5 full ABI-split release APKs, 1 exoOnly universal APK, 1 full AAB. Three processes are required because only one product flavor exists per Gradle invocation, and ABI splits and the AAB are mutually exclusive in one task graph (AGP issuetracker 402800800).

**Android-only native gotchas.** `restoreMpvLibcxx` copies the NDK r29 `libc++_shared.so` into `src/main/libs/<abi>/`; `verifyMpvLibcxx` greps for the `from_chars_floating` symbol and fails the build if missing (libVLC's older libc++ lacks `__from_chars_floating_point`, crashing mpv at load). `exoOnly` runs `pruneStaleExoOnlyLibcxx` so the exo-only APK deterministically uses the VLC AAR's libc++.

**Reproducible-build constraints (IzzyOnDroid, issue #105).** Do NOT re-add foojay-resolver or pin a JVM toolchain vendor; JDK 21 is requested vendor-neutrally. `mavenLocal()` is temporarily present and content-filtered to `io.github.yuroyami` only while KitePlayer 0.0.4 and the KiteCodec 0.0.6 plugin are local publications. A clean release runner must publish those twins locally first (and provide KiteCodec's iOS FFmpeg tree), or switch to their eventual remote repository; arbitrary Maven-local artifacts must never be allowed to shadow Central. `jitpack.io` is for the NewPipe Extractor.

---

## Key Dependencies

| Category | Library | Version |
|---|---|---|
| Networking (client) | Ktor | 3.5.0 |
| TLS (Android) | Netty | 4.1.131.Final |
| TLS provider (Android) | Conscrypt | 2.5.3 |
| Serialization | kotlinx-serialization-json | 1.11.0 |
| HTTP/REST (Klipy, OpenSubtitles) | Ktorfit | 2.7.4 (compiler plugin pinned `2.3.5`, tracks Kotlin ABI) |
| Persistence | DataStore (preferences-core) | 1.3.0-alpha09 |
| Coroutines | kotlinx-coroutines | 1.11.0 |
| Atomics | atomicfu | 0.33.0 |
| Date/time | kotlinx-datetime | 0.8.0 |
| Media (Android) | Media3 / ExoPlayer | 1.10.1 |
| Media (Android) | libVLC | 4.0.0-eap26 |
| Media (iOS) | VLCKit (CocoaPods) | 4.0.0a19 |
| Images / GIF | Coil3 | 3.4.0 |
| Theming | MaterialKolor | 5.0.0-alpha07 |
| Color picker | kolor-picker | 2.1.0 |
| Logging | Kermit | 2.1.0 |
| Navigation | Navigation3 (runtime/ui) | 1.1.1 |
| Hashing | KotlinCrypto (md/sha1/sha2/digest) | 0.8.0 |
| Files | FileKit | 0.14.1 |
| Stream resolver (Android) | NewPipe Extractor | v0.24.6 |
| Skiko (force-pinned) | skiko | 0.148.1 |

`skiko` is force-pinned across all configurations because its native binary must match what Compose was compiled against; bump it when compose-multiplatform is upgraded. The ExoPlayer FFmpeg audio renderer ships as a local AAR (`libs/libffmpeg_media3exo_1.8.0.aar`). On iOS, MPVKit and YouTubeKit are Swift Package Manager packages wired in via the Xcode project, not declared here.

---

## Syncplay Protocol (as implemented)

JSON objects delimited by `\r\n` (CRLF) over TCP. The wire layer is `app.protocol.wire.*` (one `@Serializable` DTO per payload; most fields nullable so one class serves both directions). The message envelopes are the `WireMessage` sealed hierarchy. **Note:** older `ClientMessage`/`ServerMessage`/`SyncplayProtocolHandler` names are gone — everything routes through `WireMessage` / `WireMessageDeserializer` / `WireMessageHandler`, shared by both the client (`app.room.RoomServerMessageHandler`) and the built-in server (`app.server.ClientConnection`).

### `WireMessage` sealed hierarchy (`protocol/WireMessage.kt`)

Five variants are **wire-symmetric** (same JSON both directions): `Hello`, `State`, `Set`, `TLS`, `Error`. Two keys are split because their payloads differ by direction:

| Variant | Direction | Shape |
|---|---|---|
| `ChatRequest` | client→server | `{"Chat": "msg"}` (bare string) |
| `ChatBroadcast` | server→client | `{"Chat": {"username", "message"}}` |
| `ListRequest` | client→server | `{"List": null}` |
| `ListResponse` | server→client | `{"List": {"<room>": {"<user>": ListUserData}}}` |

Each subclass implements `toJson()` as `syncplayJson.encodeToString(this)` **on purpose**: encoding via the `WireMessage` interface type would invoke the polymorphic serializer and inject an illegal `"type"` class discriminator. `dispatch(handler)` is the visitor that calls the matching `on…` method. `ListRequest.placeholder` defaults to `JsonNull` (not Kotlin `null`) so `{"List":null}` survives `explicitNulls=false` instead of collapsing to `{}`.

`WireMessageDeserializer` (`JsonContentPolymorphicSerializer`) routes by the first top-level key, and by payload shape for the two asymmetric keys: `Chat` string→`ChatRequest`, else `ChatBroadcast`; `List` JsonObject (even empty `{}`)→`ListResponse`, else→`ListRequest`. Non-object input and unknown keys throw **`SerializationException`** — the only exception type the skip-a-poisoned-line catch covers in `NetworkManager`/`ClientConnection`.

`WireMessageHandler` is the side-agnostic visitor interface; all methods default to no-op. The client overrides `onHello/onState/onSet/onTLS/onError/onListResponse/onChatBroadcast`; the server overrides `onHello/onState/onSet/onTLS/onError/onListRequest/onChatRequest`.

### `syncplayJson` (`protocol/SyncplayJson.kt`)

The one shared `Json` for both directions: `ignoreUnknownKeys=true`, `coerceInputValues=true`, `encodeDefaults=true` (byte-compat with the Python server, e.g. keeping empty `motd`), `explicitNulls=false` (drop null fields to match Python omitting absent keys).

### Real JSON shapes

**Hello** (handshake, both directions; `HelloData`):
```json
{"Hello": {"username": "u", "password": "md5hex", "room": {"name": "r"},
  "version": "1.2.255", "realversion": "1.7.5",
  "features": {"sharedPlaylists": true, "chat": true, "readiness": true,
               "managedRooms": true, "setOthersReadiness": true}}}
```
Client advertises `realversion = "1.7.5"` (matches PC `RECENT_CLIENT_THRESHOLD`) and `version = "1.2.255"` (legacy 1.2.x compat shim). `password` is client-only; `motd` is server-only.

**State** (`StateData` = `playstate` + `ping` + `ignoringOnTheFly`):
```json
{"State": {"playstate": {"position": 123.45, "paused": false, "doSeek": false, "setBy": "u"},
  "ping": {"latencyCalculation": <echo>, "clientLatencyCalculation": <ts>,
           "clientRtt": 0.045, "serverRtt": 0.05},
  "ignoringOnTheFly": {"server": 0, "client": 0}}}
```
`position` is **full-precision seconds (Double) — never rounded**; sub-second precision drives server desync detection and slowest-watcher selection.

**Set** (`SetData`; each direction populates a subset):
```json
{"Set": {"file": {"name": "M.mkv", "duration": 7200.5, "size": 5368709120}}}
{"Set": {"ready": {"isReady": true, "manuallyInitiated": true}}}
{"Set": {"room": {"name": "new-room"}}}
{"Set": {"playlistChange": {"user": "u", "files": ["a.mkv", "b.mkv"]}}}
{"Set": {"playlistIndex": {"user": "u", "index": 2}}}
{"Set": {"controllerAuth": {"room": "+r:HASH", "password": "AB-123-456"}}}
{"Set": {"user": {"u": {"event": {"joined": {}}, "room": {"name": "r"}}}}}
{"Set": {"newControlledRoom": {"roomName": "+r:HASH12", "password": "AB-123-456"}}}
```

**FileData.size is polymorphic** (`FileSizeSerializer`): decodes a JSON number, string, or `0` all to `String`; encodes a raw byte count (and the hidden sentinel `0`) as a JSON **number** but the 12-char privacy hash as a **string**. `deserialize` throws `SerializationException` on a non-primitive.

**TLS:** client `{"TLS":{"startTLS":"send"}}`, server `{"TLS":{"startTLS":"true"|"false"}}`. **Chat/Error/List** as above.

### Tolerant features decoding

`RoomFeatures` is decoded with `LenientRoomFeaturesSerializer` everywhere it appears in inbound JSON (`HelloData`, `ListUserData`, `SetData`, `UserEvent`): an inbound `features` that is **not** a JSON object (e.g. `features: []` from a minimal/third-party server, issue #152) decodes to default `RoomFeatures` instead of aborting the whole message. Defaults: all feature flags `true`; `maxChatMessageLength=150`, `maxUsernameLength=16`, `maxRoomNameLength=35`, `maxFilenameLength=250`. `@SerialName` maps `readiness→supportsReadiness`, `managedRooms→supportsManagedRooms`, `chat→supportsChat`, `sharedPlaylists→supportsSharedPlaylists`.

### Authentication & controlled rooms

- **Server password:** client sends hex MD5; server compares against its own MD5. (`md5()` in CommonUtils returns raw bytes.)
- **Controlled rooms** (`RoomPasswordProvider`, pinned to Python `utils.py`): name format `+baseName:HASH12`. `saltHash = SHA256(salt).hex`; `provisional = SHA256(roomName + saltHash).hex`; `result = SHA1(provisional + saltHash + password).hex[:12].uppercase()`. Password format `XX-###-###` (regex `[A-Z]{2}-\d{3}-\d{3}`).

---

## Synchronization Algorithm

### Constants (`ProtocolManager.Companion`, matching PC `constants.py`)

| Constant | Value | Purpose |
|---|---|---|
| `rewindThreshold` | 4 s | Force corrective rewind (PC `DEFAULT_REWIND_THRESHOLD`) |
| `SEEK_THRESHOLD` | 1 s | Min diff to count as a seek |
| `SLOWDOWN_RATE` | 0.95 | Playback speed during slowdown |
| `SLOWDOWN_THRESHOLD` | 1.5 s | Start slowdown when ahead by this |
| `SLOWDOWN_RESET_THRESHOLD` | 0.1 s | Revert speed |
| `FASTFORWARD_BEHIND_THRESHOLD` | 1.75 s | Detect "behind" condition |
| `FASTFORWARD_THRESHOLD` | 5.0 s | Trigger fastforward after sustained lag |
| `FASTFORWARD_EXTRA_TIME` | 0.25 s | Overshoot |
| `FASTFORWARD_RESET_THRESHOLD` | 3.0 s | Cooldown |
| `PING_MOVING_AVERAGE_WEIGHT` | 0.85 | RTT EMA weight (`PingService`) |
| `LIST_PROBE_INTERVAL_SECONDS` | 15 | Keep-channel-warm empty List |
| `WATCHDOG_INTERVAL_SECONDS` | 5 | Silent-disconnect watchdog tick |
| `STATE_TIMEOUT_SECONDS` | 15 | No State for this long → reconnect |

### Latency (`PingService`)

`RTT = now − echoedTimestamp`; `rtt = 0.85·rtt + 0.15·RTT` (EMA). `forwardDelay = avrRtt/2`, plus `(rtt − senderRtt)` when `senderRtt < rtt` (asymmetry: our upload is slower). Timestamps are full-precision seconds; negative RTT or senderRtt is ignored.

### State handler (`RoomServerMessageHandler.onState`)

1. Stamp `protocol.lastStateReceivedAt` first (feeds the channel-health watchdog), even for a State that gets ignored.
2. Parse `ignoringOnTheFly`: a server counter sets `serverIgnFly` and clears `clientIgnFly`; a matching client counter clears `clientIgnFly`.
3. Extract `position`, `paused`, `doSeek`, `setBy`; feed `ping` into `PingService`; `messageAge = pingService.forwardDelay`.
4. If `clientIgnFly == 0`: `agedPosition = paused ? position : position + messageAge`; `diff = playerPosSec − agedPosition`. `pausedChanged = (globalPaused != paused)` — deliberately **not** compared against `player.isPlaying()` (VLCKit's async/stale value would spam OSD). Update `globalPaused`, `globalPositionMs`, `lastGlobalPositionSetAt`.
5. **First sync** (`lastGlobalUpdate == null`, media loaded): call `noteExpectedPlaybackState(paused)` first, then seek to `agedPosition` and apply pause/play.
6. **Seek:** `doSeek && setBy != null` → reset speed to 1.0, fire `onSomeoneSeeked`.
7. **Rewind** (`SYNC_REWIND`): `diff > rewindThreshold && !doSeek` → `onSomeoneBehind`.
8. **Fastforward** (`SYNC_FASTFORWARD` **and** (follower-in-controlled-room **or** `SYNC_DONT_SLOW_WITH_ME`)): track sustained lag past `FASTFORWARD_BEHIND_THRESHOLD`, trigger at `−FASTFORWARD_THRESHOLD` with `+FASTFORWARD_EXTRA_TIME`, then cooldown. In a normal room everyone can control, so nobody force-fastforwards unless `SYNC_DONT_SLOW_WITH_ME` opts in.
9. **Slowdown** (`SYNC_SLOWDOWN`, not paused, not a seek): `diff > 1.5` and `setBy != self` → speed 0.95; revert at `diff < 0.1`.
10. Pause transition → `onSomeonePlayed` / `onSomeonePaused`.
11. **ACK** with our own State. The `play` value is `!paused` from the inbound message (**not** `player.isPlaying()` — VLCKit 4's pause/play is async; ACKing a stale `isPlaying()` makes the public server rebroadcast a phantom unpause). The periodic ACK always omits `doSeek` (mobile owns its player; real seeks are announced explicitly via `dispatcher.sendSeek`). `SYNC_DONT_SLOW_WITH_ME` uses the extrapolated room position for the ACK.

The desync-correction block is gated on `viewmodel.media != null` (with no media, `diff` looks like multi-second lag and would fire a phantom OSD).

### Channel health & playback broadcast (`ProtocolManager`)

While CONNECTED, two coroutines run: a **List-probe** every 15 s (keeps the channel warm) and a **watchdog** every 5 s that terminates the socket and fires `onDisconnected()` if no State arrives for 15 s. Playback changes are **callback-driven, not polled**: a collector on `PlayerManager.isNowPlaying` broadcasts a State **only** when the engine-reported state diverges from `expectedPaused`. Deliberate changes call `noteExpectedPlaybackState()` first to suppress re-broadcast; only engine-driven auto pause/resume (buffer underrun, audio-focus loss, EOF) actually broadcasts. Outbound paths read `expectedPlaying`, never a live `isPlaying()` probe. `serverIgnFly`/`clientIgnFly` are atomicfu atomics (built from `Dispatchers.IO`).

`invalidate()` is full teardown; `resetSyncAnchorForReconnect()` is the lightweight transient-reconnect reset (clears `lastGlobalUpdate`, `lastGlobalPositionSetAt`, and the ignFly counters only) so the first State on a new socket re-anchors. Mirrors PC `_performRetryStateReset`.

### Readiness / unpause (`RoomEventDispatcher`)

`controlPlayback` gates outbound on `uiState.isInBackground` (background auto-pause must not propagate) and on `UNPAUSE_ACTION` readiness when `supportsReadiness`. `instaplayConditionsMet` implements four modes: `IfAlreadyReady`, `IfOthersReady`, `IfMinUsersReady` (needs ≥2 ready), `Always` (default `IfOthersReady`). A controlled room without a controller can never unpause; a blocked unpause marks the user ready instead. `noteExpectedPlaybackState` is set **before** touching the player to avoid a re-broadcast race. `pendingSeekFromMs` is single-use (sentinel `NO_PENDING_SEEK = -1L`).

---

## Subsystem Breakdown

### App root (`commonMain/app/`)

| File | Purpose |
|---|---|
| `AdamScreen.kt` | Root composable: per-screen ViewModels via `viewModelFactory`, `NavDisplay` backstack, CompositionLocals (`LocalGlobalViewmodel`, `LocalRoomViewmodel`, `LocalTheme`, `LocalScreen`, `LocalSettingStyling`, `LocalChatPalette`, `LocalRoomUiState`, `LocalPrefsState`). `entryProvider` maps Screen→composable. |
| `SyncplayViewmodel.kt` | App-level VM: `backstack` (starts at `Home`), theme state, custom-theme CRUD (`changeTheme/saveNewTheme/deleteTheme`), shared-playlist toggle, persisted `USER_ID` (Uuid v7 hex, used only for Klipy). |
| `Screen.kt` | `@Serializable sealed interface Screen : NavKey`: `Home`, `Room(joinConfig?)`, `ThemeCreator(themeToEdit?)`, `ServerHost`. |
| `AbstractManager.kt` | Base for managers: `onMainThread` (`Dispatchers.Main.immediate`), `onIOThread`, open `invalidate()`, all on `vm.viewModelScope`. |
| `PlatformCallback.kt` | Platform ops interface: PiP, brightness, haptics, media session, server service, language, shortcuts, `launchSystemFilePicker`. |

### Home (`commonMain/app/home/`)

`HomeScreen.kt` (join UI; `officialServers` = `syncplay.pl:8995`–`8999`; the default host `151.80.32.178` is shown in the UI as `syncplay.pl`; sanitizes username/room: strip backslashes, cap username 149 / room 34), `HomeViewmodel.kt` (`joinRoom(config?)`; null = solo mode; owns snackbar), `JoinConfig.kt` (`@Serializable user/room/ip/port/pw`; defaults ip `syncplay.pl`, port 8997; `save()` only when `REMEMBER_INFO`). Components: `HomeAnimatedEngineButtonGroup`, `HomeTextField` (gradient-bordered, center-aligned `BasicTextField` bridging `value/onValueChange` onto `TextFieldState`), `HomeTopBar` (logo→About, theme picker, expandable global settings grid), `PopupAPropos` (version, GitHub releases link, Solo-mode button via `joinRoom(null)`), `PopupDidYaKnow` (first-launch tips, `NEVER_SHOW_TIPS`).

### Protocol (`commonMain/app/protocol/`)

| File | Purpose |
|---|---|
| `ProtocolManager.kt` | Sync orchestrator: `Session`, global state, ignFly atomics, `PingService`, channel-health, `buildStatePacket`, sync constants, version constants. |
| `Session.kt` | Per-connection state: server/user/room/password, `roomFeatures` (setter mirrors flags into protocol StateFlows), `userList`/`messageSequence` StateFlows, mutex-guarded `outboundQueue`, `sharedPlaylist`, `spIndex` (−1 = none), `ready` (inits from `READY_FIRST_HAND`), readiness counters. Defaults: host `151.80.32.178`, port 8997. |
| `WireMessage.kt` / `WireMessageDeserializer.kt` / `WireMessageHandler.kt` | See [protocol section](#syncplay-protocol-as-implemented). |
| `SyncplayJson.kt` | The shared `syncplayJson`. |
| `models/PingService.kt` | RTT EMA + asymmetry-aware `forwardDelay`. |
| `models/RoomFeatures.kt` | Feature flags + `LenientRoomFeaturesSerializer`. |
| `models/User.kt` | `index, name, readiness, file?, isController` (index 0 = current user). |
| `models/ConnectionState.kt` | `DISCONNECTED, CONNECTING, CONNECTED, SCHEDULING_RECONNECT`. |
| `models/TlsState.kt` | `TLS_NO, TLS_ASK, TLS_YES` (used by Netty and SwiftNIO). |
| `event/RoomCallback.kt` | Inbound events: pause/play/seek/rewind/fastforward, join/left, chat, playlist, file, TLS, controller-auth, connection lifecycle; emits OSD/chat + haptics. `SEEK_NOOP_THRESHOLD_MS=1000` suppresses sub-second seeks; self-seek "from" consumed single-use from `pendingSeekFromMs`; all `player.play/pause/seekTo` gated on `media != null` (VLCKit-4 NULL-media segfault). |
| `event/RoomEventDispatcher.kt` | Outbound actions: `sendHello`, `sendSeek`, `controlPlayback` (readiness gating + background gate), seek fwd/bwd, `broadcastMessage`. `clientFeatures` static manifest. No-ops in solo mode. |
| `wire/*` | One `@Serializable` DTO per payload: `HelloData`, `StateData`/`PlaystateData`/`PingData`/`IgnoringOnTheFlyData`, `SetData`/`UserSetData`/`UserEvent`, `ListUserData`, `ReadyData`, `ChatData`, `ControllerAuthData`/`NewControlledRoom`, `PlaylistChangeData`/`PlaylistIndexData`, `FileData` (+`FileSizeSerializer`), `Room`, `TLSData`, `ErrorData`. |
| `network/NetworkManager.kt` | Abstract client TCP layer. `enum NetworkEngine {KTOR, NETTY, SWIFTNIO}`. Inbound is **strictly serial**: an unbounded `Channel<String>` drained by one consumer → `syncplayJson.decodeFromString(WireMessageDeserializer)` → `dispatch(serverHandler)`. `processPacket` catches only `SerializationException` to skip a poisoned line; `reconnect()` owns the retry loop (re-entry guard on `Job.isActive`); `send()` never queues Hello/State, queues Chat/playlist/ready; `transmitPacket` appends CRLF, 10 s timeout, 3 retries then queue. No-op in solo mode. |
| `network/KtorNetworkManager.kt` | Ktor TCP fallback. `supportsTLS()=false`; `upgradeTls()` is a no-op (Ktor lacks opportunistic TLS, KTOR-6623). |

### Room (`commonMain/app/room/`)

| File | Purpose |
|---|---|
| `RoomViewmodel.kt` | Central coordinator owning all managers (`uiState`, `playerManager`, `networkManager`, `protocol`, `callback`, `dispatcher`, `serverHandler`, `playlistManager`). `enum OSDCategory {SAME_ROOM, OTHER_ROOM, SLOWDOWN, WARNING}`. `isSoloMode = (joinConfig == null)`. Rewrites `syncplay.pl` → `151.80.32.178` at connect. `checkFileMismatches` uses `FileComparison`. `dispatchOSD(category,…)` gates on per-category prefs + `OSD_NON_OPERATOR`. |
| `RoomServerMessageHandler.kt` | The client `WireMessageHandler` — the full sync algorithm and user-list/chat/TLS/error handling. |
| `MediaFileWireExt.kt` | `MediaFile.toFileData()` applying `HASH_FILENAME`/`HASH_FILESIZE` privacy modes ('1' raw / '2' 12-char SHA-256 / sentinels). Unknown duration sent as `0.0`. |
| `RoomScreenUI.kt` | Main room composable: landscape/portrait, video surface (kept composed at alpha 0 when no video), HUD, gesture interceptor, popups, D-pad focus (`LocalRoomInitialFocus`). `EnterRoomMode(isPortrait)` is the single orientation/windowing source. Background tap is two-stage: IME open → only clears focus (closes keyboard, HUD stays); otherwise hides HUD. Insets: room is immersive, so `statusBars` reports 0 — top chrome pads with `statusBars.union(displayCutout)`; landscape tabs/cards get cutout padding; video stays full-bleed. |
| `RoomUiStateManager.kt` | HUD/tab/orientation/PiP/popup state + lifecycle forwarding. Tab cards mutually exclusive; `uiOpacity = ROOM_UI_OPACITY/100`; `onLifecycleStop` pauses unless in PiP. |

**`room/ui/bottombar/`** — `RoomSectionBottomBar` (container; most controls only render when `hasVideo`), `RoomSeekbar` (chapter dots, drag bubble, D-pad seek; `analyzeChapters` called here in `LaunchedEffect(media.fileName)` — single owner), `RoomReadyButton` (hidden in solo; sends `WireMessage.readiness`), `RoomControlPanel` (aspect/screenshot/seek-to/undo-seek + unified Audio & Subtitles sheet with OpenSubtitles search; chapter dropdown deliberately does **not** re-analyze), `RoomControlsAboveSeekbar` (FastRewind/FastForward), `RoomMediaAddButton` (storage picker / Android-only custom chooser for SMB/cloud / URL), `PopupSeekToPosition` (HH:MM:SS + `customSkip`), `BlackContrastUnderlay`. Every seek path follows the same contract: set `pendingSeekFromMs`, `sendSeek(target)` (no-op in solo), `player.seekTo(target)`. Both file/subtitle pickers use the FileKit #575 iOS workaround (launch-after-dismiss).

**`room/ui/chat/`** — `RoomSectionChat` (renders only when `supportsChat`; caps at `roomFeatures.maxChatMessageLength`, does **not** strip backslashes; horizontal cutout inset + 8dp margins applied per row — NOT on the column — and the input row carries a tap-shield `pointerInput` so fat-finger taps in the notch strip/margins are consumed instead of dismissing the keyboard/HUD; `ChatTextField`'s inner `OutlinedTextField` must NOT reuse the outer `modifier` or the shield/insets double-apply), `GifPanel` (Klipy GIF/sticker search; iOS tiles need `fillMaxWidth` and alpha-as-parameter), `RoomFadingChatLayout` (transient message when HUD hidden), `ChatTextField`/`ChatBox`.

**`room/ui/rightcards/`** — `RoomSectionSlidingCards` (animated container; horizontal in landscape, vertical in portrait), `CardUserInfo` (readiness/controller/file; skipped in solo), `CardSharedPlaylist` (add/shuffle/import/export; skipped in solo), `CardRoomPrefs` (renders `SETTINGS_ROOM` + the active engine's `configurableSettings()`).

**`room/ui/tabs/`** — `RoomSectionTabs` (settings/playlist/userinfo/lock + overflow: PiP, managed-room, leave; portrait/landscape toggle gated behind `if(false)`), `RoomTab`, `RoomUnlockableLayout`, `PopupManagedRoom` (create managed room or identify as operator; sends `WireMessage.controllerAuth`).

**`room/ui/misc/`** — `RoomPlayButton` (center, readiness-gated `controlPlayback`), `RoomGestureInterceptor` (double-tap seek, long-press continuous seek, swipe brightness left / volume right; only attaches when HUD hidden; drag-start edge guard uses real `systemGestures`/`displayCutout`/`waterfall` insets with an 8%-of-height floor — issue #137 — and all zone math reads `PointerInputScope.size` + `rememberUpdatedState` insets, never composition-time captures, because the activity handles `configChanges` itself so rotation restarts nothing), `RoomBackgoundArtwork` (note filename typo; logo on gradient when no video). **`room/ui/statinfo/`** — `RoomSectionStatusInfo` (room name, connection state, reconnect, OSD, SxxExx badge; hidden in solo).

**`room/sharedplaylist/`** — `SharedPlaylistManager` (shuffle/add/select/import-export/trusted-domain gating; caps `PLAYLIST_MAX_ITEMS=250`, `PLAYLIST_MAX_CHARACTERS=10000`; `trustedEntryMatches` mirrors PC: exact host + www variant + single-label `*` wildcards + optional path prefix, no arbitrary subdomains), `MediaAccessRegistry` (durable bookmark store — the playlist transmits filenames only, so each client re-opens by name via iOS security-scoped bookmarks / Android persistable SAF URIs; self-heals by re-indexing remembered dirs), `PlaylistExt.kt` (`expect PlatformFile.indexMediaTree(): Map<String, ByteArray>`).

### Player (`commonMain/app/player/`)

`PlayerImpl.kt` (abstract engine base: lifecycle, playback, track/chapter analysis, `injectVideoURL`/`injectVideoFile`, `VideoPlayer()` composable, `startTrackingProgress`; `playerSupervisorJob` backs `playerScopeMain`/`IO`; `PLAYLIST_ADVANCE_MIN_DURATION_MS=10_000`, `PLAYLIST_ADVANCE_NEAR_END_MS=5_000`; `announcesFileLoadViaEvent`, `supportsScreenshot`, `trackerJobInterval` open hooks; iOS security-scoped file access held by the base), `PlayerManager` (`isPlayerReady`, `media`, `isNowPlaying`, `timeCurrentMillis`/`timeFullMillis`; `invalidate()` tears down on `GlobalScope` with `runCatching`), `PlayerEngine`, `Playback` (PAUSE/PLAY), `MpvSubfont.installMpvSubfontIfNeeded()` (copies bundled `subfont.ttf` into mpv's config dir — both platforms; mpv must run `config=yes`), `resolver/MediaResolver.kt` (`expect val mediaResolver`; `ResolvedMedia`, `urlLooksLikeDirectMedia`, `extractYoutubeId`). Models in `player/models/`: `MediaFile` (duration in seconds, size a String of bytes), `MediaFileLocation` (`Local`/`Remote` + `commonUri`), `Track`, `TrackChoices` (Exo `Any?` override / mpv `Int` / vlc `String` id — standing TODO to make a sealed class), `PlayerOptions` (Exo buffers; `get()` multiplies `EXO_MAX/MIN_BUFFER` by 1000), `Chapter`.

### Preferences (`commonMain/app/preferences/`)

`Pref<T>` (type-safe wrapper + `SettingConfig` DSL; `prefKeyMapper` supports Boolean/Int/Long/Float/Double/String/Set/ByteArray, throws otherwise; `value()` reads a synchronous snapshot, `flow()`/`watchPref()` reactive). `Datastore.kt` (lateinit global `datastore`, process-lifetime `datastoreScope`, eager hot `datastoreStateFlow` via `runBlocking`, `LocalPrefsState`). `PrefExtraConfig.kt` (`PerformAction`, `BooleanCallback`, `Slider`, `MultiChoice`, `ShowComposable`, `ColorPick`, `YesNoDialog`, `TextField`). `Preferences.kt` (the 60+ Pref registry; `SYNKPLAY_PREFS = "syncplayprefs.preferences_pb"`; `NETWORK_ENGINE` default `netty` Android / `swiftnio` iOS; `UNPAUSE_ACTION` default `IfOthersReady`; `MEDIA_RESOLVER_ENABLED` default true; `GLOBAL/INROOM_RESET_DEFAULTS` clear the whole DataStore). `SettingComposable.kt` (generic renderer; a String pref with no extraConfig becomes a TextField). `settings/` — `MySettings.kt` (`SETTINGS_GLOBAL` = General/Language/Syncing/Network/Advanced; `SETTINGS_ROOM` = Sync/ChatColors/ChatProps/Player/OSD/Advanced; engine categories injected at runtime, not in the static list), `SettingsUI.kt`, `SettingCategory.kt`, `SettingStyling.kt`, `PopupColorPicker.kt`, `PopupTrustedDomains.kt`.

### Theme (`commonMain/app/theme/`)

`SaveableTheme` (serializable; lazy `dynamicScheme` via MaterialKolor `SPEC_2021`), `BuiltinThemes` (default `SILVER_LAKE`; also `PYNCSLAY`, `GrayOLED`, `ALLEY_LAMP`, `BLANK_THEME`), `ThemePicker` (`availableThemes` = SILVER_LAKE/PYNCSLAY/GrayOLED/ALLEY_LAMP), `ThemeCreatorScreen`, `Theming` (brand `NeoSP1/2/3` from `BuildConfig.TRINITY_COLOR_*`, `SP_GRADIENT`, semantic chat/readiness colors `READY_GREEN=0xFF6ECB5A`/`UNREADY_RED=0xFFE85455`, spacing tokens; `flexibleGradient` returns the brand gradient when the active theme has `syncplayGradients`, else Material primary/secondary/tertiary).

### Klipy (`commonMain/app/klipy/`)

`KlipyAPI` (Ktorfit; gifs/stickers search/trending/recent + share analytics), `GifSearchResponse.kt` (`KlipySearchResponse`/`KlipySearchWrapper`/`KlipyItem`/`KlipyFile`/`KlipyResolution`/`KlipyFormat` — every field defaulted for sparse responses), `KlipyUtils` (`search/trending/recents/trackShare`; `BASE_URL = …/api/v1/${KLIPY_API_KEY}/`; per-client HTTP config sets only `Accept` to avoid a duplicate UA that Cloudflare flags).

### Subtitles (`commonMain/app/subtitles/`)

`OpenSubtitlesAPI` (Ktorfit; search params declared **alphabetically** because the API 301-redirects non-canonical query order; download is a POST with `{"file_id": N}`), `SubtitleSearch` (`API_KEY = BuildConfig.OPENSUBTITLES_API_KEY`; `expectSuccess=true`; re-installs `DefaultRequest` with UA `Synkplay v<version>`; free plan = unlimited search, ~5 downloads/day → HTTP 406 surfaces as `SubtitleDownloadResult.QuotaExceeded`; downloads sanitized against path traversal and written with `writeTextFile`).

### Utils (`commonMain/app/utils/`)

`CommonUtils.kt` (`appName`, `@ProtocolApi`, `vidExs`/`audioExs`/`ccExs`/`playlistExs`, `videoFileKitType` — omits the extension filter on iOS so the iOS 26 picker doesn't dim files, `playlistIsValid`, `md5`/`sha256`, `generateRoomPassword`, `isPlayableMediaFilename`), `FileComparisonUtils.kt` (`object FileComparison`, ported 1:1 from Python `utils.py`: `PRIVACY_HIDDENFILENAME="**Hidden filename**"`, `hashFilename`/`hashFilesize` = SHA-256 hex truncated to 12, `FILENAME_STRIP_REGEX = [-~_.\[\](): ]`, `DIFFERENT_DURATION_THRESHOLD=2.5s`, case-insensitive raw↔hashed cross-compare, size '0'/'' matches anything — **must stay byte-identical to Python**), `LogUtils.kt` (`loggy`, 7-day retention, Ktor logger bridge), `PlatformUtils.kt` (expect declarations), `VlcFlags.kt` (`tokenizeVlcFlags` — quote-aware splitter, no escapes).

---

## expect/actual Map

| Expect | Android actual | iOS actual |
|---|---|---|
| `instantiateNetworkManager()` | `NettyNetworkManager` (pref `netty`) else `KtorNetworkManager` | `SwiftNioNetworkManager` (pref `swiftnio`) else `KtorNetworkManager` |
| `ServerNetworkEngine` | Netty `ServerBootstrap` | Ktor raw sockets (IPv4 `0.0.0.0`) |
| `availablePlatformPlayerEngines` | `[ExoEngine, MpvEngine, VlcEngine]` | `[AVPlayerEngine, MpvKitEngine, VlcKitEngine]` |
| `httpClient` | OkHttp lazy singleton (15/10/15 s; UA `SynkplayMobile/<ver>`; logging filtered to `api.*`) | Darwin lazy singleton (same timeouts; NSURLCache 32 MB/256 MB; logging filtered to `api.*`) |
| `mediaResolver` | `NewPipeMediaResolver` (YouTube/SoundCloud/PeerTube/Bandcamp/MediaCCC) | `YouTubeKitMediaResolver` (YouTube only; no-op if bridge unregistered) |
| `indexMediaTree()` | SAF tree (DocumentFile) / `java.io.File`; child document-URI bytes | NSFileManager walk holding the dir scope; per-file security-scoped bookmarks |
| `WeakRef<T>` | `java.lang.ref.WeakReference` | `WeakReference` |
| `EnterRoomMode`/`ExitRoomMode` | hide bars + lock orientation | UIKit orientation mask + `setNeedsUpdateOfSupportedInterfaceOrientations` |
| `getDeviceIpAddress` | `NetworkInterface` (first non-loopback IPv4) | C interop (`ifaddrs`) |
| `getMpvConfFilePath` | `{filesDir}/mpv.conf` | `<Documents>/mpv.conf` |
| `consumePendingShortcut` | always null (uses Intents) | returns + clears a pending shortcut JoinConfig |
| `AnimatedImage` | Coil3 `AsyncImage` | `CGImageSource`/`UIImage.animatedImage` with a 64-entry LRU cache; alpha forwarded to `UIImageView.alpha` |
| `platformCallback` (PlatformCallback) | `SyncplayActivity` impl (PiP, brightness, haptics via Vibrator, foreground services, system file picker) | `ApplePlatformCallback` (PiP per engine, brightness, language→Settings, shortcuts, haptics; media-session/server-service no-ops; `launchSystemFilePicker` returns null) |

iOS Swift bridges (registered at startup in `iosApp/iOSApp.swift`): `instantiateSwiftNioNetworkManager`, `instantiateMpvKitPlayer`, `instantiateYouTubeKitBridge`. The Kotlin sides are abstract classes / nullable factory vars in `iosMain` whose `isAvailable` tracks registration.

---

## Player Engines

| Engine | Platform | Chapters | External subs | PiP | Notes |
|---|---|:-:|:-:|:-:|---|
| ExoPlayer (Media3) | Android | ✗ | ✓ | (Android PiP) | Default on `exoOnly`; `handleAudioFocus` **must stay false** or focus-loss auto-pause broadcasts a phantom unpause; ext subs require media reload; tracker 500 ms |
| MPV (libmpv/JNI) | Android | ✓ | ✓ | ✗ | `full` flavor only and the default there; precise double `time-pos` seeking; needs NDK r29 libc++; `MpvSubfont` for subs; tracker 500 ms |
| VLC (libVLC 4) | Android | ✓ | ✓ | ✗ | `full` flavor only, experimental; track ids are Strings ('-1' = none); user `VLC_CUSTOM_FLAGS` appended last; volume 0–200 |
| AVPlayer (AVFoundation) | iOS | ✗ | ✗ | ✓ | The iOS **system** engine (badge "System"); KVO on `timeControlStatus` — only `Playing` counts (buffering must not); MP4/HLS only; per-inject new AVPlayer instance |
| MPVKit (libmpv via Swift) | iOS | ✓ | ✓ | ✗ | Experimental (no longer the iOS default, VLCKit is); Swift `MpvKitPlayerBridge` over MoltenVK/Vulkan; `vo=gpu-next` forced; toggles `vid=no/auto` on bg/fg to rebuild the swapchain; `announcesFileLoadViaEvent=true`; tracker pushes time-pos (interval 0) |
| VLCKit 4 | iOS | ✓ | ✓ | ✓ | **Default iOS engine**; 250 ms main-thread position tracker (NOT libvlc callbacks — lock assert); `VLCEventsLegacyConfiguration` for async callbacks; `:start-paused`; seek-shadow convergence; every play/pause/seek guarded on `media != null`; configures AVAudioSession; volume 0–200 |
| VLCJ (libVLC 3) | Desktop | ✓ | ✓ | ✗ | Default desktop engine. CPU RV32 frames via `CallbackVideoSurface` → reused Skia bitmap → Compose `Canvas` (draw-phase `frameTick` read, no AWT embedding, HUD overlays free). libVLC construction async OFF the EDT; injects `awaitEngine()` (`isPlayerReady` only means the impl instance exists); `status().time()` = -1 when STOPPED → last-known-position cache; EOF leaves libVLC STOPPED (no paused() event) → `finished()`/`stopped()` set `isNowPlaying=false` so the auto-pause broadcast fires; aspect modes implemented in draw math (buffer is always source-sized); tracker 200 ms; volume 0–200 |

**Engine badges** (home wheel, `PlayerEngine.isSystem`/`isDefault`/`isExperimental`, one badge per engine, ladder Unavailable > Experimental > Default > System): Android = ExoPlayer System, mpv Default, VLC Experimental, KitePlayer Experimental. iOS = AVPlayer System, MPVKit Experimental, VLCKit Default, KitePlayer Experimental. Desktop = VLC Default, mpv Experimental. `isDefault` is also what `Preferences.PLAYER_ENGINE` resolves its default value from, so moving it moves the engine new installs get.

**iOS PiP** dispatches per engine in `ApplePlatformCallback.onPictureInPicture`: AVPlayer uses `AVPictureInPictureController(avPlayerLayer)`; VLCKit 4 uses its own `enter/exitPictureInPicture` via the `VlcDrawable` PiP protocol stack; **MPVKit has no PiP**. All gated on `AVPictureInPictureController.isPictureInPictureSupported()`.

**Destroy contract (all Android engines + iOS MpvKitImpl/VlcKitImpl/AVPlayerImpl):** flip `isInitialized=false` **first** (so every `isInitialized`-gated method refuses the engine), then cancel `playerSupervisorJob` (stops the position tracker, releases the `RoomViewmodel` graph), then release the native engine. mpv's process-global handle hard-crashes (`CHECK_MPV_INIT()`) if a tracker poll outlives teardown; its global observer list must be detached first.

---

## Desktop (PC) Target

`jvm("desktop")` target in `:shared` (actuals under `shared/src/desktopMain/`) + the `:desktopApp` Compose for Desktop shell. Verified end-to-end against the official `syncplay.pl` server (TLS 1.3 via Netty/JDK, video frames, duration announce, advancing position ACKs).

**Key commands:** `./gradlew :desktopApp:run` (dev), `:desktopApp:createDistributable` / `packageDmg|Msi|Deb` (ship), `:desktopApp:vlcSmokeTest [-PsmokeUrl=<url>]` (headless bundled-libVLC load + media-open probe), `-PdebugProtocol=true` on any of these flips `DEBUG_SYNCPLAY_PROTOCOL` wire logging.

**Bundled libVLC (users install nothing):** `:desktopApp:fetchVlcNatives` downloads the official VLC 3.0.21 build for the CURRENT build OS into `desktopApp/resources/<os>-<arch>/vlc` (macOS: dmg via hdiutil, `lib/` + `plugins/`; Windows: zip, DLLs + `plugins/`; Linux: none exists — falls back to system VLC via vlcj NativeDiscovery). `plugins.dat` is deleted after copy (stale-mtime cache). Compose packages `resources/` into the app image and exposes it via the `compose.application.resources.dir` system property; `BundledVlcDirectoryProvider` (a ServiceLoader-registered `DiscoveryDirectoryProvider`, priority 100) points vlcj there first. The dir is gitignored (~185 MB).

**Desktop actuals in brief:** Netty client + server engines are near-verbatim copies of the Android ones (JDK TLS, no Conscrypt; `NETWORK_ENGINE` defaults to `netty`); NewPipe resolver copied as-is (pure JVM); file ops/`indexMediaTree` are plain `java.io.File`; `AnimatedImage` decodes GIFs with Skia `Codec` (coil-gif is Android-only) behind a 64-entry LRU; DataStore + logs live in the per-OS app-data dir (`~/Library/Application Support/Synkplay`, `%APPDATA%\Synkplay`, `$XDG_DATA_HOME/synkplay`); `EnterRoomMode`/`ExitRoomMode` are no-ops; PiP/haptics/brightness/media-session are no-ops. CLI join: `--user U --room R [--host H] [--port P] [--pw W] [--media URL] [--autoplay]` (the desktop `consumePendingShortcut`).

**mpv engine (desktop):** `MpvJvmEngine`/`MpvJvmImpl` in `desktopMain app/player/mpvjvm/` over a hand-rolled JNA binding (`LibMpv.kt`, ~20 functions of the stable libmpv C ABI; JNA comes transitively from vlcj). Video uses mpv's SOFTWARE render API (`MPV_RENDER_API_TYPE_SW`): an update-callback wakes a daemon render thread, mpv writes BGRA into a native buffer sized dwidth×dheight, frames flow through the same `DesktopFramePipeline`/`FrameCanvas` as VLCJ. One daemon event thread runs `mpv_wait_event` (observes `pause`, `duration`, `eof-reached`). Options: `vo=libmpv`, `idle=yes`, `keep-open=yes` (EOF pauses on last frame, `time-pos` stays valid — the PC-parity EOF behavior), `hwdec=auto-copy` (sw render needs CPU frames), `config=yes` + `config-dir=<appdata>/mpv` (mpv.conf import/export works; `getMpvConfFilePath` is non-null on desktop). Aspect ratio via mpv itself (`video-aspect-override`/`panscan`, Android-parity) so the canvas always FITs. Tracks/chapters via `track-list/N/...`/`chapter-list/N/...` property reads, ids stored in `TrackChoices.*SelectionIndexMpv`. `isAvailable` = libmpv loadable (`MpvNativeLoader`: bundled dir → `SYNKPLAY_MPV_DIR` env → Homebrew paths → system). NOT default: VLC keeps that, its natives are guaranteed-bundled. `:desktopApp:fetchMpvNatives` bundles libmpv: on macOS a full Homebrew dependency-closure copy (otool -L recursive, handles absolute + `@loader_path` + `@rpath` refs, rewrites install names to `@loader_path`, ad-hoc re-signs — ~100 dylibs ≈ 93 MB; needs `brew install mpv` on the BUILD machine only), on Windows a libmpv dev 7z (needs 7z on PATH), Linux system-libmpv fallback. `:desktopApp:mpvSmokeTest [-PmpvSmokeUrl=...]` is the headless load/media probe. CLI: `--engine vlc|mpv` persists PLAYER_ENGINE before the UI reads it.

**Hard-won rules:** the serial protocol consumer must NEVER suspend on the UI thread — `onState` reads the tracker position cache (`timeCurrentMillis`, PC-parity) and issues player commands as fire-and-forget Main launches; a `withContext(Main)` there deadlocked the whole inbound pipeline when libVLC still owned the EDT at cold-start auto-join. macOS jpackage rejects `0.x.y` versions → macOS `packageVersion` is `1.23.x` while the app stays `0.23.x`.

## Built-in Server

The mobile app can host a full Syncplay server, a direct port of `server.py`/`utils.py`. **All shared state is confined to one thread:** `SyncplayServer.serverDispatcher = Dispatchers.IO.limitedParallelism(1)`, mirroring the single Twisted reactor. Inbound dispatch, per-watcher state timers, and connection-lost cleanup all hop onto it via `onServerThread{}`.

| File | Purpose |
|---|---|
| `server/SyncplayServer.kt` | Rooms, watchers, per-watcher state timer (initial forced State after 100 ms `doSeek=true`, then every `SERVER_STATE_INTERVAL_MS=1000`), broadcasting, controlled-room auth, `buildServerFeatures` (advertises `setOthersReadiness=true`, `persistentRooms=false`). |
| `server/ClientConnection.kt` | Per-client `WireMessageHandler`: decodes inbound JSON inside `onServerThread{}`, builds typed outbound via `sendTyped → toJson()`. MD5 password check; `onTLS` always answers `tlsResponse(false)` (no server TLS); filenames truncated to 250. |
| `server/ServerRoomManager.kt` | Room lifecycle, `findFreeUsername` (appends `_`), broadcast/broadcastRoom; `PublicServerRoomManager` (room isolation) overrides `broadcast` to the sender's room only. `getOrCreateRoom` returns `ControlledServerRoom` for `+` names. |
| `server/ServerViewmodel.kt` | Host-screen VM: config (port default 8999, validated 1–65535), `ServerStatus`, logs, device/public IP (`api.ipify.org`); invokes `serverServiceStart/Stop`. |
| `server/ui/ServerHostScreen.kt` | Config form, start/stop, status, IPs, client count, log. Shows an iOS warning (background hosting unreliable). |
| `server/model/ServerRoom.kt` | Base room: `STATE_PAUSED=0`/`STATE_PLAYING=1`; `getPosition()` advances by wall-clock and adopts the slowest watcher when stale >1 s. |
| `server/model/ControlledServerRoom.kt` | Gates `setPaused/setPosition/setPlaylist/setPlaylistIndex` on `canControl`; `getControllers()` returns empty (mirrors PC). |
| `server/model/ServerWatcher.kt` | `updateState` adds `messageAge` on unpause; relays pause changes; `compareTo` pushes no-position/no-file watchers last. |
| `server/model/ServerConfig.kt` | Port 8999, `isolateRooms=true`; `MAX_*` (chat 150, username 16, room 35, filename 250); `PROTOCOL_TIMEOUT_SECONDS=12.5`; `hashedPassword` = MD5 hex. |
| `server/model/RoomPasswordProvider.kt` | Controlled-room hash chain + `XX-###-###` validation. |
| `server/network/ServerNetworkEngine.{android,ios}.kt` | Netty `ServerBootstrap` (Android, `DelimiterBasedFrameDecoder` 64 KiB) / Ktor raw sockets (iOS, IPv4); both feed CRLF lines to `ClientConnection.handlePacket`. |

The Android server process is kept alive by `SyncplayServerService` (a foreground notification only; logic runs in `ServerViewmodel`'s scope). The general media-session foreground service is `SyncplayMediaSessionService`.

---

## Architectural Patterns

- **MVVM + StateFlow.** `RoomViewmodel` is the central hub owning all managers (each extends `AbstractManager` for `viewModelScope` dispatch). UI collects StateFlows in composables.
- **Manual DI.** No framework; constructor injection, lazy init, platform factories via `expect`/`actual`, and `CompositionLocal` for UI-scoped deps.
- **Navigation.** Navigation3 `NavDisplay` over a `SnapshotStateList<Screen>` backstack owned by `SyncplayViewmodel`; `Screen` subtypes are `@Serializable`.
- **Protocol visitor.** `WireMessage` sealed hierarchy; `dispatch(handler)` calls the matching `WireMessageHandler` method. Same handler interface implemented by client and server. `toJson()` per subclass to avoid the polymorphic `"type"` discriminator.
- **Serial inbound processing.** One unbounded `Channel<String>` drained by a single consumer on both client (`NetworkManager`) and server (`limitedParallelism(1)`), matching the Python single-reactor model and preventing interleaved State mutations.
- **Preference system.** Type-safe `Pref<T>` over DataStore with a hot `StateFlow` and a `SettingConfig` DSL that auto-generates the settings UI. A single snapshotted `LocalPrefsState` feeds `watchPref()` via `derivedStateOf` (no per-composable flow collection).
- **Tolerant deserialization invariant.** Any malformed inbound sub-field must throw `SerializationException` (never `IllegalStateException`/`error()`), the only type the skip-a-poisoned-line catch covers; a TLS-handshake failure in `RoomCallback.onReceivedTLS` must be caught locally for the same reason.

---

## Known Gaps & Platform Limitations

**Missing vs desktop:** file-switch manager (auto-find matching files across users), per-user time offset, persistent rooms / SQLite on the mobile server (ephemeral only), server statistics, IPv6 server (IPv4 only), CLI/console UI, desktop players (MPC-HC/BE, MPlayer, IINA).

**Code-level TODOs:** `TrackChoices` → sealed class; `PlayerManager.timeCurrentMillis/timeFullMillis` → `media.fileTimePos`/`fileDuration`; suppress `RoomSectionStatusInfo` overlay in PiP; portrait/landscape overflow toggle gated behind `if(false)`; check operator status before opening the identify popup; `CardSharedPlaylist` clipboard API migration; VLC Android time-tracking / hw-sw switch / `changeSubtitleSize`; `SyncplayViewmodel.isSharedPlaylistEnabled` not advertised to the server.

**Platform limitations:**
- **iOS server hosting** may not run reliably in the background (no foreground services); the mobile server always answers `TLS: false` (no server-side cert support).
- **Ktor networking** has no opportunistic TLS upgrade (KTOR-6623); encrypted connections require Netty (Android) or SwiftNIO (iOS).
- **AVPlayer (iOS):** no external subtitles, no chapters, narrow format support (MP4/HLS).
- **ExoPlayer (Android):** no chapters.
- **MPVKit (iOS):** no PiP; no Lua/`stats.lua` (custom stats overlay); hwdec/profile changes apply only on next room join; needs `config=yes` + bundled `subfont.ttf` or subtitles render blank.
- **mpv (Android):** requires the NDK r29 `libc++_shared.so` (verified at build time); ships only in the `full` flavor.
