## SyncPlay BETA:

# About Syncplay BETA:

This is the UNOFFICIAL Syncplay client for Android. If you don't know Syncplay, have a look
here: https://syncplay.pl

- What Syncplay does is: synchronize media playback (video/audio) on multiple Desktop devices (
  Linux, Windows, macOS) flawlessly while providing chat functionality.
- How it works: People gather in a room, load the same videos, and Ta-Da, if someone pauses,
  everyone has their videos paused !
- What Syncplay BETA brings to the table: What Syncplay Desktop needed is a Mobile client, people
  are using phones more than ever, so I dedicated myself to bringing its functionality to Android
  while keeping it compatible and interoperable with the Desktop version ! That means, users with
  both Syncplay Desktop and Syncplay BETA on Android can watch together. It supports the chat
  functionality, and most video formats ! (Working on the FFmpeg integration as of now, to make sure
  everyone can play all various formats). It's still a work in progress and it's really early in the
  alpha phase. There are various stuff that are still missing but exist in the official release. I
  am working on it slowly.

# Overview

This is the fully-functioning unofficial kotlin-written Android client for the amazing software
Syncplay that synchronizes playback on different devices and makes sure everyone is watching the
same thing at the same second, even if they're miles away. You can find the official client (for
Windows, Linux and macOS) here: https://syncplay.pl/
Big thanks to the creators for making the software open-source and maintaining a variety of servers
in order to let the people have fun.

# How it works

The official client does a very good job bringing support for multiple players on PC (VLC,
MPC...etc). However on Android, we need that has an integrated player, that's why I went for Google'
s amazing video player called ExoPlayer, it offers amazing functionality and flexibility and
therefore allowed to create this flawless client for Android.

# What you need to build this project into an APK:

Just import the project to Android Studio Bumble Bee, and deploy an APK. Note that the TLS
functionality won't work if you don't deploy a signed APK.

# Suggestions/Bugs :

Please let me know if there is anything going slightly wrong with the APK. As far as I am concerned,
it works like a charm on my end.
