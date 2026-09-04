<p align="center">
  <img src="https://raw.githubusercontent.com/yuroyami/syncplay-mobile/master/androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="160" alt="Synkplay">
</p>

<h1 align="center">Synkplay</h1>

<p align="center">
  <b>Watch videos together, in sync. An unofficial <a href="https://syncplay.pl">Syncplay</a> client for Android, iOS, Windows, macOS and Linux.</b><br>
  <sub>The "k" stands for Kotlin.</sub>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0+-a4c639?logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/iOS-14.0+-555?logo=apple&logoColor=white" alt="iOS 14.0+">
  <img src="https://img.shields.io/badge/Kotlin_Multiplatform-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin Multiplatform">
  <img src="https://img.shields.io/badge/License-AGPL--3.0-blue" alt="License">
</p>

---

## Download

<table align="center">
  <tr>
    <th></th>
    <th>Recommended</th>
    <th>Alternative</th>
    <th>Store</th>
  </tr>
  <tr>
    <td><b>Android</b></td>
    <td>
      <a href="https://github.com/yuroyami/syncplay-mobile/releases/latest">
        <img src="https://raw.githubusercontent.com/yuroyami/syncplay-mobile/master/art/badges/get-it-on-github.png" width="140" alt="GitHub Releases">
      </a>
    </td>
    <td>
      <a href="https://apt.izzysoft.de/fdroid/index/apk/com.reddnek.syncplay">
        <img src="https://raw.githubusercontent.com/yuroyami/syncplay-mobile/master/art/badges/IzzyOnDroid.png" width="140" alt="IzzyOnDroid">
      </a>
    </td>
    <td>
      <a href="https://play.google.com/store/apps/details?id=com.yuroyami.syncplay">
        <img src="https://raw.githubusercontent.com/yuroyami/syncplay-mobile/master/art/badges/google-play.png" width="140" alt="Google Play">
      </a>
    </td>
  </tr>
  <tr>
    <td><b>iOS</b></td>
    <td>
      <a href="https://apps.apple.com/us/app/syncplay-mobile/id6760187432">
        <img src="https://developer.apple.com/assets/elements/icons/download-on-the-app-store/download-on-the-app-store.svg" width="140" alt="App Store">
      </a>
    </td>
    <td>
      <a href="https://celloserenity.github.io/altdirect/?url=https://raw.githubusercontent.com/yuroyami/syncplay-mobile/refs/heads/master/altstore_yuroyami.json">
        <img src="https://raw.githubusercontent.com/yuroyami/syncplay-mobile/master/art/badges/AltSource_Blue.png" width="140" alt="AltStore Source">
      </a>
    </td>
    <td>
      <a href="https://github.com/yuroyami/syncplay-mobile/releases/latest">
        <img src="https://raw.githubusercontent.com/yuroyami/syncplay-mobile/master/art/badges/Download_Blue.png" width="140" alt="Download IPA">
      </a>
    </td>
  </tr>
</table>

<details>
<summary>Notes on downloads</summary>

- **Android (GitHub):** Each release ships per-ABI APKs plus a `universal` one. Most modern phones want `arm64-v8a`; pick `universal` if unsure.
- **Android (IzzyOnDroid):** A smaller APK built from the `exoOnly` flavor, carrying only the ExoPlayer engine (no bundled mpv native libraries). It uses package id `com.reddnek.syncplay`, so it installs **alongside** the GitHub / Play Store build (`com.yuroyami.syncplay`) rather than updating it. Keep one or the other to avoid two copies.
- **Android (Play Store):** Same build as GitHub, but a new version can appear here a little later because of store review.
- **iOS:** The "Download IPA" button always points to the latest release. No App Store access? Sideload via [AltStore](https://github.com/yuroyami/syncplay-mobile/wiki/How-to-install-the-app-on-iOS) with no jailbreak, or install the `.ipa` directly. See the [iOS install guide](https://github.com/yuroyami/syncplay-mobile/wiki/How-to-install-the-app-on-iOS) for both paths.
</details>

---

## Screenshots

<p align="center">
  <img src="https://raw.githubusercontent.com/yuroyami/syncplay-mobile/master/art/SS1.png" width="180" alt="Home screen">
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="https://raw.githubusercontent.com/yuroyami/syncplay-mobile/master/art/SS2.png" width="500" alt="Room screen">
</p>

---

## Features

- **Syncplay compatible.** Connects to the same servers and works alongside the official PC client. Position, pause, seek, and speed stay in sync.
- **Protocol rewritten** from Python to Kotlin, covering every core feature.
- **Multiple video engines.** Three per platform, switchable at runtime to suit your device and format.
- **Real-time chat** with emoji and inline GIFs.
- **Shared playlists** for sequential group viewing, with auto-advance.
- **Managed rooms** and a readiness system.
- **Picture-in-Picture** (Android, and iOS on AVPlayer or VLCKit).
- **TLS encryption** for secure connections.
- **Built-in server.** Host a room straight from the app, no PC required.
- **Solo mode.** Watch on your own with no server, from the solo button in the About popup.
- **Privacy controls.** The filename and file size you share with a room can be hashed or withheld.
- **Deep customization.** Settings for the UI, playback, gestures, and haptics.
- **Multi-language** via community translations.

---

## Getting Started

1. Open the app and enter a **username** and a **room** name.
2. Pick a **server** (the official `syncplay.pl` is preselected) and a **port** (`8995` to `8999`). Add a password only if your server needs one.
3. Tap **Join**, then load a video from device storage or paste a direct URL.
4. Anyone in the same room on the same file plays in sync, mobile or PC.

No server of your own? Use the built-in server to host a room, or solo mode to watch offline.

---

## Video Engines

Switch engines at runtime in the room settings. `✔` in **Default** marks the engine selected out of the box.

**Android**

| Engine | Default | Formats | Subtitles | Chapters | Licence | Notes |
|:---|:---:|:---|:---:|:---:|:---|:---|
| **mpv** (libmpv) | ✔ | Broad (HEVC, mkv, …) | Best (libass) | ✔ | GPL-2.0-or-later, and its FFmpeg is built GPLv3 | Recommended: best subtitles and performance |
| **ExoPlayer** (Media3) | ✗ | Device codecs | Basic | ✗ | Apache-2.0 | Lightweight; the only engine in the `exoOnly` / IzzyOnDroid build |
| **KitePlayer** (FFmpeg) | ✗ | Broad (HEVC, mkv, …) | ✔ | ✔ | LGPL-2.1-or-later FFmpeg | Experimental; not bundled in the `exoOnly` build |

**iOS**

| Engine | Default | Formats | Subtitles | Chapters | Licence | Notes |
|:---|:---:|:---|:---:|:---:|:---|:---|
| **VLCKit 4** | ✔ | Broad | ✔ | ✔ | LGPL-2.1-or-later | Recommended: external subtitles, chapters and Picture-in-Picture |
| **AVPlayer** (AVFoundation) | ✗ | Device codecs | ✗ | ✗ | Apple system framework | Native Apple player; no external subtitles or chapters |
| **KitePlayer** (FFmpeg) | ✗ | Broad (HEVC, mkv, …) | ✔ | ✔ | LGPL-2.1-or-later FFmpeg | Experimental |

**Desktop**

| Engine | Default | Formats | Subtitles | Chapters | Licence | Notes |
|:---|:---:|:---|:---:|:---:|:---|:---|
| **KitePlayer** (FFmpeg) | ✔ | Broad (HEVC, mkv, …) | ✔ | ✔ | LGPL-2.1-or-later FFmpeg | The only desktop engine; its decoder rides inside the KiteCodec jar |

---

## Architecture

Built with **Kotlin Multiplatform** and **Compose Multiplatform**: one shared codebase for Android, iOS and desktop (the desktop app is built from source for now; no packaged downloads yet). The networking and sync protocol is a direct port of the official Syncplay client.

| Layer | Technology |
|:---|:---|
| **UI** | Compose Multiplatform |
| **Networking** | Netty (Android) &middot; SwiftNIO (iOS) &middot; Ktor (shared fallback, no TLS) |
| **Players** | See [Video Engines](#video-engines) |

---

## How this differs from official Syncplay

**Mobile adds:** Picture-in-Picture, gesture controls (double-tap seek, swipe brightness/volume), haptic feedback, GIF chat, a built-in server, runtime engine switching, solo/offline mode, custom themes, and Android TV support.

**Not yet ported from desktop:** persistent server rooms (the built-in server keeps rooms in memory only), the file-switch manager that hunts for a matching file on every machine, and per-user time offset. The desktop build does take the same command-line join arguments.

---

## FAQ

**Can I watch with friends who use Syncplay on PC?**
> Yes, fully compatible. Same protocol, same servers, same core features.

**Android says "App not installed" when updating?**
> You are likely mixing release channels. The IzzyOnDroid build (`com.reddnek.syncplay`) and the GitHub / Play build (`com.yuroyami.syncplay`) are separate apps and cannot update over each other. Uninstall the old one first, then install the build you want.

**I don't have access to the iOS App Store.**
> Sideload via [AltStore](https://github.com/yuroyami/syncplay-mobile/wiki/How-to-install-the-app-on-iOS) (no jailbreak required) using the shipped source, or install the `.ipa` directly.

---

## Translating

<a href="https://hosted.weblate.org/engage/syncplay-mobile">
  <img src="https://hosted.weblate.org/widget/syncplay-mobile/syncplay-mobile/multi-auto.svg" alt="Translation status">
</a>

Add or improve translations on Weblate by clicking the badge above.

Thanks to **[@Zhaodaidai](https://github.com/Zhaodaidai)** (Chinese), **[@DoncanC](https://github.com/DoncanC)** (Spanish), and **[@ivy-reps](https://github.com/ivy-reps)** (Russian) for their translations.

---

## Building

**Requirements:** an Android Studio recent enough to open AGP 9.x projects, and a recent Xcode for iOS.

**Android:** clone the repo and open it in Android Studio. No extra configuration needed. Two product flavors are available:
- `full` (default): ExoPlayer + mpv + KitePlayer.
- `exoOnly`: ExoPlayer only, used for the IzzyOnDroid build. Select it with `-PexoOnly=true` or the matching Gradle variant.

**iOS:** run `pod install` in `iosApp/`, then open `iosApp.xcworkspace` (not the `.xcodeproj`) in Xcode and build for `arm64`.

**Desktop:** `./gradlew :desktopApp:run` to try it, and `packageDmg`, `packageMsi` or `packageDeb`
to build an installer. jpackage can only build for the machine it runs on, so each installer
comes from its own OS. Nothing native is downloaded: the only engine is KitePlayer and its
decoder is inside the KiteCodec jar.

### Running the tests

```bash
./gradlew :shared:desktopTest :shared:testAndroidHostTest
```

The same shared tests run twice over, once on the desktop target and once against the Android
actuals. Alongside them:

```bash
./gradlew qualityGates   # the build-time gates, all of them
./gradlew detekt         # static analysis, tuned to this repo
./gradlew koverVerify    # coverage floor over app.protocol and app.server
```

**What the tests cover:** the sync decision and the position it reports, the wire format, the
hosted server's protocol flow, room passwords, invite links, file comparison, ping and clock
offset, the slash-command parser, the resume store, settings backup, and rate limiting. The
design goldens render real composables headlessly and write PNGs into
`shared/build/design-goldens`.

**What they do not cover, and cannot:** anything that needs a device. The five player engines on
real hardware, picture-in-picture, backgrounding, the media session, TV remotes, how the glass
looks, and any two-device sync session. Nothing in this list is verified by a green build.

### Releasing

The release runs from GitHub Actions (`Release`, `workflow_dispatch`) and does everything in one
go: five Android APKs, an exo-only APK, an AAB, an IPA, and a desktop installer for each of the
three platforms. Before starting one:

1. Bump `version` in the root `build.gradle.kts` `kiteConfig { }` block. Nothing else holds the
   version; `./gradlew printReleaseIdentity` prints what the release will use.
2. Write the section for it in `CHANGELOG.md`. The workflow reads it for the GitHub release and
   the AltStore feed, and fails if there is not one.
3. Write a short version into `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
   Google Play caps this at 500 characters and `checkStoreMetadata` will tell you if it is over.
4. Run `./gradlew kiteRewriteXcode` so the iOS project carries the new version.
5. Start the workflow, choosing the Play track.

Signing secrets live in `local.properties` for a local build and in repository secrets for CI. A
release build with no keystore fails rather than producing an unsigned artifact.

---

## Acknowledgments

- [Official Syncplay](https://syncplay.pl/) for building and maintaining Syncplay
- [Et0h](https://github.com/Et0h/) for his work on official Syncplay and help in our issue tracker
- [soredake](https://github.com/soredake) for thorough testing
- [Osanosa](https://github.com/Osanosa) for valuable contributions
- Everyone who contributed translations

---

## Feedback

[Open an issue](https://github.com/yuroyami/syncplay-mobile/issues/new) for bugs or feature requests, or start a [discussion](https://github.com/yuroyami/syncplay-mobile/discussions) for general questions.

---

<p align="center">
  <sub>Licensed under <a href="./LICENSE">AGPL-3.0</a></sub>
</p>
