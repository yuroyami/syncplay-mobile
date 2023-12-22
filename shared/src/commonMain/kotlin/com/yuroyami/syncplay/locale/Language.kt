package com.yuroyami.syncplay.locale

import com.yuroyami.syncplay.utils.getSystemLanguageCode

enum class Language(val value: String) {
    ENGLISH("en"),
    CHINESE("zh"),
    FRENCH("fr"),
    ARABIC("ar"),
    SPANISH("es");

    override fun toString(): String {
        return this.name.lowercase().replaceFirstChar { it.titlecase() }
    }

    companion object {
        fun default(): Language = when (getSystemLanguageCode()) {
            "zh" -> CHINESE
            "fr" -> FRENCH
            "ar" -> ARABIC
            "es" -> SPANISH
            else -> ENGLISH
        }
    }
}