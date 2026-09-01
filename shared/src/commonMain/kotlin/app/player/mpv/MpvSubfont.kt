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
 * Installs the bundled libass fallback font into mpv's config dir, once. Used by the Android mpv
 * engine ([MpvImpl]); platforms with no mpv return null from [getMpvConfFilePath] and skip it.
 *
 * This mpv build has no system libass font provider, so libass renders nothing — for embedded ASS
 * and sideloaded SRT alike — unless mpv finds a fallback font at `<config-dir>/subfont.ttf` (see
 * mpv's `mp_ass_configure_fonts`). The font ships as a shared Compose resource
 * (`commonMain/composeResources/files/subfont.ttf`) and is copied into the mpv config dir, the
 * parent of the path returned by [getMpvConfFilePath].
 *
 * Idempotent: a single [fileExists] check after the first install. Must run before `loadfile`,
 * because mpv configures libass fonts at playback start. mpv must also be told `config=yes`
 * (Android `MPVView`) or libmpv's builtin profile leaves config loading off and mpv never scans
 * its config dir for this file.
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
