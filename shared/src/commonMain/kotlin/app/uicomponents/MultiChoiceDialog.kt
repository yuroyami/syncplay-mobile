package app.uicomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.theme.Theming

/** Creates a multi-choice dialog which is populated by the given list.
 *  Follows the app popup convention (see [SyncplayPopup]): the choices sit on a frosted glass
 *  panel over a light dim layer, centered. */
@Composable
fun MultiChoiceDialog(
    title: String = "",
    items: Map<String, String>,
    selectedItem: Map.Entry<String, String>,
    onItemClick: (Map.Entry<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        DialogBackdropBlur()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(glassScrim)
                .imePadding()
                .clickable(interactionSource = null, indication = null) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(vertical = Theming.SpaceLG)
                    .glassSurface()
                    .clickable(interactionSource = null, indication = null) { /* consume */ }
                    .padding(vertical = Theming.SpaceLG),
                horizontalAlignment = Alignment.Start
            ) {
                if (title != "") {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = Theming.SpaceXL)
                    )
                }

                val scrollState = rememberScrollState()
                Column(
                    // No weight: a short list wraps (and stays centered), a long one takes the
                    // remaining height under the title and scrolls.
                    modifier = Modifier.padding(top = Theming.SpaceSM).verticalScroll(scrollState),
                    horizontalAlignment = Alignment.Start
                ) {
                    items.entries.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true)
                                ) {
                                    onItemClick(item)
                                    onDismiss()
                                }
                                .padding(horizontal = Theming.SpaceLG, vertical = Theming.SpaceXS)
                        ) {
                            RadioButton(
                                selected = item == selectedItem,
                                onClick = {
                                    onItemClick(item)
                                    onDismiss()
                                }
                            )
                            Text(
                                text = item.key,
                                modifier = Modifier
                                    .padding(start = Theming.SpaceSM, end = Theming.SpaceLG),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
