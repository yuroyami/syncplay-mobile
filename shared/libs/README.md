# Prebuilt libraries

## libffmpeg_media3exo_1.8.0.aar

The FFmpeg audio renderer extension for Media3 ExoPlayer. It gives ExoPlayer the audio
decoders Android itself does not ship, which is what lets the ExoPlayer engine play files
whose sound is E-AC-3, TrueHD, FLAC, Opus, Vorbis or ALAC.

**What it is.** The `androidx.media3.decoder.ffmpeg` extension from the Media3 source tree,
built for four ABIs (arm64-v8a, armeabi-v7a, x86, x86_64) against FFmpeg's libavcodec 60.
Media3 does not publish this extension to Maven: the project ships the Java side and asks you
to build the native half yourself, which is why it is a file here rather than a coordinate.

**Version.** Named for the Media3 release it was built against, 1.8.0. The Media3 dependency
in the version catalog can move ahead of it without a rebuild: the extension's own interface
has been stable across the 1.x line, which is why the file name says so.

**Licence.** The Java side is Apache 2.0, from the Android Open Source Project. The bundled
FFmpeg is used under the LGPL 2.1 or later, which is FFmpeg's default: the build enables only
the audio decoders listed above and does not enable GPL components. FFmpeg's own source is at
<https://ffmpeg.org>, and the corresponding source for this build is FFmpeg's release tarball
for the version named in the extension's build script, unmodified.

**How to rebuild it.** Follow the extension's own instructions in the Media3 source tree, at
`libraries/decoder_ffmpeg/README.md`. In short: clone Media3 at the tag you want, clone
FFmpeg, run the extension's `build_ffmpeg.sh` with the decoder list, then assemble the
extension module. The result is the same AAR.

**Integrity.**

```
sha256  eb7c57daaeed34e27b87120c2e595959656118ac182b9e380b3fff940cc8d834
```

Check it with `shasum -a 256 shared/libs/libffmpeg_media3exo_1.8.0.aar`.

**Note.** This is one of the FFmpeg copies inside an Android build. mpv carries its own, and
so does KiteCodec. They are separate builds with separate licences; see the licences screen in
the app's About page for what each one is offered under.
