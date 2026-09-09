# Developing Synkplay

Run the commands below from the repository root unless a block changes directories. On Windows,
use `gradlew.bat` in place of `./gradlew`.

## Project map

| Location | Responsibility |
|---|---|
| [`shared/`](../shared) | Compose UI, room state, protocol, sync decisions and built-in server; platform integrations live in their source sets |
| [`shared/src/nonWebMain/`](../shared/src/nonWebMain) | Everything the browser cannot run: TCP sockets, the KitePlayer engine, blocking reads |
| [`shared/src/jvmShared/`](../shared/src/jvmShared) | Netty client and NewPipe resolver shared by Android and desktop |
| [`androidApp/`](../androidApp), [`iosApp/`](../iosApp), [`desktopApp/`](../desktopApp), [`webApp/`](../webApp) | Platform application shells |
| [`buildSrc/`](../buildSrc) | Release tasks, dependency reporting and quality gates |

The networking protocol is a Kotlin port of the official Syncplay client. `RoomViewmodel` owns
the room managers; the sync decision is a pure function in `app.protocol.sync.SyncDecision`.
Netty handles client TCP/TLS on Android and desktop, SwiftNIO on iOS. The shared Ktor transport
is a fallback without opportunistic TLS; the built-in server does not offer TLS.

Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) for the ground rules, the gate commands, the
source-set rules and the traps worth knowing before you change anything. Open work lives in
the issue tracker.

## Toolchain

- Install **JDK 21**. The build requests it without a vendor constraint; do not add a JVM vendor
  pin or a toolchain downloader to work around a missing local JDK.
- Use Android Studio compatible with the AGP version in
  [`gradle/libs.versions.toml`](../gradle/libs.versions.toml), and the committed Gradle wrapper.
- Android currently uses **compile/target SDK 37**, **Build Tools 37.0.0**, **NDK 29.0.14206865**,
  and **min SDK 26 (Android 8.0)**. The authoritative pins are in
  [`gradle.properties`](../gradle.properties).
- iOS requires macOS, Xcode and CocoaPods. The deployment target is **iOS 14.1**; arm64 device
  and arm64 simulator targets are configured.

Set `sdk.dir` in your local `local.properties`, or configure `ANDROID_HOME`, for the Android SDK.
Dependency versions live in the version catalog; application identity and version live in the
root [`kiteConfig` block](../build.gradle.kts).

## Android

Build a debug APK:

```sh
./gradlew :androidApp:assembleFullDebug -PexoOnly=false
```

Build the smaller ExoPlayer-only variant in a separate invocation:

```sh
./gradlew :androidApp:assembleExoOnlyDebug -PexoOnly=true
```

APKs are written under `androidApp/build/outputs/apk/`. There is one universal APK per flavor.
`-PexoOnly` selects the project's flavor model, so full and exo-only tasks must not be mixed in
one Gradle invocation.

The full flavor contains ExoPlayer, mpv and KitePlayer and uses `com.yuroyami.syncplay`.
The exo-only flavor uses `com.reddnek.syncplay`; it can coexist with the full app. It strips the
native mpv and KitePlayer libraries, but still contains other native code, including the
ExoPlayer FFmpeg audio extension. `verifyExoOnlyApk` checks the packaged APK for player libraries.

mpv arrives prebuilt through [`libmpvKt`](https://github.com/yuroyami/libmpvKt), from the Maven
repository declared in [`settings.gradle.kts`](../settings.gradle.kts). There is no local mpv
native build. AGP still uses the pinned NDK to strip packaged libraries and extract symbols.
KitePlayer resolves from Maven Central; `-PuseMavenLocal=true` explicitly enables local overrides
for the maintainer's libraries.

## iOS

Prepare the shared framework placeholder before resolving the local CocoaPod:

```sh
./gradlew :shared:generateDummyFramework
cd iosApp
pod install
open iosApp.xcworkspace
```

Build the `iosApp` scheme in Xcode. Select a development team for a device build. Use the
workspace so CocoaPods dependencies are included; VLCKit is declared once in
[`shared/build.gradle.kts`](../shared/build.gradle.kts) and linked through the shared pod.
If the local CocoaPods spec cache cannot resolve the locked dependencies, run
`pod install --repo-update`.

For a Kotlin-only compile check from the repository root:

```sh
./gradlew :shared:compileKotlinIosArm64
```

This does not compile the Swift shell or its bridges. Changes to exported Kotlin types also
need a complete Xcode workspace build against the regenerated framework.

## Desktop

```sh
./gradlew :desktopApp:run
```

Build an installer on its matching operating system:

| Host | Command | Output directory |
|---|---|---|
| macOS | `./gradlew :desktopApp:packageDmg` | `desktopApp/build/compose/binaries/main/dmg/` |
| Windows | `gradlew.bat :desktopApp:packageMsi` | `desktopApp/build/compose/binaries/main/msi/` |
| Linux | `./gradlew :desktopApp:packageDeb` | `desktopApp/build/compose/binaries/main/deb/` |

`jpackage` does not cross-build installers. `:desktopApp:createDistributable` builds an
application image. macOS package versions use a leading `1` for the app's `0.x` versions to meet
jpackage's version requirement; the app's own version remains unchanged.

KitePlayer is the only desktop engine. Its decoder arrives with the KiteFFmpeg dependency;
there is no separate native-player download task. Desktop rendering uses the Compose canvas
so the room controls can receive input over video. Add `-PdebugProtocol=true` to a Gradle
invocation when investigating wire traffic.

## Web

Early scaffolding. The target builds and the interface runs in a browser, but it cannot reach a
server and cannot play video yet. Treat it as a place to work, not a client to use.

```sh
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

That serves the app on a local development port with hot reload. For a static bundle:

| Purpose | Command | Output directory |
|---|---|---|
| Development bundle | `./gradlew :webApp:wasmJsBrowserDevelopmentWebpack` | `webApp/build/dist/wasmJs/developmentExecutable/` |
| Production bundle | `./gradlew :webApp:wasmJsBrowserDistribution` | `webApp/build/dist/wasmJs/productionExecutable/` |

Compile without bundling while working on shared code:

```sh
./gradlew :shared:compileKotlinWasmJs
```

Two gaps are deliberate and both need real work rather than configuration.

- **Nothing to connect to.** A browser tab cannot open a TCP socket, which is what the Syncplay
  protocol runs on. `WebSocketNetworkManager` sends the same CRLF-delimited JSON over a WebSocket
  instead, and no Syncplay server answers that today. The two ways forward are a bridge process
  that translates WebSocket to TCP, or a WebSocket listener added beside the TCP one in this
  app's own built-in server. The second needs no hosted infrastructure.
- **Nothing to play with.** All four existing engines decode natively. The web engine wraps the
  browser's own `<video>` element, and the element is not attached yet: `WebVideoImpl` satisfies
  the player contract and records state so the rest of the app runs. Attaching it means one
  element placed through Compose Multiplatform's `HtmlElementView`.

Some features are absent by nature rather than unfinished: hosting a server, the stream-URL
resolvers, folder scanning for shared playlists, and any filesystem access, so logs, downloaded
subtitles and resume positions have nowhere to go. Settings do persist, in the browser's
localStorage.

Compose Multiplatform's web target is Beta while the other three are stable. A failure that shows
up only in the browser is the target's before it is the app's.

## Player capabilities

Choose the engine on Home before joining. The room creates that engine once; in-room settings
configure the selected engine and do not switch to another one.

| Platform / engine | Default | Playback and subtitles | Chapters | Picture-in-picture |
|---|---|---|---|---|
| Android / mpv | Full flavor | Broad formats; embedded and external subtitles, including libass styling | Yes | Android host |
| Android / ExoPlayer | Exo-only flavor | Device video codecs plus bundled FFmpeg audio extension; embedded and external subtitles | No | Android host |
| Android / KitePlayer | No; experimental | FFmpeg playback; embedded and external subtitles, including styled ASS | Yes | Android host |
| iOS / VLCKit | Yes | Broad formats; embedded and external subtitles | Yes | Yes |
| iOS / AVPlayer | No | System-supported media; embedded text tracks, no external subtitle loading | No | Yes |
| iOS / KitePlayer | No; experimental | FFmpeg playback; embedded and external subtitles, including styled ASS | Yes | No |
| Desktop / KitePlayer | Yes; only engine | FFmpeg playback; embedded and external subtitles, including styled ASS | Yes | No |

Support depends on the actual media and device. Keep engine lifecycle, subtitle selection and
playback behavior covered by the relevant device checks when changing adapters.

Component licenses are not interchangeable with the app's [AGPL-3.0 license](../LICENSE).
The [in-app attribution list](../shared/src/commonMain/kotlin/app/home/components/Attributions.kt)
records Media3 as Apache 2.0, mpv as GPL 2.0 or later, VLCKit/libVLC as LGPL 2.1 or later, and
the KitePlayer/KiteFFmpeg libraries as Apache 2.0. Their bundled FFmpeg builds have separate
notices: see the [Exo audio extension's provenance](../shared/libs/README.md) and the corresponding
upstream native release/source notices, including [libmpvKt releases](https://github.com/yuroyami/libmpvKt/releases).
Do not infer a bundled decoder's license from the Kotlin wrapper's license.

## Verification

```sh
./gradlew :shared:desktopTest :shared:testAndroidHostTest
./gradlew qualityGates detekt koverVerify
./gradlew :shared:compileKotlinWasmJs
```

The shared tests run against both desktop and Android host implementations. They cover sync
decisions and position reporting, wire parsing, server flows, room passwords, invites, file
comparison, ping/clock offset, slash commands, resume storage, settings backup and rate limiting.
Desktop design tests also render real composables; their images appear in
`shared/build/design-goldens/`. The network-dependent subtitle E2E test is intentionally ignored.

`qualityGates` checks source and resource invariants; `detekt` uses the repository's focused
configuration. `koverVerify` enforces the protocol/server coverage floor in the root build file.
Keep that floor as a ratchet. When dependency versions change, run `./gradlew updateDocVersions`
to refresh the generated version tables.

Host tests do not prove native decoder behavior, PiP, background/interruption handling,
lock-screen controls, television remote navigation, or two-device synchronization. Use a real
Android device over ADB, not an Android emulator, and a physical iOS device for those paths.
A sync change needs a two-device session through file changes, pause/seek, EOF, backgrounding
and reconnect. A platform compile alone does not establish that these work.

## Releases

The manual [Release workflow](../.github/workflows/release.yml) runs `qualityGates` and `detekt`
before building Android and iOS. Host tests and coverage are separate checks to run before
starting it. Store uploads are optional; the Play track is an input. Desktop packaging is
optional and off by default. The GitHub release waits for enabled desktop builds, but not for
store upload jobs; it attaches APKs, the IPA and any built desktop installers. The AAB goes to
the Play upload job.

1. Update `version` in the root `kiteConfig` block. Run `./gradlew printReleaseIdentity` to obtain
   the effective version and version code.
2. Add release notes to [`CHANGELOG.md`](../CHANGELOG.md), and a Play summary of at most 500
   characters to `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Run `./gradlew kiteRewriteXcode` and review the generated identity/version changes. Run the
   relevant verification above and commit the release inputs.
4. Start the Release workflow with the intended Play track and optional destinations.
5. Check the resulting artifacts and destination statuses. A rerun for an existing version
   replaces that release's uploaded files and notes; the workflow also updates the AltStore feed.

The [release-body script](../.github/scripts/release-body.sh) builds the download table, changelog
and dependency table. `./gradlew printDependencyTable` prints the dependency information it uses.
Use `./gradlew androidReleaseAll` for local release APKs and the full AAB in `AndroidAppOutput/`;
the task invokes the two flavor builds separately and checks the output set.

Local Android release signing requires `keystore/syncplaykey.jks` and `keystore.keyAlias`,
`keystore.keyPassword`, and `keystore.storePassword` in `local.properties`. CI signing uses
repository secrets. A release build without the keystore fails; contributors can build debug
variants without the maintainer's signing credentials.
