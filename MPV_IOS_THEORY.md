# MPV on iOS — Theory of Everything

> Branch `experiment/mpv-ios`. How libmpv is built and embedded on each platform, why iOS
> MPV currently crashes at launch, and the honest answer to "one source for both platforms."

## TL;DR

- **The source already _is_ one.** Android and iOS both run the *same* upstream libmpv + FFmpeg
  + the same dep stack (dav1d, libass, freetype, harfbuzz, fribidi, libplacebo, …). What differs
  is the **toolchain** that compiles that source and the **glue** that calls it.
- **You cannot share one build _script_.** Android needs the NDK (Clang → ELF `.so`); iOS needs
  Xcode/clang (→ Mach‑O static libs in `.xcframework`). Different compilers, sysroots, output
  formats, linkers. That's physics, not laziness.
- **The real "single source of truth" is the mpv C‑API contract** — the property/command
  vocabulary (`loadfile`, `time-pos`, `pause`, `track-list/N/…`, `chapter`, `aid`/`sid`, …).
  Android's JNI layer and iOS's Swift bridge both target that identical contract, and the Kotlin
  `PlayerImpl` abstraction on top is genuinely shared.
- **iOS MPV is ~90% done already** (a prior "Fix MPVKit" commit built most of it). It is held back
  by **two concrete, fixable things**, only one of which is code.

---

## The mental model: one source, two toolchains, one contract

```
                       libmpv + FFmpeg + deps   ←─ SAME upstream C source
                        /                    \
        NDK / meson / ndk-build         Xcode / SwiftPM (MPVKit)
        (Clang → ELF .so)               (clang → Mach-O static .xcframework)
              │                                   │
   libmpv.so + libplayer.so (JNI)        Libmpv.xcframework + 30 dep frameworks
              │                                   │
        MPVLib.kt  (JNI)                  MpvKitBridgeImpl.swift  (raw C API)
              \                                   /
               \                                 /
            ┌──────────── the mpv C-API contract ────────────┐
            │  loadfile · time-pos · pause · speed · aid/sid  │  ← the true SSOT
            │  track-list/N/… · chapter-list/N/… · panscan …  │
            └─────────────────────────────────────────────────┘
                                  │
                    Kotlin PlayerImpl abstraction  ← shared, commonMain-driven
                  (ExoImpl/MpvImpl/VlcImpl | AVPlayer/MpvKit/VlcKit)
```

---

## Android pipeline (works today — the reference)

- Build scripts live in `buildscripts/` (a near-verbatim fork of `mpv-android`'s). Driven by the
  Gradle Exec task `runAndroidMpvNativeBuildScripts` (`buildSrc/.../NativeBuildConfig.kt`), wired to
  `preBuild` for the **`full`** flavor only (the `exoOnly` flavor skips all native build).
- Pipeline: `mpv_download_deps.sh` (SDK/NDK r29 + deps) → `mpv_build.sh --arch {armv7l,arm64,x86,x86_64} mpv`
  (meson+ninja / autotools cross-compile of the dep DAG into `buildscripts/prefix/<arch>/`) →
  `mpv_build.sh -n syncplay-withmpv` (**ndk-build** against `androidApp/src/main/jni/Android.mk`).
- Output: `libmpv.so` + FFmpeg `.so`s + the JNI glue **`libplayer.so`** + `libc++_shared.so`, dropped
  into `androidApp/src/main/libs/<abi>/`, auto-packaged by AGP (legacy jniLibs dir).
- **JNI surface** (`MPVLib`, package `is.xyz.mpv`): `create/init/destroy`, `command`, `attachSurface`/
  `detachSurface`, `get/setProperty{Int,Double,Boolean,String}`, `observeProperty`, `setOptionString`,
  `grabThumbnail` (Android-only). Events come back on an `mpv_wait_event` thread →
  `eventProperty`/`event`/`logMessage`.
- **Rendering = the `wid` model:** Android hands mpv the `SurfaceView`'s `Surface` via
  `mpv_set_option("wid", …)`; mpv owns a GLES context (`vo=gpu`/`gpu-next`, `gpu-context=android`,
  `opengl-es=yes`). **No `mpv_render_context_*`.**
- Versions: libmpv git master (`MPV_CLIENT_API_VERSION 2.5` ≈ 0.40.x), FFmpeg `n8.0`, NDK r29.

## iOS pipeline (MPVKit)

- **MPVKit 0.41.0** is a pure **Swift Package** (no CocoaPods) wired into `iosApp.xcodeproj` as an
  `XCRemoteSwiftPackageReference` → product **`MPVKit-GPL`**. It ships **prebuilt, static**
  `.xcframework`s (Libmpv + 7 FFmpeg libs + ~22 deps) downloaded by SwiftPM as release binaries.
- It exposes the **raw libmpv C headers** as a `[system]` clang module named `Libmpv` (umbrella over
  `client.h`/`render.h`/`render_gl.h`). There is **no Swift wrapper class** in the library — the demo
  `MPVMetalPlayerView` etc. are sample code, not API.
- **Rendering = `wid` + Metal:** `vo=gpu-next`, `gpu-api=vulkan`, `gpu-context=moltenvk` (Vulkan→Metal
  via MoltenVK), `hwdec=videotoolbox`; the `CAMetalLayer` pointer is bound via `mpv_set_option("wid",…)`
  *before* `mpv_initialize`. (MPVKit also offers an OpenGL‑ES render‑API path, unused here.)
- **The bridge that exists today:** `iosApp/iosApp/MpvKitBridge.swift` →
  `MpvKitBridgeImpl : MpvKitPlayerBridge` (`import Libmpv`). It's a complete, careful implementation:
  `mpv_create`/`mpv_initialize`, property observers (`time-pos`/`duration`/`pause`), event drain via
  `mpv_set_wakeup_callback` + serial queue with a teardown barrier, a `MPVMetalLayer` workaround for the
  MoltenVK 1×1 flicker, and even a `volume`‑type bugfix (INT64→DOUBLE). Registered at startup in
  `iosApp/iosApp/iOSApp.swift` (`MpvKitBridgeKt.instantiateMpvKitPlayer = …`).
- Kotlin side: `MpvKitPlayerBridge` (abstract contract), `MpvKitImpl : PlayerImpl` (delegates every op to
  the bridge), `MpvKitEngine` (`isAvailable = instantiateMpvKitPlayer != null`). All mirror Android's
  `MpvImpl`.
- Versions: libmpv v0.41.0, FFmpeg n8.1.1.

---

## Why iOS MPV crashes at launch — VERIFIED

Two problems. The first is the launch crash; the second is why it'd be invisible even if it launched.

### 1. Static MPVKit xcframeworks are auto‑**embedded** as malformed stub frameworks

Two facts, both verified directly against the current builds (newer than the pbxproj, so they reflect
today's config):

**Good news — static linking already works.** The real libmpv code is compiled *into the app binary*:
- Release `Synkplay` executable: **85 `mpv_*` symbols** (incl. `mpv_create`, `mpv_command`).
- Debug `Synkplay.debug.dylib`: **129 `mpv_*` symbols**.

Neither binary's load chain (`otool -L`) references the embedded frameworks. So linkage — the hard part —
is done.

**Bad news — 27–32 junk frameworks get embedded anyway.** MPVKit ships **static** libraries packaged as
`.framework`s; SwiftPM/Xcode *also* embeds them into `…app/Frameworks/`, where each becomes a malformed
stub:

| Check | `Libmpv.framework/Libmpv` (embedded stub) | `VLCKit.framework/VLCKit` (real dynamic) |
|---|---|---|
| Size | **~33–51 KB** | multi‑MB |
| Exported symbols (`nm -gU`) | **0–1** | 26,682 |
| Install name (`otool -D`) | `…/T/swbuild.tmp.…/arm64-apple` (**doesn't exist on device**) | normal `@rpath` |

These stubs are unreferenced dead weight at best, and a plausible **install/launch bundle‑validation
failure** at worst (iOS validates every embedded framework's signature/structure) — the likeliest
explanation for the historical "won't even launch." They are pure junk: the code they'd contain is
already linked into the executable.

**Fix — keep the static link, stop the embed:**
1. Xcode → target **iosApp** → **General** → *Frameworks, Libraries, and Embedded Content* → set
   **`MPVKit-GPL`** (and any `Lib*` rows) to **"Do Not Embed."**
2. Product → **Clean Build Folder** (⇧⌘K); delete stale DerivedData.
3. Rebuild and run **on a real device** (MoltenVK/Vulkan is unreliable on the Simulator).

**Bulletproof fallback** if the toggle doesn't fully stop the auto‑embed (a known SwiftPM/static‑xcframework
quirk) — safe precisely because the code is already linked into the binary; add a final **Run Script**
build phase:
```sh
rm -rf "${TARGET_BUILD_DIR}/${FRAMEWORKS_FOLDER_PATH}"/Lib*.framework
rm -rf "${TARGET_BUILD_DIR}/${FRAMEWORKS_FOLDER_PATH}"/{gnutls,nettle,hogweed,gmp,lcms2}.framework
```

**Verify the fix worked:**
```bash
APP=~/Library/Developer/Xcode/DerivedData/iosApp-*/Build/Products/Debug-iphoneos/Synkplay.app
ls "$APP/Frameworks/" | grep -i libmpv     # expect: NO Libmpv.framework
nm "$APP/Synkplay"* 2>/dev/null | grep -c mpv_create   # expect: > 0
```

### 2. `MpvKitEngine` was never added to the iOS engine list (FIXED on this branch)

`shared/src/iosMain/kotlin/app/utils/PlatformUtils.ios.kt` listed only `AVPlayerEngine` + `VlcKitEngine`.
Even with linkage fixed, MPV could never be *selected*. The picker already gates on `engine.isAvailable`
(shows a snackbar otherwise), so listing it is safe. **Done** (see "Changes on this branch").

---

## "One source to build both" — the honest answer + options

You can't merge the two build *scripts*, but the source and contract are already shared. Three levels of
ambition, increasing effort:

- **A — Make it work (lowest risk).** Apply the embed fix above; iOS MPV runs through the existing Swift
  bridge. Nothing to build from scratch — MPVKit's tested binaries do the heavy lifting. Recommended first
  step regardless of the end goal.
- **B — Unify the engine (drop the Swift bridge).** MPVKit's `Libmpv` clang module exposes the raw C API,
  so Kotlin/Native **cinterop** can call `mpv_create`/`mpv_command`/`mpv_set_property` *directly from
  Kotlin* — making iOS `MpvImpl` a near‑1:1 mirror of Android's JNI `MpvImpl`. Only the `CAMetalLayer`
  creation/attach stays as a tiny Swift/ObjC shim. This is the closest thing to "one Kotlin source drives
  libmpv on both platforms." More work; the Swift bridge already works once linked.
- **C — True single‑source build.** Stop consuming MPVKit's prebuilt binaries and compile libmpv for iOS
  from the *same* `mpv-master` + dep versions the Android `buildscripts/` use (the way MPVKit's own CLI
  does). Maximum "one source," maximum fragility/maintenance. MPVKit already does this well — rebuilding
  it is rarely worth it.

Recommended path: **A now → B if you want the architecture clean.** C only if you need lockstep
version control over both platforms' libmpv.

---

## Changes on this branch (`experiment/mpv-ios`)

- `shared/src/iosMain/kotlin/app/utils/PlatformUtils.ios.kt` — register `MpvKitEngine` in
  `availablePlatformPlayerEngines` (+ import). Safe: gated by `isAvailable`.
- `shared/src/iosMain/kotlin/app/player/mpv/MpvKitEngine.kt` — `isAvailable` is now a computed getter so
  it tracks Swift‑bridge registration regardless of init ordering.

**Still required (Xcode, not code):** the "Do Not Embed" linkage fix in §Why‑it‑crashes #1. That's the
actual launch blocker and can't be set from the Kotlin/Gradle side.
