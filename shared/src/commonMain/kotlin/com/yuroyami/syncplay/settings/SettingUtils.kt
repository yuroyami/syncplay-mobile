package com.yuroyami.syncplay.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.Flow

inline fun <reified T> Setting<T>.getSettingFlow(): Flow<T?> {
    return valueFlow(key, defaultValue)
}

@Composable
inline fun <reified T> Setting<T>.getSettingState(): State<T?> {
    return valueFlow(key, defaultValue).collectAsState(initial = defaultValue)
}

