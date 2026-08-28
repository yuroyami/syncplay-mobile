package app.player.kite

/**
 * KitePlayer on desktop, and the only engine there.
 *
 * Presentation is the shared KitePlayerVideo since 0.0.20. Its JVM actual coerces every request
 * to the pure-Compose renderer, because the JVM has no native video view to host: KitePlayer's
 * native-surface path compiles there but draws an empty box. So the in-room renderer toggle is
 * accepted and simply has one honest answer on this platform.
 *
 * Frames arrive through KiteCodec's CPU converter and become one Skia raster each; media loading
 * stays suspended until KitePlayerVideo reports its renderer attached, so decoder selection
 * cannot race video output.
 *
 * Default as well as only: [app.preferences.Preferences.PLAYER_ENGINE] resolves its initial value
 * by asking which engine is the default, so exactly one on this platform has to say yes.
 */
internal val desktopKiteEngine = KiteEngine(
    mediaResolver = DesktopKiteMediaResolver,
    isDefault = true,
)
