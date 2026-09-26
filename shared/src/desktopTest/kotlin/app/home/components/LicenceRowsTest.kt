package app.home.components

import app.utils.Platform
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The licences screen lists what each build ships, with the licence text of every entry. */
class LicenceRowsTest {

    private val json = File("src/commonMain/composeResources/files/aboutlibraries.json").readText()

    private fun rows(platform: Platform, exoOnly: Boolean = false) = licenceRows(json, platform, exoOnly)

    private fun List<LicenceRow>.named(name: String) = filter { it.name.contains(name, ignoreCase = true) }

    @Test
    fun theFullApkNamesBothFfmpegLicences() {
        val ffmpeg = rows(Platform.Android).named("FFmpeg").map { it.licence }
        assertTrue(ffmpeg.any { it.startsWith("GPL 3.0") }, "$ffmpeg")
        assertTrue(ffmpeg.any { it.startsWith("LGPL 2.1") }, "$ffmpeg")
    }

    @Test
    fun theExoPlayerOnlyApkNamesFfmpegUnderTheLgplOnly() {
        val exo = rows(Platform.Android, exoOnly = true)
        assertEquals(listOf("LGPL 2.1 or later"), exo.filter { it.name.startsWith("FFmpeg") || it.name == "KiteFFmpeg" }.map { it.licence })
        assertTrue(exo.named("mpv").isEmpty(), "no mpv in the ExoPlayer-only build")
        assertTrue(exo.none { it.licence.contains("GPL") && !it.licence.contains("LGPL") && !it.licence.contains("Lesser") }, "no GPL entry")
    }

    @Test
    fun iosAndDesktopListNoMpv() {
        for (platform in listOf(Platform.IOS, Platform.Desktop)) {
            val list = rows(platform)
            assertTrue(list.named("mpv").isEmpty(), "$platform lists mpv")
            assertTrue(list.named("FFmpeg, in KitePlayer").isNotEmpty(), "$platform misses KitePlayer's FFmpeg")
        }
        assertTrue(rows(Platform.IOS).named("VLCKit").isNotEmpty())
        assertTrue(rows(Platform.IOS).named("YouTubeKit").isNotEmpty())
    }

    /** The components that the hand-written list used to miss now come from the build itself. */
    @Test
    fun theGeneratedListCoversTheLibrariesThatWereMissing() {
        val android = rows(Platform.Android)
        for (name in listOf("Lyricist", "Uri KMP", "Kolor picker", "okhttp")) {
            assertTrue(android.named(name).isNotEmpty(), "Android misses $name")
        }
        assertTrue(android.named("libass, in KitePlayer").isNotEmpty())
        assertTrue(android.all { it.version != null || it.url != null })
    }

    @Test
    fun everyEntryWithALicenceHasItsText() {
        for (platform in Platform.entries) for (exoOnly in listOf(false, true)) {
            rows(platform, exoOnly)
                .filter { !it.licence.startsWith("Service") }
                .forEach { row -> assertTrue(!row.text.isNullOrBlank(), "$platform: ${row.name} (${row.licence}) has no licence text") }
        }
    }
}
