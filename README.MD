<p align="center">
  <img src="https://github.com/yuroyami/syncplay-mobile/blob/master/shared/src/androidMain/ic_launcher-playstore.png" width="160" alt="Synkplay">
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
        <img src="https://raw.githubusercontent.com/flocke/andOTP/master/assets/badges/get-it-on-github.png" width="140" alt="GitHub Releases">
      </a>
    </td>
    <td>
      <a href="https://apt.izzysoft.de/fdroid/index/apk/com.reddnek.syncplay">
        <img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" width="140" alt="IzzyOnDroid">
      </a>
    </td>
    <td>
      <a href="https://play.google.com/store/apps/details?id=com.yuroyami.syncplay">
        <img src="https://i0.wp.com/reefcentral.pt/wp-content/uploads/2019/04/get-on-google-play.png?fit=564%2C168&ssl=1" width="140" alt="Google Play">
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
        <img src="https://github.com/CelloSerenity/altdirect/blob/main/assets/png/AltSource_Blue.png?raw=true" width="140" alt="AltStore Source">
      </a>
    </td>
    <td>
      <a href="https://github.com/yuroyami/syncplay-mobile/releases/download/v0.23.0/synkplay-0.23.0-ios.ipa">
        <img src="https://github.com/CelloSerenity/altdirect/blob/main/assets/png/Download_Blue.png?raw=true" width="140" alt="Download IPA">
      </a>
    </td>
  </tr>
</table>

<details>
<summary>Notes on downloads</summary>

- **Android (GitHub):** Each release ships per-ABI APKs plus a `universal` one. Most modern phones want `arm64-v8a`; pick `universal` if unsure. GitHub releases are recommended over the Play Store, which can lag behind.
- **Android (IzzyOnDroid):** A lightweight APK built from the `exoOnly` flavor, so it carries only the ExoPlayer engine (no bundled mpv/VLC native libraries). Package id `com.reddnek.syncplay`.
- **iOS:** The "Download IPA" button always points to the latest release. No App Store access? See the [iOS sideloading guide](https://github.com/yuroyami/syncplay-mobile/wiki/How-to-install-the-app-on-iOS) for AltStore and other methods (jailbroken and non-jailbroken).
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

- **Full Syncplay compatibility.** Connects to the same servers and works seamlessly alongside the official PC client.
- **Protocol rewritten** from Python to Kotlin, covering every core feature.
- **mpv on both platforms.** libmpv powers playback on Android and, since v0.22.2, on iOS too.
- **Multiple video engines.** Switch player at runtime to suit your device and format.
- **Real-time chat** with emoji and inline GIFs.
- **Shared playlists** for sequential group viewing.
- **TLS encryption** for secure connections.
- **Picture-in-Picture** mode.
- **Managed rooms** and a readiness system.
- **Highly customizable.** Deep settings for UI, playback, gestures, haptics, and more.
- **Built-in server.** Host a room straight from the app, no PC required.
- **Multi-language support** via community translations.

---

## Platform Capabilities

| Feature | Android | iOS |
|:---|:---:|:---:|
| Chat | ✔ | ✔ |
| Managed Rooms | ✔ | ✔ |
| Shared Playlists | ✔ | ✔ |
| TLS | ✔ (Netty) | ✔ (SwiftNIO) |
| Picture-in-Picture | ✔ | ✔ (AVPlayer only) |
| Built-in Server | ✔ | ✔ |
| Saved Shortcuts | ✔ | ✔ |

### Video Engines

**Android**

| Engine | Default | Formats | Subtitles | Chapters | Notes |
|:---|:---:|:---|:---:|:---:|:---|
| **mpv** (libmpv) | ✔ (full build) | Broad, incl. HEVC / mkv | Best (libass) | ✔ | Recommended |
| **ExoPlayer** (Media3) | ✔ (lite build) | Device codecs | Basic | ✗ | Lightweight, ships in the IzzyOnDroid build |
| **VLC** (libVLC) | | All formats | ✔ | ✔ | Experimental |

**iOS**

| Engine | Default | Formats | Subtitles | Chapters | Notes |
|:---|:---:|:---|:---:|:---:|:---|
| **MPVKit** (libmpv) | ✔ | Broad, incl. HEVC / mkv | Best (libass) | ✔ | New in v0.22.2, recommended |
| **VLCKit** (MobileVLCKit) | | All formats | ✔ | ✔ | |
| **AVPlayer** (AVFoundation) | | Device codecs | ✗ | ✗ | Experimental, native Apple player |

---

## Architecture

Built with **Kotlin Multiplatform** and **Compose Multiplatform**: one shared codebase for Android and iOS.

| Layer | Technology |
|:---|:---|
| **UI** | Compose Multiplatform |
| **Networking** | Netty (Android) &middot; SwiftNIO (iOS) &middot; Ktor (shared fallback) |
| **Android players** | mpv (libmpv) &middot; ExoPlayer (Media3) &middot; VLC (libVLC) |
| **iOS players** | MPVKit (libmpv) &middot; VLCKit (MobileVLCKit) &middot; AVPlayer (AVFoundation) |

---

## FAQ

**Can I watch with friends who use Syncplay on PC?**
> Yes. Synkplay speaks the same protocol, connects to the same servers, and supports the same core features.

**Android says "App not installed" when updating?**
> Uninstall the previous version first, then install the new APK. This usually means the signing key or ABI differs from what you had.

**I don't have access to the iOS App Store.**
> Sideload via [AltStore](https://github.com/yuroyami/syncplay-mobile/wiki/How-to-install-the-app-on-iOS) (non-jailbroken) or install the `.ipa` directly (jailbroken).

**Which iOS engine should I pick?**
> MPVKit is the default and handles the widest range of formats and subtitles. VLCKit is a solid fallback. AVPlayer is native but limited (no external subtitles or chapters).

---

## Translating

<a href="https://hosted.weblate.org/engage/syncplay-mobile">
  <img src="https://hosted.weblate.org/widget/syncplay-mobile/syncplay-mobile/multi-auto.svg" alt="Translation status">
</a>

Contributions welcome! Click the badge above to add or improve translations on Weblate.

Thanks to **[@Zhaodaidai](https://github.com/Zhaodaidai)** (Chinese), **[@DoncanC](https://github.com/DoncanC)** (Spanish), and **[@ivy-reps](https://github.com/ivy-reps)** (Russian) for their translations.

---

## Building

**Requirements:** Android Studio 2025.3.1+ (and Xcode 16.3+ for iOS).

Clone the repo and open it in Android Studio. No extra configuration needed for the Android build.

---

## Acknowledgments

- [Official Syncplay](https://syncplay.pl/) for maintaining such remarkable software
- [Et0h](https://github.com/Et0h/) for his work on official Syncplay and help in our issue tracker
- [soredake](https://github.com/soredake) for thorough testing
- [Osanosa](https://github.com/Osanosa) for valuable contributions
- Everyone who contributed translations

---

## Feedback

[Open an issue](https://github.com/yuroyami/syncplay-mobile/issues/new) for bugs or feature requests, or start a [discussion](https://github.com/yuroyami/syncplay-mobile/discussions) for general questions.

---

<p align="center">
  <sub>Licensed under <a href="https://www.gnu.org/licenses/agpl-3.0.en.html">AGPL-3.0</a></sub>
</p>
