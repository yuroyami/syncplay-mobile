package app.server.tls

import app.utils.contextObtainer
import java.io.File

internal actual fun hostTlsDirectory(): File = File(contextObtainer().filesDir, "host-tls")
