package app.preferences.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.preferences.Preferences
import app.preferences.set
import app.preferences.value
import app.uicomponents.SyncplayPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@Composable
fun TrustedDomainsPopup(visibilityState: MutableState<Boolean>) {
    val scope = rememberCoroutineScope { Dispatchers.IO }

    val stored = Preferences.TRUSTED_DOMAINS.value()
    var text by remember {
        mutableStateOf(
            stored.split("\n", ",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
        )
    }

    SyncplayPopup(
        dialogOpen = visibilityState.value,
        widthPercent = 0.85f,
        heightPercent = 0.7f,
        strokeWidth = 0.5f,
        onDismiss = { visibilityState.value = false }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Trusted Domains",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Enter one domain per line. Only URLs from these domains will auto-load. Leave empty to allow all.",
                color = MaterialTheme.colorScheme.outline,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )

            Spacer(Modifier.height(12.dp))

            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text(
                            text = "example.com\ncdn.host.net\nmedia.server.org",
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    text = ""
                }) {
                    Text("Clear")
                }

                Spacer(Modifier.weight(1f))

                TextButton(onClick = {
                    visibilityState.value = false
                }) {
                    Text("Cancel")
                }

                TextButton(onClick = {
                    scope.launch {
                        val cleaned = text.lines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .joinToString("\n")
                        Preferences.TRUSTED_DOMAINS.set(cleaned)
                    }
                    visibilityState.value = false
                }) {
                    Text("Save")
                }
            }
        }
    }
}
