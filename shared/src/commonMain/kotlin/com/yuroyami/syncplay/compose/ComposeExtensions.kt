package com.yuroyami.syncplay.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Helvetica_Regular
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.Res.readBytes

suspend fun daynightAsset() = readBytes("files/daynight_toggle.json").decodeToString()

@Composable
fun getRegularFont() = Font(Res.font.Helvetica_Regular) //Helvetica

@Composable
fun getSyncplayFont() = Font(Res.font.Directive4_Regular)


@Composable
fun <T> rememberAsync(initial: T, lambda: suspend () -> T): T {
    val scope = rememberCoroutineScope { Dispatchers.IO }

    var v by remember { mutableStateOf(initial) }

    LaunchedEffect(null) {
        scope.launch {
            v = lambda.invoke()
        }
    }

    return v
}