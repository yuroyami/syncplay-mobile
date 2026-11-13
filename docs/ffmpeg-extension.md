# The Media3 FFmpeg extension

The Media3 FFmpeg extension adds FFmpeg audio decoders to ExoPlayer. ExoPlayer uses the audio
decoders of the device first. When the device has no decoder for an audio format, ExoPlayer uses
the FFmpeg decoders of the extension. Examples are AC-3, E-AC-3, DTS and TrueHD on devices without
those decoders. Both Android APKs carry the extension.

## Where the build gets it

Media3 does not publish this extension to Maven. The Jellyfin project builds it from the Media3
source and publishes it to Maven Central as `org.jellyfin.media3:media3-ffmpeg-decoder`. The
source of that build is at <https://github.com/jellyfin/jellyfin-androidx-media>.

- `shared/module.yaml` depends on it under `dependencies@android` (`$libs.m3.decoder.ffmpeg`).
- The AAR carries the Java classes, the native decoder `libffmpegJNI.so` for arm64-v8a,
  armeabi-v7a, x86 and x86_64, and the R8 rules of the extension.
- `ExoImpl` turns on the extension renderers of ExoPlayer when `FfmpegLibrary.isAvailable()` is
  true. Media3 then loads the renderers of the extension by reflection.

## Version

The version is the Media3 release that Jellyfin built the extension against, plus a Jellyfin
build number: `1.9.0+1` is Media3 1.9.0, build 1. `media3Ffmpeg` in `libs.versions.toml` sets it.

The app uses the extension with the newer Media3 release in the version catalog. Each Media3
method and field that the classes of `1.9.0+1` call still exists in Media3 1.12.0-beta01. The
classes come compiled, so the build cannot see a method that a later Media3 release removes. The
app then fails only when it runs. After a Media3 update, look for a newer Jellyfin build. Then play
a file with a TrueHD audio track in the ExoPlayer engine. Phones rarely have their own TrueHD
decoder, so ExoPlayer uses the extension for it.

## Decoders

These values come from the decoder symbols that `libffmpegJNI.so` exports. All four ABIs export
the same decoders.

| Input | Value |
|---|---|
| libavcodec | 60.3.100 (the FFmpeg 6.0 line) |
| Audio decoders | `aac`, `aac_latm`, `ac3`, `alac`, `dca`, `eac3`, `flac`, `mlp`, `mp3`, `pcm_alaw`, `pcm_mulaw`, `truehd` |
| Video decoders | None |

The build has no Opus, Vorbis, AMR-NB or AMR-WB decoder. Android devices decode these four
formats themselves. The Android compatibility rules require Opus and Vorbis decoders, and Android
itself includes software decoders for AMR-NB and AMR-WB. So ExoPlayer uses the device decoder for
these formats. When the device decoder cannot play such a file, ExoPlayer has no FFmpeg decoder to
fall back on. The mpv and KitePlayer engines have their own FFmpeg, so they are not affected.

The extension also has `ExperimentalFfmpegVideoRenderer`, and Media3 adds it together with the
audio renderer. It reports every format as unsupported, so it decodes nothing.

## Licence

- The Jellyfin artifact is published under the GNU General Public License v3.0, as its POM says.
- Its Java side comes from Media3, under the Apache License 2.0.
- Its FFmpeg build enables only LGPL code. `build_ffmpeg.sh` in its sources jar configures FFmpeg
  with `--disable-everything` and the decoders above, and without `--enable-gpl`.

The app is licensed under the GNU AGPL v3. Section 13 of the GNU GPL v3 and of the GNU AGPL v3
allows code under the two licences to be combined.

## Other FFmpeg builds

The app ships up to three FFmpeg builds, and each keeps its own licence:

| FFmpeg build | Which builds carry it | Licence of that FFmpeg | Source code |
|---|---|---|---|
| This extension | Both Android APKs | LGPL 2.1 or later, inside an artifact under the GPL 3.0 | [jellyfin-androidx-media](https://github.com/jellyfin/jellyfin-androidx-media) |
| [libmpvKt](https://github.com/yuroyami/libmpvKt), for mpv (the `libmpvkt` version in the catalog) | The full Android APK | GPL 3.0 or later: libmpvKt configures FFmpeg with `--enable-gpl --enable-version3` | Attached to each libmpvKt release |
| [KiteFFmpeg](https://github.com/yuroyami/KiteFFmpeg), for KitePlayer (the version that the `kiteplayer` version pulls in) | The full Android APK, iOS and desktop | LGPL 2.1 or later | Attached to each KiteFFmpeg release |

So the FFmpeg code in the full APK is under the GPL 3.0 or later. The FFmpeg code in the exoOnly
APK is LGPL 2.1 or later code, inside the Jellyfin artifact under the GPL 3.0. iOS also carries
the FFmpeg inside VLCKit. Whether that build enables GPL parts is not established.

The licences screen in About shows the entries of the running build. The generated part of that
list comes from the dependency graph, so it lists the Jellyfin artifact. The hand-written part
covers native code such as these FFmpeg builds. It is in
`shared/src/app/home/components/Attributions.kt`.
