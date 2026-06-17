<p align="center">
  <img src="https://raw.githubusercontent.com/yuroyami/syncplay-mobile/master/shared/src/androidMain/ic_launcher-playstore.png" width="160" alt="Synkplay">
</p>

<h1 align="center">Synkplay</h1>

<p align="center">
  <b>Watch videos together, in sync. An unofficial <a href="https://syncplay.pl">Syncplay</a> client for Android &amp; iOS.</b><br>
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
      <a href="https://github.com/yuroyami/syncplay-mobile/releases/download/v0.23.0/synkplay-0.23.0-ios.ipa">
        <img src="https://raw.githubusercontent.com/yuroyami/syncplay-mobile/master/art/badges/Download_Blue.png" width="140" alt="Download IPA">
      </a>
    </td>
  </tr>
</table>

<details>
<summary>Notes on downloads</summary>

- **Android (GitHub):** Each release ships per-ABI APKs plus a `universal` one. Most modern phones want `arm64-v8a`; pick `universal` if unsure.
- **Android (IzzyOnDroid):** A smaller APK built from the `exoOnly` flavor, carrying only the ExoPlayer engine (no bundled mpv/VLC native libraries). It uses package id `com.reddnek.syncplay`, so it installs **alongside** the GitHub / Play Store build (`com.yuroyami.syncplay`) rather than updating it. Keep one or the other to avoid two copies.
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
- **Multiple video engines, including mpv on both platforms.** Switch player at runtime to suit your device and format.
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
2. Pick a **server** (the official `syncplay.pl` is preselected) and a **port** (`8995`–`8999`). Add a password only if your server needs one.
3. Tap **Join**, then load a video from device storage or paste a direct URL.
4. Anyone in the same room on the same file plays in sync, mobile or PC.

No server of your own? Use the built-in server to host a room, or solo mode to watch offline.

---

## Video Engines

Switch engines at runtime in the room settings. `✔` in **Default** marks the engine selected out of the box.

**Android**

| Engine | Default | Formats | Subtitles | Chapters | Notes |
|:---|:---:|:---|:---:|:---:|:---|
| **mpv** (libmpv) | ✔ | Broad (HEVC, mkv, …) | Best (libass) | ✔ | Recommended: best subtitles and performance |
| **VLC** (libVLC) | ✗ | Broad | ✔ | ✔ | Experimental |
| **ExoPlayer** (Media3) | ✗ | Device codecs | Basic | ✗ | Lightweight; the only engine in the `exoOnly` / IzzyOnDroid build |

**iOS**

| Engine | Default | Formats | Subtitles | Chapters | Notes |
|:---|:---:|:---|:---:|:---:|:---|
| **MPVKit** (libmpv) | ✔ | Broad (HEVC, mkv, …) | Best (libass) | ✔ | New in v0.22.2; recommended |
| **VLCKit 4** | ✗ | Broad | ✔ | ✔ | Good fallback; supports external subtitles and chapters |
| **AVPlayer** (AVFoundation) | ✗ | Device codecs | ✗ | ✗ | Native Apple player; no external subtitles or chapters |

---

## Architecture

Built with **Kotlin Multiplatform** and **Compose Multiplatform**: one shared codebase for Android and iOS. The networking and sync protocol is a direct port of the official Syncplay client.

| Layer | Technology |
|:---|:---|
| **UI** | Compose Multiplatform |
| **Networking** | Netty (Android) &middot; SwiftNIO (iOS) &middot; Ktor (shared fallback, no TLS) |
| **Players** | See [Video Engines](#video-engines) |

---

## How this differs from official Syncplay

**Mobile adds:** Picture-in-Picture, gesture controls (double-tap seek, swipe brightness/volume), haptic feedback, GIF chat, a built-in server, runtime engine switching, solo/offline mode, custom themes, and Android TV support.

**Not yet ported from desktop:** the command-line interface, persistent server rooms (the built-in server is in-memory only), the file-switch manager, and per-user time offset.

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

**Requirements:** a recent Android Studio (Narwhal / 2025.2+), and Xcode 16.3+ for iOS.

**Android:** clone the repo and open it in Android Studio. No extra configuration needed. Two product flavors are available:
- `full` (default): ExoPlayer + mpv + VLC.
- `exoOnly`: ExoPlayer only, used for the IzzyOnDroid build. Select it with `-PexoOnly=true` or the matching Gradle variant.

**iOS:** run `pod install` in `iosApp/`, then open `iosApp.xcworkspace` (not the `.xcodeproj`) in Xcode and build for `arm64`.

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
