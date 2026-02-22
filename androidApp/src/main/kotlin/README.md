## Where is the Android code?

All Android code (including activities and the application class) lives in `shared/src/androidMain/` — this is intentional.

The app was originally single-module, but AGP 9.0+ requires a dedicated app module separate from the shared KMP module. The `androidApp` module is therefore kept as thin as possible, acting only as an entry point, similar to how iosApp does it.