package app.uicomponents

import app.utils.httpClient
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnimatedImageLoadTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @BeforeTest
    fun start() {
        server.createContext("/declared") { exchange ->
            val body = ByteArray(THREE_MB)
            exchange.sendResponseHeaders(200, body.size.toLong())
            runCatching { exchange.responseBody.use { it.write(body) } }
        }
        // No Content-Length: only the byte count while the body streams can stop this one.
        server.createContext("/streamed") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            runCatching { exchange.responseBody.use { out -> repeat(3) { out.write(ByteArray(ONE_MB)) } } }
        }
        server.createContext("/missing") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
    }

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun aBodyOverTheCapIsDropped() = runBlocking {
        assertNull(httpClient.downloadAtMost("$base/declared", ONE_MB.toLong()), "declared length")
        assertNull(httpClient.downloadAtMost("$base/streamed", ONE_MB.toLong()), "streamed body")
    }

    @Test
    fun aBodyUnderTheCapArrivesWhole() = runBlocking {
        assertEquals(THREE_MB, httpClient.downloadAtMost("$base/streamed", 4L * ONE_MB)?.size)
    }

    @Test
    fun aFailedAnswerIsNoImage() = runBlocking {
        assertNull(httpClient.downloadAtMost("$base/missing", ONE_MB.toLong()))
    }

    @Test
    fun aLargeStillImageDecodesAtTheTileSize() {
        val frame = AnimatedImageCache.decode(png(1200, 800))!!.frames.single()
        assertEquals(768 to 512, frame.bitmap.width to frame.bitmap.height)
    }

    @Test
    fun everyFrameOfALargeAnimationDecodesAtTheTileSize() {
        val animation = AnimatedImageCache.decode(gif(1024, 1024, frames = 3, delayCs = 5))!!
        assertEquals(3, animation.frames.size)
        animation.frames.forEach { assertEquals(512 to 512, it.bitmap.width to it.bitmap.height) }
        assertEquals(50L, animation.frames.first().durationMs)
    }

    @Test
    fun anImageOverTheLimitsIsRefused() {
        assertNull(AnimatedImageCache.decode(png(2500, 2000)))
    }

    private fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()

    /** An animated GIF of [frames] frames, each shown for [delayCs] hundredths of a second. */
    private fun gif(width: Int, height: Int, frames: Int, delayCs: Int): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.prepareWriteSequence(null)
            repeat(frames) { index ->
                val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                image.graphics.apply { color = java.awt.Color(index * 80, 0, 0); fillRect(0, 0, width, height); dispose() }
                val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), null)
                val format = metadata.nativeMetadataFormatName
                val root = metadata.getAsTree(format) as IIOMetadataNode
                val control = IIOMetadataNode("GraphicControlExtension").apply {
                    setAttribute("disposalMethod", "none")
                    setAttribute("userInputFlag", "FALSE")
                    setAttribute("transparentColorFlag", "FALSE")
                    setAttribute("delayTime", delayCs.toString())
                    setAttribute("transparentColorIndex", "0")
                }
                root.appendChild(control)
                metadata.setFromTree(format, root)
                writer.writeToSequence(IIOImage(image, null, metadata), null)
            }
            writer.endWriteSequence()
        }
        return out.toByteArray()
    }

    private companion object {
        const val ONE_MB = 1 shl 20
        const val THREE_MB = 3 shl 20
    }
}
