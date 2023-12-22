package com.yuroyami.syncplay.locale

import com.yuroyami.syncplay.utils.format
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
import org.jetbrains.compose.resources.readResourceBytes

object Localization {

    val lang: Language
        get() = Language.default()

    private val strings: MutableMap<String, Map<String, String>> = HashMap()
    private val resourcePath: (Language) -> String = { "values/${it.value}/strings.xml" }

//    fun stringResource(key: String): String {
//        load(lang)
//        return strings[lang.value]!![key]!!
//    }

    fun stringResource(key: String, vararg args: String): String {
        return try {
            load(lang)
            strings[lang.value]!![key]!!.format(*args)
        } catch (e: NullPointerException) {
            load(Language.ENGLISH)
            strings[Language.ENGLISH.value]?.get(key)!!.format(*args)
        }
    }

    private fun load(language: Language) {
        if (strings.containsKey(language.value)) return
        val xmlContent = runBlocking { readResourceBytes(resourcePath(language)) }.decodeToString()
        val parsed = parseXml(xmlContent)
        strings[language.value] = parsed
    }

    private fun parseXml(xml: String): Map<String, String> {
        val map = HashMap<String, String>()
        val res = XML.decodeFromString(LocalizedStrings.serializer(), xml)
        res.strings.forEach {
            map[it.name] = it.v
        }
        return map
    }

    @Serializable
    @XmlSerialName("resources", "", "")
    data class LocalizedStrings(
        @XmlElement(true)
        val strings: MutableList<LocalizedString>
    ) {
        @Serializable
        @XmlSerialName("string", "", "")
        data class LocalizedString(
            @XmlElement(false)
            @XmlSerialName("name", "", "")
            val name: String,

            @XmlValue(true)
            val v: String
        )
    }
}