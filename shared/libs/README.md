# Prebuilt libraries

## libffmpeg_media3exo_1.8.0.aar

This file is the FFmpeg audio extension for Media3 ExoPlayer. ExoPlayer uses the audio decoders of
the device first. When the device has no decoder for an audio format, ExoPlayer falls back to the
FFmpeg decoders in this file. Examples are AC-3, E-AC-3, DTS and TrueHD on devices without those
decoders.

**What it is.** The `androidx.media3.decoder.ffmpeg` extension from the Media3 source tree, built
for four ABIs (arm64-v8a, armeabi-v7a, x86, x86_64) against FFmpeg 6.0 (libavcodec 60.3.100).
Media3 does not publish this extension to Maven. It ships the Java side as source code, and you
build the native part yourself. So the extension is a file in this folder, not a Maven coordinate.
[`shared/build.gradle.kts`](../build.gradle.kts) adds it by its file name.

**Version.** The file name names the Media3 release that the extension was built against: 1.8.0.
The app uses it with the newer Media3 release in the version catalog. Rebuild the AAR when a
Media3 release changes the interface of the extension.

**Licence.** The Java side is Apache 2.0, from the Android Open Source Project. The bundled FFmpeg
is used under the LGPL 2.1 or later, which is the default licence of FFmpeg. The build enables
only the audio decoders in the table below, and no GPL component. The source of FFmpeg is at
<https://ffmpeg.org>. The corresponding source for this build is the FFmpeg 6.0 release,
unmodified.

**Build inputs.** These values come from the shipped binary, so they describe the file that is
here.

| Input | Value |
|---|---|
| Media3 version | `1.8.0` (from the file name) |
| libavcodec | 60.3.100 (the FFmpeg 6.0 line) |
| Decoder symbols | `aac`, `aac_latm`, `ac3`, `alac`, `amrnb`, `amrwb`, `dca`, `eac3`, `flac`, `mlp`, `mp3`, `opus`, `pcm_alaw`, `pcm_mulaw`, `truehd`, `vorbis` |
| ABIs | arm64-v8a, armeabi-v7a, x86, x86_64 |
| Toolchain | clang 18.0.1 of NDK r27 (build 12027248, based on r522817). The binary also names clang 21.0.0 of NDK r29 (build 13989888, based on r563880c). |

The binary does not contain the FFmpeg configure line. So the decoder list comes from the decoder
symbols that the library exports.

### Rebuild

Follow the instructions of the extension in the Media3 source tree, at
`libraries/decoder_ffmpeg/README.md`. The steps below apply those instructions to this file. Run
them in an empty folder, with `ANDROID_NDK_HOME` set to an Android NDK.

1. Get the Media3 source:

   ```bash
   git clone --depth 1 --branch 1.8.0 https://github.com/androidx/media.git
   ```

2. Get the FFmpeg 6.0 source into the `jni` folder of the extension:

   ```bash
   git clone --depth 1 --branch n6.0 https://git.ffmpeg.org/ffmpeg.git media/libraries/decoder_ffmpeg/src/main/jni/ffmpeg
   ```

3. Go to that `jni` folder:

   ```bash
   cd media/libraries/decoder_ffmpeg/src/main/jni
   ```

4. Build FFmpeg with the decoders from the table. The first argument is the `src/main` folder of
   the extension. `21` is the minimum Android API level. On macOS, use `darwin-x86_64` in place of
   `linux-x86_64`.

   ```bash
   ./build_ffmpeg.sh "$(dirname "$PWD")" "$ANDROID_NDK_HOME" linux-x86_64 21 vorbis opus flac alac pcm_mulaw pcm_alaw mp3 amrnb amrwb aac aac_latm ac3 eac3 dca mlp truehd
   ```

5. Go back to the root of the Media3 checkout:

   ```bash
   cd ../../../../..
   ```

6. Build the AAR:

   ```bash
   ./gradlew :lib-decoder-ffmpeg:assembleRelease
   ```

7. Copy the release AAR from the build output of the `lib-decoder-ffmpeg` module into
   `shared/libs/`. When the file name changes, change it in `shared/build.gradle.kts` too.
8. Replace the checksum below.

**Integrity.**

```text
sha256  eb7c57daaeed34e27b87120c2e595959656118ac182b9e380b3fff940cc8d834
```

Check the file:

```bash
shasum -a 256 shared/libs/libffmpeg_media3exo_1.8.0.aar
```

**Other FFmpeg builds.** The full Android APK carries three separate FFmpeg builds: this one, the
one in [libmpvKt](https://github.com/yuroyami/libmpvKt) for mpv, and the one in
[KiteFFmpeg](https://github.com/yuroyami/KiteFFmpeg) for KitePlayer. The exoOnly APK carries only
this one. Each build has its own licence. See the project page of each build for its licence.
