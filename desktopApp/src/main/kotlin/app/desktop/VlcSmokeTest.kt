package app.desktop

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import kotlin.system.exitProcess

/**
 * Headless check that libVLC loads — run with `./gradlew :desktopApp:vlcSmokeTest`.
 * The gradle task points compose.application.resources.dir at the fetched natives, so this
 * exercises the exact discovery path the packaged app uses (BundledVlcDirectoryProvider).
 * Exits 0 and prints the libVLC version on success; any load failure throws and exits nonzero.
 */
fun main(args: Array<String>) {
    val url = args.firstOrNull()
    val factory = if (url != null) MediaPlayerFactory("-vv") else MediaPlayerFactory()
    println("VLC SMOKE OK — libVLC ${factory.application().version()}")

    // Optional deeper probe: open a media URL and report whether time advances.
    // Run with: ./gradlew :desktopApp:vlcSmokeTest -PsmokeUrl=https://...
    if (url != null) {
        val player = factory.mediaPlayers().newMediaPlayer()
        val ok = player.media().play(url)
        println("VLC SMOKE media().play returned $ok")
        Thread.sleep(12_000)
        println("VLC SMOKE media time=${player.status().time()}ms length=${player.status().length()}ms playing=${player.status().isPlaying}")
        player.release()
    }

    factory.release()
    exitProcess(0)
}
