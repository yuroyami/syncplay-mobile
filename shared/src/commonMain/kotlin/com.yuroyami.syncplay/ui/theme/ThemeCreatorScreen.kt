package com.yuroyami.syncplay.ui.theme

import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yuroyami.syncplay.managers.ThemeManager.Companion.Theme.Companion.BLANK_THEME


@Composable
fun ThemeCreatorScreenUI() {
    var currentTheme by remember { mutableStateOf(BLANK_THEME) }
    Scaffold(
        bottomBar = {
            BottomAppBar {
                FilledTonalButton(onClick = {

                }) {
                    Text("Save")
                }
            }
        }
    ) {

    }
}