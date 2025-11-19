<h1 align="center"> Syncplay for Mobile (Android & iOS) </h1> <br>  
<p align="center">  
  <a href="">  
    <img alt="Syncplay for Mobile" title="Syncplay Mobile" src="https://github.com/yuroyami/syncplay-mobile/blob/master/art/LOGO.png?raw=true" width="250">  
  </a>  
</p>  
<p align="center">  
  <b> Syncplay - The Unofficial Mobile Client (for Android and iOS) </b>  
</p>  
<p align="center" style="display: flex; justify-content: center; gap: 10px; flex-wrap: wrap; align-items: center;">
  <a href="https://github.com/yuroyami/syncplay-mobile/releases/latest" style="display: flex; align-items: center; gap: 5px;">
    <img src="https://upload.wikimedia.org/wikipedia/commons/d/d7/Android_robot.svg" alt="Android" width="20">
    <img alt="Syncplay Android on GitHub releases" title="Syncplay Android" src="https://raw.githubusercontent.com/flocke/andOTP/master/assets/badges/get-it-on-github.png" width="150">
  </a>

  <a href="https://apt.izzysoft.de/fdroid/index/apk/com.reddnek.syncplay" style="display: flex; align-items: center; gap: 5px;">
    <img src="https://upload.wikimedia.org/wikipedia/commons/d/d7/Android_robot.svg" alt="Android" width="20">
    <img alt="Syncplay Android on IzzyOnDroid Repo" title="Syncplay Android" src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" width="150">
  </a>

  <a href="https://play.google.com/store/apps/details?id=com.yuroyami.syncplay" style="display: flex; align-items: center; gap: 5px;">
    <img src="https://upload.wikimedia.org/wikipedia/commons/d/d7/Android_robot.svg" alt="Android" width="20">
    <img alt="Syncplay Android on Google Play store" title="Syncplay Android" src="https://i0.wp.com/reefcentral.pt/wp-content/uploads/2019/04/get-on-google-play.png?fit=564%2C168&ssl=1" width="150">
  </a>
</p>
<p align="center">Syncplay comes bundled with multiple built-in video players on both platforms.<br> More info is available throughout this readme.<br>Note: The IzzyOnDroid Android release only contains ExoPlayer

<!-- START doctoc generated TOC please keep comment here to allow auto update -->  
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->  

## Table of Contents

- [Introduction](#introduction)
- [Features](#features)
- [How to use](#how-to-use)
- [Capabilities](#capabilities)
- [Architecture](#architecture)
- [FAQ](#faq)
- [Feedback](#feedback)
- [Build Process](#build-process)
- [Acknowledgments](#acknowledgments)
- [License](#license)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->  

## Introduction

[![OS - Android](https://img.shields.io/badge/Android-a4c639?style=for-the-badge&logo=android)]()[![OS - iOS](https://img.shields.io/badge/iOS-555555?style=for-the-badge&logo=apple)]()<br>
[![Version Release](https://img.shields.io/badge/Version-0.16.0-e2203c?style=for-the-badge&logo=v)]()[![Written Language](https://img.shields.io/badge/Made%20with-Kotlin-lightgrey?style=for-the-badge&logo=kotlin)]()[![is Maintained?](https://img.shields.io/badge/Maintained-YES-green?style=for-the-badge)]()[![License](https://img.shields.io/badge/License-AGPL--3.0-brightgreen?style=for-the-badge)]()[![Requirement](https://img.shields.io/badge/REQUIREMENT-Android%207.0%20and%20later-blueviolet?style=for-the-badge&logo=android%20studio)]()[![Requirement](https://img.shields.io/badge/REQUIREMENT-iOS%2014.0%20and%20later-blueviolet?style=for-the-badge&logo=xcode)]()

Syncplay Mobile is a unofficial Syncplay client brings the remarkable functionality of Syncplay PC client to the mobile platforms (Android and iOS). You can synchronize media playback with your friends, allowing you to watch videos together, even
when you're physically apart.

It includes real-time chat functionality, enabling seamless communication while enjoying synchronized playback on your Android or iOS device. You can use Syncplay Mobile with your friends who also use Syncplay Mobile, or Syncplay on PC.

**Requires at least Android 7.0 Nougat or iOS 14.0**

<p align="center">  
  <img src = "https://raw.githubusercontent.com/yuroyami/syncplay-mobile/master/art/SS1.png" width=150 alt="">  
  <img src = "https://raw.githubusercontent.com/yuroyami/syncplay-mobile/master/art/SS3.png" width=150 alt="">  
</p>  

## Features

* Seamless compatibility with Syncplay's official PC client.
* Offers the same core functionality as Syncplay for PC, with a meticulously rewritten Syncplay protocol from Python to Kotlin.
* Bundled with multiple video players with varying capabilities.
* Real-time colorful chat functionality, including support for emojis.
* Extensive range of settings and preferences for customization.
* Multi-language support.

<p align="center">  
  <img src = "https://raw.githubusercontent.com/yuroyami/syncplay-mobile/master/art/SS2.png" width="30%" alt="">  
  <img src = "https://raw.githubusercontent.com/yuroyami/syncplay-mobile/master/art/SS4.png" width="30%" alt="">  
</p>  

## How to use

### How to use (Android)

Usage on Android is fairly simple:

- Download the latest release APK from [here](https://github.com/yuroyami/syncplay-mobile/releases/latest).
- Install the APK. If any installation issues arise, uninstall the previous version of the app before installing the new one.
- Open Syncplay. Specify a username of your choice, a room name of your choice (Tell your friends about it). Select a server from the list (Tell your friends about this one too)
- Click "Join Room". You will be taken to the Room screen. You're all set. Tell your friends to join the same room and server.
- ...and that's it. Load the same video file as your friends and enjoy the synchronized playback.

### How to use (iOS)

The iOS version cannot be distributed in package format (like Android's APKs). The only ways to install an app on iOS are to either compile this project by yourself on your macOS, or someone would kindly donate to host this app on the Apple app
store (the fee is a yearly $99). The iOS app compiles and runs without issues on any iOS device with 14.0 (I assume this can be lowered further but I didn't try).

## Capabilities

### General capability chart

The UI on both Android and iOS are 99.9% identical, minus some differences due to platform-specific behavior. This chart table will demonstrate things that work, and things that do not work, on each platform and video player.

| Feature                       | Android                                 | iOS                           |
|-------------------------------|-----------------------------------------|-------------------------------|
| Chat                          | <p align="center">✔</p>                 | <p align="center">✔</p>       |
| Settings & Preferences        | <p align="center">✔</p>                 | <p align="center">✔</p>       |
| Shared Playlists              | <p align="center">✔</p>                 | ✔ (Untested)                  |
| Video players                 | - mpv (Default)<br>- ExoPlayer<br>- VLC | - VLC (Default)<br>- AVPlayer |
| Supports TLS                  | <p align="center">✔ (Netty)</p>         | ✔ (SwiftNIO)                  |
| Picture-in-Picture (PIP) mode | <p align="center">✔</p>                 | ✔ (only AVPlayer)             |
| Can save shortcuts            | <p align="center">✔</p>                 | ✔ (Unstable)                  |

### Video player capability chart

Some video players are more potent than others in certain areas. Here's a general overview to help you quickly pick the best contender

| <font size="2">Feature</font>                                        | mpv<br>Android                            | ExoPlayer<br>Android           | VLC<br>Android          | AVPlayer<br>iOS                | VLC<br>iOS              |
|----------------------------------------------------------------------|-------------------------------------------|--------------------------------|-------------------------|--------------------------------|-------------------------|
| <font size="3">Supported<br>formats</font>                           | Most formats (including mkv and HEVC)     | Depends on device capabilities | All formats             | Depends on device capabilities | All formats             |
| <font size="3">Stability with Syncplay</font>                        | Stable (recommended)                      | Mildly Stable                  | Experimental            | Broken (for now)               | Alpha                   |
| <font size="3">Supports Subtitles (including sideloaded ones)</font> | <p align="center">Best (via libass) ✔</p> | Basic support                  | <p align="center">✔</p> | <p align="center">No</p>       | <p align="center">✔</p> |
| <font size="3">Can play from URLs/URIs                               | <p align="center">✔</p></font>            | <p align="center">✔</p>        | <p align="center">✔</p> | <p align="center">✔</p>        | <p align="center">✔</p> |
| <font size="3">Supports Chapters                                     | <p align="center">✔</p></font>            | <p align="center">No</p>       | <p align="center">✔</p> | <p align="center">No</p>       | <p align="center">✔</p> |

## Architecture

* <b>UI:</b> Compose multiplatform
* <b>Architecture:</b> modular (mostly MVVM)
* <b>Network backbone:</b> Netty (Android), SwiftNIO (iOS) and Ktor (both)
* <b>Preferences:</b> Jetpack Datastore
* <b>Integrated media players:</b>
  **_Android_**: Exoplayer (via media3) - mpv (via libmpv) - VLC (via libVLC)
  **_iOS_**: AVPlayer (via AVFoundation) - VLC (via MobileVLCKit)

## F.A.Q

> If my friend uses Syncplay on PC, can I watch with them ?

- Yes, you can. Syncplay Mobile is made to be interoperable with official Syncplay on PC because it implements the same protocol and the same servers and features.

> On Android, I get an error saying "App not installed" upon installing the app. What's wrong ?

- Seems like the app can't be updated. Freshly install it: Uninstall the older version before installing the new one.

> Where is the iOS release ?

- The app is not on the Apple app store. However, you can sideload the app via `AltStore` if you have a normal non-jailbroken iPhone/iPad.
- If you have a jailbroken device, simply sideload the latest `.ipa` file which you will find attached to every release.

## Translating

Syncplay is available in the following languages :

[![Translation status](https://hosted.weblate.org/widget/syncplay-mobile/syncplay-mobile/multi-auto.svg)](https://hosted.weblate.org/engage/syncplay-mobile)

- Thanks to [@Zhaodaidai](https://github.com/Zhaodaidai) for the Chinese (Simplified) translation.
- Thanks to [@DoncanC](https://github.com/DoncanC) for the Spanish translation.
- Thanks to [@ivy-reps](https://github.com/ivy-reps) for the Russian translation.

* If you want to contribute with a translation in a language that isn't available in Syncplay, or  
  enhance the actual translations, you can click the image above to go to the Weblate website
  which is linked to this repository and you can easily add/edit/remove translations.
* For more info, please refer to [#132](https://github.com/yuroyami/syncplay-mobile/issues/30)

## Feedback

Feel free to [file an issue or a feature request](https://github.com/yuroyami/syncplay-mobile/issues/new).

If there's anything you'd like to chat about, please feel free to open a new discussion.

## Build Process

Requirement: Android Studio Jellyfish or newer. If you want to build the iOS version, you will ***also*** need to have Xcode 15.2 or newer. Your Android Studio will need to have the `Kotlin Multiplatform Mobile` plugin installed.
Simply open the repo project using its GitHub link and build the app. It won't require any further tweaking.

## Acknowledgments

Thanks to [Official Syncplay](https://www.syncplay.pl/) for maintaining and open-sourcing such an  
amazing software.

Thanks to [Et0h](https://www.github.com/Et0h/) for his amazing hard work on official Syncplay and  
for lending a hand in our issues tracker section.

Thanks to [soredake](https://www.github.com/soredake) for their thorough testing.

Thanks to [Osanosa](https://www.github.com/Osanosa) for his valuable contributions.

Thanks to everyone who contributed with translations for the app.

## License

Syncplay for Android is under  
the [AGPL-3.0 Open-Source License](https://www.gnu.org/licenses/agpl-3.0.en.html)
