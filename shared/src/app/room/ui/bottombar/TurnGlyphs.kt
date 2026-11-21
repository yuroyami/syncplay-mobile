package app.room.ui.bottombar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/*
 * The glyphs of the full-screen key on phones and tablets. Each one shows both views of the room:
 * the view that the room is in now as a faint outline, and the view that the key turns to as a
 * solid phone, with an arrow from one to the other. They use a 24 x 24 viewport, like the Material
 * glyphs of the other room keys. The tint keeps the alpha of the faint outline.
 */

/** The alpha of the outline of the view that the room is in now. */
private const val FAINT = 0.42f

private val Ink = SolidColor(Color.Black)

private fun glyph(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply(block)
        .build()

/** A rounded rectangle, clockwise from its top left corner. */
private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    lineTo(x + w - r, y)
    arcTo(r, r, 0f, false, true, x + w, y + r)
    lineTo(x + w, y + h - r)
    arcTo(r, r, 0f, false, true, x + w - r, y + h)
    lineTo(x + r, y + h)
    arcTo(r, r, 0f, false, true, x, y + h - r)
    lineTo(x, y + r)
    arcTo(r, r, 0f, false, true, x + r, y)
    close()
}

/** The view that the room is in now: a faint outline. */
private fun ImageVector.Builder.outline(x: Float, y: Float, w: Float, h: Float) {
    path(stroke = Ink, strokeAlpha = FAINT, strokeLineWidth = 2f) { roundRect(x, y, w, h, 1.8f) }
}

/** The view that the key turns to: a solid phone with its screen cut out. The short ends are thicker, as on a phone. */
private fun ImageVector.Builder.phone(x: Float, y: Float, w: Float, h: Float) {
    path(fill = Ink, pathFillType = PathFillType.EvenOdd) {
        roundRect(x, y, w, h, 2f)
        if (h >= w) roundRect(x + 1.7f, y + 2.9f, w - 3.4f, h - 5.8f, 0.5f)
        else roundRect(x + 2.9f, y + 1.7f, w - 5.8f, h - 3.4f, 0.5f)
    }
}

/** The arrow: the stroked [curve], and a solid head that points down with its tip at [tipX], [tipY]. */
private fun ImageVector.Builder.arrow(tipX: Float, tipY: Float, curve: PathBuilder.() -> Unit) {
    path(stroke = Ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = curve)
    path(fill = Ink, stroke = Ink, strokeLineWidth = 0.8f, strokeLineJoin = StrokeJoin.Round) {
        moveTo(tipX, tipY)
        lineTo(tipX + 2.5f, tipY - 3.6f)
        lineTo(tipX - 2.5f, tipY - 3.6f)
        close()
    }
}

/** The key of the portrait room: a faint upright phone, and an arrow to a solid phone on its side. */
val TurnToLandscapeGlyph: ImageVector by lazy {
    glyph("turn-to-landscape") {
        outline(2f, 9f, 7f, 12f)
        phone(10.5f, 13f, 12f, 8f)
        arrow(tipX = 16.5f, tipY = 11.2f) {
            moveTo(5.5f, 6.6f)
            curveTo(6f, 1.6f, 16.5f, 1.6f, 16.5f, 7.6f)
        }
    }
}

/** The key of the landscape room: a faint phone on its side, and an arrow to a solid upright phone. */
val TurnToPortraitGlyph: ImageVector by lazy {
    glyph("turn-to-portrait") {
        outline(2f, 14f, 11f, 7f)
        phone(14.5f, 8f, 8f, 13f)
        arrow(tipX = 18.5f, tipY = 7.3f) {
            moveTo(7f, 11.6f)
            curveTo(7f, 5f, 10f, 2.2f, 13.5f, 2.2f)
            curveTo(16.6f, 2.2f, 18.5f, 2.8f, 18.5f, 3.7f)
        }
    }
}
