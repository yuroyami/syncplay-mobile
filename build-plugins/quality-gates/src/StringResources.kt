package io.github.yuroyami.synkplay.buildplugins.qualitygates

import org.w3c.dom.Comment
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** The kind of one resource entry in a `strings.xml` file. */
internal enum class ResourceKind { STRING, PLURALS, ARRAY }

/**
 * One entry of a `strings.xml` file, as an XML parser reads it. [text] has the entities decoded.
 * [comment] is the comment directly above the entry, with only white space between the two.
 */
internal data class StringEntry(
    val kind: ResourceKind,
    val name: String,
    val text: String,
    val items: List<String>,
    val comment: String?,
)

/** Reads the string resource files with a real XML parser, so `name ="key"` counts like any other spelling. */
internal object StringResources {
    private val cache = HashMap<Path, List<StringEntry>>()

    fun read(file: Path): List<StringEntry> = cache.getOrPut(file) { parse(file) }

    /** The names of every string and plural in [file]. Arrays are not keys that Lyricist reads one by one. */
    fun keys(file: Path): List<String> = read(file).filter { it.kind != ResourceKind.ARRAY }.map { it.name }

    /** The `android:name` of every `<locale>` in an Android locale configuration file. */
    fun localeConfigNames(file: Path): List<String> {
        val locales = document(file).getElementsByTagName("locale")
        return (0 until locales.length).map { (locales.item(it) as Element).getAttribute("android:name") }
    }

    private fun document(file: Path): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // No DTD and no external entities: a resource file never needs either.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            isXIncludeAware = false
            isExpandEntityReferences = false
            isIgnoringComments = false
        }
        return try {
            factory.newDocumentBuilder().parse(file.toFile())
        } catch (e: Exception) {
            throw IllegalStateException("$file is not valid XML: ${e.message}", e)
        }
    }

    private fun parse(file: Path): List<StringEntry> {
        val entries = mutableListOf<StringEntry>()
        var comment: String? = null
        val children = document(file).documentElement.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            when {
                node is Comment -> comment = node.data.trim()
                node is Element -> {
                    val kind = when (node.tagName) {
                        "string" -> ResourceKind.STRING
                        "plurals" -> ResourceKind.PLURALS
                        "string-array" -> ResourceKind.ARRAY
                        else -> null
                    }
                    if (kind != null) {
                        val items = (0 until node.childNodes.length).mapNotNull { node.childNodes.item(it) as? Element }.map { it.textContent }
                        entries += StringEntry(kind, node.getAttribute("name"), node.textContent, items, comment)
                    }
                    comment = null
                }
                // White space between a comment and its entry keeps the comment; other text drops it.
                node.nodeType == Node.TEXT_NODE && node.textContent.isNotBlank() -> comment = null
            }
        }
        return entries
    }
}
