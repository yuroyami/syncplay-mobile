package app.server.tls

import java.io.ByteArrayOutputStream

/**
 * The few DER (the binary encoding of certificates) shapes that a self-signed certificate needs.
 * Each function returns one complete element: its tag, its length and its content.
 */
internal object Der {

    fun sequence(vararg parts: ByteArray): ByteArray = element(0x30, concat(parts))

    fun set(vararg parts: ByteArray): ByteArray = element(0x31, concat(parts))

    /** A non-negative integer from its big-endian bytes, with no leading zero beyond the one DER needs. */
    fun integer(value: ByteArray): ByteArray {
        var start = 0
        while (start < value.size - 1 && value[start] == 0.toByte() && value[start + 1] >= 0) start++
        val trimmed = value.copyOfRange(start, value.size)
        val content = if (trimmed[0] < 0) byteArrayOf(0) + trimmed else trimmed
        return element(0x02, content)
    }

    fun oid(dotted: String): ByteArray {
        val arcs = dotted.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        out.write((arcs[0] * 40 + arcs[1]).toInt())
        for (arc in arcs.drop(2)) {
            val groups = mutableListOf<Int>()
            var rest = arc
            do {
                groups += (rest and 0x7F).toInt()
                rest = rest shr 7
            } while (rest > 0)
            for (i in groups.indices.reversed()) out.write(groups[i] or if (i > 0) 0x80 else 0)
        }
        return element(0x06, out.toByteArray())
    }

    fun utf8String(text: String): ByteArray = element(0x0C, text.encodeToByteArray())

    /** A bit string whose content is whole bytes. */
    fun bitString(bytes: ByteArray): ByteArray = element(0x03, byteArrayOf(0) + bytes)

    /** UTCTime before 2050 and GeneralizedTime from then on, as X.509 requires. [utc] is yyyyMMddHHmmss. */
    fun time(utc: String): ByteArray {
        val year = utc.take(4).toInt()
        return if (year < 2050) element(0x17, (utc.drop(2) + "Z").encodeToByteArray())
        else element(0x18, (utc + "Z").encodeToByteArray())
    }

    /** A context-specific constructed tag around [content], such as the [0] of a certificate version. */
    fun explicit(tag: Int, content: ByteArray): ByteArray = element(0xA0 or tag, content)

    private fun element(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        writeLength(out, content.size)
        out.write(content)
        return out.toByteArray()
    }

    private fun writeLength(out: ByteArrayOutputStream, length: Int) {
        if (length < 0x80) {
            out.write(length)
            return
        }
        val bytes = mutableListOf<Int>()
        var rest = length
        while (rest > 0) {
            bytes += rest and 0xFF
            rest = rest shr 8
        }
        out.write(0x80 or bytes.size)
        for (i in bytes.indices.reversed()) out.write(bytes[i])
    }

    private fun concat(parts: Array<out ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        for (part in parts) out.write(part)
        return out.toByteArray()
    }
}
