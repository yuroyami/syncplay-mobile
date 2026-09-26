# Developing Synkplay

This guide explains how to build, run and check Synkplay, a Kotlin Multiplatform client for
[Syncplay](https://syncplay.pl/). Run every command from the repository root. On Windows, use
`gradlew.bat` in place of `./gradlew`.

Two words appear everywhere in this repository:

- A **room** is the group of people who watch together. A Syncplay server keeps everyone in the
  room at the same position.
- An **engine** is one of the video players that the app can drive, such as ExoPlayer or mpv.

## Project map

| Location | Contents |
|---|---|
| [`shared/`](../shared) | The Compose UI, the room state, the protocol, the sync decisions and the built-in server. Platform code lives in the platform source sets. |
| [`shared/src/nonWebMain/`](../shared/src/nonWebMain) | Code that a browser cannot run: TCP sockets, the KitePlayer engine and blocking reads. |
| [`shared/src/jvmShared/`](../shared/src/jvmShared) | The Netty client and the NewPipe media resolver, for Android and desktop. A media resolver turns a page link, such as a YouTube link, into a video stream. |
| [`androidApp/`](../androidApp), [`iosApp/`](../iosApp), [`desktopApp/`](../desktopApp), [`webApp/`](../webApp) | The platform app shells. |
| [`buildSrc/`](../buildSrc) | The release tasks, the dependency report and the quality gates. |

The protocol code is a Kotlin port of the official Syncplay client. `RoomViewmodel` holds one
room. It owns the managers of that room, such as `PlayerManager`, `ProtocolManager` and
`SharedPlaylistManager`. The sync decision is a pure function: `decideSync()` in
`app/protocol/sync/SyncDecision.kt`.

Netty carries the client connection on Android and desktop. SwiftNIO carries it on iOS. Both can
upgrade the connection to TLS when the server offers it. The shared Ktor transport is a fallback
without TLS. The built-in server does not offer TLS.

Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) before you change anything. It has the ground rules,
the gate commands, the source-set rules and the known traps. Open work is in the issue tracker.

## Toolchain

- Install **JDK 21**. The build asks for JDK 21 and names no vendor. Do not add a JVM vendor pin or
  a toolchain downloader. The IzzyOnDroid rebuild servers bring their own JDK 21 (issue #105).
- Use an Android Studio version that supports the AGP version in
  [`gradle/libs.versions.toml`](../gradle/libs.versions.toml). Use the committed Gradle wrapper.
- Android uses compile and target **SDK 37**, **Build Tools 37.0.0**, **NDK 29.0.14206865** and
  **min SDK 26** (Android 8.0). The pins are in [`gradle.properties`](../gradle.properties).
- iOS needs macOS, Xcode and CocoaPods. The deployment target is **iOS 15.0**. The build has an
  arm64 device target and an arm64 simulator target.

Set `sdk.dir` in `local.properties`, or set `ANDROID_HOME`, so that Gradle finds the Android SDK.

`gradle.properties` turns on the Gradle build cache and the configuration cache. A task that
reads the `Project` object while it runs breaks the configuration cache. Read what the task
needs while it is configured, as `printDependencyTable` does.

Dependency versions are in the version catalog. The app name, the app ID and the version are in
the root [`kiteConfig` block](../build.gradle.kts). KiteConfig is a Gradle plugin. It keeps these
values in one place and writes them into the Android, iOS and desktop projects.

## Android

The Android app has two flavors:

- **full**: ExoPlayer, mpv and KitePlayer. The app ID is `com.yuroyami.syncplay`.
- **exoOnly**: ExoPlayer only. The app ID is `com.reddnek.syncplay`, so this app installs next to
  the full app.

The `-PexoOnly` property selects one flavor for the whole Gradle run. Do not mix full and exoOnly
tasks in one Gradle run.

Build a debug APK of the full flavor:

```bash
./gradlew :androidApp:assembleFullDebug -PexoOnly=false
```

Build a debug APK of the exoOnly flavor in a separate Gradle run:

```bash
./gradlew :androidApp:assembleExoOnlyDebug -PexoOnly=true
```

Gradle writes the APKs under `androidApp/build/outputs/apk/`. Each flavor has one universal APK
that holds every ABI.

The exoOnly build removes the mpv libraries and the KitePlayer libraries (`libkitecodec_jni.so`
and `libkiteplayer_libass_jni.so`). The only player code left is the ExoPlayer FFmpeg audio
extension, which ExoPlayer uses.

Each APK gets a native library check as part of its assemble task:

- `verifyExoOnlyDebugApk` and `verifyExoOnlyReleaseApk` fail the build when a player library is
  inside the exoOnly APK.
- `verifyFullDebugDecoders` and `verifyFullReleaseDecoders` fail the build when the full APK has
  an ABI without the KitePlayer decoder. The one exception is 32-bit x86, which KitePlayer does
  not build for.

mpv comes prebuilt from [libmpvKt](https://github.com/yuroyami/libmpvKt), through the Maven
repository that [`settings.gradle.kts`](../settings.gradle.kts) declares. There is no local mpv
build. AGP still uses the pinned NDK to strip the packaged libraries and to extract symbols.
KitePlayer comes from Maven Central.

To test local builds of the `io.github.yuroyami` libraries (such as KiteConfig and KitePlayer),
add `-PuseMavenLocal=true`. Gradle then takes those libraries from your local Maven repository. A
release build with this flag fails on purpose, because nobody else could rebuild it.

## Android TV

The same APK runs on phones and on Android TV. On a television, people use the app with a remote
(D-pad). The app keeps its content inside a safe margin at the screen edge. It also lists the
videos on the device itself, because a remote cannot use the system file picker.

These rules apply to television work:

- An Android phone emulator never counts as verification.
- You can use the Android TV emulator for television work. A real television decides the final
  pass.
- Do not add a second focus style for television. The drawn controls already paint the focus
  ring (see `app/uicomponents/controls/ControlSupport.kt`).

### Check focus without an emulator

`TvFieldEscapeTest`, `TvModalFocusTest` and `TvControlsTest` press keys against the render harness.
The render harness is a set of desktop tests that draw real composables without a device. So most
focus rules need no emulator. These tests run with `./gradlew :shared:desktopTest`.

### Set up the emulator

The steps and the faults below come from an Apple silicon Mac. Another host can need a different
GPU mode, or an x86_64 system image.

Install the system image:

```bash
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "system-images;android-36;google-tv;arm64-v8a"
```

Create the device:

```bash
$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd -n tv-1080p -k "system-images;android-36;google-tv;arm64-v8a" -d tv_1080p
```

Start the emulator. It boots in about a minute.

```bash
$ANDROID_HOME/emulator/emulator -avd tv-1080p -no-snapshot -gpu swiftshader_indirect -no-audio -no-boot-anim
```

### Use the emulator

Send a remote key. The other keys are `KEYCODE_DPAD_UP`, `KEYCODE_DPAD_LEFT`,
`KEYCODE_DPAD_RIGHT`, `KEYCODE_DPAD_CENTER` and `KEYCODE_BACK`.

```bash
adb shell input keyevent KEYCODE_DPAD_DOWN
```

Take a screenshot. The focused control draws its focus ring, so the screenshot shows where the
focus is.

```bash
adb exec-out screencap -p > tv.png
```

The system image has no file picker, so the app shows its own video list. Copy a video to the
emulator:

```bash
adb push video.mp4 /sdcard/Movies/
```

Then tell the media scanner about the file. After that, the video list of the app shows it.

```bash
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Movies/video.mp4
```

### Known faults

- The accessibility dump returns no root node while a video decodes. During playback, read the
  screen from screenshots.
- mpv's video output on the software renderer takes all the processor time of the emulator. Test
  with ExoPlayer, and judge mpv on a real device.
- Both GPU modes of the emulator can fail after a while, and `adb` then hangs. Restart the
  emulator.
- The official server drops connections that arrive less than 3 seconds apart. When you restart
  the app often, test in solo mode (watching alone, with no server). To start solo mode, open
  About from the logo on Home, then pick **Watch alone**.
- The television keyboard floats over the app and reports a height of zero. Code must check
  whether the keyboard is visible, not how tall it is.

## iOS

Build the placeholder shared framework. CocoaPods needs it to resolve the local pod.

```bash
./gradlew :shared:generateDummyFramework
```

Install the pods:

```bash
pod install --project-directory=iosApp
```

Open the workspace:

```bash
open iosApp/iosApp.xcworkspace
```

Build the `iosApp` scheme in Xcode. For a device build, select a development team. Always open
the workspace, not the project, so that the CocoaPods dependencies are part of the build.
[`shared/build.gradle.kts`](../shared/build.gradle.kts) declares VLCKit once, and the shared pod
links it.

If the local CocoaPods spec cache cannot resolve the locked versions, update the cache:

```bash
pod install --repo-update --project-directory=iosApp
```

To check that the Kotlin code compiles for iOS:

```bash
./gradlew :shared:compileKotlinIosArm64
```

This check does not compile the Swift shell or its bridges. A change to a Kotlin type that Swift
uses also needs a full Xcode build of the workspace.

## Desktop

Run the desktop app:

```bash
./gradlew :desktopApp:run
```

Build an installer on the operating system that it is for. jpackage cannot build an installer for
another operating system.

| Host | Command | Output directory |
|---|---|---|
| macOS | `./gradlew :desktopApp:packageDmg` | `desktopApp/build/compose/binaries/main/dmg/` |
| Windows | `gradlew.bat :desktopApp:packageMsi` | `desktopApp/build/compose/binaries/main/msi/` |
| Linux | `./gradlew :desktopApp:packageDeb` | `desktopApp/build/compose/binaries/main/deb/` |

`:desktopApp:createDistributable` builds an app image without an installer. On macOS, jpackage
does not accept a version that starts with 0. So the macOS package of a `0.x.y` app has the
version `1.x.y`. The app itself still shows its real version.

KitePlayer is the only desktop engine. Its decoder comes with the KiteFFmpeg dependency, so there
is no separate download task. The desktop app draws video on the Compose canvas, so that the room
controls over the video can take clicks. To log every protocol line, add `-PdebugProtocol=true`
to the Gradle command.

## Web

The web target is early work. It builds, and the interface runs in a browser. It cannot reach a
server, and it cannot play video.

Start a local development server:

```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

Build a static bundle:

| Purpose | Command | Output directory |
|---|---|---|
| Development bundle | `./gradlew :webApp:wasmJsBrowserDevelopmentExecutableDistribution` | `webApp/build/dist/wasmJs/developmentExecutable/` |
| Production bundle | `./gradlew :webApp:wasmJsBrowserDistribution` | `webApp/build/dist/wasmJs/productionExecutable/` |

Compile the shared code for the browser without a bundle:

```bash
./gradlew :shared:compileKotlinWasmJs
```

Two gaps block a usable web client. Both are tracked:
#ISSUE(web-client-has-no-server-to-reach) and #ISSUE(web-client-has-no-video).

- **No server to reach.** A browser tab cannot open a TCP socket, and the Syncplay protocol runs
  over TCP. `WebSocketNetworkManager` sends the same JSON lines, each one ending in CRLF, over a
  WebSocket. No Syncplay server accepts WebSocket connections today. One fix is a bridge process
  that translates WebSocket to TCP. The other fix is a WebSocket listener beside the TCP listener
  in the app's own built-in server. The second fix needs no hosting.
- **No video.** Every other engine decodes in native code, which a browser cannot load. The web
  engine, `WebVideoImpl`, wraps the browser's `<video>` element, but no element is attached yet.
  The engine follows the engine contract and records state, so the rest of the app runs. To
  attach the element, place one `<video>` through Compose Multiplatform's `HtmlElementView`.

Some features cannot exist in a browser:

- hosting a server, because a tab cannot listen on a port
- the media resolvers, because NewPipe Extractor is a JVM library and YouTubeKit is Swift
- scanning a folder for the shared playlist (the list of files that everyone in the room follows)
- files on disk, so the web client keeps no log file and no downloaded subtitles

Settings persist in the browser's localStorage. The saved resume positions are a setting, so they
persist too.

## Engine capabilities

Pick the engine on Home before you join. Home shows what each engine supports. The room creates
the engine once. The room settings configure that engine, and they do not switch to another one.

| Platform and engine | Default | Playback and subtitles | Chapters | Picture-in-picture |
|---|---|---|---|---|
| Android, mpv | Yes, in the full flavor | Broad format support. Embedded and external subtitles, with libass styling. | Yes | Yes |
| Android, ExoPlayer | Yes, in the exoOnly flavor | The device's video codecs, plus the bundled FFmpeg audio extension. Embedded and external subtitles. | No | Yes |
| Android, KitePlayer | No (experimental) | FFmpeg playback. Embedded and external subtitles, with styled ASS. | Yes | Yes |
| iOS, VLCKit | Yes | Broad format support. Embedded and external subtitles. | Yes | Yes |
| iOS, AVPlayer | No | The formats that iOS supports. Embedded text tracks. No external subtitle files. | No | Yes |
| iOS, KitePlayer | No (experimental) | FFmpeg playback. Embedded and external subtitles, with styled ASS. | Yes | No |
| Desktop, KitePlayer | Yes (the only engine) | FFmpeg playback. Embedded and external subtitles, with styled ASS. | Yes | No |
| Web, browser video | Yes (the only engine) | Nothing plays yet. See [Web](#web). | No | No |

On Android, picture-in-picture belongs to the app window, so it works with every engine. On
Android, KitePlayer needs a 64-bit device, because its decoder library exists only for arm64-v8a
and x86_64.

What plays depends on the file and on the device. When you change an engine adapter, check its
lifecycle, its subtitle selection and its playback on a real device.

The app is under the [AGPL-3.0 licence](../LICENSE). Its components keep their own licences. The
[in-app attribution list](../shared/src/commonMain/kotlin/app/home/components/Attributions.kt)
lists Media3 as Apache 2.0, mpv as GPL 2.0 or later, VLCKit and libVLC as LGPL 2.1 or later, and
KitePlayer and KiteFFmpeg as Apache 2.0. Each bundled FFmpeg build has its own licence:

- the FFmpeg in the ExoPlayer audio extension: see [`shared/libs/README.md`](../shared/libs/README.md)
- the FFmpeg in mpv: see the [libmpvKt releases](https://github.com/yuroyami/libmpvKt/releases)
- the FFmpeg in KitePlayer: see [KiteFFmpeg](https://github.com/yuroyami/KiteFFmpeg)

Do not infer the licence of a bundled decoder from the licence of its Kotlin wrapper.

## Verification

Run the tests:

```bash
./gradlew :shared:desktopTest :shared:testAndroidHostTest
```

Run the static checks:

```bash
./gradlew qualityGates detekt koverVerify
```

Compile the web target:

```bash
./gradlew :shared:compileKotlinWasmJs
```

What these tasks check:

- `:shared:desktopTest` runs the common tests on the desktop JVM, and the desktop-only tests.
  `:shared:testAndroidHostTest` runs the common tests against the Android implementations on the
  host JVM.
- The common tests cover sync decisions and position reports, wire parsing, server flows, room
  passwords, invite links, file comparison, ping and clock offset, slash commands, resume
  storage, settings backup and rate limiting.
- The render harness writes its images to `shared/build/design-goldens/`.
- `qualityGates` runs nine build-time checks, such as the string resource and locale checks.
  `detekt` uses the rule set of this repository.
- `koverVerify` fails when the line coverage of `app.protocol` and `app.server` drops below
  `COVERAGE_FLOOR` in the root [`build.gradle.kts`](../build.gradle.kts). Raise the floor when
  coverage grows. Do not lower it.

The live subtitle test is off by default. It needs the network, and each run uses one download
from the daily quota of the OpenSubtitles key (5 a day on the free plan). Run it only when you
need it:

```bash
./gradlew :shared:desktopTest -PliveSubtitles --tests app.subtitles.SubtitleDownloadE2ETest
```

Tests on the host do not prove native decoding, picture-in-picture, backgrounding and
interruptions, lock-screen controls, television remote navigation, or sync between two devices.

- Check these paths on a real Android device over ADB, and on a real iPhone or iPad.
- An Android phone emulator does not count as verification. For television work, see
  [Android TV](#android-tv).
- Check a sync change with two devices in one room. Go through a file change, a pause, a seek,
  the end of a file, backgrounding and a reconnect. A compile on each platform does not show that
  these work.

## Releases

The manual [Release workflow](../.github/workflows/release.yml) runs `qualityGates` and `detekt`,
and in a second job the desktop tests, the Android host tests and `koverVerify`. It builds Android
and iOS only after both jobs pass, so a failing test stops the release before any upload.

- The store uploads are optional. You select the Play track when you start the workflow.
- The desktop packages are optional and off by default.
- The GitHub release waits for the builds, including enabled desktop builds. It does not wait for
  the store uploads.
- The GitHub release gets the APKs, the IPA and any desktop installers. The AAB goes only to the
  Play upload job.

To make a release:

1. Set `version` in the root `kiteConfig` block.
2. Print the version and the version code that the build uses:

   ```bash
   ./gradlew printReleaseIdentity
   ```

3. Add the release notes to [`CHANGELOG.md`](../CHANGELOG.md), under a `## <version>` heading.
4. Write a Play summary of that section, by hand, to
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. Put the most important
   changes first. `checkStoreMetadata` fails when the summary is missing, longer than 500
   characters, shorter than 40, or a placeholder such as "Maintenance update.", and when
   `CHANGELOG.md` has no section for the version.
5. Write the version into the Xcode project, then review the change:

   ```bash
   ./gradlew kiteApplyIos
   ```

6. Run the checks in [Verification](#verification). Commit the release inputs.
7. Start the Release workflow. Select the Play track and the destinations.
8. Check the artifacts and the status of each destination.

A second run for a version that already has a release replaces the files and the notes of that
release. The workflow also adds the version to the AltStore feed, `altstore_yuroyami.json`.

The [release-body script](../.github/scripts/release-body.sh) writes the download table, the
changelog and the dependency table of the release notes. Print the dependency data that it uses:

```bash
./gradlew printDependencyTable
```

Build the release APKs and the full AAB on your machine, into `AndroidAppOutput/`:

```bash
./gradlew androidReleaseAll
```

This task builds the two flavors in separate Gradle runs and checks the set of output files. It
also checks that the release certificate signed each APK, and that each APK carries the version
being released. The check needs `apksigner` and `aapt2` from the SDK build tools. The task finds
them through `ANDROID_HOME` or `sdk.dir` in `local.properties`, and fails when it cannot. Pass
`-PallowUnverifiedApks=true` only for a test build that is not for release.

A local release build needs the signing keystore: `keystore/syncplaykey.jks`, plus
`keystore.keyAlias`, `keystore.keyPassword` and `keystore.storePassword` in `local.properties`.
The CI signs with repository secrets. A release build without the keystore fails. Debug builds
need no keystore, so contributors can build them without the signing credentials.

### Reproducing a published APK

The exoOnly APK is built so that anyone can rebuild it from source and compare it with the
published file. IzzyOnDroid checks each release this way. The build needs no keystore, no
`local.properties` and no repository secret.

A `yuroyami.keyOpenSubsApi` entry in `local.properties` replaces the committed OpenSubtitles key,
and your APK then differs from the published file. Remove that entry before you build.

1. Check out the tag of the release. A tag is `v` followed by the version, so release 0.25.0 has
   the tag `v0.25.0`.
2. Build with **JDK 21**, the version that [Toolchain](#toolchain) names:

   ```bash
   ./gradlew assembleExoOnlyRelease -PexoOnly=true -PunsignedRelease=true
   ```

3. Find the APK in `androidApp/build/outputs/apk/exoOnly/release/`. Its name contains `unsigned`,
   so nobody can take it for a release. `-PunsignedRelease=true` skips signing on purpose, even
   on a machine that holds the keystore.
4. Copy the signature from the published APK onto your APK with
   [apksigcopier](https://github.com/obfusk/apksigcopier).
5. Compare the two files byte for byte.
