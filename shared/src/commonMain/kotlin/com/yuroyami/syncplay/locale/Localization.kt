package com.yuroyami.syncplay.locale

import com.yuroyami.syncplay.utils.format
import com.yuroyami.syncplay.utils.loggy
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.readResourceBytes

object Localization {

    val lang: Language
        get() = Language.default()

    private val strings: MutableMap<String, Map<String, String>> = HashMap()
    private val resourcePath: (Language) -> String = { "strings/${it.value}/strings.xml" }

//    fun stringResource(key: String): String {
//        load(lang)
//        return strings[lang.value]!![key]!!
//    }

    fun stringResource(key: String, vararg args: String): String {
        loggy("TRYINGGGGG: $key")

        return try {
            load(lang)
            strings[lang.value]!![key]!!.format(*args)
        } catch (e: NullPointerException) {
            load(Language.ENGLISH)
            strings[Language.ENGLISH.value]?.get(key)?.format(*args) ?: throw Exception("")
        } catch (e: Exception) {
            loggy(e.stackTraceToString())
            loggy(key)
            ""
        }

    }

    private fun load(language: Language) {
        if (strings.containsKey(language.value)) return
        val xmlContent = runBlocking { readResourceBytes(resourcePath(language)) }.decodeToString()
        val parsed = parseXml(xmlContent)
        strings[language.value] = parsed
    }

    private fun parseXmlContent(xmlContent: String): Map<String, String> {
        val map = HashMap<String, String>()
        val regex = "<string name=\"(.*?)\">(.*?)</string>".toRegex()
        regex.findAll(xmlContent).forEach { matchResult ->
            val key = matchResult.groups[1]?.value ?: ""
            val value = matchResult.groups[2]?.value ?: ""
            map[key] = value
        }
        return map
    }

    private fun parseXml(xml: String): Map<String, String> {
        val map = HashMap<String, String>()
//        val document = Ksoup.parse(xml, Parser.xmlParser())
//        document.selectFirst("resources")?.children()?.forEach {
//            if (it.tagName() == "string") {
//                loggy("ELEMENTO $it")
//                val sName = it.attr("name")
//                val sValue = it.textNodes().joinToString(separator = "") { s -> s.coreValue() }
//                map[sName] = sValue
//            }
//        }

        return map
    }
}