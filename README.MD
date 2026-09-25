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

Synkplay is an independent [Syncplay](https://syncplay.pl/) client for phones, tablets and Android TV.
A browser version is in development, and it cannot join a room yet.
When one person in the room pauses, plays or skips, the same thing happens for everyone else.
Synkplay speaks the same protocol as the original Syncplay, so friends on a computer can join the same room.

> [!NOTE]
> Each person opens the same video from their own file or link. Synkplay only keeps playback in step. It never sends your video or your screen to anyone.

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

Android needs version 8.0 or newer. iPhone and iPad need iOS 15.0 or newer.
The stores review each build first, so a store update can arrive later than the GitHub release.

The browser version is not ready yet. On a computer, install the [official Syncplay client](https://syncplay.pl/download/) and join the same room.

<details>
<summary><b>Which download should I choose?</b></summary>

- **Android:** Google Play is the simplest option. The full APK on GitHub is the same app, with mpv, ExoPlayer and KitePlayer, and it runs on every supported CPU. KitePlayer needs a 64-bit device.
- **IzzyOnDroid:** a smaller build with ExoPlayer only. It installs next to the full app as a separate app, with its own settings.
- **iPhone and iPad:** the App Store. To sideload instead, [set up AltStore Classic](https://faq.altstore.io/), then use **Add to AltStore** above.

</details>

## Start a room

1. Everyone joins the same room: the same server, the same port and the same room name. The official Syncplay server (syncplay.pl) is the default.
2. Everyone opens the same video, from a file on their device or from a link.
3. Press play when everyone shows as **Ready**. Synkplay marks you as ready when you join. A setting turns this off.

To invite people, use **Share the room** inside the room. The link opens a web page that offers to open Synkplay or to install it.
To join from a link, paste it into the room field on Home. Synkplay then fills in the server, the port, the room name and the password.
When the server has a password, the link contains it, so share the link only with the people you want in the room.

To watch by yourself, tap the logo on Home and pick **Watch alone**. It needs no server, and it offers to continue where you stopped.

## What you can do

- Play, pause and seek together.
- Chat with text, emoji and GIFs while you watch.
- Share a playlist that moves on to the next file by itself.
- Set your own time offset when your copy has a longer intro.
- Load subtitle files, or search OpenSubtitles from the room, on engines that support subtitle files.
- Host a room on your own device: pick **Host mine** on Home.
- Use the whole app with a remote on Android TV.

<details>
<summary><b>Also in there</b></summary>

- Themes, gesture controls and a reduced-motion option.
- Undo for the shared playlist.
- Preferred audio and subtitle languages.
- Picture-in-Picture on Android, and on iOS with AVPlayer or VLCKit.
- Managed rooms, on servers that support them. In a managed room, only the room operators control playback and the playlist.
- A choice to share your file name and file size as they are, hashed, or not at all.
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

Yes. They join the same server, port and room, and open the same video. Synkplay and the official Syncplay client work together in one room.

</details>

<details>
<summary><b>A video or subtitle does not play right.</b></summary>

Leave the room, pick a different engine on Home, and join again. An engine is one of the video players that Synkplay can use.
The default engine is **mpv** in the full Android app, **ExoPlayer** in the IzzyOnDroid app and **VLCKit** on iOS.
KitePlayer is still experimental. AVPlayer cannot load subtitle files.
Home shows what each engine supports. [Compare the engines](docs/DEVELOPING.md#engine-capabilities).

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
**[Verify a release](docs/DEVELOPING.md#reproducing-a-published-apk)** &nbsp;·&nbsp;
**[Support development](https://ko-fi.com/sidenaakram)**

<details>
<summary><b>Credits</b></summary>

- [Syncplay](https://syncplay.pl/) and [Et0h](https://github.com/Et0h), for the original project and their help.
- [soredake](https://github.com/soredake), for testing.
- [Osanosa](https://github.com/Osanosa), for contributions.
- [mukulhase](https://github.com/mukulhase), for Android TV support.
- [mvanhorn](https://github.com/mvanhorn), for the Home keyboard fix.
- [Zhaodaidai](https://github.com/Zhaodaidai), [DoncanC](https://github.com/DoncanC), [ivy-reps](https://github.com/ivy-reps) and everyone translating on Weblate.

</details>

---

<p align="center">
  Built by <a href="https://github.com/yuroyami">yuroyami</a> with Kotlin &amp; Compose Multiplatform.<br>
  <a href="CHANGELOG.md">Release notes</a> &nbsp;·&nbsp;
  <a href="PRIVACY_POLICY.md">Privacy</a> &nbsp;·&nbsp;
  <a href="LICENSE">AGPL-3.0 license</a>
</p>
