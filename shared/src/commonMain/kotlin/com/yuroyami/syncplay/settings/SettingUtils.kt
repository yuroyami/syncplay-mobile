@file:Suppress("UNCHECKED_CAST")
package com.yuroyami.syncplay.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.Flow

inline fun <reified T> Setting<T>.getSettingFlow(): Flow<T?> {
    return valueFlow(key, defaultValue)
}

inline fun <reified T> Setting<T>.getSettingValueBlockingly(): T? {
    return valueBlockingly(key, defaultValue)
}

@Composable
inline fun <reified T> Setting<T>.getSettingState(): State<T> {
    return valueFlow(key, defaultValue).collectAsState(initial = defaultValue) as State<T>
}

inline fun <reified T> String.settingV(): T? {
    val settingInQuestion = settingsMapper[this] as Setting<T>
    return settingInQuestion.getSettingValueBlockingly<T>()
}

inline fun <reified T> String.settingF(): Flow<T?> {
    val settingInQuestion = settingsMapper[this] as Setting<T>
    return settingInQuestion.getSettingFlow()
}

@Composable
inline fun <reified T> String.settingS(): State<T> {
    val settingInQuestion = settingsMapper[this] as Setting<T>
    return settingInQuestion.getSettingState()
}


/* Convenience extensions */
fun String.settingInt() = this.settingV<Int>() as Int
fun String.settingDouble() = this.settingV<Double>() as Double
fun String.settingString() = this.settingV<String>() as String
fun String.settingBoolean() = this.settingV<Boolean>() as Boolean

@Composable fun String.settingIntState() = this.settingS<Int>()
@Composable fun String.settingDoubleState() = this.settingS<Double>()
@Composable fun String.settingStringState() = this.settingS<String>()
@Composable fun String.settingBooleanState() = this.settingS<Boolean>()


/* Mappers */
val settingsMapper: HashMap<String, Setting<out Any>> = hashMapOf()

fun HashMap<Setting<out Any>, String>.populate(categs: List<SettingCategory>) {
    categs.forEach { it.settingList.clear() }

    this.forEach { (setting, categoryID) ->
        /** Mapping settings as values to their keys for easy later access */
        settingsMapper[setting.key] = setting

        /** Populating categories with their settings */
        categs.first { it.keyID == categoryID }.settingList.add(setting)
    }
}



