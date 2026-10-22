package io.github.yuroyami.synkplay.buildplugins.appconfig

private val semanticVersion = Regex("""(\d+)\.(\d+)\.(\d+)""")

/**
 * The build number for Android (`versionCode`) and iOS (`CURRENT_PROJECT_VERSION`).
 * Layout `1 | major(3) | minor(3) | patch(2) | rebuild(1)`. For example,
 * 0.26.0 with rebuild 1 gives 1000026001.
 */
internal fun buildNumber(version: String, rebuild: Int): Long {
    val match = checkNotNull(semanticVersion.matchEntire(version)) {
        "The app version \"$version\" must have the form major.minor.patch, for example 1.4.0."
    }
    val (major, minor, patch) = match.destructured.toList().map(String::toInt)
    check(major in 0..999 && minor in 0..999 && patch in 0..99 && rebuild in 0..9) {
        "The app version \"$version\" (rebuild $rebuild) does not fit the build number layout: " +
            "major and minor 0 to 999, patch 0 to 99, rebuild 0 to 9."
    }
    return "1%03d%03d%02d%d".format(major, minor, patch, rebuild).toLong()
}

