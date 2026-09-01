package app.uicomponents.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.theme.Space
import app.theme.palette

/** A 1dp full bleed rule. The only separator in the app. */
@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = palette.rule) {
    Box(modifier.fillMaxWidth().height(Space.hair).background(color))
}

/** The vertical twin, for cells in a row. */
@Composable
fun VerticalRule(modifier: Modifier = Modifier, color: Color = palette.rule) {
    Box(modifier.fillMaxHeight().width(Space.hair).background(color))
}
