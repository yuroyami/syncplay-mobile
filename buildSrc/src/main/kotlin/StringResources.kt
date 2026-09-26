import org.gradle.api.GradleException
import org.w3c.dom.Comment
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** The kind of one resource entry in a `strings.xml` file. */
internal enum class ResourceKind { STRING, PLURALS, ARRAY }

/**
 * One entry of a `strings.xml` file, as an XML parser reads it.
 *
 * [text] is the entry's text with entities decoded. [comment] is the comment directly above the
 * entry, with only white space between the two, or null.
 */
internal data class StringEntry(
    val kind: ResourceKind,
    val name: String,
    val text: String,
    val items: List<String>,
    val comment: String?,
)

/**
 * Reads the string resource files with a real XML parser, so an attribute written as
 * `name ="key"` counts like any other. Each file is parsed once per build and shared by the gates.
 */
internal object StringResources {
    private val cache = ConcurrentHashMap<String, Pair<Long, List<StringEntry>>>()

    fun read(file: File): List<StringEntry> {
        val stamp = file.lastModified()
        cache[file.absolutePath]?.let { (seen, entries) -> if (seen == stamp) return entries }
        val entries = parse(file)
        cache[file.absolutePath] = stamp to entries
        return entries
    }

    /** The names of every string and plural in [file]; arrays are not keys that Lyricist reads one by one. */
    fun keys(file: File): List<String> =
        read(file).filter { it.kind != ResourceKind.ARRAY }.map { it.name }

    private fun parse(file: File): List<StringEntry> {
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
        val document = try {
            factory.newDocumentBuilder().parse(file)
        } catch (e: Exception) {
            throw GradleException("${file.path} is not valid XML: ${e.message}", e)
        }
        val entries = mutableListOf<StringEntry>()
        var comment: String? = null
        val children = document.documentElement.childNodes
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
                        val items = node.childElements().map { it.textContent }
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

    private fun Element.childElements(): List<Element> {
        val out = mutableListOf<Element>()
        for (i in 0 until childNodes.length) (childNodes.item(i) as? Element)?.let { out += it }
        return out
    }
}
