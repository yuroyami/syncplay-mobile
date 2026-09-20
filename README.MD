<p align="center">
  <img src="art/synkplay_app_badge_512.png" width="180" height="180" alt="Synkplay logo on its original white-grey background with rounded app badge corners">
</p>

<p align="center">
  <img src="art/readme-feature.webp?v=2f0963b4833e" width="960" alt="Synkplay banner: your video, everyone in sync, on a big screen and two phones. Available on Android and iOS through Google Play and the App Store. Compatible with original official Syncplay for PC: syncplay.pl and github.com/Syncplay/syncplay.">
</p>

<p align="center">
  <b>Android · iOS · Web</b>
</p>

<p align="center">
  <a href="#download">Download</a> &nbsp;·&nbsp;
  <a href="#start-a-room">Start a room</a> &nbsp;·&nbsp;
  <a href="#screenshots">Screenshots</a> &nbsp;·&nbsp;
  <a href="#help">Help</a> &nbsp;·&nbsp;
  <a href="docs/DEVELOPING.md">Build</a>
</p>

Synkplay is an independent [Syncplay](https://syncplay.pl/) client for phones, tablets and Android TV, with a browser version on the way.
When one person in the room pauses, plays or skips, the same thing happens for everyone else.
It speaks the same protocol as the original Syncplay, so friends on a computer can join the same room.

> [!NOTE]
> Each person opens the same video from their own file or link. Synkplay only keeps playback in step. Nothing from your video or screen is sent to anyone.

## Download

<!-- Rows have the same purpose on both platforms: store, direct file, other source. -->
<table align="center">
  <tr>
    <th align="center">Android</th>
    <th align="center">iPhone &amp; iPad</th>
  </tr>
  <tr>
    <td align="center">
      <a href="https://play.google.com/store/apps/details?id=com.yuroyami.syncplay">
        <img src="art/badges/google-play.png" width="140" height="42" alt="Install from Google Play">
      </a><br><sub>Store install</sub>
    </td>
    <td align="center">
      <a href="https://apps.apple.com/app/id6760187432">
        <img src="art/badges/app-store.png" width="140" height="42" alt="Install from the App Store">
      </a><br><sub>Store install</sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <a href="https://github.com/yuroyami/syncplay-mobile/releases/latest">
        <img src="art/badges/get-it-on-github.png" width="140" height="42" alt="Get the Android APK from GitHub Releases">
      </a><br><sub>Full Android APK</sub>
    </td>
    <td align="center">
      <a href="https://github.com/yuroyami/syncplay-mobile/releases/latest">
        <img src="art/badges/Download_Blue.png" width="140" height="42" alt="Get the IPA from GitHub Releases for sideloading">
      </a><br><sub>IPA for sideloading</sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <a href="https://apt.izzysoft.de/fdroid/index/apk/com.reddnek.syncplay">
        <img src="art/badges/IzzyOnDroid.png" width="140" height="42" alt="Get the ExoPlayer-only Android app from IzzyOnDroid">
      </a><br><sub>ExoPlayer only</sub>
    </td>
    <td align="center">
      <a href="https://celloserenity.github.io/altdirect/?url=https://raw.githubusercontent.com/yuroyami/syncplay-mobile/refs/heads/master/altstore_yuroyami.json">
        <img src="art/badges/AltSource_Blue.png" width="140" height="42" alt="Add the Synkplay source to AltStore">
      </a><br><sub>Add to AltStore</sub>
    </td>
  </tr>
</table>

Android 8.0 or newer. The IPA needs iOS 14.1 or newer. Store builds usually land a few days after the GitHub release.

The browser version is not ready yet. On a computer, install the [official Syncplay client](https://syncplay.pl/download/) and join the same room.

<details>
<summary><b>Which download should I choose?</b></summary>

- **Android:** Google Play is the easy route. The full APK on GitHub is the same app, with mpv, ExoPlayer and KitePlayer, and it runs on every supported CPU.
- **IzzyOnDroid:** a smaller build with ExoPlayer only. It installs next to the full app as a separate app, with its own settings.
- **iPhone and iPad:** the App Store. If you would rather sideload, [set up AltStore Classic](https://faq.altstore.io/), then use **Add to AltStore** above.

</details>

## Start a room

1. Everyone joins the same room: same server, same port, same room name. The default public server works fine.
2. Everyone opens the same video, from a file on their device or from a link.
3. Tap **Ready**. When everyone is ready, press play.

You can share an invite link from inside the room. The link only opens on a device that has Synkplay installed.

To watch by yourself, tap the logo on Home and pick **Watch alone**. It needs no server, and it remembers where you stopped.

## What you can do

- Play, pause and seek together.
- Chat with text, emoji and GIFs while you watch.
- Share a playlist that moves on to the next file by itself.
- Set your own time offset when your copy has a longer intro.
- Load your own subtitle files, on players that support them.
- Host a room on your own device with **Host mine** on Home.
- Work the whole app with a remote on Android TV.

<details>
<summary><b>Also in there</b></summary>

- Themes, gesture controls and a reduced-motion option.
- Undo for the shared playlist.
- Preferred audio and subtitle languages.
- Picture-in-Picture on Android, and on iOS with AVPlayer or VLCKit.
- Managed rooms, where one controller decides playback.
- Hidden or hashed filenames and file sizes.
- Encrypted connections, when the server supports them.

</details>

## Screenshots

<p align="center">
  <a href="art/screenshots/android-portrait.png"><img src="art/screenshots/android-portrait.webp" width="180" alt="Android home in portrait: room details, server and video engine choices"></a>
  &nbsp;&nbsp;
  <a href="art/screenshots/android-landscape.png"><img src="art/screenshots/android-landscape.webp" width="500" alt="Android room in landscape: video, playback controls, chat and user info"></a>
</p>

<p align="center"><sub>Android home (portrait) · Android room (landscape) · Click a screenshot to open it full size.</sub></p>

## Help

<details>
<summary><b>Can my friends use Syncplay on PC?</b></summary>

Yes. They join the same server, port and room, and open the same video. Synkplay and the official Syncplay client mix fine in one room.

</details>

<details>
<summary><b>A video or subtitle does not play right.</b></summary>

Leave the room, pick a different player on Home, and join again.
The defaults are **mpv** on the full Android build and **VLCKit** on iOS.
KitePlayer is still experimental on phones. AVPlayer cannot load subtitle files.
[Compare the players](docs/DEVELOPING.md#player-capabilities).

</details>

<details>
<summary><b>Why do I have two Android apps?</b></summary>

The IzzyOnDroid app and the full app (GitHub or Google Play) use different app IDs, so Android treats them as two apps.
One does not update the other, and each keeps its own settings.

</details>

For anything else: [Report a bug](https://github.com/yuroyami/syncplay-mobile/issues/new?template=bug_report.yml) · [Ask a question](https://github.com/yuroyami/syncplay-mobile/discussions) · [Request a feature](https://github.com/yuroyami/syncplay-mobile/issues/new?template=feature_request.yml)

## Contributing

Translations are the easiest way to help. Bug reports with steps to reproduce, and tested patches, are welcome too.

**[Translate on Weblate](https://hosted.weblate.org/engage/syncplay-mobile/)** &nbsp;·&nbsp;
**[Build & test](docs/DEVELOPING.md)** &nbsp;·&nbsp;
**[Support development](https://ko-fi.com/sidenaakram)**

<details>
<summary><b>Credits</b></summary>

- [Syncplay](https://syncplay.pl/) and [Et0h](https://github.com/Et0h), for the original project and their help.
- [soredake](https://github.com/soredake), for testing.
- [Osanosa](https://github.com/Osanosa), for contributions.
- [Zhaodaidai](https://github.com/Zhaodaidai), [DoncanC](https://github.com/DoncanC), [ivy-reps](https://github.com/ivy-reps) and everyone translating on Weblate.

</details>

---

<p align="center">
  Built by <a href="https://github.com/yuroyami">yuroyami</a> with Kotlin &amp; Compose Multiplatform.<br>
  <a href="CHANGELOG.md">Release notes</a> &nbsp;·&nbsp;
  <a href="PRIVACY_POLICY.md">Privacy</a> &nbsp;·&nbsp;
  <a href="LICENSE">AGPL-3.0 license</a>
</p>
