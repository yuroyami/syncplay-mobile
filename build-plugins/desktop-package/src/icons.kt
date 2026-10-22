package io.github.yuroyami.synkplay.buildplugins.desktoppackage

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.writeBytes

/** The installer icons for each OS. */
internal class Icons(val mac: Path, val windows: Path, val linux: Path)

private const val BASE_SIZE = 1024

/** Apple's icon grid corner radius, as a fraction of the side. */
private const val MACOS_CORNER_RATIO = 0.225

/** The ICNS entry types, each with its pixel size. */
private val ICNS_ENTRIES = listOf(
    "ic07" to 128, "ic08" to 256, "ic09" to 512, "ic10" to 1024,
    "ic11" to 32, "ic12" to 64, "ic13" to 256, "ic14" to 512,
)

/** The Windows ICO entry sizes. */
private val ICO_SIZES = listOf(16, 24, 32, 48, 64, 128, 256)

/**
 * Draws the background to cover a square, puts the foreground in the middle at [percent] of each
 * side, and writes `app.icns` (rounded corners), `app.ico` and `app.png` (512 px) into [dir].
 */
internal fun writeIcons(foreground: Path, background: Path, percent: Int, dir: Path): Icons {
    System.setProperty("java.awt.headless", "true")
    require(percent in 1..100) { "logoPercent must be from 1 to 100, found $percent." }
    val fg = checkNotNull(ImageIO.read(foreground.toFile())) { "$foreground is not a PNG or JPEG image." }
    val bg = checkNotNull(ImageIO.read(background.toFile())) { "$background is not a PNG or JPEG image." }
    val base = BufferedImage(BASE_SIZE, BASE_SIZE, BufferedImage.TYPE_INT_RGB).draw {
        color = Color.WHITE
        fillRect(0, 0, BASE_SIZE, BASE_SIZE)
        drawFitted(bg, 0, 0, BASE_SIZE, BASE_SIZE, cover = true)
        val side = (BASE_SIZE * percent / 100).coerceAtLeast(1)
        val inset = (BASE_SIZE - side) / 2
        drawFitted(fg, inset, inset, side, side, cover = false)
    }
    val arc = BASE_SIZE * MACOS_CORNER_RATIO.toFloat() * 2
    val rounded = BufferedImage(BASE_SIZE, BASE_SIZE, BufferedImage.TYPE_INT_ARGB).draw {
        clip = RoundRectangle2D.Float(0f, 0f, BASE_SIZE.toFloat(), BASE_SIZE.toFloat(), arc, arc)
        drawImage(base, 0, 0, null)
    }
    val icons = Icons(dir.resolve("app.icns"), dir.resolve("app.ico"), dir.resolve("app.png"))
    icons.mac.writeBytes(icns(rounded))
    icons.windows.writeBytes(ico(base))
    icons.linux.writeBytes(png(resize(base, 512)))
    return icons
}

private inline fun BufferedImage.draw(block: Graphics2D.() -> Unit): BufferedImage {
    val g = createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.composite = AlphaComposite.SrcOver
        g.block()
    } finally {
        g.dispose()
    }
    return this
}

/** Draws [image] centered in the box, keeping its aspect ratio: covering the box, or inside it. */
private fun Graphics2D.drawFitted(image: BufferedImage, x: Int, y: Int, w: Int, h: Int, cover: Boolean) {
    val ratios = listOf(w.toDouble() / image.width, h.toDouble() / image.height)
    val scale = if (cover) ratios.max() else ratios.min()
    val dw = (image.width * scale).toInt().coerceAtLeast(1)
    val dh = (image.height * scale).toInt().coerceAtLeast(1)
    val previous = clip
    clip = Rectangle(x, y, w, h)
    drawImage(image, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null)
    clip = previous
}

private fun resize(image: BufferedImage, size: Int): BufferedImage =
    if (image.width == size && image.height == size) image
    else BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).draw { drawImage(image, 0, 0, size, size, null) }

private fun png(image: BufferedImage): ByteArray =
    ByteArrayOutputStream().also { check(ImageIO.write(image, "PNG", it)) { "This JDK has no PNG encoder." } }.toByteArray()

private fun bigEndian(value: Int) = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

private fun littleEndian(value: Int, bytes: Int) = ByteArray(bytes) { (value ushr (8 * it)).toByte() }

/** A macOS `.icns` file with PNG entries. */
private fun icns(square: BufferedImage): ByteArray {
    val body = ByteArrayOutputStream()
    for ((type, size) in ICNS_ENTRIES) {
        val payload = png(resize(square, size))
        body.write(type.toByteArray(Charsets.US_ASCII))
        body.write(bigEndian(payload.size + 8))
        body.write(payload)
    }
    val entries = body.toByteArray()
    return ByteArrayOutputStream().apply {
        write("icns".toByteArray(Charsets.US_ASCII))
        write(bigEndian(entries.size + 8))
        write(entries)
    }.toByteArray()
}

/** A Windows `.ico` file with PNG entries. */
private fun ico(square: BufferedImage): ByteArray {
    val payloads = ICO_SIZES.map { png(resize(square, it)) }
    val out = ByteArrayOutputStream()
    out.write(littleEndian(0, 2))
    out.write(littleEndian(1, 2))
    out.write(littleEndian(ICO_SIZES.size, 2))
    var offset = 6 + ICO_SIZES.size * 16
    ICO_SIZES.forEachIndexed { index, size ->
        // A 256 pixel side is stored as 0, as the format asks.
        val stored = if (size == 256) 0 else size
        out.write(byteArrayOf(stored.toByte(), stored.toByte(), 0, 0))
        out.write(littleEndian(1, 2))
        out.write(littleEndian(32, 2))
        out.write(littleEndian(payloads[index].size, 4))
        out.write(littleEndian(offset, 4))
        offset += payloads[index].size
    }
    payloads.forEach(out::write)
    return out.toByteArray()
}
