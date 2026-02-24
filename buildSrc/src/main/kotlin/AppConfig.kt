object AppConfig {
    const val javaVersion = 21

    const val compileSdk = 36
    const val minSdk = 26

    const val versionName = "0.17.0"
    val versionCode = ("1" + versionName.split(".").joinToString("") { it.padStart(3, '0') }).toInt()

    const val exoOnly = false
    const val forGooglePlay = false

    const val ndkRequired = "29.0.14206865"

    val abiCodes = mapOf(
        "armeabi-v7a" to "armv7l",
        "arm64-v8a" to "arm64",
        "x86" to "x86",
        "x86_64" to "x86_64"
    )

    val mpvLibs = listOf(
        "libavcodec.so", "libavdevice.so", "libavfilter.so",
        "libavformat.so", "libavutil.so", "libmpv.so", "libplayer.so",
        "libswresample.so", "libswscale.so"
    )
}
