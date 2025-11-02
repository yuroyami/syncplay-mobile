This folder contains the scripts required to build the **Android native libraries** used by `syncplay-mobile`, including:

* **libmpv** – video engine used by the app (built for 4 architectures)
* **FFmpeg audio decoder for ExoPlayer** *(optional but recommended for full audio format support)*

⚠️ **Supported platforms:** Linux and macOS
❌ **Not supported:** Windows and WSL (even WSL2)


### How does this interest me?

In general, if your only goal is to 
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