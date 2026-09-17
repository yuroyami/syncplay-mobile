# Contributing to Synkplay

This file is the whole briefing. If you are a person, read the first four sections and come
back to the rest when you touch that area. If you are an AI agent, read all of it before your
first edit.

Work is tracked in [GitHub Issues](https://github.com/yuroyami/syncplay-mobile/issues) and
nowhere else. There is no planning folder, no ledger, no private roadmap. An issue is the
record, a plan is a `plan:` label, and a plan with prose to keep has a tracking issue.

---

## 1. Ground rules

- Stay on `master`. Do not create a branch, a worktree or a pull request unless you were asked
  for one.
- No em dashes. Not in code, comments, documentation, commit messages or issue text. Use a
  colon, a comma, brackets, or two sentences.
- Comments are one or two lines. Say why, not what. A file full of narration is worse than a
  file with none.
- Write for someone whose first language is not English and who has five minutes. Short
  sentences, plain verbs, one idea per sentence.
- Do not commit secrets. Signing details and API keys load from `local.properties`, which is
  ignored.
- Do not use an Android emulator for verification. Test on a real device over `adb`, or verify
  statically and say that is what you did.

## 2. Before you commit

Run the gates. They are fast and they catch things review does not.

```bash
./gradlew :shared:desktopTest qualityGates detekt
```

If you touched Android or iOS source, also compile that side:

```bash
./gradlew :shared:compileAndroidMain :shared:compileKotlinIosSimulatorArm64
```

Kotlin compiling does not prove the Swift bridges still work. If you changed anything the iOS
app calls across the boundary, build the Xcode workspace against the regenerated framework.

Other commands worth knowing:

```bash
./gradlew :desktopApp:run              # run the desktop app
./gradlew printReleaseIdentity         # version, version code, application id, iOS target
./gradlew updateDocVersions            # after a dependency bump, or checkDocVersions fails
./gradlew kitePlan                     # preview identity and asset changes
./gradlew kiteApplyIos                 # update Xcode before archiving outside the Gradle IDE
./gradlew kiteCheck                    # check the resolved cross-platform configuration
./gradlew androidReleaseAll            # both Android APKs and the bundle
```

Add `-PdebugProtocol=true` to any desktop task to log the wire traffic.

KiteConfig 2.0.1 applies identity and icons automatically. Gradle IDE sync updates the Xcode
project; opening Xcode alone does not. Versions and platform rebuild counters live in the root
`kiteConfig` block. JVM levels are shared there, and app modules/locales are detected automatically.

## 3. What the gates check, and why each one exists

Every gate in `buildSrc/src/main/kotlin/QualityGates.kt` was added because that exact thing
already went wrong. Each was proved by planting a violation before it was committed. If you add
a gate, do the same.

| Gate | What it stops |
|---|---|
| Protocol exception types | Inbound protocol code throwing anything but `SerializationException` |
| String resources | A duplicate string key, which crashes the app at first use |
| Locale parity | A key that exists in a translation but not in English |
| String arguments | A translation whose placeholders differ in order or number from English |
| Dead resources | Strings and drawables nothing references |
| Settings reachable | A preference that declares a row and is never shown |
| Destroy contract | An engine that cancels its job before disabling itself |
| Store metadata | A missing release note, or one over the store's character limit |
| Documentation versions | The dependency tables drifting from the version catalogue |

Coverage over the protocol and server packages has a floor enforced by `koverVerify`. It is a
ratchet: raise it when you add tests, never lower it to make a build pass.

`detekt` is deliberately almost entirely switched off. What remains enabled maps to defects this
repository actually had.

## 4. How the code is laid out

Kotlin Multiplatform. One shared module holds nearly everything, and four thin shells host it.

| Module | What it is |
|---|---|
| `shared` | The app: protocol, sync, player engines, server, and the whole interface |
| `androidApp` | One activity |
| `desktopApp` | A Compose for Desktop window |
| `webApp` | A browser entry point |
| `iosApp` | An Xcode project with Swift bridges |
| `buildSrc` | All non-trivial build logic. The four build files stay declarative |

Source sets, and the one rule that keeps them honest:

```
commonMain ─┬─ nonWebMain ─┬─ jvmShared ─┬─ androidMain
            │              │             └─ desktopMain
            │              └─ iosMain
            └─ wasmJsMain
```

- `commonMain` is what all four platforms can run, and a browser is one of them.
- `nonWebMain` is for things a browser has no equivalent of: a TCP socket, a native decoder, a
  real filesystem, a thread that may block.
- `jvmShared` holds real shared files, not copies. Android and desktop use the same network
  client and the same media resolver from there.
- Before adding to `nonWebMain`, ask whether the code would compile in a browser. If it would,
  it belongs in `commonMain`.
- **Never write `Dispatchers.IO`, `runBlocking`, or a direct filesystem write in `commonMain`.**
  Each one breaks the web build. Use `app.utils.ioDispatcher`, `readBlockingOrNull`, and
  `FileKitCompat` instead. All three throw on the web, which lands in the `runCatching` the call
  sites already have.
- A new source set has to be added to the gate root lists in `buildSrc`, or the gates skip it.

The reference implementation is the Python desktop client in `syncplay-pc-src-master/`. Several
things here match it line for line on purpose. See section 7 before you tidy any of them.

## 5. Traps

These have all bitten someone. In each case the obvious change is the wrong change.

### Playback and the room

- **Never read a live `isPlaying()` on an outbound path.** One engine applies pause and play
  asynchronously, so a stale read makes the public server rebroadcast a pause nobody asked for.
  Read `expectedPlaying`.
- **Call `noteExpectedPlaybackState` before touching the engine, not after.** One engine reports
  the change synchronously from inside `play()`, so the other order is a race.
- The state acknowledgement sends `play = !paused` taken from the inbound message. Substituting
  the engine's own state brings the phantom unpause straight back.
- `pausedChanged` compares the room's state against the inbound message, never against the
  player. Comparing against the player spams notices while an engine catches up.
- The periodic acknowledgement always omits the seek flag. This app owns its player and announces
  real seeks explicitly, so adding it back produces seek storms.
- **Announce a seek before moving the engine.** The other order lets the room's own correction
  pull the player back.
- Every engine play, pause and seek is guarded on media being loaded. One engine crashes on null
  media rather than returning an error.
- The desync correction is also gated on media being loaded. With no media the difference looks
  like several seconds of lag and fires a notice about nothing.
- Media is installed and the duration cleared **before** the engine loads, because load events
  read the current media.
- The broadcast position comes from the reportable position, not the engine position. A client
  that has just loaded a file would otherwise report near zero and drag the whole room back.
- The pending seek origin is single use, and a negative value means none, because zero is a real
  position.
- Seeks under one second are suppressed and not recorded for undo.

### Engine lifecycle

- **Destroy in one order: disable the engine, cancel its supervisor job, then release the native
  handle.** One engine crashes hard if its position tracker outlives teardown. A gate enforces
  this.
- Detach the process-global observer list before destroying the mpv view. mpv keeps one core per
  process, so a leftover observer outlives the room.
- The mpv log and event callbacks look like dead Kotlin. The native library resolves them by name
  at runtime. Deleting them compiles cleanly and breaks mpv at load.
- ExoPlayer's audio focus handling stays off. Turning it on lets a focus loss auto-pause
  broadcast a pause to the whole room.
- The base seek refuses while backgrounded, and every override calls it.

### Protocol and wire format

- **Anything malformed inbound must throw `SerializationException` and nothing else.** Any other
  exception escapes the skip-a-bad-line catch and drops the connection. A gate enforces this.
- Catch a TLS handshake failure locally inside its own callback, for the same reason.
- **The serial inbound consumer must never suspend on the main thread.** A `withContext(Main)`
  in the state handler once deadlocked the whole pipeline at cold start.
- Encode each wire message through its own `toJson()`. Encoding through the interface type
  invokes the polymorphic serializer and injects a class discriminator the server rejects.
- The room-list request placeholder defaults to a JSON null, not a Kotlin null. A Kotlin null
  collapses the message to an empty object and it stops meaning anything.
- **Position on the wire stays full precision.** Rounding it destroys the server's own desync
  detection and its slowest-watcher selection.
- Keep the file comparison and the controlled-room hashing byte-identical to the reference. A
  tidied regex breaks file matching and room passwords across clients.
- Never send the features field outbound. It is a remote crash on the reference server. A test
  fails the build if a builder emits it.

### Interface

- **Never nest one glass panel inside another.** The blur samples a capture containing itself and
  recurses until the render thread dies.
- Blur cannot sample a platform video view. Over one there is nothing to read, so the panel
  correctly falls back to a plain tonal one. That is not a bug.
- Text auto-sizing alone never shrinks a label under ellipsis, because cut text counts as
  fitting. Route every shrinking label through the wrapper with an explicit size range.
- `fillMaxWidth(fraction).widthIn(max)` never caps anything. A width cap has to be one layout
  step.
- Launch a file picker only after its menu has finished dismissing, or iOS crashes.
- Anything a long-lived pointer coroutine reads goes through `rememberUpdatedState`. The activity
  handles its own configuration changes, so rotation restarts nothing.
- Chat never strips backslashes. The JSON layer escapes them.
- The chat message cap is the server's advertised limit, floored at one.
- On iOS, image alpha is a parameter, never a modifier.

### Strings and resources

- **Never use `<plurals>`.** The generator collapses every form to zero, one, two and else, which
  is wrong for Russian, Polish and Arabic. Use suffixed keys plus the plural helper.
- Every `<string>` must come before the lists at the end of each file. The generator reads
  elements in order.
- **Never reorder placeholders in a translation.** The generator strips the position markers and
  fills left to right, so a reordered translation prints the room name where the password goes. A
  gate catches this.
- Edit `values-en/strings.xml` only. `values/strings.xml` is a generated byte copy.
- Compose resources understand only `\n`, `\t`, `\uXXXX` and `\\`. Android-style escapes render
  literally, and SVG does not load on Android at all.
- Resource fonts do not resolve in the desktop render harness. The goldens judge layout, not
  letterforms.

### Build and packaging

- **Never add a generated build-config field called `DEBUG`.** Xcode already defines it and the
  exported header stops compiling.
- Never import the shared build-config package and the generated resources package in one file.
  A case-insensitive filesystem makes one silently fail to resolve. Use `app.utils.appName`.
- A duplicate native runtime library failing to merge is a signal, not a nuisance. Compare the
  two libraries. Adding a pick-first ships a mismatched runtime.
- The stripped Android flavour is not free of native code. It still carries an audio decoder,
  a security provider, the preference store and a graphics library.
- The application id swap for that flavour lives in the root configuration block. Identity
  applies after module configuration, so a module-level override can never win.
- Never hand-edit generated launcher assets. Edit the source artwork, regenerate, then rerun the
  logo task.
- macOS packaging rejects a version starting with zero, so the macOS package version runs ahead
  of the app version on purpose. Do not sync them.
- **Reproducible builds:** never re-add the toolchain resolver plugin, never pin a JVM vendor,
  and never run the daemon JVM update task.

### Platform

- Desktop pins the Compose canvas renderer. macOS routes clicks to the topmost native view, so a
  native surface swallows every control drawn over the video.
- The KitePlayer decoder has no https support of its own. Network media arrives through the app's
  own transport module, which supplies the bytes.
- Declare the subtitle service's query parameters alphabetically. It redirects any other order
  and the search silently returns nothing.
- Set only the `Accept` header on the animated image client. A second user-agent header gets the
  request flagged.
- The layout stays left to right in every language, Arabic included. That is a decision, not an
  oversight.

## 6. Standing decisions

A change that contradicts one of these is wrong, however good the reasoning. If you think one
should move, open an issue and say why. Do not just do it.

1. **Password fields stay visible.** No masking. This is a usability ruling and is not a defect.
   Storing passwords in plain preferences is a separate, real problem, and it has its own issue.
2. **Do not rewrite the protocol, the sync algorithm, the server or the serialization.** Surgical
   fixes only.
3. **This app owns its player.** The reference client drives an external player over an
   inter-process channel and works around not knowing what it is doing. Do not port those
   workarounds back.
4. **Inbound processing is strictly serial**, on both the client and the server, matching the
   reference's single-reactor model.
5. **Change the sync algorithm through the pure functions**, not through the message handler.
   The decision and the reported position are pure functions with tests, which is what makes any
   of this provable.
6. **Time comes from the shared clock**, never the system clock, in protocol and server code. That
   is what lets a test advance time instead of sleeping.
7. **The native player build lives in its own repository.** Do not add it back here.
8. **No Android emulator** for verification.
9. **The engine picker is not a popup.** It is part of the home form.

## 7. Things that look wrong and are correct

Do not "fix" these. Each was checked and each is deliberate.

- The latency service assigns the round-trip value before validating it. That matches the
  reference line for line.
- The client's fallback of zero for a missing position matches the reference. The server's
  handling of an absent playstate is a different case and must not be unified with it.
- The state packet's compare-and-set on the ignore counters is deliberately safer than the
  reference's unconditional reset. Do not restore parity there.
- The room copies its watcher list for each broadcast. The copy is what lets a broadcast remove
  a watcher without a concurrent modification.
- The handshake gate is stricter than the reference on purpose. Commands before the handshake
  are refused. Keep that when adding message types.
- One iOS engine reads its position by polling the native clock on the main thread rather than
  taking callbacks. Callback delivery there is unreliable and trips an assertion.
- The chrome over video is fixed dark whatever the theme is, because the room is pinned dark.
- The glass panel's inner tint, sheen and rim all branch on whether the palette is dark. They
  used to be fixed, which left a light theme looking bruised.

## 8. Reporting and tracking

- A defect or a missing capability goes in an issue. Say what you see, what you expected, and how
  to reproduce it without naming a file only you have.
- A security problem goes to [SECURITY.md](SECURITY.md), not to a public issue.
- A commit that closes an issue says `Fixes #n`.
- Commit messages are plain sentences. No AI co-author trailer.
- If you found a trap that belongs in section 5, add it in the same change, one or two lines,
  pointing at the issue or commit that taught it.
