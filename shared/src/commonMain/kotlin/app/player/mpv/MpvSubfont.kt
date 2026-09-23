package app.player.mpv

import app.utils.fileExists
import app.utils.getMpvConfFilePath
import app.utils.ioDispatcher
import app.utils.loggy
import app.utils.writeFileBytes
import kotlinx.coroutines.withContext
import syncplaymobile.shared.generated.resources.Res

/**
 * Installs the bundled libass fallback font into mpv's config directory, once. The Android mpv
 * engine ([MpvImpl]) uses it. Platforms without mpv return null from [getMpvConfFilePath] and
 * skip it.
 *
 * This mpv build has no system font provider for libass. So libass renders no subtitles at all
 * (embedded ASS and sideloaded SRT alike) unless mpv finds a fallback font at
 * `<config-dir>/subfont.ttf` (see mpv's `mp_ass_configure_fonts`). The font ships as a shared
 * Compose resource (`commonMain/composeResources/files/subfont.ttf`). This function copies it
 * into the mpv config directory, the parent of the path that [getMpvConfFilePath] returns.
 *
 * After the first install, a call costs one [fileExists] check. The call must run before
 * `loadfile`, because mpv configures libass fonts when playback starts. mpv must also get
 * `config=yes` (the Android `MpvImpl` does this by giving `MpvOptions` a config directory).
 * Otherwise the builtin libmpv profile leaves config loading off, and mpv never looks for this
 * file.
 */
suspend fun installMpvSubfontIfNeeded() {
    val configDir = getMpvConfFilePath()?.substringBeforeLast('/') ?: return
    val dest = "$configDir/subfont.ttf"
    if (fileExists(dest)) return
    withContext(ioDispatcher) {
        try {
            val bytes = Res.readBytes("files/subfont.ttf")
            writeFileBytes(dest, bytes)
            loggy("mpv: installed subfont.ttf (${bytes.size} B) for libass -> $dest")
        } catch (e: Exception) {
            loggy("mpv: failed to install subfont.ttf: ${e.message}")
        }
    }
}
