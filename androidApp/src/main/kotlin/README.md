## Where is the Android code?

All Android code lives in `shared/src/androidMain/`, on purpose. This includes the activity
(`app.SyncplayActivity`) and the application class (`app.SynkplayApp`).

AGP 9 needs the Android application in its own module, separate from the Kotlin Multiplatform
module. So the `androidApp` module is only a thin entry point: its manifest, its resources and its
build file. The `iosApp` shell is thin in the same way.
