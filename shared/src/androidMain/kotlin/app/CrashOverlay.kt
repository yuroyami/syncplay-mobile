package app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.utils.loggy
import kotlinx.coroutines.flow.MutableStateFlow

object CrashHandler {
    val crashTrace = MutableStateFlow<String?>(null)

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val trace = throwable.stackTraceToString()
            loggy(trace)
            crashTrace.value = trace

            if (Looper.myLooper() == Looper.getMainLooper()) {
                // Restart the main looper so Compose can still render the crash overlay
                while (true) {
                    try {
                        Looper.loop()
                    } catch (_: Throwable) {
                        // Swallow subsequent crashes to keep the overlay visible
                    }
                }
            }
            // Background thread: let it die, main thread + Compose keep running
        }
    }
}

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Crash Report",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Crash Trace", trace))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Copy")
                    }

                    Button(
                        onClick = { CrashHandler.crashTrace.value = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF666666))
                    ) {
                        Text("Dismiss")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            SelectionContainer {
                Text(
                    text = trace,
                    color = Color(0xFFFF6B6B),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}
