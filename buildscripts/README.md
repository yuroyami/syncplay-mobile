This folder contains the scripts required to build the **Android native libraries** used by `syncplay-mobile`, including:

* **libmpv** – video engine used by the app (built for 4 architectures)
* **FFmpeg audio decoder for ExoPlayer** *(optional but recommended for full audio format support)*

⚠️ **Supported platforms:** Linux and macOS
❌ **Not supported:** Windows and WSL (even WSL2)

---

## 1. Download Dependencies

Run the script below to download the **Android SDK + NDK** locally inside `buildscripts`.
This ensures a **reproducible build** that doesn't depend on your system SDK.

```bash
cd buildscripts
./download.sh
```

You can also run this inside Android Studio's terminal.

> **Important:** The full path to `download.sh` **must not contain spaces**.
> Example of what will **fail**:
>
> ```
> /usr/local/bin/PROJECTS/Syncplay  Mobile/buildscripts/download.sh
>                          ^ space here causes errors
> ```

---

## 2. Build Native Libraries

First, build **libmpv** for all Android architectures:

```bash
./buildall.sh --arch armv7l mpv
./buildall.sh --arch arm64 mpv
./buildall.sh --arch x86 mpv
./buildall.sh --arch x86_64 mpv
```

Then build everything together (this will actually create the so libs based on the JNI interface and put them inside androidMain/libs)

```bash
./buildall.sh
```

This final command is equivalent to:

```bash
./buildall.sh syncplay-withmpv
```

---

### Cleaning Builds

To clean build artifacts before rebuilding:

```bash
./buildall.sh --clean
```

For a full reset, also remove the `prefix` folder:

```bash
rm -rf prefix
```

---

### Rebuilding Specific Components

If a specific component (e.g. `dav1d`, `libass`, or `ffmpeg`) fails or needs to be rebuilt:

```bash
./buildall.sh dav1d
```

To rebuild **one component from scratch**:

```bash
./buildall.sh -n ffmpeg --clean
```

⚠️ If you rebuild a component, do it for **all 4 architectures**.

Each component is basically a single script that can be found in:

```
buildscripts/scripts/
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