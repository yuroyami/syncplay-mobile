<h1 align="center"> Syncplay for Mobile (Android & iOS) </h1> <br>
<p align="center">
  <a href="">
    <img alt="Syncplay for Mobile" title="Syncplay Mobile" src="https://github.com/yurowitz/syncplay-mobile/blob/master/art/LOGO.png?raw=true" width="250">
  </a>
</p>
<p align="center">
  <b> Syncplay - The Unofficial Mobile Client (for Android and iOS) </b>
</p>
<p align="center">
  <a href="https://apt.izzysoft.de/fdroid/index/apk/com.reddnek.syncplay">
    <img alt="Syncplay Android on IzzyOnDroid Repo" title="Syncplay Android" src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" width="200">
  </a>
</p> 

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->

## Table of Contents

- [Introduction](#introduction)
- [Features](#features)
- [How to use](#how-to-use)
- [Roadmap](#roadmap)
- [Components and architecture](#components-and-architecture)
- [F.A.Q](#F.A.Q)
- [Feedback](#feedback)
- [Build Process](#build-process)
- [Acknowledgments](#acknowledgments)
- [License](#license)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Introduction

[![OS - Android](https://img.shields.io/badge/OS-Android-yellowgreen?style=for-the-badge&logo=android)]()
[![OS - iOS](https://img.shields.io/badge/OS-iOS-lightgray?style=for-the-badge&logo=apple)]()
<br>
[![Version Release](https://img.shields.io/badge/Version-0.13.0-f6abb6?style=for-the-badge&logo=v)]()
[![Written Language](https://img.shields.io/badge/Made%20with-Kotlin-lightgrey?style=for-the-badge&logo=kotlin)]()
[![is Maintained?](https://img.shields.io/badge/Maintained-YES-green?style=for-the-badge)]()
[![License](https://img.shields.io/badge/License-AGPL--3.0-brightgreen?style=for-the-badge)]()
[![Status](https://img.shields.io/badge/Status-BETA-0cf?style=for-the-badge&logo=statuspage)]()
[![Requirement](https://img.shields.io/badge/REQUIREMENT-Android%206.0%20and%20later-blueviolet?style=for-the-badge&logo=android%20studio)]()
[![Requirement](https://img.shields.io/badge/REQUIREMENT-iOS%2014.0%20and%20later-blueviolet?style=for-the-badge&logo=xcode)]()

Introducing Syncplay Mobile, an app that brings the remarkable functionality of Syncplay PC to the mobile platforms (Android and iOS). With Syncplay Mobile, you can synchronize media playback with your friends, allowing you to watch videos together, even when you're physically apart.

In its beta release, Syncplay Mobile faithfully replicates the core features found in the PC version of Syncplay. This includes real-time chat functionality, enabling seamless communication while enjoying synchronized playback on your Android or iOS device.

Experience the convenience of Syncplay on your phone and enjoy shared video watching with friends. Embrace the joy of shared entertainment with Syncplay Mobile.

**Cannot run on Android versions below Android 6.0 (Codename: Marshmallow) and iOS 14.0 **

<p align="center">
  <img src = "https://raw.githubusercontent.com/yurowitz/syncplay-mobile/master/art/SS1.png" width=150>
  <img src = "https://raw.githubusercontent.com/yurowitz/syncplay-mobile/master/art/SS3.png" width=150>
</p>

## Features

* Seamless compatibility with Syncplay's official PC client and other Syncplay Mobile clients.
* Offers the same core functionality as Syncplay for PC, with a meticulously rewritten Syncplay protocol from Python to Kotlin.
* Integrated 2 high-performance video players (ExoPlayer and mpv) tailored to your device's capabilities and beyond.
* Real-time colorful chat functionality, including support for emojis.
* Extensive range of settings and preferences for customization.
* Supports all audio track formats and most video formats.
* Ability to load custom external subtitle files.
* Efficient native Kotlin codebase for optimal performance.
* Multi-language support (Currently available in English, Chinese, French and Arabic, with more languages coming soon).
* Compatible with Android 6.0 Marshmallow up to the latest Android 14 UpsideDownCake release.
* Compatible with iOS 14.0 up to the latest iOS 17.2 release.

<p align="center">
  <img src = "https://raw.githubusercontent.com/yurowitz/syncplay-mobile/master/art/SS2.png" width="30%">
  <img src = "https://raw.githubusercontent.com/yurowitz/syncplay-mobile/master/art/SS4.png" width="30%">
</p>

## How to use
### How to use (Android)

Usage on Android is fairly simple:
- Download the latest release APK from [here](https://github.com/yurowitz/syncplay-mobile/releases/latest).
- Install the APK. If any installation issues arise, uninstall the previous version of the app before installing the new one.
- Open Syncplay. Specify a username of your choice, a room name of your choice (Tell your friends about it)
- Select a server from the list (Tell your friends about this one too)
- Click "Join Room". You will be taken to the Room screen. You're all set. Tell your friends to join the same room and server.
- Ta-Dah ! Just load the same video file as your friends and enjoy the synchronized playback.

### How to use (iOS)

The iOS version cannot be distributed in package format (like Android's APKs). The only ways to install
an app on iOS are to either compile this project by yourself on your macOS, or someone would kindly
donate to host this app on the Apple app store (the fee is a yearly $99).

Please note that the iOS version is not fully cooked yet, I will modify this readme once the development
is actually done. I'd say it's 95%, nearly done.

## Roadmap
These are the things I am willing to add/adjust in the future :

- [x] Adopt original Syncplay's Chat functionality
- [x] Shared Playlists
- [x] Support for custom/private servers
- [x] URL Support (as of 0.10.0)
- [x] Multiple player engines (Exoplayer, mpv)
- [x] Picture-in-picture mode (Floating window)
- [x] TLS/SSL Secure Connection Support
- [x] Multi-language Support (WIP)
    - [x] English
    - [x] Arabic
    - [x] French
    - [x] Chinese (by [Zhaodaidai](https://github.com/Zhaodaidai))
    - [x] Spanish (by [DoncanC](https://github.com/DoncanC))

## Components and architecture

* <b>UI:</b> Compose multiplatform
* <b>Architecture:</b> modular (mostly MVVM)
* <b>Network backbone:</b> Netty (Android) and Ktor (iOS)
* <b>Preferences:</b> Jetpack Datastore
* <b>Integrated media players:</b> Exoplayer or mpv (Android) - AVPlayer (iOS)

## F.A.Q

* If my friend uses Syncplay on PC, can I watch with them ?
  <br>-> Yes, you can. Syncplay Android is made to be interoperable.
* On Android, I get an error saying "App not installed" upon installing the app. What's wrong ?
  <br>-> Uninstall the older version before installing the new one.
* Can I use the ipa package to install the iOS to my device ?
  <br>-> If that is possible, why not ? I will publish the ipa package nonetheless once it's ready.

## Translating

* If you want to contribute with a translation in a language that isn't available in Syncplay, or
  enhance the actual translations, please refer to [#30](https://github.com/yurowitz/syncplay-mobile/issues/30)

## Feedback

Feel free to [file an issue](https://github.com/yurowitz/syncplay-mobile/issues/new).

If there's anything you'd like to chat about, please feel free to open a new discussion.

## Build Process

The project is developed under Android Studio Hedgehog | 2023.3.1
Make sure you have a version equal or later than the one I am using (Or you will need to downgrade Gradle plugins). Download the source code ZIP and
extract it somewhere, then open it using Android Studio. Then you can just build the app using a
custom JKS keystore of your choice (Edit the keystore information on the
module's ```build.gradle.kts```).

## Acknowledgments

Thanks to [Official Syncplay](https://www.syncplay.pl/) for maintaining and open-sourcing such an
amazing software.

Thanks to [Et0h](https://www.github.com/Et0h/) for his amazing hard work on official Syncplay and
for lending a hand in our issues tracker section.

Thanks to [Zhaodaidai](https://www.github.com/Zhaodaidai) for their contribution with the Chinese
translation for the app.

Thanks to [soredake](https://www.github.com/soredake) for their thorough testing.

## License

Syncplay for Android is under
the [AGPL-3.0 Open-Source License](https://www.gnu.org/licenses/agpl-3.0.en.html)