package io.github.yuroyami.synkplay.buildplugins.appconfig

import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.TaskAction
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory

/** Android density folders and their scale. The adaptive canvas is 108 dp, the legacy icon 48 dp. */
private val DENSITIES = listOf("mdpi" to 1.0, "hdpi" to 1.5, "xhdpi" to 2.0, "xxhdpi" to 3.0, "xxxhdpi" to 4.0)

private const val IOS_SIZE = 1024

/**
 * Draws the app icons from the logo. The background covers the canvas, and the mark sits in the
 * middle at a share of each side.
 *
 * - Android, per density: `ic_launcher_foreground.png` and `ic_launcher_background.png` (the
 *   adaptive layers), `ic_launcher.png` and `ic_launcher_round.png` (the legacy icons). The
 *   adaptive XML files in `mipmap-anydpi-*` stay as they are.
 * - iOS: `AppIcon-1024.png`, opaque.
 */
@TaskAction(ExecutionAvoidance.Disabled)
fun syncAppIcons(
    @Input(inferTaskDependency = false) logoForeground: Path,
    @Input(inferTaskDependency = false) logoBackground: Path,
    androidLogoPercent: Int,
    iosLogoPercent: Int,
    // Not outputs: the Android build reads res/, and it would then run this command first.
    @Input(inferTaskDependency = false) androidResources: Path,
    @Input(inferTaskDependency = false) iosAppIcon: Path,
) {
    System.setProperty("java.awt.headless", "true")
    require(androidLogoPercent in 1..100) { "androidLogoPercent must be from 1 to 100." }
    require(iosLogoPercent in 1..100) { "iosLogoPercent must be from 1 to 100." }
    check(androidResources.isDirectory()) { "$androidResources is not a folder." }
    check(iosAppIcon.isDirectory()) { "$iosAppIcon is not a folder." }
    val fg = checkNotNull(ImageIO.read(logoForeground.toFile())) { "$logoForeground is not a PNG image." }
    val bg = checkNotNull(ImageIO.read(logoBackground.toFile())) { "$logoBackground is not a PNG image." }

    var written = 0
    fun write(image: BufferedImage, file: Path) {
        file.parent.createDirectories()
        check(ImageIO.write(image, "PNG", file.toFile())) { "This JDK has no PNG encoder." }
        written++
    }
    for ((qualifier, scale) in DENSITIES) {
        val dir = androidResources.resolve("mipmap-$qualifier")
        val adaptive = (108 * scale).toInt()
        val legacy = (48 * scale).toInt()
        val side = (adaptive * androidLogoPercent / 100).coerceAtLeast(1)
        val offset = (adaptive - side) / 2
        write(argb(adaptive).draw { drawFitted(fg, offset, offset, side, side, cover = false) }, dir.resolve("ic_launcher_foreground.png"))
        write(
            BufferedImage(adaptive, adaptive, BufferedImage.TYPE_INT_RGB).draw {
                color = Color.WHITE
                fillRect(0, 0, adaptive, adaptive)
                drawFitted(bg, 0, 0, adaptive, adaptive, cover = true)
            },
            dir.resolve("ic_launcher_background.png"),
        )
        val square = argb(legacy).draw {
            drawFitted(bg, 0, 0, legacy, legacy, cover = true)
            drawFitted(fg, 0, 0, legacy, legacy, cover = false)
        }
        write(square, dir.resolve("ic_launcher.png"))
        write(
            argb(legacy).draw {
                clip = Ellipse2D.Float(0f, 0f, legacy.toFloat(), legacy.toFloat())
                drawImage(square, 0, 0, null)
            },
            dir.resolve("ic_launcher_round.png"),
        )
    }
    val iosSide = (IOS_SIZE * iosLogoPercent / 100).coerceAtLeast(1)
    val iosInset = (IOS_SIZE - iosSide) / 2
    write(
        BufferedImage(IOS_SIZE, IOS_SIZE, BufferedImage.TYPE_INT_RGB).draw {
            // Any transparency in the background ends up on white.
            color = Color.WHITE
            fillRect(0, 0, IOS_SIZE, IOS_SIZE)
            drawFitted(bg, 0, 0, IOS_SIZE, IOS_SIZE, cover = true)
            drawFitted(fg, iosInset, iosInset, iosSide, iosSide, cover = false)
        },
        iosAppIcon.resolve("AppIcon-1024.png"),
    )
    println("Wrote $written icon files from ${logoForeground.fileName} and ${logoBackground.fileName}.")
}

private fun argb(size: Int) = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)

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

/** Draws [image] centered in the box: scaled to fill it ([cover]) or to fit inside it. */
private fun Graphics2D.drawFitted(image: BufferedImage, x: Int, y: Int, w: Int, h: Int, cover: Boolean) {
    val ratio = if (cover) maxOf(w.toDouble() / image.width, h.toDouble() / image.height) else minOf(w.toDouble() / image.width, h.toDouble() / image.height)
    val dw = (image.width * ratio).toInt().coerceAtLeast(1)
    val dh = (image.height * ratio).toInt().coerceAtLeast(1)
    val previous = clip
    if (cover) clip = Rectangle(x, y, w, h)
    drawImage(image, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null)
    clip = previous
}
