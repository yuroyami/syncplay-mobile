package app.utils

import app.preferences.Preferences.SYNKPLAY_PREFS
import app.preferences.createDataStore
import app.preferences.datastore
import java.io.File

/**
 * Per-user application data directory, following each OS's convention:
 *  - Windows: %APPDATA%\Synkplay
 *  - macOS:   ~/Library/Application Support/Synkplay
 *  - Linux:   $XDG_DATA_HOME/synkplay (or ~/.local/share/synkplay)
 *
 * Holds the DataStore preferences file, the log directory and the cache directory.
 */
val desktopAppDataDir: File by lazy {
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    val dir = when {
        os.contains("windows") -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "Synkplay")
        os.contains("mac") -> File(home, "Library/Application Support/Synkplay")
        else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "synkplay")
    }
    dir.apply { mkdirs() }
}

/** Creates the global DataStore once per process. Call it from main() before any UI. */
fun initializeDatastore() {
    runCatching {
        datastore = createDataStore(
            producePath = { File(desktopAppDataDir, SYNKPLAY_PREFS).absolutePath }
        )
    }
}

/**
 * A join request parsed from the command line (see Main.kt). The home screen reads it once
 * through [consumePendingShortcut]. It is the desktop version of the iOS Quick Actions.
 */
var pendingDesktopJoin: app.home.JoinConfig? = null
