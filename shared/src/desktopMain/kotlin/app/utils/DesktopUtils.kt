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
 * Holds the DataStore preferences file and the log directory.
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

/** One-time process init for the global DataStore. Call from main() before any UI. */
fun initializeDatastore() {
    runCatching {
        datastore = createDataStore(
            producePath = { File(desktopAppDataDir, SYNKPLAY_PREFS).absolutePath }
        )
    }
}

/**
 * A join request parsed from the command line (see Main.kt), consumed once by the home
 * screen through [consumePendingShortcut] — the desktop analog of iOS Quick Actions.
 */
var pendingDesktopJoin: app.home.JoinConfig? = null
