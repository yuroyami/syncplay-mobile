This folder contains a few necessary scripts to build the Android native libraries for `syncplay-mobile`, including:
- Core native components for the mpv video player (such as libmpv.so) across the 4 main Android architectures
- FFmpeg Audio decoder for ExoPlayer (optional but recommended for full audio format support)

The build process is supported on Linux and macOS only. Windows and WSL are **not** supported.

## Downloading dependencies

To ensure a reproducible build process, run `download.sh` to automatically download the Android SDK (including the NDK). These tools will be downloaded locally within the buildscripts folder, isolating the build environment from any existing SDK installation on your machine.

To get started, navigate to the `buildscripts` folder and execute:
```bash
./download.sh
```

**Important:** Ensure the complete file path to `download.sh` contains no whitespace characters, as this will cause build errors.
Example: This will not run because it has a white space in the folder name "Syncplay Mobile": 
`usr/local/bin/Projects/Syncplay Mobile/buildscripts/download.sh`

## Build

If this is the first time you run the process, just do:
```sh
./buildall.sh
```

Run `buildall.sh` with `--clean` to clean the build directories before building if you want to start anew.
For a guaranteed clean build also run `rm -rf prefix` beforehand.

By default this will build only for 32-bit ARM (`armv7l`).
You probably want to build for AArch64 too, and perhaps Intel x86.

To do this run one (or more) of these commands **before** ./buildall.sh:
```sh
./buildall.sh --arch armv7l mpv
./buildall.sh --arch arm64 mpv
./buildall.sh --arch x86 mpv
./buildall.sh --arch x86_64 mpv
```
then do your `./buildall.sh`.

# Developing

## Rebuilding a single component

If you've made changes to a single component (e.g. ffmpeg or mpv) and want a new build you can of course just run ./buildall.sh but it's also possible to just build a single component like this:

```sh
./buildall.sh -n ffmpeg
# optional: add --clean to build from a clean state
```

Note that you might need to rebuild for other architectures (`--arch`) too depending on your device.

Afterwards, build mpv-android and install the apk:

```sh
./buildall.sh -n
adb install -r ../app/build/outputs/apk/default/debug/app-default-universal-debug.apk
```

## Using Android Studio

You can use Android Studio to develop the Java part of the codebase. Before using it, make sure to build the project at least once by following the steps in the **Build** section.

You should point Android Studio to existing SDK installation at `mpv-android/buildscripts/sdk/android-sdk-linux`.
Then click "Open an existing Android Studio project" and select `mpv-android`.

Note that if you build from Android Studio only the Java/Kotlin part will be built.
If you make any changes to libraries (ffmpeg, mpv, ...) or mpv-android native code (`app/src/main/jni/*`), first rebuild native code with:

```sh
./buildall.sh -n
```

then build the project from Android Studio.
