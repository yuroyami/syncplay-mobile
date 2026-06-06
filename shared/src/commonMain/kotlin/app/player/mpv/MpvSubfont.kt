package app.player.mpv

import app.utils.fileExists
import app.utils.getMpvConfFilePath
import app.utils.loggy
import app.utils.writeFileBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import syncplaymobile.shared.generated.resources.Res

/**
 * Installs the bundled libass fallback font into mpv's config dir, once. Shared by the Android
 * ([MpvImpl]) and iOS ([app.player.mpv.MpvKitImpl]) mpv engines.
 *
 * This mpv build (Android libmpv + MPVKit on iOS) has no system libass font provider, so libass
 * renders nothing — for embedded ASS and sideloaded SRT alike — unless mpv finds a fallback font at
 * `<config-dir>/subfont.ttf` (see mpv's `mp_ass_configure_fonts`). The font ships as a single shared
 * Compose resource (`commonMain/composeResources/files/subfont.ttf`) and is copied into the platform
 * mpv config dir: `<filesDir>` on Android, `<Documents>` on iOS — both the parent of the path
 * returned by [getMpvConfFilePath].
 *
 * Idempotent: a single [fileExists] check after the first install. Must run before `loadfile`,
 * because mpv configures libass fonts at playback start. mpv must also be told `config=yes` (Android
 * `MPVView` / the iOS bridge) or libmpv's builtin profile leaves config loading off and mpv never
 * scans its config dir for this file.
 */
suspend fun installMpvSubfontIfNeeded() {
    val configDir = getMpvConfFilePath()?.substringBeforeLast('/') ?: return
    val dest = "$configDir/subfont.ttf"
    if (fileExists(dest)) return
    withContext(Dispatchers.IO) {
        try {
            val bytes = Res.readBytes("files/subfont.ttf")
            writeFileBytes(dest, bytes)
            loggy("mpv: installed subfont.ttf (${bytes.size} B) for libass -> $dest")
        } catch (e: Exception) {
            loggy("mpv: failed to install subfont.ttf: ${e.message}")
        }
    }
}
