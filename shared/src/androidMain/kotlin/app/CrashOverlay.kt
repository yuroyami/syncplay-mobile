package app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import SyncplayMobile.shared.KiteBuildConfig
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.zIndex
import app.theme.Space
import app.theme.Type
import app.uicomponents.controls.DestructiveAction
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Text
import app.utils.loggy
import kotlinx.coroutines.flow.MutableStateFlow

object CrashHandler {
    val crashTrace = MutableStateFlow<String?>(null)

    /** How many times the debug overlay may restart the main looper before giving up. */
    private const val MAX_LOOPER_RESTARTS = 10

    /**
     * Debug builds keep the process alive and draw the trace on screen, which is the fastest way
     * to read a crash on a device. Release builds log it and hand it to the platform handler:
     * a swallowed fatal never reached Play vitals, and the user was left with a frozen app
     * instead of a clean restart.
     */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val trace = throwable.stackTraceToString()
            loggy(trace)

            if (KiteBuildConfig.IS_DEBUG) {
                crashTrace.value = trace
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    // Restart the main looper so Compose can still render the crash overlay.
                    // Bounded: if the overlay itself is what keeps crashing, an endless restart
                    // loop just spins the CPU behind a frozen screen, so after a few attempts the
                    // crash goes to the platform handler and the app restarts cleanly.
                    var restarts = 0
                    while (restarts < MAX_LOOPER_RESTARTS) {
                        restarts++
                        try {
                            Looper.loop()
                        } catch (_: Throwable) {
                            // Swallow subsequent crashes to keep the overlay visible
                        }
                    }
                }
                // Background thread: let it die, main thread + Compose keep running
                return@setDefaultUncaughtExceptionHandler
            }

            previous?.uncaughtException(thread, throwable)
        }
    }
}

/** The last-resort overlay: the trace in monospace, copy and dismiss. Fixed colours on purpose. */
@Composable
fun CrashOverlay() {
    val crashTrace by CrashHandler.crashTrace.collectAsState()
    val trace = crashTrace ?: return
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(Float.MAX_VALUE)
            .background(Color(0xF0121212))
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(Space.gutter)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Crash report", color = Color.White, style = Type.display)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.gapTight)) {
                    SecondaryAction("Copy", onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Crash Trace", trace))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    })
                    DestructiveAction("Dismiss", onClick = { CrashHandler.crashTrace.value = null })
                }
            }
            Spacer(Modifier.height(Space.gap))
            SelectionContainer {
                Text(
                    text = trace,
                    color = Color(0xFFFF6B6B),
                    style = Type.note.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                )
            }
        }
    }
}
