package app.player.kite

/**
 * KitePlayer on desktop, and the only engine there.
 *
 * Presentation is the shared KitePlayerVideo since 0.0.20, pinned to the Compose canvas here.
 * Since KitePlayer 0.0.21 the JVM has a real native view, an AWT canvas, and it is the library's
 * desktop default, but macOS routes a click to the topmost native view, so every control this
 * room draws over the video would stop taking input. The in-room renderer toggle is accepted and
 * has one honest answer on this platform.
 *
 * Frames arrive through KiteFFmpeg's CPU converter and become one Skia raster each; media loading
 * stays suspended until KitePlayerVideo reports its renderer attached, so decoder selection
 * cannot race video output.
 *
 * Default as well as only: [app.preferences.Preferences.PLAYER_ENGINE] resolves its initial value
 * by asking which engine is the default, so exactly one on this platform has to say yes.
 */
internal val desktopKiteEngine = KiteEngine(
    mediaResolver = DesktopKiteMediaResolver,
    isDefault = true,
    forcesComposeCanvas = true,
)
