This folder contains the scripts required to build the **Android native libraries** used by `syncplay-mobile`, including:

* **libmpv** – video engine used by the app (built for 4 architectures)
* **FFmpeg audio decoder for ExoPlayer** *(optional but recommended for full audio format support)*

⚠️ **Supported platforms:** Linux and macOS
❌ **Not supported:** Windows and WSL (even WSL2)


### How does this interest me?

If all you want is a full build of Syncplay—with mpv integrated and ExoPlayer’s FFmpeg audio renderer working—just run the regular build task in Android Studio. It handles everything for you: downloading dependencies, building the mpv libraries, building the FFmpeg audio renderer, and wiring it all up before the app builds.

The clean task is also linked to custom cleanup steps that clear out native libraries and their generated files.

If you'd rather do it manually, check out "Typical Full Build Workflow" below for the full step-by-step process in case you need to debug or track down issues.


### Cleaning Builds

To clean build artifacts before rebuilding:

```bash
./mpv_build.sh --clean
```

For a full reset, also remove the `prefix` folder:

```bash
rm -rf prefix
```

---

## ✅ Typical Full Build Workflow

```
./download.sh

./buildall.sh --arch armv7l mpv
./buildall.sh --arch arm64 mpv
./buildall.sh --arch x86 mpv
./buildall.sh --arch x86_64 mpv

./buildall.sh
```

---

## Output

Once build is complete:

* The compiled `.so` native libraries will appear under
  `shared/src/androidMain/libs/` (one subfolder per architecture)

You can proceed to building the APK now that the native libs are ready to be packaged.