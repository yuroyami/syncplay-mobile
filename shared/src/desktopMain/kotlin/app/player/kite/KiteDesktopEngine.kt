package app.player.kite

/**
 * KitePlayer on desktop, and the only engine there. An engine is one of the video players the
 * app can drive.
 *
 * The video shows through the shared KitePlayerVideo, forced to the Compose canvas here.
 * KitePlayer's default desktop renderer is a native view (an AWT canvas), but macOS sends a click
 * to the topmost native view, so no control that the room draws over the video would take input.
 * The room's renderer toggle is accepted, but on desktop it always ends on the Compose canvas.
 *
 * Frames pass through KiteFFmpeg's CPU converter and become one Skia raster each. Media loading
 * waits until KitePlayerVideo reports its renderer attached, so decoder selection cannot race the
 * video output.
 *
 * It is also the default engine: [app.preferences.Preferences.PLAYER_ENGINE] picks its initial
 * value by asking which engine is the default, so exactly one engine per platform must say yes.
 */
internal val desktopKiteEngine = KiteEngine(
    mediaResolver = DesktopKiteMediaResolver,
    isDefault = true,
    forcesComposeCanvas = true,
)
