<h1 align="center"> Syncplay for Android </h1> <br>
<p align="center">
  <a href="">
    <img alt="Syncplay BETA for Android" title="Syncplay Android" src="https://github.com/chromaticnoob/syncplay-android/blob/master/art/LOGO.png?raw=true" width="250">
  </a>
</p>
<p align="center">
  <b> Syncplay BETA - The Unofficial Android Client </b>
</p>
<p align="center">
  <a href="https://apt.izzysoft.de/fdroid/index/apk/com.reddnek.syncplay">
    <img alt="Syncplay Android on IzzyOnDroid Repo" title="Syncplay Android" src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" width="200">
  </a>
  <a href="https://play.google.com/store/apps/details?id=com.reddnek.syncplay">
    <img alt="Download it on Play store" title="Syncplay Android" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" width="200">
  </a>
</p> 

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->

## Table of Contents

- [Introduction](#introduction)
- [Features](#features)
- [How to use](#how-to-use)
- [Roadmap](#roadmap)
- [F.A.Q](#F.A.Q)
- [Feedback](#feedback)
- [Build Process](#build-process)
- [Acknowledgments](#acknowledgments)
- [License](#license)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Introduction

[![OS - Android](https://img.shields.io/badge/OS-Android-yellowgreen?style=for-the-badge&logo=android)]()
[![Version Release](https://img.shields.io/badge/Version-0.9.0-b00b69?style=for-the-badge&logo=v)]()
[![Written Language](https://img.shields.io/badge/Made%20with-Kotlin-lightgrey?style=for-the-badge&logo=kotlin)]()
[![is Maintained?](https://img.shields.io/badge/Maintained-YES-green?style=for-the-badge)]()
[![License](https://img.shields.io/badge/License-AGPL--3.0-brightgreen?style=for-the-badge)]()
[![Status](https://img.shields.io/badge/Status-BETA-0cf?style=for-the-badge&logo=statuspage)]()
[![Requirement](https://img.shields.io/badge/REQUIREMENT-Android%205.0%20and%20later-blueviolet?style=for-the-badge&logo=android%20studio)]()

Syncplay Android is the unofficial Android port for the amazing free software that synchronises
media players so that faraway friends can watch videos together, Syncplay.

Syncplay Android, which is still in its beta release, brings the most important functions that
Syncplay on Desktop has, and ports it to Android flawlessly, such as real-time chat functionality.

**Cannot run on Android versions below Android 5.0 (Codename: Lollipop)**

<p align="center">
  <img src = "https://raw.githubusercontent.com/chromaticnoob/syncplay-android/master/art/SS1.png" width=150>
</p>

## Features

* Flawless interoperability with Syncplay's official desktop client and other Syncplay BETA Android
  clients.
* Same base functionality as Syncplay for Desktop. The Syncplay protocol was re-written from Python
  to Kotlin line by line.
* Integrated lightning-speed video player, whose capabilities depend on your device.
* Real-time chat functionality, with emojis support.
* A huge set of settings and preferences to tweak and customize your client.
* Supports all audio track formats.
* Supports loading custom external subtitle files.
* No delay or latency opening the app or the room.
* App written in efficient native Kotlin code, reassurring the optimal performance.
* Multi-language support (Available Language: English, Arabic. More languages on the way)
* Supports Android 5.0 Lollipop up to Android 13 Tiramisu.

<p align="center">
  <img src = "https://raw.githubusercontent.com/chromaticnoob/syncplay-android/master/art/SS2.png" width="30%">
  <img src = "https://raw.githubusercontent.com/chromaticnoob/syncplay-android/master/art/SS4.png" width="30%"><br>
  <img src = "https://raw.githubusercontent.com/chromaticnoob/syncplay-android/master/art/SS3.png" width="80%">
</p>

## How to use

Usage is fairly simple:
- Download the latest release APK from [here](https://github.com/chromaticnoob/syncplay-android/releases/latest).
- Uninstall any old version you have, then install the latest release APK you downloaded (To make sure installation goes smoothly).
- Open Syncplay.
- Specify a username of your choice, a room name of your choice (Tell your friends about it)
- Select a server from the list (Tell your friends about this one too)
- Click JOIN/CREATE Room. You will be taken to the Room screen. You're all set. Tell your friends to join the same room and server.
- Ta-Dah ! Just load the same video file as your friends and enjoy the synchronized playback.

## Roadmap
These are the things I am willing to add/adjust in the future :

- [x] Adopt original Syncplay's Chat functionality
- [x] Shared Playlists
- [x] Support for custom/private servers
- [x] Night Mode
- [x] URL Support (as of 0.11.0, unreleased)
- [ ] TLS/SSL Secure Connection Support
- [x] Multi-language Support
  - [x] English
  - [x] Arabic
  - [x] French
  - [x] Chinese (by [Zhaodaidai](https://github.com/Zhaodaidai))
  - [ ] Spanish
  - [ ] Portuguese
  - [ ] Japanese
  - [ ] German
  - [ ] Italian
  - [ ] Russian

## F.A.Q

* If my friend uses Syncplay on PC, can I watch with them ?
  -> Yes, you can. Syncplay for Android works perfectly even if there are 100 people in the room,
  with clients for PC or Android.
* I get an error saying "App not installed" upon installing the app. What's wrong ?
  -> Uninstall the older version before installing the new one.

## Translating

* If you want to contribute with a translation in a language that isn't available in Syncplay, or
  enhance the actual translations, please refer to #30

## Feedback

Feel free to [file an issue](https://github.com/chromaticnoob/syncplay-android/issues/new).

If there's anything you'd like to chat about, please feel free to open a new discussion.

## Build Process

The project is developed under Android Studio Flamingo | 2022.2.1 Canary 3
Make sure you have a version equal or later than the one I am using. Dowload the source code ZIP and
extract it somewhere, then open it using Android Studio. Then you can just build the app using a
custom JKS keystore of your choice (Edit the keystore information on the
module's ```build.gradle```).

## Acknowledgments

Thanks to [Official Syncplay](https://www.syncplay.pl/) for maintaining and open-sourcing such an
amazing software.

Thanks to [Et0h](https://www.github.com/Et0h/) for his amazing hard work on official Syncplay and
for lending a hand in our issues tracker section.

Thanks to [Zhaodaidai](https://www.github.com/Zhaodaidai) for their contribution with the Chinese
translation for the app.

## License

Syncplay for Android is under
the [AGPL-3.0 Open-Source License](https://www.gnu.org/licenses/agpl-3.0.en.html)