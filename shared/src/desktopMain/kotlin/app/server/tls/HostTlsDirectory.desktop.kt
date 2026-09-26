package app.server.tls

import app.utils.desktopAppDataDir
import java.io.File

internal actual fun hostTlsDirectory(): File = File(desktopAppDataDir, "host-tls")
