<h1 align="center"> Syncplay for Android </h1> <br>
<p align="center">
  <a href="https://gitpoint.co/">
    <img alt="Syncplay BETA for Android" title="Syncplay Android" src="https://github.com/chromaticnoob/syncplay-android/blob/master/art/LOGO.png?raw=true" width="250">
  </a>
</p>
<p align="center">
  <b> Syncplay BETA - The Unofficial Android Client </b>
</p>

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->

## Table of Contents

- [Introduction](#introduction)
- [Features](#features)
- [How to use](#how-to-use)
- [Roadmap](#roadmap)
- [F.A.Q](#f.a.q)
- [Feedback](#feedback)
- [Build Process](#build-process)
- [Acknowledgments](#acknowledgments)
- [License](#license)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Introduction

[![OS - Android](https://img.shields.io/badge/OS-Android-yellowgreen?style=for-the-badge&logo=android)]()
[![Version Release](https://img.shields.io/badge/Version-0.7.0-yellow?style=for-the-badge&logo=v)]()
[![Written Language](https://img.shields.io/badge/Made%20with-Kotlin-lightgrey?style=for-the-badge&logo=kotlin)]()
[![is Maintained?](https://img.shields.io/badge/Maintained-YES-green?style=for-the-badge)]()
[![License](https://img.shields.io/badge/License-AGPL--3.0-brightgreen?style=for-the-badge)]()
[![Status](https://img.shields.io/badge/Status-BETA-0cf?style=for-the-badge&logo=statuspage)]()
[![Requirement](https://img.shields.io/badge/REQUIREMENT-Android%205.0%20and%20later-blueviolet?style=for-the-badge&logo=android%20studio)]()

Syncplay BETA is the unofficial Android port for the amazing free software that synchronises media players so that faraway friends can watch videos together, Syncplay.

Syncplay BETA brings the most important functions that Syncplay on Desktop has, and ports it to Android flawlessly, such as real-time chat functionality.

**Cannot run on Android versions below Android 5.0 (Codename: Lollipop)**

<p align="center">
  <img src = "https://raw.githubusercontent.com/chromaticnoob/syncplay-android/master/art/SS1.png" width=150>
</p>

## Features
* Flawless interoperability with Syncplay's official desktop client and other Syncplay BETA Android clients. 
* Same base functionality as Syncplay for Desktop. The Syncplay protocol was re-written from Python to Kotlin line by line.
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
  <img src = "https://raw.githubusercontent.com/chromaticnoob/syncplay-android/master/art/SS2.png" width=200>
  <img src = "https://raw.githubusercontent.com/chromaticnoob/syncplay-android/master/art/SS3.png" width=750>
</p>

## How to use
The functionality is much simpler than desktop Syncplay. There are no additional arguments you can pass to the player, there is also no feature yet allowing users to join custom servers, just the default ones. Therefore, the usage is very easy :
- Download the latest release APK from [here](https://github.com/chromaticnoob/syncplay-android/releases/latest).
- Uninstall any old version you have, then install the latest release APK you downloaded (To make sure installation goes smoothly).
- Open Syncplay.
- Specify a username of your choice, a room name of your choice (Tell your friends about it)
- Select a server from the list (Tell your friends about this one too)
- Click JOIN/CREATE Room. You will be taken to the Room screen. You're all set. Tell your friends to join the same room and server.
- Ta-Dah ! Just load the same video file as your friends and enjoy the synchronized playback.

## Roadmap
These are the things I am willing to add/adjust in the future :

- [x] Adopt original Syncplay's Chat functionality (as of 0.1.0)
- [x] Enable FFmpeg Audio extension to play all possible audio track formats. (as of 0.6.3j)
- [x] Add Color preferences and in-room settings (as of 0.7.0)
- [ ] Shared Playlists
- [ ] Fix TLS/SSL Secure Connection
- [ ] Support for custom/private servers
- [ ] Self-Updater
- [ ] Support for LibVLC plugin integration to play all possible video formats without depencing on device capabilities
- [x] Multi-language Support
    - [x] English (as of 0.7.0)
    - [x] Arabic (as of 0.7.0)
    - [ ] French
    - [ ] Spanish
    - [ ] Portuguese
    - [ ] Japanese
    - [ ] German
    - [ ] Italian
    - [ ] Russian
  
## F.A.Q

* If my friend uses Syncplay on PC, can I watch with them ? 
  -> Yes, you can. Syncplay for Android works perfectly even if there are 100 people in the room, with clients for PC or Android.
* I get an error saying "App not installed" upon installing the app. What's wrong ? 
  -> Uninstall the older version before installing the new one.
  
## Feedback

Feel free to [file an issue](https://github.com/chromaticnoob/syncplay-android/issues/new).

If there's anything you'd like to chat about, please feel free to open a new discussion.

## Build Process

The project is developed under Android Studio Dolphin | 2021.3.1 Beta 4
Make sure you have a version equal or later than the one I am using. Dowload the source code ZIP and extract it somewhere, then open it using Android Studio. Then you can just build the app using a custom JKS keystore of your choice (Edit the keystore information on the module's ```build.gradle```).

## Acknowledgments

Thanks to [Official Syncplay](https://www.syncplay.pl/) for maintaining and open-sourcing such an amazing software.

## License
Syncplay for Android is under the [AGPL-3.0 Open-Source License](https://www.gnu.org/licenses/agpl-3.0.en.html)
