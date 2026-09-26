# Contributing to Synkplay

This guide is for people and for coding agents.

- People: read sections 1 to 4 before your first change. Read the rest when you work in that area.
- Coding agents: read the whole guide before your first edit.

Open work lives in [GitHub Issues](https://github.com/yuroyami/syncplay-mobile/issues). The
repository has no planning folder and no private roadmap. A `plan:` label groups the issues of one
plan, and a `tracking` issue holds the goal and the order of work of that plan.

Words that this guide uses:

- A **room** is the group of people who watch together through a Syncplay server.
- An **engine** is one of the video players that the app can drive: ExoPlayer, mpv, KitePlayer,
  AVPlayer, VLCKit or the browser's video element.
- The **reference** is the official Syncplay client and server, written in Python:
  <https://github.com/Syncplay/syncplay>. Several parts of this app match it line for line on
  purpose, so read section 7 before you tidy any of them. Git ignores `syncplay-pc-src-master/`,
  so you can keep a local copy of the reference there.
- The **hosted server** is the Syncplay server that the app can run itself, so that other people
  can join a room on your device.
- The **room controls** are the buttons, the seek bar and the chat that show over the video.

---

## 1. Ground rules

- Stay on `master`. Do not create a branch, a worktree or a pull request unless someone asks for
  one.
- Do not use em dashes or en dashes in code, comments, documentation, commit messages or issue
  text. Use a colon, a comma, parentheses or two sentences.
- Keep comments to one or two lines. Say why, not what. A file full of narration is worse than a
  file with none.
- Write for a reader whose first language is not English and who has five minutes. Use short
  sentences, plain verbs and one idea per sentence.
- Do not commit secrets. The signing keystore and its passwords load from `local.properties`,
  which git ignores.
- Two API keys are in the repository:
  - The KLIPY key (the animated image search) is committed on purpose, so that outside builds
    match the published ones.
  - The OpenSubtitles key has a committed fallback. A `yuroyami.keyOpenSubsApi` entry in
    `local.properties` replaces the fallback.
- An Android phone emulator never counts as verification. Test on a real device over `adb`, or
  check the code by reading it and say that this is what you did.
- For television work, you may use the Android TV emulator. See
  [Android TV](docs/DEVELOPING.md#android-tv) for the setup. A real television still decides the
  final pass.

## 2. Before you commit

Run the gates. They are fast, and they catch things that review does not.

```bash
./gradlew :shared:desktopTest qualityGates detekt
```

If you changed Android or iOS code, also compile that side:

```bash
./gradlew :shared:compileAndroidMain :shared:compileKotlinIosSimulatorArm64
```

If you changed `commonMain`, also compile the web target:

```bash
./gradlew :shared:compileKotlinWasmJs
```

A Kotlin compile does not prove that the Swift bridges still work. If you changed anything that
the iOS app calls across the Kotlin and Swift boundary, build the Xcode workspace against the new
framework.

`koverVerify` checks the line coverage floor of the `app.protocol` and `app.server` packages
(`COVERAGE_FLOOR` in the root `build.gradle.kts`). No pre-commit step and no workflow runs it. Run
it by hand when you change those packages:

```bash
./gradlew koverVerify
```

The floor is a ratchet. Raise it when you add tests. Never lower it to make a build pass.

For an Android release:

- Add a store note under `fastlane/metadata/android/en-US/changelogs/`, named by the version code.
  Write it by hand as a summary of the version's `CHANGELOG.md` section. It has 40 to 500
  characters and is not a placeholder such as "Maintenance update.", or `checkStoreMetadata`
  fails.
- The version code is `1`, then the major version in three digits, the minor version in three, the
  patch in two and the rebuild number in one. So 0.25.0 with rebuild 1 is `1000025001`.

### Other commands

Run the desktop app:

```bash
./gradlew :desktopApp:run
```

Print the release identity (versions, build numbers, application id and iOS deployment target):

```bash
./gradlew printReleaseIdentity
```

Preview the identity and asset changes that KiteConfig would make:

```bash
./gradlew kitePlan
```

Write the identity and assets into the Xcode project:

```bash
./gradlew kiteApplyIos
```

Check the resolved cross-platform configuration:

```bash
./gradlew kiteCheck
```

Build both Android APKs and the bundle into `AndroidAppOutput/`:

```bash
./gradlew androidReleaseAll
```

To log every protocol line that the app sends and receives, add `-PdebugProtocol=true` to a
Gradle build.

### KiteConfig

KiteConfig is the Gradle plugin that applies the app's identity (name, version, application ids)
and its icons to every platform. The `kiteConfig` block in the root `build.gradle.kts` configures
it. That block holds the version, the rebuild numbers and the JVM level.

- KiteConfig applies its changes by itself (`autoApply = true`).
- A Gradle sync in Android Studio or IntelliJ also updates the Xcode project.
- Opening Xcode alone does not run Gradle. If you have not synced, run `kiteApplyIos` before you
  build in Xcode.

## 3. What the gates check

`qualityGates` runs the gates in `buildSrc/src/main/kotlin/QualityGates.kt`. Each gate exists
because the thing it checks went wrong before. If you add a gate, plant a violation first and
confirm that the gate fails on it.

| Task | What it stops |
|---|---|
| `checkProtocolThrows` | A call to `error`, `check`, `checkNotNull`, `require` or `requireNotNull` in inbound protocol code or in the hosted server's `ClientConnection.kt` |
| `checkStringResources` | A duplicate string or plural key in any language |
| `checkLocaleParity` | A key that exists in a translation but not in English. It only reports missing translations, unless you pass `-PstrictLocales=true` |
| `checkStringArguments` | A translation whose placeholders differ in order or number from English, or an English string that repeats a placeholder |
| `checkDeadResources` | Nothing. It only warns about strings and drawables that nothing references |
| `checkSettingsReachable` | A preference that declares a title, a summary and an icon, but that no settings or engine code names |
| `checkDestroyContract` | An engine `destroy()` that does not set `isInitialized = false` before it cancels `playerSupervisorJob` |
| `checkStoreMetadata` | A store short description over 80 characters, a full description over 4000, a Play release note that is missing, over 500 characters or a placeholder, or a version with no section in `CHANGELOG.md` |

Other checks:

- `verifyExoOnlyDebugApk` and `verifyExoOnlyReleaseApk` run with the assemble task of the
  ExoPlayer-only flavour. That flavour is the Android build with ExoPlayer as its only engine
  (`-PexoOnly=true`). The task fails when the APK carries a native library whose name starts with
  a forbidden prefix, such as `libmpv`, `libkitecodec` or `libkiteplayer`
  (`buildSrc/src/main/kotlin/ExoOnlyApkGate.kt`).
- `verifyFullDebugDecoders` and `verifyFullReleaseDecoders` run with the assemble task of the full
  flavour. They fail when the APK has an ABI without the KitePlayer decoder, `libkitecodec_jni.so`
  (`buildSrc/src/main/kotlin/KiteDecoderApkGate.kt`).
- The design lint is a test in `shared/src/desktopTest` (`DesignLint.kt`). It fails on:
  - a Material 3 component import (`ColorScheme` is allowed)
  - an `sp` literal outside `Tokens.kt`
  - `MaterialTheme` or a ripple outside `Tokens.kt` and `SaveableTheme.kt`
  - text under 11sp
  - a `clickable`, `combinedClickable`, `selectable` or `toggleable` without semantics
- `detekt` runs its default rules, except the `comments` rule set and the rules that
  `config/detekt/detekt.yml` switches off by name. `maxIssues` is 0, so one finding fails the
  build.

## 4. How the code is laid out

The app is Kotlin Multiplatform. One shared module holds nearly everything, and thin shells host
it.

| Module | What it is |
|---|---|
| `shared` | The app: protocol, sync, engines, the hosted server and the whole interface. The Android activity, `SyncplayActivity`, is in `shared/src/androidMain` |
| `androidApp` | The Android application: manifest, resources, signing and packaging. It holds no Kotlin code |
| `desktopApp` | A Compose for Desktop window |
| `webApp` | A browser entry point |
| `iosApp` | An Xcode project with Swift bridges |
| `buildSrc` | Build logic that is more than a declaration: the gates, the ExoPlayer-only APK check and the release tasks |

Put new build logic in `buildSrc`, not in a `build.gradle.kts` file.

The source sets:

```
commonMain ─┬─ nonWebMain ─┬─ jvmShared ─┬─ androidMain
            │              │             └─ desktopMain
            │              └─ iosMain
            └─ wasmJsMain

commonTest ─── nonWebTest ─┬─ desktopTest
                           ├─ iosTest
                           └─ androidHostTest
```

- `commonMain` is the code that every platform runs, and a browser is one of the platforms.
- `nonWebMain` holds what a browser has no equivalent for: a TCP socket, a native decoder, a real
  file system, a thread that may block.
- `jvmShared` holds files that Android and desktop share, not copies. The Netty network client and
  the NewPipe media resolver live there.
- Before you add code to `nonWebMain`, ask whether it would compile in a browser. If it would, it
  belongs in `commonMain`.
- The test source sets follow the same split. A test that blocks a thread goes in `nonWebTest`.
  `:shared:desktopTest` also runs the common and non-web tests.
- **Never write `Dispatchers.IO`, `runBlocking` or a direct file system write in `commonMain`.**
  Each one breaks the web build. Use these instead:
  - `app.utils.ioDispatcher`. On the web it is `Dispatchers.Default`.
  - `readBlockingOrNull`. On the web it returns null.
  - `FileKitCompat`. On the web its functions throw `UnsupportedOperationException`.

Two build lists name source sets, and neither covers all of them. This is tracked in
#ISSUE(gates-skip-the-newest-source-sets).

- The detekt `source` list in the root `build.gradle.kts` names `commonMain`, `androidMain`,
  `desktopMain`, `jvmShared`, `iosMain`, `commonTest`, `desktopTest`, `androidApp/src/main/java`
  and `desktopApp/src/main/kotlin`. It misses `nonWebMain`, `wasmJsMain`, `nonWebTest` and
  `webApp`. The `androidApp/src/main/java` folder does not exist.
- `checkProtocolThrows` scans `app/protocol` in `commonMain`, `nonWebMain` and `wasmJsMain`, and
  the server's `ClientConnection.kt`. It misses the protocol code in `jvmShared` and `iosMain`.
- When you add a source set, add it to both lists.

## 5. Traps

Each item below is code that looks wrong but is needed, or a rule that prevents a known defect.
In each case, the obvious change is the wrong one. A line that ends with an issue number or a
commit hash points at the change that taught it.

### Playback and the room

The client answers each `State` message from the server with a `State` of its own. This guide
calls that answer the state acknowledgement. The ignore counters (`ignoringOnTheFly` in the
protocol) mark a play state change that the other side has not confirmed yet.

- **Never read a live `isPlaying()` on an outbound path.** VLCKit applies play and pause
  asynchronously, so a stale read makes the public server send out a pause that nobody asked for.
  Read `expectedPlaying`.
- **Call `noteExpectedPlaybackState` before you touch the engine, not after.** Some engines,
  ExoPlayer among them, report the change synchronously from inside `play()` and `pause()`, so the
  other order is a race.
- The state acknowledgement sends `play = !paused` from the inbound message. If you put the
  engine's own state there, the room sees an unpause that nobody asked for.
- `pausedChanged` compares the inbound paused flag with the room's last known state, never with
  the player. A comparison with the player sends notices while an engine catches up.
- The periodic state acknowledgement never sets the seek flag. This app owns its player and
  announces real seeks explicitly, so a seek flag there causes a flood of seeks.
- **Announce a seek before you move the engine.** In the other order, the room's own correction
  pulls the player back.
- The room dispatcher (`RoomEventDispatcher`) checks that media is loaded before every play, pause
  and seek. VLCKit also checks in its own play and pause, because its native play reads the media
  before it validates it.
- The drift correction (the rewind, slowdown or fast-forward that brings a player back to the room)
  runs only with media loaded, in the foreground, and with no seek pending. With no media, the
  difference looks like several seconds of lag and fires a notice about nothing.
- The base player installs the media and clears the duration **before** the engine loads, because
  load events read the current media.
- The position that the app sends comes from `reportableStatePositionSec`, not from the engine. A
  client that has just loaded a file would otherwise report a position near zero and pull the whole
  room back.
- A seek's origin and target travel with the sent state in a `LocalSeek`. The `LocalSeek` never
  goes on the wire, and the server's echo returns it only once (`consumeSeekEcho`).
- A seek that the ignore counters hold back stays queued as the latest intent, and a later pause
  or play keeps it. Dropping it or sending it early leaves the room on the slower peer's position
  (f9ea907f).
- A seek of less than one second still moves the player. It only skips the chat notice and the
  undo record.
- Online, the server's echo of a seek fills the undo history. Only solo mode records a seek for
  undo when it sends it.
- `controlPlayback` does nothing while the app is in the background: nothing reaches the engine
  and nothing goes to the room.
- Readiness is the Syncplay feature where each person marks themselves ready before play starts.
  When the room supports it, a play that the readiness conditions refuse marks the user as ready
  instead.
- The seek bar shows a preview while you drag it and seeks once on release. It takes the seek
  origin from the engine on the first drag event.
- Only a press or a drag puts the seek bar in use. Focus alone must never freeze the track.
- Key the state of the seek bar, the gestures and the chapters to the loaded file (for example
  `remember(media?.location)`), and check the media again when the seek commits. Otherwise a drag
  that starts on one file seeks the next file (71e1ee6a).
- `analyzeChapters` has one caller, the seek bar. mpv, VLCKit and KitePlayer clear the chapter
  list before they fill it, so a second caller blanks the chapter marks. ExoPlayer, AVPlayer and
  the web engine do nothing there.
- The base `jumpToChapter` only announces the seek, because each engine moves itself. Never add a
  seek to the base.
- Keep every engine's position sample fresh on a timer. The room reports that cached sample and
  extrapolates it for 2 seconds at most, so a frozen sample pulls every peer back (#158).
- Keep the video surface composed once the player is ready. Hide it with an `alpha` modifier placed
  before the `background` modifier. Then the engine is not torn down, and an empty room does not
  paint black.
- On Android, set the picture-in-picture flag before you ask the system to enter
  picture-in-picture. Then hide the room controls.
- When the app stops, the player pauses on this device only, unless the app is in
  picture-in-picture. The room is not told. The `background` flag stays `@Volatile`, because code
  off the main thread reads it.

### Engine lifecycle

- **Destroy in one order: set `isInitialized = false`, cancel `playerSupervisorJob`, then release
  the native engine.** A position tracker that outlives teardown reaches a released engine.
  `checkDestroyContract` checks the first two steps. It does not check the release step.
- mpv starts a new core for every file. Stop the old core's flows before that core closes, so that
  its last events (an end of file, a shutdown) never reach the next file.
- ExoPlayer's audio focus handling stays off. With it on, an audio focus loss pauses ExoPlayer, and
  the app sends that pause to the whole room.
- The base `seekTo` writes the target into the position sample at once, so the next state
  acknowledgement reports the target. Every other engine calls it before it seeks. VLCKit calls it
  only when it submits a held seek to the native player.
- The base `seekTo` has no background check. `controlPlayback` and the sync decision handle the
  background.

### VLCKit on iOS

VLCKit is the iOS engine built on libVLC. The app pins VLCKit 4.0.0a19.

- Keep these VLCKit protections. Each one defends against the asynchronous behaviour of VLCKit 4:
  - the post-seek guard (`seekGuard`)
  - the first-frame priming flag (`primingFirstFrame`)
  - the 250 ms debounce on the Paused state
  - the deferred startup seek (`VlcSeekRequests`)
  - the drawable barrier (see below)
- VLCKit's `time` property reads an interpolated cache that can stall while the video plays. Read
  the native clock through `VlcClock.h`, which the engine polls every 250 ms (#158).
- `VlcClock.h` borrows VLCKit's native player pointer. Never release the pointer, because the
  wrapper owns it (71e1ee6a).
- The native VLC clock returns 0 once its input stops. `VlcClock.h` reports that as unavailable,
  so the end-of-file check keeps the last real position (71e1ee6a).
- `VlcClock.h` and the VLC seek code assume the millisecond time API of VLCKit 4.0.0a19. Newer VLC
  versions change the native time unit, so recheck both on any upgrade (71e1ee6a).
- Keep `VLCEventsLegacyConfiguration`. Without it, VLCKit 4 runs callbacks on libVLC threads, some
  of them under its timer lock, where a native getter can re-enter libVLC (#156).
- Legacy delivery queues only the state value, and the wrapper's `state` is a cache. Check the
  live native state with `SyncplayVlcStateMatches` before you act on an event (71e1ee6a).
- Compare VLCKit media with `compare()` or with the native descriptor, never by reference or URL.
  MediaChanged swaps the wrapper for the same file (71e1ee6a).
- The drawable barrier, `player.drawable = player.drawable` before `setMedia`, is not a no-op. The
  synchronous setter drains the queued play and pause calls (71e1ee6a).
- After a media swap, VLCKit can report the old Playing state before the new input exists.
  `VlcSeekRequests` holds one startup seek until the input can seek, for 30 seconds at most
  (71e1ee6a).
- A paused VLC seek leaves the native clock on the old position until playback resumes.
  `VlcSeekGuard` holds the target on purpose, so never force playback to confirm the seek
  (71e1ee6a).
- VLCKit's seek-end signal is not an acknowledgement of one seek. Only the native clock reaching
  the target shows that a seek landed, and `VlcSeekCompletion` waits for that (71e1ee6a).

### Protocol and wire format

The Syncplay protocol sends one JSON message per line. This section names these message types:
`Hello` (the handshake), `State` (play state and position), `Set` (changes to the room, a user or
the playlist) and `List` (the user list).

- **Malformed inbound input must throw `SerializationException` and nothing else.** The hosted
  server's `ClientConnection.handlePacket` catches only that type, and then it drops the client.
  `checkProtocolThrows` enforces the rule.
- The client's `NetworkManager.processPacket` skips a line that it cannot parse, whatever the
  exception, and keeps the session. The reference client drops the connection instead. The skip is
  on purpose, because the official server sends shapes that strict models reject (#152).
- Catch a TLS handshake failure inside `RoomCallback.onReceivedTLS`. The catch there ends the
  connection and starts a retry.
- **The inbound consumer, which handles server messages one at a time, must never suspend on the
  main thread.** The state handler starts its work on Main with `launch` and does not wait for it.
  A `withContext(Main)` there can deadlock the whole pipeline at cold start.
- Encode each wire message through its own `toJson()`. Encoding through the `WireMessage`
  interface type uses the polymorphic serializer, which adds a `"type"` class discriminator that
  the protocol does not allow.
- The `List` request placeholder defaults to `JsonNull`, not to a Kotlin `null`. With a Kotlin
  `null`, the message becomes `{}` and the server does not recognise it.
- **Position on the wire stays full precision.** Rounding breaks the server's own desync
  detection and its choice of the slowest watcher.
- A controlled room is a room that only its operators can control, after they enter the room's
  operator password. Keep the file comparison and the controlled-room hashing byte-identical to
  the reference. A tidied regex breaks file matching and operator passwords across clients.
- Never send a `features` key inside a `Set` message. The reference server has no handler for it
  and drops the sender's connection. `SetFeaturesIsInboundOnlyTest` fails the build if a builder
  emits one. `Hello` sends `features` on purpose.
- `Hello`, `State`, the TLS request and every `List` request never go into the replay queue for a
  reconnect. A replayed `State` carries an old position and pulls the room back.
- The reconnect re-anchor (`resetSyncAnchorForReconnect`) also clears the ignore counters. The
  file-load re-anchor (`reanchorSyncOnFileLoad`) does not. Do not merge them.
- Fast-forward needs the fast-forward setting. Then it applies only to a person who cannot control
  a controlled room, or to a user who turned on the option that stops the room slowing down for
  them (`dontSlowWithMe`). This matches the reference client.
- The server password digest is lowercase hex on both sides, as the reference's `hexdigest` makes
  it. Uppercase breaks every server.
- Apply the playlist change of a `Set` message before its playlist index, so that a bundled index
  resolves against the new list.
- The roster (the list of people in the room) models only the current room. Joins to other rooms
  become notices.

### Interface

Glass is the frosted, see-through panel style, built with the Haze library. The render harness
(`DesignHarness` in `shared/src/desktopTest`) draws composables without a window, and it can press
keys.

Glass:

- Glass inside a Haze source cannot sample the capture that contains it. So the glass style keeps
  the default `Behind` source selection: with `All`, the render thread recurses until it dies.
- Glass inside the room samples the room's own capture of the video layer (`roomHazeState`), never
  the app-wide capture that it sits inside.
- Only `glassEnabled()` and `glassEnabledNow()` decide whether glass is on. ExoPlayer and mpv
  choose their video surface from `glassEnabledNow()` once, when they are created. KitePlayer reads
  `glassEnabled()` during composition and switches its render path live.
- Glass keeps a transparent background, a 40 percent inner dim applied before the tint, and the
  Quality performance mode. The dim is black on dark themes and white on light themes.
- Glass cannot sample a `SurfaceView` or a native video view. Over one, a glass panel shows a plain
  tonal fill, and that is correct. With glass on, ExoPlayer and mpv draw into a `TextureView` and
  KitePlayer draws on the Compose canvas, so glass can blur their video.
- Below Android 12 and on low-memory devices, `videoSurfaceSupportsGlass()` is false, so ExoPlayer
  and mpv keep the `SurfaceView`.

Frames and the room controls:

- Every dialog and panel goes through the one `Modal` frame (`Modal.kt`). `Modal` owns the dialog
  window, the scrim, the entry focus, scrolling, the keyboard inset, Escape and Back.
- Three small overlays are plain `Popup`s instead: the long-press menu of a GIF in chat
  (`ChatMediaMenu.kt`), the desktop hover tooltip (`GlyphButton.kt`) and the help tip
  (`HelpTip.kt`).
- `fillMaxWidth(fraction).widthIn(max)` never caps anything. A width cap has to be one layout step,
  as in the `widthFraction` modifier of `Modal`.
- Give a modal one accent or primary action. Each one claims the modal's entry focus, and with two,
  the first one attached takes it (533fd252).
- The room controls fade out and stay composed, because the chat draft, the GIF drawer and the drag
  state of the seek bar live inside them. Only picture-in-picture and the lock mode remove them.
- A tap on the room background with the keyboard open only clears focus. Without the keyboard, and
  once the player is ready, the tap hides the room controls.
- A key on the direction pad shows hidden room controls again when a video is loaded and the screen
  is not locked. Centre and Enter also play or pause, Left and Right also seek, and Up and Down only
  show the controls (`SyncplayActivity.onKeyDown`). This works on any Android device. On a
  television, it is the only way back to the controls.
- Room gestures ignore drags that start inside the system gesture, cutout and waterfall insets. The
  top and bottom guards are at least 8 percent of the height.
- Anything that a long-lived pointer coroutine reads goes through `rememberUpdatedState`. The
  activity handles its own configuration changes, so a rotation restarts nothing.

Chat:

- Put each chat row's tap shield (an empty tap handler, so that a near miss does not hide the room
  controls) before its inset padding. Apply the cutout inset to each row, not to the column. Never
  reuse the outer modifier on the inner text field.
- Wrap the sender and the message body in separate first-strong isolates. Event lines are not
  wrapped as a whole, but each name inside them goes through `isolated()`. The name colouring finds
  names by those isolates.
- Mark chat messages as seen only while the room controls are visible, and keep `seen` a plain
  field. Otherwise the fading chat shows nothing.
- Force-clear the chat focus when the room controls hide, or the keyboard stays up behind a field
  that nobody can see.
- Chat never strips backslashes. The JSON layer escapes them.
- The chat message cap is the server's advertised limit, floored at 1, so that a server that
  advertises 0 cannot block every message.
- On iOS, an animated image ignores Compose alpha modifiers. Pass alpha as the `alpha` parameter of
  `AnimatedImage`.
- Give each GIF tile a fixed size, full width with a square aspect ratio, and let the image fill it
  with `matchParentSize`. On iOS an empty image view reports zero size, and Compose never measures
  it again.
- Keep `expectSuccess = true` on the animated image client, and leave `coerceInputValues` off. Then
  a refused request never decodes as an empty result.

Media, playlist and settings:

- Keep the composable that holds a file picker launcher on screen until the picker returns. If the
  host closes first, the launcher leaves composition and the picked file is lost.
- Set the playlist shuffle flag before you launch the file picker, because the picker's callback
  reads the flag and then clears it.
- Remove a media folder from the preference and from `MediaAccessRegistry` in the same step, or its
  saved access grant leaks.
- `MultiChoice.entries` stays a composable lambda, because a cached map stops a language change from
  reaching the labels.
- In the navigation setup (`AdamScreen.kt`), apply the saveable state decorator before the
  ViewModel store decorator. Reach the room's ViewModel only through its weak reference.
- The home form fills in a random room name on purpose. A fixed default would put strangers in the
  same room.
- On Android, `EnterRoomMode` hides the system bars again after every composition, because popups
  bring them back. `ExitRoomMode` is the first call on the home screen.
- Compose's `TextAutoSize` never shrinks a label that an ellipsis has already cut, because the cut
  text counts as fitting. Give a shrinking label a `FontSizeRange` through the app's `Text` in
  `app.uicomponents.controls`.

Television:

- Gate television behaviour on `LocalIsTelevision`, never on `isTelevision()` inside a
  composable. The render harness sets the local to test a remote (533fd252).
- A control that a remote must press uses `clickable`, `toggleable` or `selectable`, which already
  fire on Center. Do not add key handlers to controls (533fd252).
- Use the drawn controls only. Do not add a Material control or a second focus style for
  television, because `controlStates` in `ControlSupport.kt` already paints the focus ring.
- `Field` handles the direction pad and the Center key. On a television the field stays read-only
  until Center opens the keyboard, and the direction pad moves focus out of it.
- `Modal` places the entry focus under a remote or a keyboard: on the field that the caller names,
  or else on its first field, or else on its primary or accent action.
- Check whether the keyboard is visible, not its height (`softKeyboardVisible`). A television
  keyboard floats over the app and reports no height.
- The render harness can press keys. `TvFieldEscapeTest`, `TvModalFocusTest` and `TvControlsTest`
  use it, so most focus rules are tested without an emulator.

### Strings and resources

Lyricist generates Kotlin string objects from the `strings.xml` files, and the app reads every
string through them.

- **Never use `<plurals>`.** Lyricist collapses every form to zero, one, two and other, which is
  wrong for Russian, Polish and Arabic. Use keys with the suffixes `_zero`, `_one`, `_two`, `_few`,
  `_many` and `_other`, and the `pluralForm` helper in `app/i18n/Plurals.kt`.
- Put every `<string>` before the `<string-array>` lists at the end of each file. Lyricist reads the
  elements in order.
- **Never reorder placeholders in a translation.** Lyricist strips the position markers (`%1$`)
  and fills the placeholders from left to right. A reordered translation prints the room name where
  the password goes. `checkStringArguments` catches it.
- Edit only `values-en/strings.xml`. The `syncDefaultStrings` task writes `values/strings.xml` from
  it, without the keys in `values/strings_untranslatable.xml`.
- Lyricist copies each string into a Kotlin string literal, so a backslash in `strings.xml` is a
  Kotlin escape. Use a valid Kotlin escape such as `\n`, `\t`, `\uXXXX`, `\"` or `\\`. An escape
  that Kotlin does not know stops the build.
- Do not add an SVG to the Compose resources. An SVG does not load on Android.
- Resource fonts do not load in the render harness, so the golden tests judge layout and spacing,
  not letterforms.

### Build and packaging

- **Never add a generated build-config field called `DEBUG`.** KiteConfig generates a public
  object, so each field becomes a property in the exported Objective-C header. Xcode defines
  `DEBUG=1` in Debug builds, so the header stops compiling. The field is `IS_DEBUG`.
- Only the libmpvkt library ships `libc++_shared.so`. If a second dependency brings its own copy,
  the merge fails. Then find out which copy is newer. Do not add a `pickFirst`, because it can ship
  the older copy.
- The ExoPlayer-only flavour is not free of native code. It still carries:
  - an audio decoder (`libffmpegJNI.so`)
  - a security provider (`libconscrypt_jni.so`)
  - the preference store (`libdatastore_shared_counter.so`)
  - a graphics library (`libandroidx.graphics.path.so`)
  - KitePlayer's subtitle renderer, libass (`libkiteplayer_libass_jni.so`)
- The ExoPlayer-only APK keeps the name `syncplay-<version>-exo-only.apk`, because the
  IzzyOnDroid updater finds the file by that name. An unsigned release adds `-unsigned` before
  `.apk`.
- KiteConfig owns the Android application id. Set the application id of the ExoPlayer-only flavour
  in the root `kiteConfig` block (`appId`), not in `androidApp/build.gradle.kts`.
- KiteConfig generates the Android launcher icons from `synkplay_fg.png` and `synkplay_bg.png`,
  and it tracks each generated file by checksum. It never overwrites an edited file automatically,
  so a hand edit stops later updates. Change the source artwork, then run `./gradlew kiteApply`.
- The macOS package turns a `0.x.y` app version into `1.x.y`, because jpackage rejects a version
  that starts with zero. Do not sync the two versions.
- **Reproducible builds (#105):** never add the foojay toolchain resolver plugin, never pin a JVM
  vendor, and never run `updateDaemonJvm`.
- Keep the locale sort task (`LocaleOrder.kt`) after Lyricist. Lyricist writes its language table
  in the order that it reads the folders, and that order differs between machines.
- The Ktorfit compiler plugin version follows Kotlin, not the Ktorfit library. After a Kotlin
  upgrade, check `compilerPluginVersion` in `shared/build.gradle.kts`.
- Skiko is pinned on purpose (`resolutionStrategy.force`). Upgrade it together with Compose
  Multiplatform.
- Maven Local is off unless you pass `-PuseMavenLocal=true`, and then it serves only the
  `io.github.yuroyami` group. A release build refuses the flag. Do not add a `mavenLocal()` that is
  always on.
- `dependenciesInfo` stays off for the APK and the bundle. The dependency block that it adds can be
  read only by Google, so outside checkers such as IzzyOnDroid cannot verify it.
- The ExoPlayer DASH, HLS and RTSP modules have no imports and are not dead code. ExoPlayer loads
  them by reflection.
- Keep both ExoPlayer layouts, because the surface type of `PlayerView` can only be set in XML.
- Each platform's engine list needs exactly one default engine. The engine preference picks the
  first default at startup.
- The desktop test source set needs `compose.desktop.currentOs`, because the render harness loads
  its Skia library from it.
- The hosting foreground service starts as the last step of a successful start, and it stays
  `START_NOT_STICKY`. So a killed process does not bring back a notification with no server behind
  it.

### Platform

- Desktop pins KitePlayer to the Compose canvas (`forcesComposeCanvas`). macOS sends a click to the
  topmost native view, so a native video surface would take every click meant for the room
  controls.
- KitePlayer fetches network media through its own `kiteplayer-network` module, which uses Ktor
  (OkHttp on Android and desktop, Darwin on iOS).
- On Android, KitePlayer opens a picked file by its real path when it can, and otherwise through
  the `fd:` protocol. Never reopen a descriptor through `/proc/self/fd`, because real devices refuse
  it.
- On iOS, hold exactly one security scope for the video, and never start access to the same file
  twice. Release the scope when you switch to a link.
- On iOS, subtitle files hold their own scopes. The base player releases them together with the
  video's scope, and VLCKit releases its subtitle scope when the next subtitle replaces it or at
  teardown.
- KitePlayer changes ship in this order: KiteFFmpeg, then KitePlayer, then this app. Maven Central
  shows what really shipped.
- `KiteImpl` keeps its player in a `StateFlow`, not in Compose state, because the engine is built
  off the main thread.
- Declare the query parameters of the subtitle service (`OpenSubtitlesAPI`) in alphabetical order.
  The service answers any other order with a redirect, which costs one round trip on every search.
- Set only the `Accept` header on the animated image client. A second `User-Agent` header gets the
  request flagged.
- The layout stays left to right in every language, Arabic included. That is a decision, not an
  oversight.

## 6. Standing decisions

A change that contradicts one of these is wrong, however good the reasoning. If you think one
should change, open an issue and say why. Do not just do it.

1. **Password fields show the password in plain text, on purpose.** No masking. This is a
   usability ruling, not a defect. Password storage in plain preferences is a separate problem,
   tracked in #ISSUE(passwords-stored-in-plain-preferences).
2. **Do not rewrite the protocol, the sync algorithm, the server or the serialization.** Make
   small, targeted fixes only.
3. **This app owns its player.** The reference client drives an external player over an
   inter-process channel, and it works around not knowing what that player is doing. Do not port
   those workarounds here.
4. **Inbound processing is serial** on both the client and the server: one message at a time, in
   arrival order, as in the reference.
5. **Change the sync algorithm through the pure functions**, not through the message handler. The
   sync decision (`SyncDecision.kt`) and the reported position are pure functions with tests, and
   that is what makes them provable.
6. **Protocol and server code read time from `SyncClock`**, never from the system clock. Then a
   test can advance time instead of sleeping.
7. **The mpv native build lives outside this repository.** The app gets mpv from the published
   `libmpvkt` artifacts. Do not add the native build back here.
8. **An Android phone emulator never counts as verification.** The Android TV emulator may be used
   for television work (see [Android TV](docs/DEVELOPING.md#android-tv)). A real television still
   decides the final pass.
9. **Android TV support adds no second focus style.** The drawn controls already paint the focus
   ring.
10. **The engine picker is not a popup.** It is part of the home form.
11. **The roster header keeps its small icon switch** between the compact and the expanded list,
    and it does not repeat the user count.
12. **Each Android flavour ships one universal APK**, with no ABI splits. Google Play still gets the
    bundle and serves each phone only its own libraries.
13. **There is no QR code and no local network discovery for joining.** The people in a room are
    usually in different places, and the invite link covers sharing.
14. **Mute hides one person's chat for the rest of the session and nothing more.** A stronger block
    is not planned.
15. **There is no button to report a user, on purpose.** The "Report a bug" link in About stays.
16. **The six chat colours have their own defaults** (`MessagePalette.kt`) and do not follow the
    theme.
17. **The KLIPY API key is committed in plain text on purpose**, so that outside builds match the
    published ones.

## 7. Things that look wrong and are correct

Do not "fix" these. Each one was checked, and each one is deliberate.

- The latency service (`PingService`) assigns the round-trip value before it validates it. That
  matches the reference line for line.
- The client's fallback of zero for a missing position matches the reference. The server's
  handling of an absent play state is a different case. Do not unify the two.
- When the app builds an outgoing `State`, it resets the server's ignore counter with a
  compare-and-set. That is safer than the reference's unconditional reset, on purpose. Do not
  restore parity there.
- The room copies its watcher list for each broadcast. The copy lets a broadcast remove a watcher
  without a concurrent modification.
- The hosted server does not run a command that arrives before the handshake, and it drops that
  client (`requireLogged`). The reference server still runs the command, so this is stricter on
  purpose. Keep it when you add a message type.
- The hosted server drops a client on the first line that it cannot parse, as the reference server
  does (`ClientConnection.handlePacket`).
- `ControlledServerRoom.getControllers()` returns an empty list on purpose, as in the reference.
  Controller status comes from `canControl`.
- Server tests may check state right after `handlePacket`, because `onServerThread` returns only
  when its work is done.
- The chrome surface (`chromeSurface`, the panel without blur that floats over the video) is fixed
  dark whatever the theme is, because the room keeps the video area dark.
- The glass panel's inner wash, sheen and rim follow `palette.isDark`. A fixed pair of colours
  looks wrong on light themes.

## 8. Reporting and tracking

- A defect or a missing capability goes in an issue. Say what you see, what you expected, and how
  to reproduce it without naming a file that only you have.
- A security problem goes to [SECURITY.md](SECURITY.md), not to a public issue.
- A commit that closes an issue says `Fixes #n`.
- Commit messages are plain sentences. Do not add an AI co-author trailer.
- When a commit carries over a contributor's mechanism, for example from their pull request,
  credit that person with a `Co-authored-by` trailer.
- If you find a trap that belongs in section 5, add it in the same change. Keep it to one or two
  lines, and point at the issue or commit that taught it.
