package app.uicomponents.controls

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The drawn glyph set: a 20dp box, a 1.5dp stroke and flat line ends. Every glyph is a path on a
 * 20 x 20 viewport, so it matches the thin lines of the controls. Most glyphs are stroked; Play,
 * Pause and More are filled.
 */

private fun stroked(name: String, builder: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 20f, viewportHeight = 20f)
        .apply(builder)
        .build()

private fun ImageVector.Builder.line(vararg points: Float) {
    path(stroke = SolidColor(androidx.compose.ui.graphics.Color.Black), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Butt, strokeLineJoin = StrokeJoin.Miter) {
        moveTo(points[0], points[1])
        var i = 2
        while (i + 1 < points.size) { lineTo(points[i], points[i + 1]); i += 2 }
    }
}

private fun ImageVector.Builder.closedFill(vararg points: Float) {
    path(fill = SolidColor(androidx.compose.ui.graphics.Color.Black)) {
        moveTo(points[0], points[1])
        var i = 2
        while (i + 1 < points.size) { lineTo(points[i], points[i + 1]); i += 2 }
        close()
    }
}

/** Two crossing lines. */
val CloseGlyph: ImageVector by lazy {
    stroked("close") { line(5f, 5f, 15f, 15f); line(15f, 5f, 5f, 15f) }
}

/** A left-pointing chevron for back. */
val BackGlyph: ImageVector by lazy {
    stroked("back") { line(12f, 4f, 6f, 10f, 12f, 16f) }
}

/** Play: a solid triangle. */
val PlayGlyph: ImageVector by lazy {
    stroked("play") { closedFill(6f, 4f, 16f, 10f, 6f, 16f) }
}

/** Pause: two 3dp bars. */
val PauseGlyph: ImageVector by lazy {
    stroked("pause") { closedFill(5f, 4f, 8f, 4f, 8f, 16f, 5f, 16f); closedFill(12f, 4f, 15f, 4f, 15f, 16f, 12f, 16f) }
}

/** Search: a circle and a stem. */
val SearchGlyph: ImageVector by lazy {
    stroked("search") {
        path(stroke = SolidColor(androidx.compose.ui.graphics.Color.Black), strokeLineWidth = 1.5f) {
            moveTo(13f, 8.5f)
            arcTo(4.5f, 4.5f, 0f, true, true, 4f, 8.5f)
            arcTo(4.5f, 4.5f, 0f, true, true, 13f, 8.5f)
        }
        line(11.8f, 11.8f, 16.5f, 16.5f)
    }
}

/** Send: a right-pointing arrow. */
val SendGlyph: ImageVector by lazy {
    stroked("send") { line(3f, 10f, 16f, 10f); line(11f, 5f, 16f, 10f, 11f, 15f) }
}

/** A plus. */
val AddGlyph: ImageVector by lazy {
    stroked("add") { line(10f, 4f, 10f, 16f); line(4f, 10f, 16f, 10f) }
}

/** A check mark. */
val CheckGlyph: ImageVector by lazy {
    stroked("check") { line(4f, 10.5f, 8.5f, 15f, 16f, 6f) }
}

/** One flat line, for an answer that is neither yes nor no. */
val DashGlyph: ImageVector by lazy {
    stroked("dash") { line(5f, 10f, 15f, 10f) }
}

/** Three stacked lines: a list, the playlist. */
val ListGlyph: ImageVector by lazy {
    stroked("list") { line(4f, 6f, 16f, 6f); line(4f, 10f, 16f, 10f); line(4f, 14f, 16f, 14f) }
}

/** A cog: eight square teeth around a round hole. */
val SettingsGlyph: ImageVector by lazy {
    stroked("settings") {
        path(stroke = SolidColor(androidx.compose.ui.graphics.Color.Black), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Butt, strokeLineJoin = StrokeJoin.Miter) {
            val teeth = 8
            val rootR = 6.3f
            val tipR = 8.6f
            val step = 2 * PI / teeth
            // Each tooth spans 42% of its slot at the tip and 54% at the root, so its sides lean in a little.
            val tipHalf = step * 0.21
            val rootHalf = step * 0.27
            fun x(a: Double, r: Float) = 10f + r * cos(a).toFloat()
            fun y(a: Double, r: Float) = 10f + r * sin(a).toFloat()
            for (i in 0 until teeth) {
                val a = PI / 8 + i * step
                if (i == 0) moveTo(x(a - rootHalf, rootR), y(a - rootHalf, rootR)) else lineTo(x(a - rootHalf, rootR), y(a - rootHalf, rootR))
                lineTo(x(a - tipHalf, tipR), y(a - tipHalf, tipR))
                lineTo(x(a + tipHalf, tipR), y(a + tipHalf, tipR))
                lineTo(x(a + rootHalf, rootR), y(a + rootHalf, rootR))
                arcTo(rootR, rootR, 0f, false, true, x(a + step - rootHalf, rootR), y(a + step - rootHalf, rootR))
            }
            close()
        }
        path(stroke = SolidColor(androidx.compose.ui.graphics.Color.Black), strokeLineWidth = 1.5f) {
            moveTo(12.6f, 10f)
            arcTo(2.6f, 2.6f, 0f, true, true, 7.4f, 10f)
            arcTo(2.6f, 2.6f, 0f, true, true, 12.6f, 10f)
        }
    }
}

/** Three dots, vertical: more. */
val MoreGlyph: ImageVector by lazy {
    stroked("more") {
        closedFill(9f, 4f, 11f, 4f, 11f, 6f, 9f, 6f)
        closedFill(9f, 9f, 11f, 9f, 11f, 11f, 9f, 11f)
        closedFill(9f, 14f, 11f, 14f, 11f, 16f, 9f, 16f)
    }
}

/** A closed padlock: a body and a shackle that comes all the way down. */
val LockGlyph: ImageVector by lazy {
    stroked("lock") {
        line(5f, 9.5f, 15f, 9.5f, 15f, 16.5f, 5f, 16.5f, 5f, 9.5f)
        path(stroke = SolidColor(androidx.compose.ui.graphics.Color.Black), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Butt, strokeLineJoin = StrokeJoin.Miter) {
            moveTo(7.5f, 9.5f)
            lineTo(7.5f, 7f)
            arcTo(2.5f, 2.5f, 0f, false, true, 12.5f, 7f)
            lineTo(12.5f, 9.5f)
        }
    }
}

/** An open padlock: the same body, the shackle left hanging clear on one side. */
val UnlockGlyph: ImageVector by lazy {
    stroked("unlock") {
        line(5f, 9.5f, 15f, 9.5f, 15f, 16.5f, 5f, 16.5f, 5f, 9.5f)
        path(stroke = SolidColor(androidx.compose.ui.graphics.Color.Black), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Butt, strokeLineJoin = StrokeJoin.Miter) {
            moveTo(7.5f, 9.5f)
            lineTo(7.5f, 7f)
            arcTo(2.5f, 2.5f, 0f, false, true, 12.5f, 7f)
        }
    }
}
