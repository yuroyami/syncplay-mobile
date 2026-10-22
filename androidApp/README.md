## Where is the Android code?

All Android code lives in `shared/src@android/`, on purpose. This includes the activity
(`app.SyncplayActivity`) and the application class (`app.SynkplayApp`).

The Kotlin Toolchain builds an Android app only from an `android/app` module, and the shared code
is a Kotlin Multiplatform library module. So the `androidApp` module is only a thin entry point:
its `module.yaml`, its manifest (`src/AndroidManifest.xml`), its resources (`res/`), its R8 rules
(`proguard-rules.pro`) and its baseline profile (`src/main/baselineProfiles/`). The `iosApp` shell
is thin in the same way.
