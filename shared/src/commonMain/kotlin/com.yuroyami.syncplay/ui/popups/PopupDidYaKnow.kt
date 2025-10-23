package com.yuroyami.syncplay.ui.popups

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.logic.datastore.DataStoreKeys.MISC_NEVER_SHOW_TIPS
import com.yuroyami.syncplay.logic.datastore.writeValue
import com.yuroyami.syncplay.ui.screens.adam.LocalViewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getStringArray
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.tips

object PopupDidYaKnow {

    /** Use only once in AdamScreen rather than declaring it in two places */
    @Composable
    fun DidYaKnowPopup(state: MutableState<Boolean>) {
        val viewmodel = LocalViewmodel.current
        val visible by remember { state }
        val tips = remember { mutableStateListOf<String>() }

        if (visible) {
            var tipIndex by remember { mutableIntStateOf(0) }

            LaunchedEffect(null) {
                //We only fetch tips when necessary
                tips.addAll(getStringArray(Res.array.tips).shuffled())
            }

            AlertDialog(
                onDismissRequest = {
                    state.value = false
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (tipIndex == tips.size - 1) tipIndex = 0 else ++tipIndex
                        }
                    ) {
                        Text("Next tip") //TODO Localize
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewmodel.viewModelScope.launch(Dispatchers.IO) {
                                writeValue(MISC_NEVER_SHOW_TIPS, true)
                            }
                            state.value = false
                        }
                    ) {
                        Text("Don't show me tips again") //TODO Localize
                    }
                },
                icon = {
                    Icon(imageVector = Icons.Filled.Lightbulb, null)
                },
                title = {
                    Text("Did you know?")
                },
                text = {
                    tips.getOrNull(tipIndex)?.let { tip ->
                        Text(tip)
                    }
                }
            )
        }
    }
}