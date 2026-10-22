# Developing Synkplay

This guide explains how to build, run and check Synkplay, a Kotlin Multiplatform client for
[Syncplay](https://syncplay.pl/). Run every command from the repository root. On Windows, use
`kotlin.bat` in place of `./kotlin`.

Two words appear everywhere in this repository:

- A **room** is the group of people who watch together. A Syncplay server keeps everyone in the
  room at the same position.
- An **engine** is one of the video players that the app can drive, such as ExoPlayer or mpv.

## Project map

| Location | Contents |
|---|---|
| [`shared/`](../shared) | The Compose UI, the room state, the protocol, the sync decisions and the built-in server. Platform code lives in the platform source sets. |
| [`shared/src@nonWeb/`](../shared/src@nonWeb) | Code that a browser cannot run: TCP sockets, the KitePlayer engine and blocking reads. |
| [`shared/src@jvmShared/`](../shared/src@jvmShared) | The Netty client and the NewPipe media resolver, for Android and desktop. A media resolver turns a page link, such as a YouTube link, into a video stream. |
| [`androidApp/`](../androidApp), [`iosApp/`](../iosApp), [`desktopApp/`](../desktopApp), [`webApp/`](../webApp) | The platform app shells. |
| [`build-plugins/`](../build-plugins) | Local build plugins: the generated constants and the release identity, the KSP run, VLCKit, the quality gates, the Android release checks and the desktop installers. |
| [`tools/android-lint/`](../tools/android-lint) | A small program that runs Android Lint on the Android app. |
| [`scripts/android_release.py`](../scripts/android_release.py) | Builds and checks the Android release files. |

The protocol code is a Kotlin port of the official Syncplay client. `RoomViewmodel` holds one
room. It owns the managers of that room, such as `PlayerManager`, `ProtocolManager` and
`SharedPlaylistManager`. The sync decision is a pure function: `decideSync()` in
`app/protocol/sync/SyncDecision.kt`.

Netty carries the client connection on Android and desktop. SwiftNIO carries it on iOS. Both can
upgrade the connection to TLS when the server offers it. The shared Ktor transport is a fallback
without TLS.

A certificate that the system does not trust is not a plain failure. The app shows its SHA-256
fingerprint, and the person can trust that fingerprint for that server address. `CertificatePins`
keeps the answer, and a later, different certificate for the same address gets a warning. The
check is `PinningTrustManager` on Android and desktop, and the custom verification in
`SwiftNioNetworkManager.swift` on iOS. Nothing trusts a certificate that no one has seen.

The built-in server offers TLS only when the host turns on **Encrypt connections**, which is off
by default, because Syncplay for PC refuses the host's own certificate. The server then makes an
EC certificate once (`HostCertificate`), keeps it in the app's storage, and shows its fingerprint
in the hosting panel. Only the Netty server engines on Android and desktop can switch a socket to
TLS in the middle of a connection, so an iOS host does not offer the switch.

Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) before you change anything. It has the ground rules,
the gate commands, the source-set rules and the known traps. Open work is in the issue tracker.

## Toolchain

The build uses the [Kotlin Toolchain](https://github.com/JetBrains/kotlin-toolchain): the
`./kotlin` script at the repository root. [`project.yaml`](../project.yaml) lists the modules, and
each module has a `module.yaml`. The script downloads the pinned toolchain on the first run.

- The toolchain downloads the JDK that the build needs (JDK 21, `jvm.jdk.version` in
  [`common.module-template.yaml`](../common.module-template.yaml)). You do not install a JDK for
  the build.
- Dependency versions are in the version catalog, [`libs.versions.toml`](../libs.versions.toml).
- Android uses compile SDK **37.2**, target SDK **37**, Build Tools **37.0.0** and **min SDK 26**
  (Android 8.0). The values are in [`androidApp/module.yaml`](../androidApp/module.yaml). The
  toolchain builds Android with its bundled Android Gradle plugin, so the release build also needs
  the default NDK of that plugin, **NDK 28.2.13676358**, to strip the native libraries.
- iOS needs macOS and Xcode. The deployment target is **iOS 15.0**. The build has an arm64 device
  target and an arm64 simulator target.

Set `ANDROID_HOME` to the Android SDK. If it is not set, the toolchain downloads an SDK of its own.

The app name, the version and the generated constants are in the `app-config` block of
[`shared/module.yaml`](../shared/module.yaml). `app-config` is a local build plugin in
[`build-plugins/app-config`](../build-plugins/app-config). It generates `KiteBuildConfig`, and its
commands write the version into the Android, desktop and Xcode files.

## Android

The Android app has two builds:

- **full**: ExoPlayer, mpv and KitePlayer. The app ID is `com.yuroyami.syncplay`.
- **exoOnly**: ExoPlayer only. The app ID is `com.reddnek.syncplay`, so this app installs next to
  the full app.

Build a debug APK of the full app:

```bash
./kotlin build -m androidApp -p android
```

The APK is `build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk`. It holds every
ABI. Install it on a connected device with `./kotlin run -m androidApp`.

The exoOnly app exists only as a release build. [Releases](#releases) shows how to build it.
`scripts/android_release.py` builds the release APK with the exoOnly app ID, and with
`KiteBuildConfig.EXOPLAYER_ONLY` set to true. Then it removes the mpv libraries and the KitePlayer
libraries (`libkitecodec_jni.so` and `libkiteplayer_libass_jni.so`) from the APK, and signs the
APK again. The only player code left is the ExoPlayer FFmpeg audio extension, which ExoPlayer
uses.

The toolchain stores the native libraries uncompressed. For the full universal APK, the script
compresses them and sets `android:extractNativeLibs` to true, which makes the download about half
as big. The exoOnly APK and the AAB keep the uncompressed libraries.

The script also checks each release APK:

- The exoOnly APK fails the check when a player library is inside.
- The full APK fails the check when it has an ABI without the KitePlayer decoder. The one
  exception is 32-bit x86, which KitePlayer does not build for.
- Each APK fails the check when its code calls a Java API above min SDK 26 outside a guarded
  call, or when it holds a class of Google Play services or Tink. The toolchain has no core
  library desugaring, so such a call crashes on older devices.
  [`scripts/apk_api_check.py`](../scripts/apk_api_check.py) does this check with `dexdump` and
  `aapt2` from the Build Tools.

mpv comes prebuilt from [libmpvKt](https://github.com/yuroyami/libmpvKt), through the Maven
repository that [`common.module-template.yaml`](../common.module-template.yaml) declares. There is
no local mpv build. KitePlayer comes from Maven Central.

The Android Gradle plugin inside the toolchain writes no native debug symbols. So
`scripts/android_release.py` writes them itself: it extracts the debug sections of each native
library of the release build with the NDK's `llvm-objcopy`, into
`AndroidAppOutput/symbols/native-debug-symbols.zip`. libmpvKt ships mpv and FFmpeg stripped, so
those libraries have no debug sections. The release workflow runs
[`add-libmpvkt-symbols.sh`](../.github/scripts/add-libmpvkt-symbols.sh), which takes their symbol
files from the libmpvKt release and adds them to the same file. The Play upload sends that file.
Each libmpvKt release from 0.3.0 on attaches these symbol files. The script fails when a symbol
file does not match the library in the APK.

To test local builds of the `io.github.yuroyami` libraries (such as KitePlayer), add
`- mavenLocal` to `repositories` in
[`common.module-template.yaml`](../common.module-template.yaml), and do not commit it. A release
build with that entry fails on purpose, because nobody else could rebuild it.

### Baseline profile

A baseline profile lists the code that Android compiles ahead of time when it installs the app.
Both release builds package
[`baseline-prof.txt`](../androidApp/src/main/baselineProfiles/baseline-prof.txt). A build never
generates this file, so every machine builds the same APK. The file in the repository is a
hand-written first profile. It covers the app's own packages on the way from a cold start to an
open room.

The repository has no generator for a measured profile, because the Kotlin Toolchain has no module
type for Android tests that drive a device. To measure a new profile, use a separate Gradle
project with the Baseline Profile plugin against an installed release build. Then replace
`baseline-prof.txt` with its output.

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
focus rules need no emulator. These tests run with `./kotlin test -m shared -p jvm`.

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

Prepare VLCKit, the iOS video engine. The `vlckit` build plugin downloads the VLCKit archive of the
version in [`shared/module.yaml`](../shared/module.yaml), checks its SHA-256, and writes
`build/vlckit/VLCKit.xcframework`. The archive stays in `~/Library/Caches/Synkplay/VLCKit`, so
this step downloads it once.

```bash
./kotlin do prepareVlcKit
```

Open the project:

```bash
open iosApp/module.xcodeproj
```

Build the `app` scheme in Xcode. For a device build, select a development team. The first build
phase of the app target runs the Kotlin Toolchain, which builds the shared code as a framework.
The Swift code imports it as `KotlinModules`. The project links and embeds VLCKit from
`build/vlckit/`, and it gets SwiftNIO and YouTubeKit as Swift packages.

Build the app for the simulator without Xcode open:

```bash
./kotlin build -m iosApp -p iosSimulatorArm64
```

To check only that the Kotlin code compiles for iOS:

```bash
./kotlin build -m shared -p iosArm64
```

This check does not compile the Swift shell or its bridges. A change to a Kotlin type that Swift
uses also needs a full Xcode build.

### Privacy manifest

`iosApp/src/PrivacyInfo.xcprivacy` declares the APIs on Apple's required-reason list that the
final app calls, and the data that the app collects. The API list comes from the built bundle,
because dependencies call these APIs too. Read it from a simulator build:

```bash
nm -u Synkplay.app/Synkplay.debug.dylib Synkplay.app/Frameworks/*.framework/* | grep -E '_(f?stat|lstat|fstatat|f?statv?fs|getattrlist|mach_absolute_time)$'
```

| Category | Who calls it | Reason |
|---|---|---|
| File timestamp | the app, the Kotlin framework, VLCKit (`stat`, `fstat`, `lstat`, `fstatat`) | `C617.1` files in the app container, `3B52.1` files that the user picked |
| System boot time | the Kotlin framework, VLCKit (`mach_absolute_time`) | `35F9.1` time between events in the app |
| Disk space | VLCKit (`fstatfs`) | `E174.1` space checks before a file is written |
| User defaults | VLCKit (`NSUserDefaults`) | `CA92.1` data of the app itself |

SwiftNIO ships its own manifests. The collected data matches the App Store privacy answers, see
[`STORE_DECLARATIONS.md`](../STORE_DECLARATIONS.md). Check the table again after you add or
upgrade a dependency.

## Desktop

Run the desktop app:

```bash
./kotlin run -m desktopApp
```

To see code changes without a restart, add `--compose-hot-reload`. This option runs the app on the
JetBrains Runtime.

Build an installer on the operating system that it is for. jpackage cannot build an installer for
another operating system.

```bash
./kotlin do packageDesktop -m desktopApp
```

The command writes a DMG on macOS, an MSI on Windows and a DEB on Linux into
`build/desktop/desktopApp/`. On Windows, jpackage needs WiX 3 on the `PATH`. The `desktop-package`
build plugin takes jpackage from the JDK of the build. On macOS, jpackage does not accept a version
that starts with 0. So the macOS package of a `0.x.y` app has the version `1.x.y`. The app itself
still shows its real version. The macOS app needs macOS 10.13 or later.

KitePlayer is the only desktop engine. Its decoder comes with the KiteFFmpeg dependency, so there
is no separate download task. The desktop app draws video on the Compose canvas, so that the room
controls over the video can take clicks. To log every protocol line, set
`SYNKPLAY_DEBUG_PROTOCOL=true` for the build.

## Web

The web target is early work. It builds, and the interface runs in a browser. It cannot reach a
server, and it cannot play video.

Build the web app:

```bash
./kotlin build -m webApp -p wasmJs
```

The page and its files are in `build/tasks/_webApp_buildWasmJsAppWasmJsDebug/`. Serve that folder
with any static web server, for example `python3 -m http.server`. A release build
(`./kotlin build -m webApp -p wasmJs -v release`) writes into
`build/tasks/_webApp_buildWasmJsAppWasmJsRelease/`.

Compile the shared code for the browser without the app:

```bash
./kotlin build -m shared -p wasmJs
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
| Android, mpv | Yes, in the full app | Broad format support. Embedded and external subtitles, with libass styling. | Yes | Yes |
| Android, ExoPlayer | Yes, in the exoOnly app | The device's video codecs, plus the bundled FFmpeg audio extension. Embedded and external subtitles. | No | Yes |
| Android, KitePlayer | No (experimental) | FFmpeg playback. Embedded and external subtitles, with styled ASS. | Yes | Yes |
| iOS, VLCKit | Yes | Broad format support. Embedded and external subtitles. | Yes | Yes |
| iOS, AVPlayer | No | The formats that iOS supports. Embedded text tracks. No external subtitle files. | No | Yes |
| iOS, KitePlayer | No (experimental) | FFmpeg playback. Embedded and external subtitles, with styled ASS. | Yes | No |
| Desktop, KitePlayer | Yes (the only engine) | FFmpeg playback. Embedded and external subtitles, with styled ASS. | Yes | No |
| Web, browser video | Yes (the only engine) | Nothing plays yet. See [Web](#web). | No | No |

On Android, picture-in-picture belongs to the app window, so it works with every engine. On
Android, KitePlayer runs on arm64-v8a, armeabi-v7a and x86_64 devices. It has no decoder for
32-bit x86, so it is not offered there.

What plays depends on the file and on the device. When you change an engine adapter, check its
lifecycle, its subtitle selection and its playback on a real device.

The app is under the [AGPL-3.0 licence](../LICENSE). Its components keep their own licences.
The licences screen in About lists them for the running build, each with its version and its full
licence text:

- The Maven dependencies are listed in
  [`aboutlibraries.json`](../shared/composeResources/files/aboutlibraries.json), with the texts
  in `shared/config/aboutlibraries/licenses/`. `python3 scripts/licences.py` writes the file from
  the dependency graph and the POM files in the toolchain's download cache. Run it after each
  dependency change, and commit the new file with that change. The release workflow runs
  `python3 scripts/licences.py --check` and fails when the file is out of date.
- Native code inside a library, the iOS packages, the original Syncplay and the two services are
  hand-written in
  [`Attributions.kt`](../shared/src/app/home/components/Attributions.kt). Each
  bundled FFmpeg build has its own licence, see [`shared/libs/README.md`](../shared/libs/README.md).

Do not infer the licence of a bundled decoder from the licence of its Kotlin wrapper.

## Verification

Run the tests:

```bash
./kotlin test -m shared -p jvm -p android
```

Run the desktop tests again with the coverage check:

```bash
python3 scripts/coverage.py
```

Run the static checks:

```bash
./kotlin do qualityGates
./kotlin do detekt
./kotlin check appVersion storeMetadata defaultStrings
```

Compile the web target:

```bash
./kotlin build -m shared -p wasmJs
```

Run Android Lint on the app. It reads the libraries of the debug build, so build that first:

```bash
./kotlin build -m androidApp -p android
./kotlin run -m android-lint
```

Test the connection handlers of the iOS client on a Mac, with no simulator:

```bash
swift test --package-path iosApp/NioHandlerTests
```

What these commands check:

- `-p jvm` runs the common tests on the desktop JVM, and the desktop-only tests. `-p android`
  runs the common tests against the Android implementations on the host JVM.
- The common tests cover sync decisions and position reports, wire parsing, server flows, room
  passwords, invite links, file comparison, ping and clock offset, slash commands, resume
  storage, settings backup and rate limiting.
- The render harness writes its images to `shared/build/design-goldens/`.
- The Swift package in `iosApp/NioHandlerTests` tests the line decoder and the TLS handshake
  tracker of the iOS client. Its source is a link to `iosApp/src/NioConnectionHandlers.swift`,
  so the app and the tests build the same file.
- `scripts/coverage.py` runs the `-p jvm` tests with the Kover coverage agent. It fails when the
  line coverage of `app.protocol` and `app.server` drops below `COVERAGE_FLOOR` in the script.
  Raise the floor when coverage grows. Do not lower it. The report is
  `build/coverage/html/index.html`.
- `qualityGates` runs nine checks, such as the string resource and locale checks. `detekt` uses
  the rule set of this repository. The three checks after them are listed in
  [CONTRIBUTING.md](../CONTRIBUTING.md#3-what-the-gates-check).
- Android Lint writes its reports to `build/android-lint/`. Pass `-- --exitcode` to make an error
  fail the run.

The live subtitle test is off by default. It needs the network, and each run uses one download
from the daily quota of the OpenSubtitles key (5 a day on the free plan). Run it only when you
need it:

```bash
./kotlin test -m shared -p jvm --jvm-args=-Dsynkplay.liveSubtitles=true --include-classes=app.subtitles.SubtitleDownloadE2ETest
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

The manual [Release workflow](../.github/workflows/release.yml) runs the quality gates, detekt and
the three checks, and in a second job the desktop tests with the coverage check and the Android
host tests. It builds Android and iOS only after both jobs pass, so a failing test stops the
release before any upload.

- The store uploads are optional. You select the Play track when you start the workflow.
- The desktop packages are optional and off by default.
- The GitHub release waits for the builds, including enabled desktop builds. It does not wait for
  the store uploads.
- The GitHub release gets the APKs, the IPA and any desktop installers. The AAB goes only to the
  Play upload job.

To make a release:

1. Set `version` in the `app-config` block of `shared/module.yaml`.
2. Write the version into the Android, desktop and Xcode files, then review the change:

   ```bash
   ./kotlin do syncAppVersion
   ```

3. Print the version and the version code that the build uses:

   ```bash
   ./kotlin do printReleaseIdentity
   ```

4. Add the release notes to [`CHANGELOG.md`](../CHANGELOG.md), under a `## <version>` heading.
5. Write a Play summary of that section, by hand, to
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. Put the most important
   changes first. The `storeMetadata` check fails when the summary is missing, longer than 500
   characters, shorter than 40, or a placeholder such as "Maintenance update.", and when
   `CHANGELOG.md` has no section for the version.
6. Run the checks in [Verification](#verification). Commit the release inputs.
7. Start the Release workflow. Select the Play track and the destinations.
8. Check the artifacts and the status of each destination.

A second run for a version that already has a release replaces the files and the notes of that
release. The workflow also adds the version to the AltStore feed, `altstore_yuroyami.json`.

The [release-body script](../.github/scripts/release-body.sh) writes the download table, the
changelog and the dependency table of the release notes. Print the dependency data that it uses:

```bash
./kotlin do printDependencyTable
```

Build the release APKs, the full AAB, the R8 mapping and the native debug symbols on your machine,
into `AndroidAppOutput/`. The script deletes that folder first. Pass `--output <folder>` to write
somewhere else:

```bash
python3 scripts/android_release.py
```

The script builds the two apps one after the other and checks the set of output files. It also
checks that the release certificate signed each APK, and that each APK carries the version being
released. The check needs `apksigner` and `aapt2` from the SDK build tools. The script finds them
through `ANDROID_HOME`, and fails when it cannot. Pass `--allow-unverified` only for a test build
that is not for release. `--full-only` and `--exo-only` build one app.

A local release build needs the signing keystore, `keystore/syncplaykey.jks`, and a
`keystore.properties` file at the repository root. git ignores both. The file holds four keys:

```properties
storeFile=../keystore/syncplaykey.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`storeFile` is relative to `androidApp/`. The CI writes the file from repository secrets. A release
build without the file fails. Set `SYNKPLAY_UNSIGNED_RELEASE=true` to build unsigned files on
purpose. Debug builds need no keystore, so contributors can build them without the signing
credentials.

### App Store encryption answer

`ITSAppUsesNonExemptEncryption` in `iosApp/src/Info.plist` is false. The file is committed, and no
build step writes it.

- With the key set to true, App Store Connect refuses an upload until it has approved export
  compliance documentation for the app.
- That documentation then gives a code, which goes into `Info.plist` as
  `ITSEncryptionExportComplianceCode`. Add that key by hand.
- Sources: [Complying with encryption export regulations](https://developer.apple.com/documentation/security/complying-with-encryption-export-regulations)
  and [Export compliance documentation for encryption](https://developer.apple.com/help/app-store-connect/reference/app-information/export-compliance-documentation-for-encryption/).

### Reproducing a published APK

The exoOnly APK is built so that anyone can rebuild it from source and compare it with the
published file. IzzyOnDroid checks each release this way. The build needs no keystore, no
`local.properties` and no repository secret.

A `yuroyami.keyOpenSubsApi` entry in `local.properties`, or the `SYNKPLAY_OPENSUBTITLES_KEY`
environment variable, replaces the committed OpenSubtitles key. Your APK then differs from the
published file. Remove both before you build.

1. Check out the tag of the release. A tag is `v` followed by the version, so release 0.25.0 has
   the tag `v0.25.0`.
2. Install NDK 28.2.13676358 in the Android SDK, and set `ANDROID_HOME`. The toolchain brings its
   own JDK, the same one on every machine.
3. Build the unsigned exoOnly APK:

   ```bash
   SYNKPLAY_UNSIGNED_RELEASE=true python3 scripts/android_release.py --exo-only
   ```

4. Find the APK in `AndroidAppOutput/`. Its name ends in `-unsigned.apk`, so nobody can take it for
   a release. `SYNKPLAY_UNSIGNED_RELEASE=true` skips signing on purpose, even on a machine that
   holds the keystore.
5. Copy the signature from the published APK onto your APK with
   [apksigcopier](https://github.com/obfusk/apksigcopier). The published APK has only an APK
   Signature Scheme v2 signature. Apart from that signature block, it is the unsigned APK.
6. Compare the two files byte for byte.
